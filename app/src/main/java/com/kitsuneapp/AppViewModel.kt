package com.kitsuneapp

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsuneapp.backup.BackupCoordinator
import com.kitsuneapp.backup.FolderOption
import com.kitsuneapp.backup.GitHubFailure
import com.kitsuneapp.backup.GitHubPreview
import com.kitsuneapp.backup.WifiUnavailable
import com.kitsuneapp.data.OwnershipException
import com.kitsuneapp.data.Records
import com.kitsuneapp.data.Repository
import com.kitsuneapp.data.StagedSnapshot
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppState(val records: Records? = null, val openingError: String? = null, val needsNewDatabase: Boolean = false)

internal sealed interface RestorePreview : Closeable {
    class Local(val snapshot: StagedSnapshot) : RestorePreview {
        override fun close() = snapshot.close()
    }

    class Cloud(val source: GitHubPreview) : RestorePreview {
        override fun close() = source.close()
    }
}

class AppViewModel internal constructor(
    application: Application,
    private val openDatabase: suspend (Boolean) -> Pair<Repository, BackupCoordinator>,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, { startNew ->
        val app = application as KitsuneApplication
        app.repository(startNew) to app.backups()
    })

    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    val isBusy get() = working.value
    private val message = MutableStateFlow<String?>(null)
    val error = message.asStateFlow()
    private val pendingRestore = MutableStateFlow<RestorePreview?>(null)
    internal val restorePreview = pendingRestore.asStateFlow()
    private val localRestoreResult = MutableStateFlow(false)
    internal val localRestoreSucceeded = localRestoreResult.asStateFlow()
    private var observe: Job? = null
    private lateinit var repository: Repository
    lateinit var backupServices: BackupCoordinator
        private set

    init { open(false) }

    fun open(startNew: Boolean) {
        observe?.cancel()
        mutable.value = AppState()
        observe = viewModelScope.launch {
            try {
                val (opened, backups) = openDatabase(startNew)
                repository = opened
                backupServices = backups
                repository.observe().collect { mutable.value = AppState(records = it) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutable.value = AppState(openingError = error.userMessage("无法打开本地数据库"),
                    needsNewDatabase = error is OwnershipException)
            }
        }
    }

    fun backup(onSuccess: () -> Unit = {}, work: suspend BackupCoordinator.() -> Unit) =
        save(onSuccess) { backupServices.work() }

    internal fun previewLocalRestore(uri: Uri) = backup {
        replaceRestorePreview(RestorePreview.Local(openFile(uri)))
    }

    internal fun previewFolderRestore(option: FolderOption, onSuccess: () -> Unit) = backup(onSuccess) {
        replaceRestorePreview(RestorePreview.Local(openFolder(option)))
    }

    internal fun previewCloudRestore() = backup {
        replaceRestorePreview(RestorePreview.Cloud(github.preview()))
    }

    internal fun restoreBackup(preview: RestorePreview, protection: Uri?) = useRestorePreview(preview) {
        when (preview) {
            is RestorePreview.Local -> {
                restore(preview.snapshot, protection)
                localRestoreResult.value = true
            }
            is RestorePreview.Cloud -> restoreCloud(preview.source, protection)
        }
    }

    internal fun confirmCloudBackup(preview: RestorePreview.Cloud) = useRestorePreview(preview) {
        github.confirm(preview.source)
    }

    internal fun discardRestorePreview(preview: RestorePreview? = pendingRestore.value) {
        if (!isBusy && pendingRestore.value === preview) replaceRestorePreview(null)
    }

    internal fun clearLocalRestoreResult() { localRestoreResult.value = false }

    private fun replaceRestorePreview(next: RestorePreview?) {
        val previous = pendingRestore.value
        pendingRestore.value = next
        if (next is RestorePreview.Local) clearLocalRestoreResult()
        previous?.close()
    }

    private fun useRestorePreview(preview: RestorePreview, work: suspend BackupCoordinator.() -> Unit) {
        if (pendingRestore.value !== preview) return
        backup {
            // The running job owns the snapshot until the restore and its cleanup finish.
            pendingRestore.value = null
            var retry = false
            try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (currentCoroutineContext().isActive) {
                    pendingRestore.value = preview
                    retry = true
                }
                throw failure
            } finally {
                if (!retry) withContext(NonCancellable + Dispatchers.IO) { preview.close() }
            }
        }
    }

    override fun onCleared() {
        replaceRestorePreview(null)
        super.onCleared()
    }

    fun clearError() { message.value = null }

    fun save(onSuccess: () -> Unit = {}, work: suspend Repository.() -> Unit) {
        if (working.value || !::repository.isInitialized) return
        working.value = true
        clearError()
        viewModelScope.launch {
            try {
                repository.work()
                working.value = false
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message.value = error.userMessage("操作失败，请重试")
            } finally {
                working.value = false
            }
        }
    }
}

fun Exception.userMessage(fallback: String): String = when (this) {
    is IllegalArgumentException, is IllegalStateException, is GitHubFailure, is WifiUnavailable -> message
        ?.takeIf { it.any { char -> char in '一'..'龥' } }
        ?: fallback
    else -> fallback
}
