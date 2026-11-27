package com.kitsuneapp.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.kitsuneapp.KitsuneApplication
import com.kitsuneapp.data.*
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class FolderState(
    val installation: String,
    val uri: String,
    val revision: Long = -1,
    val generation: Long = -1,
    val succeededAt: String? = null,
    val published: Publication? = null,
    val error: String? = null,
    val failedRevision: Long = -1,
    val cleanupPending: Boolean = false,
    val authError: Boolean = false,
)

internal enum class FolderActivity { IDLE, CHECKING, BACKING_UP }

internal data class FolderOption(val marker: Publication, val document: Document) {
    val time: Instant? get() = AutoBackupName.parse(marker.name)?.time
}

internal fun folderDisplayName(uri: String): String = runCatching {
    readableTreePath(DocumentsContract.getTreeDocumentId(Uri.parse(uri)))
}.getOrElse { Uri.parse(uri).lastPathSegment?.takeIf(String::isNotBlank) ?: "已授权目录" }

class BackupCoordinator internal constructor(private val context: Context, private val repository: Repository) {
    val snapshots = SnapshotStore(context, repository)
    val csv = CsvTransfers(repository, snapshots)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val preferences = context.getSharedPreferences("backup-targets", Context.MODE_PRIVATE)
    private val gson = Gson()
    // Recovery and target replacement serialize with publication, not with ordinary entry writes.
    internal val publication = Mutex()
    internal val github = GitHubBackup(context, repository, snapshots, publication)
    private val scheduleLock = Mutex()
    private val mutableFolder = MutableStateFlow(loadFolder())
    internal val directory = mutableFolder.asStateFlow()
    private val mutableActivity = MutableStateFlow(FolderActivity.IDLE)
    internal val folderActivity = mutableActivity.asStateFlow()

    private fun loadFolder(): FolderState? = runCatching {
        gson.fromJson(preferences.getString("folder", null), FolderState::class.java)
    }.getOrNull()

    private fun persistFolder(state: FolderState?) {
        check(preferences.edit().putString("folder", state?.let(gson::toJson)).commit()) { "无法保存文件夹备份状态" }
        mutableFolder.value = state
    }

    fun startup() {
        scope.launch {
            try {
                scheduleFolder(verify = true)
                github.schedule()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Startup and later commits reconcile the durable revision.
            }
        }
    }

    fun verifyFolder() {
        scope.launch {
            try {
                scheduleFolder(verify = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Non-blocking background verification; errors are surfaced when the worker runs.
            }
        }
    }

    fun changed() {
        scope.launch {
            try {
                scheduleFolder()
                github.schedule()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Startup and later commits reconcile the durable revision.
            }
        }
    }

    suspend fun configureFolder(uri: Uri) = withContext(Dispatchers.IO) {
        publication.withLock {
            val state = repository.ownedState()
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            try {
                FolderBackup(context, uri).list()
                val old = directory.value
                if (old?.uri != uri.toString() || old.installation != state.installationId ||
                    old.generation == state.generation && old.revision > state.revision) {
                    persistFolder(FolderState(state.installationId, uri.toString()))
                    if (old != null && old.uri != uri.toString()) runCatching {
                        context.contentResolver.releasePersistableUriPermission(Uri.parse(old.uri), flags)
                    }
                } else if (old.error != null || old.authError || old.failedRevision >= 0) {
                    persistFolder(old.copy(error = null, failedRevision = -1, authError = false))
                }
            } catch (error: Throwable) {
                if (directory.value?.uri != uri.toString()) runCatching {
                    context.contentResolver.releasePersistableUriPermission(uri, flags)
                }
                throw error
            }
        }
        scheduleFolder(force = true)
    }

    suspend fun scheduleFolder(force: Boolean = false, verify: Boolean = false) = withContext(Dispatchers.IO) {
        scheduleLock.withLock {
            val app = repository.ownedState()
            val folder = directory.value ?: return@withLock
            if (folder.installation != app.installationId ||
                folder.generation == app.generation && folder.revision > app.revision) return@withLock
            val current = !folder.cleanupPending && folder.generation == app.generation && folder.revision == app.revision
            if (!force && !verify && current) return@withLock
            val manager = WorkManager.getInstance(context)
            val binding = folder.uri.shaKey()
            val name = "kitsune-folder-" + app.installationId + "-" + app.generation + "-" + binding
            val scheduled = manager.getWorkInfosForUniqueWork(name).get()
            val hasPending = scheduled.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            val hasRunning = scheduled.any { it.state == WorkInfo.State.RUNNING }
            val waitingRetry = scheduled.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 }
            val healthy = current && folder.error == null && !folder.authError
            if (hasPending && (!force || hasRunning)) {
                if ((verify || force) && healthy && !waitingRetry && mutableActivity.value == FolderActivity.IDLE) {
                    mutableActivity.value = FolderActivity.CHECKING
                }
                return@withLock
            }
            val policy = if (force && !hasRunning) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
            val markChecking = (verify || force) && healthy && mutableActivity.value == FolderActivity.IDLE
            if (markChecking) mutableActivity.value = FolderActivity.CHECKING
            try {
                manager.enqueueUniqueWork(name, policy,
                    OneTimeWorkRequestBuilder<FolderWorker>().setInputData(
                        androidx.work.workDataOf("installation" to app.installationId, "generation" to app.generation, "binding" to binding)
                    ).build()).result.get()
            } catch (error: Throwable) {
                if (markChecking && mutableActivity.value == FolderActivity.CHECKING) {
                    mutableActivity.value = FolderActivity.IDLE
                }
                throw error
            }
        }
    }

    internal suspend fun runFolderWithTimeout(
        installation: String?,
        generation: Long,
        binding: String?,
        timeoutMs: Long = 8 * 60 * 1000L,
    ): Boolean {
        val completed = withTimeoutOrNull(timeoutMs) {
            runFolder(installation, generation, binding)
        }
        if (completed != null) return completed
        recordFolderTimeout(installation, generation, binding)
        return false
    }

    private suspend fun recordFolderTimeout(installation: String?, generation: Long, binding: String?) =
        withContext(NonCancellable + Dispatchers.IO) {
            publication.withLock {
                try {
                    val state = repository.ownedState()
                    val target = directory.value ?: return@withLock
                    if (installation != state.installationId || generation != state.generation || binding != target.uri.shaKey())
                        return@withLock
                    if (target.installation != state.installationId ||
                        target.generation == state.generation && target.revision > state.revision) return@withLock
                    persistFolder(target.copy(
                        error = "备份检查或写入超时，请重试",
                        failedRevision = state.revision,
                        authError = false,
                    ))
                } finally {
                    mutableActivity.value = FolderActivity.IDLE
                }
            }
        }

    internal suspend fun runFolder(installation: String?, generation: Long, binding: String?): Boolean = withContext(Dispatchers.IO) {
        publication.withLock {
            try {
                val state = repository.ownedState()
                val target = directory.value ?: return@withLock true
                if (installation != state.installationId || generation != state.generation || binding != target.uri.shaKey())
                    return@withLock true
                if (target.installation != state.installationId ||
                    target.generation == state.generation && target.revision > state.revision) return@withLock true
                val folder = FolderBackup(context, Uri.parse(target.uri))
                try {
                    if (target.generation == state.generation && target.revision == state.revision) {
                        mutableActivity.value = FolderActivity.CHECKING
                        when (finishCleanup(folder, target)) {
                            FolderCleanup.COMPLETE -> return@withLock true
                            FolderCleanup.RETRY -> {
                                if (!target.cleanupPending) persistFolder(target.copy(cleanupPending = true))
                                return@withLock false
                            }
                            FolderCleanup.MISSING_PUBLICATION -> Unit
                        }
                    }
                    mutableActivity.value = FolderActivity.BACKING_UP
                    val file = File.createTempFile("folder-write-", ".json", context.cacheDir)
                    try {
                        val info = file.outputStream().use { snapshots.export(BackupKind.AUTO, it) }
                        val marker = folder.publish(file, info) {
                            check(repository.ownedState().generation == state.generation) { "备份已切换到新数据集" }
                        }
                        val confirmed = target.copy(revision = info.revision, generation = state.generation,
                            succeededAt = utcNow(), published = marker, error = null, failedRevision = -1,
                            cleanupPending = true, authError = false)
                        persistFolder(confirmed)
                        return@withLock finishCleanup(folder, confirmed) == FolderCleanup.COMPLETE
                    } finally {
                        file.delete()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: FolderAuthException) {
                    persistFolder((directory.value ?: target).copy(
                        error = error.message ?: "备份目录授权已失效，请重新授权",
                        failedRevision = state.revision,
                        authError = true,
                    ))
                    false
                } catch (error: FolderAccessException) {
                    persistFolder((directory.value ?: target).copy(
                        error = error.message ?: "无法访问备份目录，请检查目录是否可用或重试",
                        failedRevision = state.revision,
                        authError = false,
                    ))
                    false
                } catch (error: Exception) {
                    val message = (error as? IllegalArgumentException)?.message?.takeIf { it.contains("备份") }
                        ?: "无法写入备份目录，请检查授权和可用空间"
                    persistFolder((directory.value ?: target).copy(
                        error = message,
                        failedRevision = state.revision,
                        authError = false,
                    ))
                    false
                }
            } finally {
                mutableActivity.value = FolderActivity.IDLE
            }
        }
    }

    private suspend fun finishCleanup(folder: FolderBackup, state: FolderState): FolderCleanup {
        val marker = state.published ?: return FolderCleanup.MISSING_PUBLICATION
        val result = folder.cleanup(marker, snapshots)
        if (result == FolderCleanup.COMPLETE && (state.cleanupPending || state.error != null || state.authError)) {
            persistFolder(state.copy(cleanupPending = false, error = null, failedRevision = -1, authError = false))
        }
        return result
    }

    internal suspend fun folderOptions(): List<FolderOption> = withContext(Dispatchers.IO) {
        val target = usableFolder()
        FolderBackup(context, Uri.parse(target.uri)).published().map { FolderOption(it.first, it.second) }
    }

    internal suspend fun openFolder(option: FolderOption): StagedSnapshot = withContext(Dispatchers.IO) {
        val target = usableFolder()
        FolderBackup(context, Uri.parse(target.uri)).open(option.marker, option.document, snapshots)
    }

    private suspend fun usableFolder(): FolderState {
        val state = repository.ownedState()
        return requireNotNull(directory.value?.takeIf { it.installation == state.installationId && (it.generation != state.generation || it.revision <= state.revision) }) {
            "请先重新授权文件夹备份目录"
        }
    }

    suspend fun openFile(uri: Uri): StagedSnapshot = withContext(Dispatchers.IO) {
        val copy = File.createTempFile("restore-download-", ".json", context.cacheDir)
        try {
            context.contentResolver.input(uri).use { from ->
                copy.outputStream().use { to -> copyChecked(from, to) }
            }
            copy.inputStream().use { snapshots.stage(it) }
        } finally {
            copy.delete()
        }
    }

    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val file = File.createTempFile("manual-write-", ".json", context.cacheDir)
        try {
            file.outputStream().use { snapshots.export(BackupKind.MANUAL, it) }
            context.contentResolver.verifiedCopy(file, uri)
        } finally {
            file.delete()
        }
    }

    suspend fun restore(staged: StagedSnapshot, safetyUri: Uri? = null) = withContext(Dispatchers.IO) {
        publication.withLock { restoreProtected(staged, safetyUri) }
    }

    internal suspend fun restoreCloud(preview: GitHubPreview, safetyUri: Uri?) = withContext(Dispatchers.IO) {
        publication.withLock {
            val config = github.checkRestore(preview)
            restoreProtected(checkNotNull(preview.stage), safetyUri) { github.checkRestore(preview) }
            github.restoredCloud(config, preview)
        }
    }

    private suspend fun restoreProtected(staged: StagedSnapshot, safetyUri: Uri?,
                                         beforeCommit: suspend () -> Unit = {}) {
        snapshots.restore(staged, beforeCommit) { state ->
            val file = File.createTempFile("restore-protection-", ".json", context.cacheDir)
            try {
                val protected = file.outputStream().use { snapshots.export(BackupKind.PRE_RESTORE, it) }
                check(protected.datasetId == state.datasetId && protected.revision == state.revision) {
                    "本机数据已变化，请重新确认恢复"
                }
                if (safetyUri != null) context.contentResolver.verifiedCopy(file, safetyUri)
                else {
                    val target = usableFolder()
                    FolderBackup(context, Uri.parse(target.uri)).protect(file, protected)
                }
            } finally {
                file.delete()
            }
        }
    }
}

class FolderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val backup = try {
            (applicationContext as KitsuneApplication).backups()
        } catch (error: OwnershipException) {
            return Result.success()
        }
        val done = backup.runFolderWithTimeout(
            inputData.getString("installation"),
            inputData.getLong("generation", -1L),
            inputData.getString("binding"),
        )
        return if (done) {
            backup.changed()
            Result.success()
        } else if (backup.directory.value?.authError == true) {
            Result.failure()
        } else if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
}

private fun String.shaKey(): String = java.security.MessageDigest.getInstance("SHA-256").digest(encodeToByteArray()).hex()
