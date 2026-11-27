package com.kitsuneapp

import android.app.Application
import android.util.AtomicFile
import androidx.room.Room
import com.kitsuneapp.backup.BackupCoordinator
import com.kitsuneapp.data.KitsuneDatabase
import com.kitsuneapp.data.Repository
import com.kitsuneapp.data.newId
import com.kitsuneapp.data.validateId
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class KitsuneApplication : Application() {
    private val lock = Mutex()
    private var active: Repository? = null
    private lateinit var backupCoordinator: BackupCoordinator

    suspend fun backups(): BackupCoordinator {
        repository()
        return backupCoordinator
    }

    suspend fun repository(startNew: Boolean = false): Repository = withContext(Dispatchers.IO) {
        lock.withLock {
            active?.let { return@withLock it }
            val identity = AtomicFile(File(noBackupFilesDir, "installation"))
            val installationId = if (identity.baseFile.exists()) {
                validateId(identity.readFully().decodeToString())
            } else {
                newId().also { writeMarker(identity, it) }
            }
            val pointer = AtomicFile(File(noBackupFilesDir, "database"))
            val databaseName = when {
                startNew -> "kitsune-" + newId() + ".db"
                pointer.baseFile.exists() -> "kitsune-" + validateId(pointer.readFully().decodeToString()) + ".db"
                else -> "kitsune.db"
            }
            val newDatabase = !getDatabasePath(databaseName).exists()
            val database = Room.databaseBuilder(this@KitsuneApplication, KitsuneDatabase::class.java, databaseName).build()
            val repository = Repository(database, installationId) { backupCoordinator.changed() }
            try {
                repository.initialize(newDatabase)
                if (startNew) {
                    resetBackupTargets()
                    writeMarker(pointer, databaseName.removePrefix("kitsune-").removeSuffix(".db"))
                }
                cleanupTemporaryBackups()
                backupCoordinator = BackupCoordinator(this@KitsuneApplication, repository)
                active = repository
                backupCoordinator.startup()
                repository
            } catch (error: Throwable) {
                database.close()
                throw error
            }
        }
    }

    private fun resetBackupTargets() {
        for (name in listOf("backup-targets", "github-target")) {
            check(getSharedPreferences(name, MODE_PRIVATE).edit().clear().commit()) { "无法重置旧备份连接" }
        }
        contentResolver.persistedUriPermissions.forEach { grant ->
            val flags = (if (grant.isReadPermission) android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (grant.isWritePermission) android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            runCatching { contentResolver.releasePersistableUriPermission(grant.uri, flags) }
        }
    }

    private fun cleanupTemporaryBackups() {
        val prefixes = listOf("stage-", "backup-check-", "folder-write-", "manual-write-", "restore-protection-", "restore-download-", "github-")
        cacheDir.listFiles().orEmpty().filter { file ->
            file.isFile && prefixes.any { file.name.startsWith(it) }
        }.forEach { it.delete() }
    }

    private fun writeMarker(file: AtomicFile, text: String) {
        val output = file.startWrite()
        try {
            output.write(text.encodeToByteArray())
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }
}
