package com.kitsuneapp.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.google.gson.Gson
import com.kitsuneapp.data.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal const val IO_BUFFER = 32 * 1024
internal const val CLOUD_LIMIT = 32L * 1024 * 1024

internal data class Publication(
    val name: String,
    val datasetId: String,
    val snapshotId: String,
    val sha256: String,
    val size: Long,
)

internal data class Document(val name: String, val uri: Uri)
internal enum class FolderCleanup { COMPLETE, MISSING_PUBLICATION, RETRY }

internal class FolderBackup(private val context: Context, tree: Uri) {
    private val resolver = context.contentResolver
    private val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    private val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    private val gson = Gson()

    fun list(): List<Document> {
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = resolver.query(children, columns, null, null, null) ?: error("无法读取备份目录，请重新授权")
        return cursor.use {
            buildList {
                while (it.moveToNext()) add(Document(it.getString(1), DocumentsContract.buildDocumentUriUsingTree(parent, it.getString(0))))
            }
        }
    }

    suspend fun publish(file: File, info: SnapshotInfo, beforePublish: suspend () -> Unit): Publication = withContext(Dispatchers.IO) {
        val sha = file.inputStream().use { digest(it) }
        val docs = list()
        val time = OffsetDateTime.parse(info.exportedAt).toInstant().truncatedTo(ChronoUnit.SECONDS)
        val existingUris = docs.mapTo(HashSet()) { it.uri }
        var sequence = nextAutoBackupSequence(docs.asSequence().map(Document::name), time)
        while (true) {
            currentCoroutineContext().ensureActive()
            val name = AutoBackupName(time, sequence).filename()
            val dest = create("application/json", name)
            if (dest.name != name || dest.uri in existingUris) {
                if (dest.uri !in existingUris) remove(dest)
                sequence = Math.addExact(sequence, 1L)
                continue
            }
            val marker = Publication(name, info.datasetId, info.snapshotId, sha, file.length())
            try {
                resolver.verifiedCopy(file, dest.uri, sha)
                beforePublish()
                val done = create("application/octet-stream", "$name.ok")
                val bytes = gson.toJson(marker).encodeToByteArray()
                try {
                    require(done.name == "$name.ok") { "备份发布标记重名，请重试" }
                    resolver.output(done.uri).use { it.write(bytes) }
                    require(resolver.input(done.uri).use { it.readSmall(4096).contentEquals(bytes) }) { "备份发布标记校验失败" }
                } catch (error: Throwable) {
                    remove(done)
                    throw error
                }
                return@withContext marker
            } catch (error: Throwable) {
                remove(dest)
                throw error
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("无法分配自动备份文件名")
    }

    suspend fun cleanup(keep: Publication, store: SnapshotStore): FolderCleanup = withContext(Dispatchers.IO) {
        val current = published()
        val keepCandidate = current.firstOrNull { it.first == keep } ?: return@withContext FolderCleanup.MISSING_PUBLICATION
        val verified = open(keepCandidate.first, keepCandidate.second, store)
        verified.close()
        var cleaned = true
        for ((marker, data, markerFile) in current) {
            currentCoroutineContext().ensureActive()
            if (marker.datasetId != keep.datasetId || marker == keep) continue
            val valid = try {
                open(marker, data, store).use { true }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (valid && (!remove(data) || !remove(markerFile))) cleaned = false
        }
        if (cleaned) FolderCleanup.COMPLETE else FolderCleanup.RETRY
    }

    fun published(): List<Triple<Publication, Document, Document>> {
        val docs = list()
        val data = docs.filter { it.name.startsWith("kitsune-auto-") && it.name.endsWith(".json") }.groupBy(Document::name)
        return docs.mapNotNull { marker ->
            if (!marker.name.endsWith(".json.ok")) return@mapNotNull null
            val name = marker.name.removeSuffix(".ok")
            val order = AutoBackupName.parse(name) ?: return@mapNotNull null
            val file = data[name]?.singleOrNull() ?: return@mapNotNull null
            val parsed = runCatching {
                resolver.input(marker.uri).use { gson.fromJson(it.readSmall(4096).decodeToString(), Publication::class.java) }
            }.getOrNull() ?: return@mapNotNull null
            if (parsed.name == file.name && parsed.size > 0 && (parsed.sha256 as String?)?.matches(Regex("[0-9a-f]{64}")) == true) {
                order to Triple(parsed, file, marker)
            } else null
        }.sortedByDescending { it.first }.map { it.second }
    }

    suspend fun open(marker: Publication, document: Document, store: SnapshotStore): StagedSnapshot = withContext(Dispatchers.IO) {
        val file = File.createTempFile("backup-check-", ".json", context.cacheDir)
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val size = resolver.input(document.uri).use { input ->
                file.outputStream().use { output -> copyChecked(input, output, marker.size, md) }
            }
            require(size == marker.size && md.digest().hex() == marker.sha256) { "文件夹备份摘要不匹配" }
            val staged = file.inputStream().use { store.stage(it) }
            if (staged.info.kind != BackupKind.AUTO || staged.info.datasetId != marker.datasetId || staged.info.snapshotId != marker.snapshotId) {
                staged.close()
                error("文件夹备份身份不匹配")
            }
            staged
        } finally {
            file.delete()
        }
    }

    suspend fun protect(file: File, info: SnapshotInfo) = withContext(Dispatchers.IO) {
        val time = backupFilenameTime(OffsetDateTime.parse(info.exportedAt).toInstant())
        val doc = create("application/json", "kitsune-pre-restore-$time-${info.snapshotId}.json")
        try {
            resolver.verifiedCopy(file, doc.uri)
        } catch (error: Throwable) {
            remove(doc)
            throw error
        }
    }

    private fun create(mime: String, name: String): Document {
        val uri = DocumentsContract.createDocument(resolver, parent, mime, name) ?: error("备份目录无法创建文件")
        val column = DocumentsContract.Document.COLUMN_DISPLAY_NAME
        val actual = resolver.query(uri, arrayOf(column), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: error("无法确认已创建的备份文件")
        return Document(actual, uri)
    }

    private fun remove(item: Document): Boolean = runCatching { DocumentsContract.deleteDocument(resolver, item.uri) }.getOrDefault(false)
}

internal fun ContentResolver.input(uri: Uri): InputStream = openInputStream(uri) ?: error("无法打开所选文件")
internal fun ContentResolver.output(uri: Uri): OutputStream = openOutputStream(uri, "wt") ?: error("无法写入所选文件")

internal suspend fun ContentResolver.verifiedCopy(file: File, uri: Uri, expected: String? = null) {
    val expectedDigest = expected ?: file.inputStream().use { digest(it) }
    file.inputStream().use { from -> output(uri).use { to -> copyChecked(from, to) } }
    require(input(uri).use { digest(it) } == expectedDigest) { "备份写入后校验失败" }
}

internal suspend fun digest(input: InputStream, algorithm: String = "SHA-256", prefix: ByteArray? = null): String {
    val digest = MessageDigest.getInstance(algorithm)
    if (prefix != null) digest.update(prefix)
    copyChecked(input, null, Long.MAX_VALUE, digest)
    return digest.digest().hex()
}

internal suspend fun copyChecked(input: InputStream, output: OutputStream?, maximum: Long = Long.MAX_VALUE,
                                 digest: MessageDigest? = null): Long {
    val buffer = ByteArray(IO_BUFFER)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= maximum) { "备份文件大小超过允许范围" }
        output?.write(buffer, 0, read)
        digest?.update(buffer, 0, read)
    }
    return total
}

internal fun InputStream.readSmall(maximum: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(minOf(maximum, 8192))
    while (true) {
        val size = read(buffer)
        if (size < 0) return out.toByteArray()
        require(out.size() + size <= maximum) { "响应或标记内容过长" }
        out.write(buffer, 0, size)
    }
}
