package com.kitsuneapp.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.kitsuneapp.KitsuneApplication
import com.kitsuneapp.data.BackupKind
import com.kitsuneapp.data.DataState
import com.kitsuneapp.data.OwnershipException
import com.kitsuneapp.data.Repository
import com.kitsuneapp.data.SnapshotStore
import com.kitsuneapp.data.StagedSnapshot
import com.kitsuneapp.data.utcNow
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class GitHubPreview(
    val target: GitHubTarget,
    val repositoryId: Long,
    val binding: String,
    val datasetId: String,
    val generation: Long,
    val condition: String,
    val stage: StagedSnapshot?,
    val pause: GitHubPause?,
) : Closeable {
    override fun close() { stage?.close() }
}

internal class GitHubPreviewChanged(message: String) : IllegalStateException(message)

private const val MINUTE = 60_000L
private const val WRITE_INTERVAL = 30 * MINUTE
private const val MAX_FAILURES = 6
private const val WORK_BUDGET = 8 * MINUTE

internal class GitHubBackup(
    private val context: Context,
    private val repository: Repository,
    private val snapshots: SnapshotStore,
    private val publication: Mutex,
    private val createClient: (GitHubTarget, String, Boolean) -> GitHubClient = { target, token, wifi ->
        GitHubClient(context, target, token, wifi)
    },
) {
    private val prefs = context.getSharedPreferences("github-target", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val clock = BackupClock(context)
    private val scheduleLock = Mutex()
    private val mutableState = MutableStateFlow(runCatching {
        gson.fromJson(prefs.getString("target", null), GitHubState::class.java)
    }.getOrNull())
    val state = mutableState.asStateFlow()
    private val mutableRunning = MutableStateFlow(false)
    val running = mutableRunning.asStateFlow()
    private val network = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
    fun wifiAvailable() = network.activeWifi() != null
    fun retryDelay(gate: DelayGate?) = clock.refreshed(gate)?.remaining(clock.now) ?: 0L

    val wifiChanges = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.isBackupWifi())
            }
        }
        network.registerDefaultNetworkCallback(callback)
        trySend(wifiAvailable())
        awaitClose { network.unregisterNetworkCallback(callback) }
    }

    @Synchronized
    private fun persist(data: GitHubState) {
        val edit = prefs.edit().putString("target", gson.toJson(data))
        data.lastWrite?.let { edit.putString(targetKey(data), gson.toJson(it)) }
        check(edit.commit()) { "无法保存 GitHub 备份状态" }
        mutableState.value = data
    }

    private fun targetKey(state: GitHubState): String =
        "time:" + state.installation + ":" + state.repositoryId + ":" + state.target.branch

    suspend fun connect(ownerRepo: String, branch: String, token: String, wifi: Boolean) = withContext(Dispatchers.IO) {
        publication.withLock {
            val local = repository.ownedState()
            val parts = ownerRepo.trim().split("/")
            require(parts.size == 2 && parts.all { it.matches(Regex("[A-Za-z0-9_.-]{1,100}")) && it !in listOf(".", "..") }) {
                "请输入 owner/repository"
            }
            require(branch.isNotBlank() && branch.length <= 255 && branch.none { it.isISOControl() }) { "请输入有效分支" }
            val safeToken = token.trim()
            require(safeToken.startsWith("github_pat_") && safeToken.all { it.isLetterOrDigit() || it == '_' }) { "请输入细粒度访问令牌" }
            val target = GitHubTarget(parts[0], parts[1], branch.trim())
            val remote = createClient(target, safeToken, wifi).version()
            val previous = state.value
            val sameRepo = previous?.installation == local.installationId && previous.repositoryId == remote.repo.id
            val sameTarget = sameRepo && previous?.target?.branch == target.branch
            val configuration = GitHubState(local.installationId, target, remote.repo.id,
                BackupSecret(local.installationId).encrypt(safeToken), wifi, local.generation,
                succeededAt = previous?.takeIf { sameTarget && it.belongsTo(local) }?.succeededAt,
                attempts = if (sameTarget) previous?.attempts.orEmpty() else emptyList(),
                retry = if (sameTarget) previous?.retry else null,
                capacity = if (sameRepo) checkNotNull(previous).capacity else Capacity(thresholdMb = previous?.capacity?.thresholdMb ?: 800))
            val lastWrite = gson.fromJson(prefs.getString(targetKey(configuration), null), DelayGate::class.java)
            persist(configuration.copy(lastWrite = lastWrite))
        }
    }

    suspend fun setThreshold(raw: String) = withContext(Dispatchers.IO) {
        publication.withLock {
            val local = repository.ownedState()
            val config = ownerConfig(local)
            persist(config.copy(capacity = config.capacity.setThreshold(raw)))
        }
    }

    suspend fun setWifi(binding: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        publication.withLock {
            val config = ownerConfig(repository.ownedState())
            check(config.binding == binding) { "GitHub 连接已更改，请重试" }
            persist(config.copy(wifiOnly = enabled))
        }
    }

    suspend fun acknowledgePause(id: String) = withContext(Dispatchers.IO) {
        publication.withLock {
            val local = repository.ownedState()
            val config = state.value ?: return@withLock
            if (config.pause(local)?.id == id) persist(config.copy(acknowledgedPause = id))
        }
    }

    suspend fun estimateBytes(): Long = snapshots.measure()

    suspend fun dismissCapacity(repositoryId: Long, checkedSize: Long) = withContext(Dispatchers.IO) {
        publication.withLock {
            val config = ownerConfig(repository.ownedState())
            if (config.repositoryId != repositoryId) return@withLock
            val updated = config.capacity.copy(shownKb = checkedSize, pending = false).rejudge()
            persist(config.copy(capacity = updated))
        }
    }

    private fun client(configuration: GitHubState): GitHubClient {
        val token = try {
            BackupSecret(configuration.installation).decrypt(configuration.encryptedToken)
        } catch (_: Exception) {
            throw GitHubConnectionInvalid("GitHub 凭据需重新配置")
        }
        return createClient(configuration.target, token, configuration.wifiOnly)
    }

    private fun ownerConfig(local: DataState): GitHubState {
        return requireNotNull(state.value?.takeIf { it.belongsTo(local) }) { "请先重新配置 GitHub 备份" }
    }

    suspend fun preview(resolvePause: Boolean): GitHubPreview = withContext(Dispatchers.IO) {
        publication.withLock {
            val local = repository.ownedState()
            val config = validateRetry(ownerConfig(local))
            val pause = if (resolvePause) config.pause(local)?.takeUnless { it.reason == GitHubPauseReason.CONNECTION }
                ?: throw GitHubPreviewChanged("备份暂停状态已更改，请重新核对") else null
            withUserFailure(config) {
                val client = client(config)
                val remote = client.verifiedVersion(config)
                val stage = remote.backup?.let { download(client, it) { file -> file.inputStream().use { snapshots.stage(it) } } }
                GitHubPreview(config.target, config.repositoryId, config.binding, local.datasetId, local.generation,
                    remote.condition, stage, pause)
            }
        }
    }

    suspend fun confirm(preview: GitHubPreview) {
        withContext(Dispatchers.IO) {
            publication.withLock {
                val current = repository.ownedState()
                val config = validateRetry(ownerConfig(current))
                verifyPreview(config, current, preview)
                if (preview.pause == null || preview.pause.id != config.pause(current)?.id)
                    throw GitHubPreviewChanged("备份暂停状态已更改，请重新核对")
                val remote = withUserFailure(config) { client(config).verifiedVersion(config) }
                if (remote.condition != preview.condition) {
                    persist(config.withPause(GitHubPauseReason.CONFLICT))
                    throw GitHubPreviewChanged("远端备份已更改，请重新核对")
                }
                val paused = if (preview.pause.reason == GitHubPauseReason.RESTORED)
                    config.withPause(GitHubPauseReason.RESTORED) else config
                persist(paused.copy(generation = current.generation, consent = preview.condition, bypassFrequency = true,
                    lastRevision = if (config.generation == current.generation) config.lastRevision else -1,
                    failures = 0, permanent = false, failedRevision = -1, status = null))
            }
        }
        schedule(manual = true)
    }

    internal suspend fun checkRestore(preview: GitHubPreview): GitHubState {
        val local = repository.ownedState()
        val config = validateRetry(ownerConfig(local))
        verifyPreview(config, local, preview)
        val remote = withUserFailure(config) { client(config).verifiedVersion(config) }
        if (remote.condition != preview.condition || preview.stage == null)
            throw GitHubPreviewChanged("远端备份已更改，请重新核对")
        return config
    }

    internal suspend fun restoredCloud(config: GitHubState, preview: GitHubPreview) {
        val local = repository.ownedState()
        persist(config.withPause(GitHubPauseReason.RESTORED).copy(generation = local.generation, accepted = preview.condition,
            lastRevision = -1, pending = null, permanent = false, status = null))
    }

    private suspend fun <T> withUserFailure(config: GitHubState, operation: suspend () -> T): T {
        return try {
            operation()
        } catch (failure: GitHubFailure) {
            if (failure.waitMs != null) persist(config.copy(retry = clock.after(failure.waitMs)))
            if (failure.requiresConnection) persist(config.withPause(GitHubPauseReason.CONNECTION, failure.message.orEmpty()))
            throw failure
        } catch (failure: GitHubConnectionInvalid) {
            persist(config.withPause(GitHubPauseReason.CONNECTION, failure.message.orEmpty()))
            throw failure
        }
    }

    private fun verifyPreview(config: GitHubState, local: DataState, preview: GitHubPreview) {
        if (config.target != preview.target || config.repositoryId != preview.repositoryId || config.binding != preview.binding ||
            local.datasetId != preview.datasetId || local.generation != preview.generation)
            throw GitHubPreviewChanged("GitHub 目标或本机数据集已更改，请重新核对")
    }

    private data class ScheduledGitHub(val local: DataState, val config: GitHubState, val delay: Long)

    suspend fun schedule(manual: Boolean = false) = withContext(Dispatchers.IO) {
        scheduleLock.withLock {
            val observed = clock.after(if (manual) 0 else MINUTE)
            val plan = publication.withLock plan@ {
                val local = repository.ownedState()
                val config = try { ownerConfig(local) } catch (_: IllegalArgumentException) { return@plan null }
                if (config.generation != local.generation || (config.paused != null && config.consent == null)) return@plan null
                if (config.permanent && config.failedRevision == local.revision && !manual) return@plan null
                val needsWrite = config.lastRevision != local.revision || config.consent != null || config.attempts.isNotEmpty()
                if (!needsWrite && !manual) return@plan null
                val timed = normalizeGates(config.copy(pending = config.pending ?: observed), manual)
                persist(timed)
                ScheduledGitHub(local, timed, neededDelay(timed, manual))
            } ?: return@withLock
            val local = plan.local
            val config = plan.config
            val wait = plan.delay
            val manager = WorkManager.getInstance(context)
            val name = "kitsune-github-" + local.installationId + "-" + config.binding + "-" + local.generation
            val jobs = manager.getWorkInfosForUniqueWork(name).get()
            val running = jobs.any { it.state == WorkInfo.State.RUNNING }
            val pending = jobs.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            if (pending && (!manual || running)) return@withLock
            val task = OneTimeWorkRequestBuilder<GitHubWorker>()
                .setInputData(androidx.work.workDataOf("manual" to manual, "binding" to config.binding, "generation" to local.generation))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(wait, TimeUnit.MILLISECONDS).build()
            manager.enqueueUniqueWork(name, if (manual && !running && pending) ExistingWorkPolicy.REPLACE
                else ExistingWorkPolicy.APPEND_OR_REPLACE, task).result.get()
        }
    }

    suspend fun run(manual: Boolean, binding: String?, generation: Long): Boolean = withContext(Dispatchers.IO) {
        publication.withLock {
            val local = repository.ownedState()
            var config = try { ownerConfig(local) } catch (_: IllegalArgumentException) { return@withLock true }
            if (config.binding != binding || local.generation != generation) return@withLock true
            if (config.generation != local.generation || (config.paused != null && config.consent == null)) return@withLock true
            if (config.permanent && config.failedRevision == local.revision && !manual) return@withLock true
            var attemptedRevision = local.revision
            try {
                val normalized = normalizeGates(config, manual)
                if (normalized != config) persist(normalized)
                config = normalized
                if ((config.retry?.remaining(clock.now) ?: 0) > 0) return@withLock false
                if (config.attempts.isEmpty() && neededDelay(config, manual) > 0) return@withLock false
                if (config.wifiOnly && !wifiAvailable()) throw WifiUnavailable()
                mutableRunning.value = true
                val api = client(config)
                val remote = api.verifiedVersion(config)
                config = reconcile(config, remote, api, local)
                if (config.paused != null && config.consent == null) return@withLock true
                val allowed = config.consent ?: config.accepted
                if (allowed == null || allowed != remote.condition) {
                    persist(config.withPause(GitHubPauseReason.CONFLICT).copy(status = null))
                    return@withLock true
                }
                if (config.lastRevision == local.revision && config.consent == null) {
                    persist(config.copy(status = "已是最新", pending = null, retry = null,
                        failures = 0, failedRevision = -1, permanent = false))
                    return@withLock true
                }
                if (neededDelay(config, manual) > 0) return@withLock false
                val file = File.createTempFile("github-upload-", ".json", context.cacheDir)
                try {
                    val info = file.outputStream().use { output ->
                        snapshots.export(BackupKind.AUTO, object : OutputStream() {
                            var bytes = 0L
                            override fun write(value: Int) { count(1); output.write(value) }
                            override fun write(value: ByteArray, offset: Int, length: Int) {
                                count(length); output.write(value, offset, length)
                            }
                            private fun count(length: Int) {
                                bytes += length
                                require(bytes <= CLOUD_LIMIT) { "当前完整快照超过 32 MiB，请使用本地文件备份" }
                            }
                        })
                    }
                    attemptedRevision = info.revision
                    val sha = file.gitSha()
                    val content = file.inputStream().use { digest(it) }
                    val attempt = UploadAttempt(local.generation, info.datasetId, info.revision, info.snapshotId,
                        allowed, content, sha, !manual && !config.bypassFrequency)
                    config = config.copy(attempts = config.attempts + attempt,
                        lastWrite = clock.after(WRITE_INTERVAL), status = "正在上传")
                    persist(config)
                    require(api.upload(file, allowed) == sha) { "GitHub 写入返回的 SHA 不匹配，需重新核对" }
                    config = confirmSuccess(config, attempt, sha)
                    updateCapacity(config, api, attempt.automatic)
                } finally {
                    file.delete()
                }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val current = state.value ?: config
                val remote = error as? GitHubFailure
                val failures = if (current.failedRevision == attemptedRevision) current.failures + 1 else 1
                val retry = error is WifiUnavailable || remote?.waitMs != null || error is IOException && remote == null
                val shouldRetry = error is WifiUnavailable || retry && failures < MAX_FAILURES
                val delay = maxOf(remote?.waitMs ?: 0L, MINUTE * (1 shl (failures - 1).coerceIn(0, 5)))
                val message = when {
                    error is WifiUnavailable || remote != null -> error.message
                    error is IllegalArgumentException && error.message?.any { it in '一'..'龥' } == true -> error.message
                    else -> "GitHub 备份未确认，请检查网络并重试"
                }
                val failed = current.copy(status = message, failures = failures, failedRevision = attemptedRevision,
                    permanent = !shouldRetry, retry = if (retry) clock.after(delay) else null)
                persist(if (remote?.requiresConnection == true || error is GitHubConnectionInvalid)
                    failed.withPause(GitHubPauseReason.CONNECTION, message.orEmpty()) else failed)
                !shouldRetry
            } finally {
                mutableRunning.value = false
            }
        }
    }

    private fun normalizeGates(config: GitHubState, manual: Boolean): GitHubState =
        config.copy(
            lastWrite = clock.refreshed(config.lastWrite),
            retry = clock.refreshed(config.retry),
            pending = clock.refreshed(config.pending) ?: clock.after(if (manual) 0 else MINUTE),
        )

    private fun neededDelay(config: GitHubState, manual: Boolean): Long {
        val now = clock.now
        val wait = config.retry?.remaining(now) ?: 0L
        if (manual || config.bypassFrequency) return wait
        return maxOf(wait, config.pending?.remaining(now) ?: 0, config.lastWrite?.remaining(now) ?: 0)
    }

    private fun validateRetry(config: GitHubState): GitHubState {
        val gate = clock.refreshed(config.retry)
        val normalized = if (gate != config.retry) config.copy(retry = gate).also(::persist) else config
        require((gate?.remaining(clock.now) ?: 0L) == 0L) { "GitHub 要求等待，稍后再重试" }
        return normalized
    }

    private suspend fun reconcile(config: GitHubState, remote: RemoteVersion, api: GitHubClient, local: DataState): GitHubState {
        val remoteFile = remote.backup ?: return config
        val possible = config.attempts.filter { it.gitSha == remoteFile.sha }
        if (possible.isEmpty()) return config
        return download(api, remoteFile) { file ->
            val sha = file.inputStream().use { digest(it) }
            val candidate = possible.lastOrNull { it.sha256 == sha } ?: return@download config
            if (config.consent != null && candidate.condition != config.consent) return@download config
            val matches = file.inputStream().use { snapshots.stage(it) }.use {
                it.info.datasetId == candidate.datasetId && it.info.snapshotId == candidate.snapshotId
            }
            if (!matches) return@download config
            if (candidate.datasetId != local.datasetId || candidate.generation != local.generation) {
                val paused = if (config.consent == remote.condition) config.copy(attempts = emptyList())
                    else config.withPause(GitHubPauseReason.CONFLICT).copy(attempts = emptyList())
                persist(paused)
                return@download paused
            }
            val confirmed = confirmSuccess(config, candidate, remoteFile.sha)
            updateCapacity(confirmed, api, candidate.automatic)
            state.value ?: confirmed
        }
    }

    private fun confirmSuccess(config: GitHubState, attempt: UploadAttempt, sha: String): GitHubState {
        val next = config.copy(accepted = sha, consent = null, bypassFrequency = false, lastRevision = attempt.revision,
            succeededAt = utcNow(), paused = null, pauseReason = null, pauseId = null, acknowledgedPause = null,
            attempts = emptyList(), pending = null, retry = null, failures = 0,
            failedRevision = -1, permanent = false, status = null)
        persist(next)
        return next
    }

    private suspend fun updateCapacity(config: GitHubState, api: GitHubClient, automatic: Boolean) {
        if (!automatic) return
        var limited: DelayGate? = null
        val current = try {
            val metadata = api.repository()
            require(metadata.id == config.repositoryId) { "GitHub 仓库身份已变化" }
            config.capacity.copy(sizeKb = metadata.sizeKb, checkedAt = utcNow(), failed = false).rejudge()
        } catch (error: CancellationException) {
            throw error
        } catch (failure: Exception) {
            limited = (failure as? GitHubFailure)?.waitMs?.let(clock::after)
            config.capacity.copy(failed = true)
        }
        persist(config.copy(capacity = current, retry = limited ?: config.retry))
    }

    private suspend fun GitHubClient.verifiedVersion(config: GitHubState): RemoteVersion = version().also {
        if (it.repo.id != config.repositoryId) throw GitHubConnectionInvalid("GitHub 仓库身份已变化，请重新配置")
    }

    private suspend fun <T> download(client: GitHubClient, remote: RemoteBackup, consume: suspend (File) -> T): T {
        val file = File.createTempFile("github-download-", ".json", context.cacheDir)
        try {
            client.download(remote, file)
            return consume(file)
        } finally {
            file.delete()
        }
    }
}

class GitHubWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val coordinator = try {
            (applicationContext as KitsuneApplication).backups()
        } catch (_: OwnershipException) {
            return Result.success()
        }
        val done = withTimeoutOrNull(WORK_BUDGET) { coordinator.github.run(inputData.getBoolean("manual", false), inputData.getString("binding"),
            inputData.getLong("generation", -1L)) } == true
        if (!done) return Result.retry()
        coordinator.changed()
        return Result.success()
    }
}
