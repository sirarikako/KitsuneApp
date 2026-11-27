package com.kitsuneapp.backup

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.kitsuneapp.data.KitsuneDatabase
import com.kitsuneapp.data.Repository
import com.kitsuneapp.data.backupStatusTime
import com.kitsuneapp.data.hex
import com.kitsuneapp.data.newId
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class FolderBackupTest {
    @Test
    fun missingJsonOrMarkerIsRebuiltWithoutANewRevision() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = Files.createTempDirectory(base.cacheDir.toPath(), "folder-test-").toFile()
        val provider = BackupDocuments(folder)
        val resolver = ContentResolver.wrap(provider)
        val installation = newId()
        val context = object : ContextWrapper(base) {
            override fun getContentResolver() = resolver
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("$name-$installation", mode)
        }
        val database = Room.inMemoryDatabaseBuilder(context, KitsuneDatabase::class.java).build()
        try {
            val repository = Repository(database, installation)
            repository.initialize(true)
            val state = repository.ownedState()
            val uri = "content://com.kitsuneapp.test.folder/tree/root"
            val binding = MessageDigest.getInstance("SHA-256").digest(uri.encodeToByteArray()).hex()
            check(context.getSharedPreferences("backup-targets", Context.MODE_PRIVATE).edit()
                .putString("folder", Gson().toJson(FolderState(installation, uri))).commit())
            val backup = BackupCoordinator(context, repository)
            suspend fun runBackup() = backup.runFolder(installation, state.generation, binding)

            suspend fun verifyCurrent(): FolderState {
                val current = checkNotNull(backup.directory.value)
                val option = backup.folderOptions().single()
                assertEquals(current.published, option.marker)
                backup.openFolder(option).use {
                    assertEquals(state.datasetId, it.info.datasetId)
                    assertEquals(state.revision, it.info.revision)
                }
                assertNull(current.error)
                assertFalse(current.cleanupPending)
                return current
            }

            assertTrue(runBackup())
            val initial = verifyCurrent()
            assertTrue(runBackup())
            assertEquals(initial, backup.directory.value)
            val initialName = checkNotNull(initial.published).name
            assertTrue(File(folder, initialName).delete())
            assertTrue(File(folder, "$initialName.ok").isFile)

            provider.canCreate = false
            assertFalse(runBackup())
            assertEquals(initial.succeededAt, backup.directory.value?.succeededAt)
            assertNotNull(backup.directory.value?.error)

            provider.canCreate = true
            assertTrue(runBackup())
            val afterDeletedJson = verifyCurrent()
            assertNotEquals(initial.published, afterDeletedJson.published)
            assertTrue(Instant.parse(afterDeletedJson.succeededAt) > Instant.parse(initial.succeededAt))
            assertTrue(backupStatusTime(afterDeletedJson.succeededAt)!!.endsWith("+08:00"))

            val newName = checkNotNull(afterDeletedJson.published).name
            assertTrue(File(folder, "$newName.ok").delete())
            assertTrue(File(folder, newName).isFile)
            assertTrue(runBackup())
            val afterDeletedMarker = verifyCurrent()
            assertNotEquals(afterDeletedJson.published, afterDeletedMarker.published)
            assertTrue(Instant.parse(afterDeletedMarker.succeededAt) > Instant.parse(afterDeletedJson.succeededAt))
            assertEquals(state.revision, repository.ownedState().revision)

            // Confirmed JSON content corruption -> automatically rebuilt.
            val corruptedJsonName = checkNotNull(afterDeletedMarker.published).name
            File(folder, corruptedJsonName).writeText("{corrupted")
            assertTrue(runBackup())
            val afterCorruptedJson = verifyCurrent()
            assertNotEquals(afterDeletedMarker.published, afterCorruptedJson.published)
            assertTrue(Instant.parse(afterCorruptedJson.succeededAt) > Instant.parse(afterDeletedMarker.succeededAt))
            assertEquals(state.revision, repository.ownedState().revision)

            // Confirmed completion marker corruption -> automatically rebuilt.
            val corruptedMarkerName = checkNotNull(afterCorruptedJson.published).name
            File(folder, "$corruptedMarkerName.ok").writeText("not-valid-json")
            assertTrue(runBackup())
            val afterCorruptedMarker = verifyCurrent()
            assertNotEquals(afterCorruptedJson.published, afterCorruptedMarker.published)
            assertTrue(Instant.parse(afterCorruptedMarker.succeededAt) > Instant.parse(afterCorruptedJson.succeededAt))
            assertEquals(state.revision, repository.ownedState().revision)

            // Temporary directory inaccessibility -> do NOT rebuild or delete valid backup.
            val intactName = checkNotNull(afterCorruptedMarker.published).name
            provider.queryReturnsNull = true
            assertFalse(runBackup())
            val afterNullQuery = checkNotNull(backup.directory.value)
            assertEquals(afterCorruptedMarker.published, afterNullQuery.published)
            assertEquals(afterCorruptedMarker.succeededAt, afterNullQuery.succeededAt)
            assertNotNull(afterNullQuery.error)
            assertFalse(afterNullQuery.authError)
            assertTrue(File(folder, intactName).isFile)
            assertTrue(File(folder, "$intactName.ok").isFile)

            // Temporary file read failure -> do NOT rebuild or delete valid backup.
            provider.queryReturnsNull = false
            provider.failOpenFile = true
            assertFalse(runBackup())
            val afterReadError = checkNotNull(backup.directory.value)
            assertEquals(afterCorruptedMarker.published, afterReadError.published)
            assertEquals(afterCorruptedMarker.succeededAt, afterReadError.succeededAt)
            assertNotNull(afterReadError.error)
            assertFalse(afterReadError.authError)
            assertTrue(File(folder, intactName).isFile)
            assertTrue(File(folder, "$intactName.ok").isFile)

            // Revoked authorization -> mark authError without deleting or rebuilding.
            provider.failOpenFile = false
            provider.throwSecurity = true
            assertFalse(runBackup())
            val afterAuthError = checkNotNull(backup.directory.value)
            assertEquals(afterCorruptedMarker.published, afterAuthError.published)
            assertEquals(afterCorruptedMarker.succeededAt, afterAuthError.succeededAt)
            assertTrue(afterAuthError.authError)
            assertNotNull(afterAuthError.error)
            assertTrue(File(folder, intactName).isFile)
            assertTrue(File(folder, "$intactName.ok").isFile)

            // Once access returns and backup is intact -> clears error without rewriting or changing succeededAt.
            provider.throwSecurity = false
            assertTrue(runBackup())
            val afterRecovered = verifyCurrent()
            assertEquals(afterCorruptedMarker.published, afterRecovered.published)
            assertEquals(afterCorruptedMarker.succeededAt, afterRecovered.succeededAt)
            assertFalse(afterRecovered.authError)
        } finally {
            database.close()
            base.deleteSharedPreferences("backup-targets-$installation")
            base.deleteSharedPreferences("github-target-$installation")
            folder.deleteRecursively()
        }
    }

    private class BackupDocuments(private val folder: File) : ContentProvider() {
        var canCreate = true
        var queryReturnsNull = false
        var failOpenFile = false
        var throwSecurity = false

        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/octet-stream"

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): MatrixCursor? {
            if (throwSecurity) throw SecurityException("Permission revoked")
            if (queryReturnsNull) return null
            val columns = checkNotNull(projection).toList().toTypedArray()
            val files = if (uri.lastPathSegment == "children") folder.listFiles().orEmpty().toList()
                else listOf(File(folder, DocumentsContract.getDocumentId(uri))).filter(File::isFile)
            return MatrixCursor(columns).apply {
                for (file in files) addRow(columns.map { column ->
                    when (column) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> file.name
                        else -> error("Unexpected column $column")
                    }
                })
            }
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle = Bundle().apply {
            if (throwSecurity) throw SecurityException("Permission revoked")
            val request = checkNotNull(extras)
            @Suppress("DEPRECATION")
            val uri = checkNotNull(request.getParcelable<Uri>("uri"))
            when (method) {
                "android:createDocument" -> {
                    val name = checkNotNull(request.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    check(canCreate && File(folder, name).createNewFile())
                    putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(uri, name))
                }
                "android:deleteDocument" -> check(File(folder, DocumentsContract.getDocumentId(uri)).delete())
                else -> error("Unexpected method $method")
            }
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (throwSecurity) throw SecurityException("Permission revoked")
            if (failOpenFile) throw java.io.FileNotFoundException("Temporarily unavailable")
            return ParcelFileDescriptor.open(File(folder, DocumentsContract.getDocumentId(uri)), ParcelFileDescriptor.parseMode(mode))
        }

        override fun insert(uri: Uri, values: ContentValues?) = throw UnsupportedOperationException()
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) =
            throw UnsupportedOperationException()
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    }
}
