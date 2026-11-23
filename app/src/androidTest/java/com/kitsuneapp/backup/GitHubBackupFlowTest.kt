package com.kitsuneapp.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.kitsuneapp.data.BackupKind
import com.kitsuneapp.data.KitsuneDatabase
import com.kitsuneapp.data.Repository
import com.kitsuneapp.data.SnapshotStore
import com.kitsuneapp.data.hex
import com.kitsuneapp.data.newId
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GitHubBackupFlowTest {
    @Test
    fun cloudWriteRequiresTheReviewedVersionAndPausesUntilPublishingSucceeds() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val installation = newId()
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("$name-$installation", mode)
        }
        val database = Room.inMemoryDatabaseBuilder(context, KitsuneDatabase::class.java).build()
        try {
            val repository = Repository(database, installation)
            repository.initialize(true)
            val local = repository.ownedState()
            val snapshots = SnapshotStore(context, repository)
            val remote = TestRemote()
            fun newBackup() = GitHubBackup(context, repository, snapshots, Mutex()) { target, token, _ ->
                GitHubClient(target, token, remote.http, "https://example.invalid/".toHttpUrl()) {}
            }
            val backup = newBackup()
            suspend fun connect() = backup.connect("owner/repo", "branch", "github_pat_test", false)
            suspend fun runBackup() =
                assertTrue(backup.run(manual = true, binding = checkNotNull(backup.state.value).binding, generation = local.generation))
            fun pause() = checkNotNull(backup.state.value).pause(local)
            suspend fun reviewAndConfirm() = backup.preview(resolvePause = true).use { backup.confirm(it) }
            suspend fun newSnapshot(): ByteArray = ByteArrayOutputStream().apply {
                snapshots.export(BackupKind.AUTO, this)
            }.toByteArray()

            connect()
            backup.preview(resolvePause = false).use { preview ->
                assertNull(preview.stage)
                assertTrue(runCatching { backup.confirm(preview) }.exceptionOrNull() is GitHubPreviewChanged)
            }
            reviewAndConfirm()
            assertEquals(GitHubPauseReason.REVIEW, pause()?.reason)
            assertNull(backup.state.value?.succeededAt)
            runBackup()
            assertEquals(1, remote.writes)
            assertNull(pause())
            assertNotNull(backup.state.value?.succeededAt)
            backup.preview(resolvePause = false).use { preview ->
                assertEquals(local.datasetId, preview.stage?.info?.datasetId)
                assertEquals(7L, backup.checkRestore(preview).repositoryId)
                remote.file = newSnapshot()
                assertTrue(runCatching { backup.checkRestore(preview) }.exceptionOrNull() is GitHubPreviewChanged)
            }

            runBackup()
            val conflict = checkNotNull(pause())
            assertEquals(GitHubPauseReason.CONFLICT, conflict.reason)
            backup.preview(resolvePause = true).use { assertNotNull(it.stage) }
            backup.acknowledgePause(conflict.id)
            assertEquals(conflict, newBackup().state.value?.pause(local))
            assertEquals(conflict.id, newBackup().state.value?.acknowledgedPause)
            assertEquals(1, remote.writes)

            backup.preview(resolvePause = true).use { preview ->
                remote.file = null
                assertTrue(runCatching { backup.confirm(preview) }.exceptionOrNull() is GitHubPreviewChanged)
            }
            backup.preview(resolvePause = true).use { assertEquals("", it.condition); assertNull(it.stage) }
            reviewAndConfirm()
            remote.file = newSnapshot()
            runBackup()
            assertEquals(1, remote.writes)
            assertEquals(conflict, pause())
            assertNull(backup.state.value?.consent)

            reviewAndConfirm()
            runBackup()
            assertEquals(2, remote.writes)
            assertNull(pause())
            assertNull(backup.state.value?.acknowledgedPause)

            remote.deny = true
            runBackup()
            assertEquals(GitHubPauseReason.CONNECTION, pause()?.reason)
            assertNotEquals(conflict.id, pause()?.id)
            assertEquals(2, remote.writes)

            remote.deny = false
            connect()
            reviewAndConfirm()
            runBackup()
            assertNull(pause())
            assertEquals(3, remote.writes)
            backup.preview(resolvePause = false).use { preview ->
                val config = backup.checkRestore(preview)
                snapshots.restore(checkNotNull(preview.stage), { backup.checkRestore(preview) }) {
                    error("The empty local database does not need a protection file")
                }
                backup.restoredCloud(config, preview)
            }
            val restored = repository.ownedState()
            assertEquals(GitHubPauseReason.RESTORED, backup.state.value?.pause(restored)?.reason)
            val before = checkNotNull(backup.state.value)
            assertTrue(backup.run(manual = true, binding = before.binding, generation = restored.generation))
            assertEquals(3, remote.writes)
            backup.preview(resolvePause = true).use { backup.confirm(it) }
            assertEquals(GitHubPauseReason.RESTORED, backup.state.value?.pause(restored)?.reason)
            assertTrue(backup.run(manual = true, binding = before.binding, generation = restored.generation))
            assertEquals(4, remote.writes)
            assertNull(backup.state.value?.pause(restored))
        } finally {
            database.close()
            base.deleteSharedPreferences("github-target-$installation")
        }
    }

    private class TestRemote {
        var deny = false
        var file: ByteArray? = null
        var writes = 0
            private set
        private val ref = "1".repeat(40)
        private val tree = "2".repeat(40)

        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            var status = 200
            val body = when {
                deny -> { status = 401; "{}" }
                request.url.encodedPath.endsWith("/branches/branch") ->
                    """{"commit":{"sha":"$ref","commit":{"tree":{"sha":"$tree"}}}}"""
                request.url.encodedPath.endsWith("/git/trees/$tree") -> """{"truncated":false,"tree":[]}"""
                request.method == "PUT" -> {
                    val buffer = Buffer()
                    checkNotNull(request.body).writeTo(buffer)
                    val json = JsonParser.parseString(buffer.readUtf8()).asJsonObject
                    assertEquals(file?.let(::sha), json.get("sha")?.asString)
                    assertEquals("branch", json["branch"].asString)
                    val published = Base64.getDecoder().decode(json["content"].asString)
                    file = published
                    writes++
                    """{"content":{"sha":"${sha(published)}"}}"""
                }
                request.url.encodedPath.endsWith("/contents/backups/latest.json") -> {
                    assertEquals(ref, request.url.queryParameter("ref"))
                    val current = file
                    if (current == null) {
                        status = 404
                        "{}"
                    } else {
                        val content = Base64.getEncoder().encodeToString(current)
                        """{"type":"file","size":${current.size},"sha":"${sha(current)}","content":"$content","encoding":"base64"}"""
                    }
                }
                else -> """{"private":true,"id":7,"size":1}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()

        private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1").run {
            update("blob ${bytes.size}\u0000".encodeToByteArray())
            digest(bytes).hex()
        }
    }
}
