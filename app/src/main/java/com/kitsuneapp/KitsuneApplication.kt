package com.kitsuneapp

import android.app.Application
import android.util.AtomicFile
import androidx.room.Room
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
            val repository = Repository(database, installationId)
            try {
                repository.initialize(newDatabase)
                if (startNew) writeMarker(pointer, databaseName.removePrefix("kitsune-").removeSuffix(".db"))
                active = repository
                repository
            } catch (error: Throwable) {
                database.close()
                throw error
            }
        }
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
