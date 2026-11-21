package com.kitsuneapp.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

internal data class GitHubTarget(val owner: String, val repository: String, val branch: String)
internal data class RepositoryInfo(val id: Long, val sizeKb: Long)
internal data class RemoteBackup(val ref: String, val sha: String, val size: Long, val base64: String?)
internal data class RemoteVersion(val repo: RepositoryInfo, val backup: RemoteBackup?) {
    val condition get() = backup?.sha.orEmpty()
}

internal class GitHubFailure(val status: Int, val waitMs: Long?, text: String) : IOException(text)
internal class WifiUnavailable : IOException("等待连接到 Wi-Fi")

internal class GitHubClient(
    private val target: GitHubTarget,
    private val token: String,
    private val calls: Call.Factory,
    private val endpoint: HttpUrl,
    private val checkNetwork: () -> Unit,
) {
    private val repoPath = arrayOf("repos", target.owner, target.repository)
    private val gson = Gson()

    constructor(context: Context, target: GitHubTarget, token: String, wifiOnly: Boolean) : this(
        target, token, connection(context, wifiOnly),
        "https://api.github.com/".toHttpUrl(), { verifyWifi(context, wifiOnly) },
    )

    suspend fun repository(): RepositoryInfo = get(url()) { response ->
        val json = response.objectBody()
        require(json.get("private")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true) {
            "GitHub 备份只支持私有仓库"
        }
        RepositoryInfo(json.get("id").asLong, json.get("size").asLong.also { require(it >= 0) { "GitHub 仓库容量无效" } })
    }

    suspend fun version(): RemoteVersion {
        val repository = repository()
        val branch = get(url("branches", target.branch)) { it.objectBody() }.getAsJsonObject("commit")
        val sha = branch.get("sha").asString.checkGitSha()
        val file = get(url("contents", "backups", "latest.json", ref = sha), CONTENT_JSON, allowMissing = true) { response ->
            if (response.code == 404) null else {
                val json = response.objectBody()
                require(json.get("type").asString == "file") { "远端备份路径不是普通文件" }
                val length = json.get("size").asLong
                require(length >= 0) { "远端备份大小无效" }
                RemoteBackup(sha, json.get("sha").asString.checkGitSha(), length,
                    json.get("content")?.takeIf { length <= 1024 * 1024 && json.get("encoding")?.asString == "base64" }?.asString)
            }
        }
        if (file == null) {
            val root = branch.getAsJsonObject("commit").getAsJsonObject("tree").get("sha").asString.checkGitSha()
            val folder = treeItem(root, "backups")
            if (folder != null) {
                require(folder.get("type").asString == "tree") { "GitHub backups 路径不是目录" }
                require(treeItem(folder.get("sha").asString.checkGitSha(), "latest.json") == null) {
                    "备份文件存在但无法读取，请检查权限"
                }
            }
        }
        return RemoteVersion(repository, file)
    }

    private suspend fun treeItem(sha: String, path: String): JsonObject? = get(url("git", "trees", sha)) {
        val tree = it.objectBody()
        require(tree.get("truncated")?.asBoolean == false) { "无法可靠确认远端路径是否存在" }
        tree.getAsJsonArray("tree").firstOrNull { item -> item.asJsonObject.get("path").asString == path }?.asJsonObject
    }

    suspend fun download(remote: RemoteBackup, file: File) {
        require(remote.size <= CLOUD_LIMIT) { "远端快照超过 32 MiB，请下载后从本地恢复" }
        val read = if (remote.base64 != null) {
            val bytes = Base64.getDecoder().decode(remote.base64.replace("\n", "").replace("\r", ""))
            require(bytes.size.toLong() == remote.size) { "GitHub 备份大小不匹配" }
            withContext(Dispatchers.IO) { file.writeBytes(bytes) }
            bytes.size.toLong()
        } else get(url("contents", "backups", "latest.json", ref = remote.ref), "application/vnd.github.raw+json") { response ->
            response.body?.byteStream()?.use { from ->
                file.outputStream().use { to -> copyChecked(from, to, minOf(remote.size, CLOUD_LIMIT)) }
            } ?: error("GitHub 没有返回备份文件")
        }
        require(read == remote.size && file.gitSha() == remote.sha) { "GitHub 备份大小或 SHA 校验失败" }
    }

    suspend fun upload(file: File, previous: String): String {
        require(file.length() <= CLOUD_LIMIT) { "快照超过 32 MiB，请使用本地文件备份" }
        val prefix = gson.toJson(buildMap {
            put("message", "Update Kitsune backup")
            put("branch", target.branch)
            if (previous.isNotEmpty()) put("sha", previous)
        }).dropLast(1) + ",\"content\":\""
        val job = kotlinx.coroutines.currentCoroutineContext().job
        val body = object : RequestBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = prefix.encodeToByteArray().size + (file.length() + 2) / 3 * 4 + 2

            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8(prefix)
                val shield = object : FilterOutputStream(sink.outputStream()) {
                    override fun write(buffer: ByteArray, offset: Int, size: Int) = out.write(buffer, offset, size)
                    override fun close() = flush()
                }
                Base64.getEncoder().wrap(shield).use { encoded ->
                    file.inputStream().use { from ->
                        val buffer = ByteArray(IO_BUFFER)
                        while (true) {
                            if (!job.isActive) throw IOException("Backup interrupted")
                            val read = from.read(buffer)
                            if (read < 0) break
                            encoded.write(buffer, 0, read)
                        }
                    }
                }
                sink.writeUtf8("\"}")
            }
        }
        return request(builder(url("contents", "backups", "latest.json"), CONTENT_JSON).put(body).build()) {
            it.objectBody().getAsJsonObject("content").get("sha").asString.checkGitSha()
        }
    }

    private fun url(vararg parts: String, ref: String? = null): HttpUrl = endpoint.newBuilder().apply {
        (repoPath.asList() + parts).forEach { addPathSegment(it) }
        if (ref != null) addQueryParameter("ref", ref)
    }.build()

    private fun builder(url: HttpUrl, media: String) = Request.Builder().url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", media)
        .header("X-GitHub-Api-Version", "2022-11-28")

    private suspend fun <T> get(url: HttpUrl, media: String = CONTENT_JSON, allowMissing: Boolean = false,
                                parse: suspend (Response) -> T): T =
        request(builder(url, media).build(), allowMissing, parse)

    private suspend fun <T> request(request: Request, allowMissing: Boolean = false, parse: suspend (Response) -> T): T =
        coroutineScope {
            checkNetwork()
            val call = calls.newCall(request)
            val cleanup = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                withContext(Dispatchers.IO) {
                    call.execute().use { response ->
                        if (!response.isSuccessful && !(allowMissing && response.code == 404)) throw response.failure()
                        parse(response)
                    }
                }
            } finally {
                cleanup.cancel()
            }
        }

    private fun Response.objectBody(): JsonObject {
        val stream = body?.byteStream() ?: error("GitHub 响应为空")
        val data = stream.use { it.readSmall(3 * 1024 * 1024) }
        return JsonParser.parseString(data.decodeToString()).asJsonObject
    }

    private fun Response.failure(): GitHubFailure {
        val raw = runCatching { body?.byteStream()?.use { it.readSmall(16 * 1024).decodeToString() } }.getOrNull().orEmpty()
        val limited = code == 429 || code in listOf(403, 422) && (
            header("Retry-After") != null || header("X-RateLimit-Remaining") == "0" || raw.contains("rate limit", true))
        if (limited) return GitHubFailure(code, rateLimitDelay(header("Retry-After"), header("X-RateLimit-Remaining"),
            header("X-RateLimit-Reset"), header("Date")), "GitHub 请求达到频率限制")
        return when (code) {
            401 -> GitHubFailure(code, null, "GitHub Token 已失效，请重新配置")
            403 -> GitHubFailure(code, null, "GitHub 权限不足，请检查 Contents 写权限或分支规则")
            404 -> GitHubFailure(code, null, "GitHub 仓库、分支或路径不可访问，请检查配置")
            409 -> GitHubFailure(code, 60_000, "GitHub 版本发生并发变化，正在重新核对")
            422 -> GitHubFailure(code, if (raw.contains("sha", true) || raw.contains("already exists", true)) 60_000 else null,
                "GitHub 无法应用备份写入，请检查目标")
            else -> GitHubFailure(code, if (code >= 500) 60_000 else null, "GitHub 请求失败（HTTP $code）")
        }
    }

    private companion object {
        const val CONTENT_JSON = "application/vnd.github.object+json"
        val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS).callTimeout(150, TimeUnit.SECONDS).build()

        fun connection(context: Context, wifi: Boolean): Call.Factory {
            if (!wifi) return http
            val manager = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
            val network = manager.activeNetwork ?: throw WifiUnavailable()
            verifyWifi(context, true)
            return http.newBuilder().socketFactory(network.socketFactory)
                .dns(object : Dns {
                    override fun lookup(hostname: String) = network.getAllByName(hostname).toList()
                }).build()
        }

        fun verifyWifi(context: Context, wifi: Boolean) {
            if (!wifi) return
            val manager = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
            val current = manager.activeNetwork ?: throw WifiUnavailable()
            val capabilities = manager.getNetworkCapabilities(current) ?: throw WifiUnavailable()
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) throw WifiUnavailable()
        }
    }
}

private fun String.checkGitSha(): String = also { require(matches(Regex("[0-9a-f]{40}"))) { "GitHub 返回无效的版本摘要" } }

internal suspend fun File.gitSha(): String = withContext(Dispatchers.IO) {
    inputStream().use { digest(it, "SHA-1", ("blob " + length() + "\u0000").encodeToByteArray()) }
}

internal fun rateLimitDelay(retry: String?, remaining: String?, reset: String?, date: String?): Long {
    fun timestamp(text: String?) = text?.let {
        runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().epochSecond }.getOrNull()
    }
    val now = timestamp(date) ?: Instant.now().epochSecond
    val retrySeconds = retry?.toLongOrNull() ?: timestamp(retry)?.minus(now) ?: 0L
    val resetSeconds = if (remaining == "0") (reset?.toLongOrNull() ?: now) - now else 0L
    return maxOf(60L, retrySeconds, resetSeconds).coerceAtMost(Long.MAX_VALUE / 1000) * 1000
}
