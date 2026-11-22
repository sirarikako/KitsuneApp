package com.kitsuneapp.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
)

internal data class FolderOption(val marker: Publication, val document: Document)

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
    private val mutableRunning = MutableStateFlow(false)
    val folderRunning = mutableRunning.asStateFlow()

    private fun loadFolder(): FolderState? = runCatching {
        gson.fromJson(preferences.getString("folder", null), FolderState::class.java)
    }.getOrNull()

    private fun persistFolder(state: FolderState?) {
        check(preferences.edit().putString("folder", state?.let(gson::toJson)).commit()) { "无法保存文件夹备份状态" }
        mutableFolder.value = state
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

    suspend fun scheduleFolder(force: Boolean = false) = withContext(Dispatchers.IO) {
        scheduleLock.withLock {
            val app = repository.ownedState()
            val folder = directory.value ?: return@withLock
            if (folder.installation != app.installationId ||
                folder.generation == app.generation && folder.revision > app.revision) return@withLock
            if (!force && !folder.cleanupPending && folder.generation == app.generation && folder.revision == app.revision) return@withLock
            val manager = WorkManager.getInstance(context)
            val binding = folder.uri.shaKey()
            val name = "kitsune-folder-" + app.installationId + "-" + app.generation + "-" + binding
            val scheduled = manager.getWorkInfosForUniqueWork(name).get()
            if (scheduled.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) return@withLock
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<FolderWorker>().setInputData(
                    androidx.work.workDataOf("installation" to app.installationId, "generation" to app.generation, "binding" to binding)
                ).build()).result.get()
        }
    }

    internal suspend fun runFolder(installation: String?, generation: Long, binding: String?): Boolean = withContext(Dispatchers.IO) {
        publication.withLock {
            val state = repository.ownedState()
            val target = directory.value ?: return@withLock true
            if (installation != state.installationId || generation != state.generation || binding != target.uri.shaKey())
                return@withLock true
            if (target.installation != state.installationId ||
                target.generation == state.generation && target.revision > state.revision) return@withLock true
            val folder = FolderBackup(context, Uri.parse(target.uri))
            mutableRunning.value = true
            try {
                if (target.generation == state.generation && target.revision == state.revision) {
                    when (finishCleanup(folder, target)) {
                        FolderCleanup.COMPLETE -> return@withLock true
                        FolderCleanup.RETRY -> return@withLock false
                        FolderCleanup.MISSING_PUBLICATION -> Unit
                    }
                }
                val file = File.createTempFile("folder-write-", ".json", context.cacheDir)
                try {
                    val info = file.outputStream().use { snapshots.export(BackupKind.AUTO, it) }
                    val marker = folder.publish(file, info) {
                        check(repository.ownedState().generation == state.generation) { "备份已切换到新数据集" }
                    }
                    val confirmed = target.copy(revision = info.revision, generation = state.generation,
                        succeededAt = utcNow(), published = marker, error = null, failedRevision = -1, cleanupPending = true)
                    persistFolder(confirmed)
                    return@withLock finishCleanup(folder, confirmed) == FolderCleanup.COMPLETE
                } finally {
                    file.delete()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = (error as? IllegalArgumentException)?.message?.takeIf { it.contains("备份") }
                    ?: "无法写入备份目录，请检查授权和可用空间"
                persistFolder((directory.value ?: target).copy(error = message, failedRevision = state.revision))
                false
            } finally {
                mutableRunning.value = false
            }
        }
    }

    private suspend fun finishCleanup(folder: FolderBackup, state: FolderState): FolderCleanup {
        val marker = state.published ?: return FolderCleanup.MISSING_PUBLICATION
        val result = folder.cleanup(marker, snapshots)
        if (result == FolderCleanup.COMPLETE && (state.cleanupPending || state.error != null)) {
            persistFolder(state.copy(cleanupPending = false, error = null, failedRevision = -1))
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
        val done = withTimeoutOrNull(8 * 60 * 1000L) { backup.runFolder(inputData.getString("installation"),
            inputData.getLong("generation", -1L), inputData.getString("binding")) } == true
        return if (done) {
            backup.changed()
            Result.success()
        } else if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
}

private fun String.shaKey(): String = java.security.MessageDigest.getInstance("SHA-256").digest(encodeToByteArray()).hex()
