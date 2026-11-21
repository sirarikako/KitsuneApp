package com.kitsuneapp.backup

import com.google.gson.JsonParser
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class GitHubBackupTest {
    private val ref = "1".repeat(40)
    private val tree = "2".repeat(40)
    private val sha = "3".repeat(40)

    @Test
    fun validatesPrivateTargetAndConfirmsMissingPathIndependently() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("{\"private\":true,\"id\":7,\"size\":800000}"))
            server.enqueue(json(branch()))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(json("{\"truncated\":false,\"tree\":[]}"))
            val remote = client(server).version()
            assertEquals("", remote.condition)
            assertEquals(7L, remote.repo.id)
            assertEquals("/repos/example/storage/branches/release%2Ffirst", server.takeAt(2).path)
            assertEquals(ref, server.takeRequest().requestUrl?.queryParameter("ref"))
            assertEquals("/repos/example/storage/git/trees/$tree", server.takeRequest().path)
        }
    }

    @Test
    fun keepsAuthFailureSeparateFromFileMissing() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("{\"private\":true,\"id\":7,\"size\":0}"))
            server.enqueue(json(branch()))
            server.enqueue(MockResponse().setResponseCode(403).setBody("{\"message\":\"Resource not accessible\"}"))
            val error = runCatching { client(server).version() }.exceptionOrNull()
            assertTrue(error is GitHubFailure)
            assertEquals(403, (error as GitHubFailure).status)
            assertNull(error.waitMs)
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun rawDownloadUsesPinnedRefAndRechecksByteCountAndBlobDigest() = runBlocking {
        MockWebServer().use { server ->
            val source = File.createTempFile("kitsune-mock-source", ".json")
            val output = File.createTempFile("kitsune-mock-download", ".json")
            try {
                val body = "a".repeat(1024 * 1024 + 1)
                source.writeText(body)
                server.enqueue(MockResponse().setBody(body))
                client(server).download(RemoteBackup(ref, source.gitSha(), source.length(), null), output)
                val request = server.takeRequest()
                assertEquals("application/vnd.github.raw+json", request.getHeader("Accept"))
                assertEquals(ref, request.requestUrl?.queryParameter("ref"))
                assertTrue(source.readBytes().contentEquals(output.readBytes()))
                server.enqueue(MockResponse().setBody(body))
                assertTrue(runCatching { client(server).download(RemoteBackup(ref, sha, source.length(), null), output) }.isFailure)
            } finally {
                source.delete()
                output.delete()
            }
        }
    }

    @Test
    fun uploadIncludesBlobPreconditionAndStreamsExactOriginalBytes() = runBlocking {
        MockWebServer().use { server ->
            val source = File.createTempFile("kitsune-mock-upload", ".json")
            try {
                val bytes = ByteArray(IO_BUFFER * 3 + 5) { it.toByte() }
                source.writeBytes(bytes)
                server.enqueue(json("{\"content\":{\"sha\":\"$sha\"}}"))
                assertEquals(sha, client(server).upload(source, tree))
                val request = server.takeRequest()
                assertEquals("PUT", request.method)
                val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                assertEquals(tree, json.get("sha").asString)
                assertEquals("release/first", json.get("branch").asString)
                assertArrayEquals(bytes, Base64.getDecoder().decode(json.get("content").asString))
            } finally {
                source.delete()
            }
        }
    }

    @Test
    fun appliesRateLimitAndBootRebase() {
        assertEquals(120_000L, rateLimitDelay("120", null, null, "Sat, 21 Nov 2026 08:00:00 GMT"))
        assertEquals(60_000L, rateLimitDelay(null, null, null, null))
        val clock = DelayGate(3, 1000, 60_000)
        assertEquals(1L, clock.remaining(60_999))
        assertEquals(60_000L, clock.rebase(4, 5).remaining(5))
    }

    @Test
    fun capacityThresholdEqualityGrowthAndRecovery() {
        val equal = Capacity(sizeKb = 800_000).rejudge()
        assertFalse(equal.pending)
        assertTrue(equal.copy(sizeKb = 800_001).rejudge().pending)
        assertFalse(equal.copy(sizeKb = 880_000, shownKb = 800_001).rejudge().pending)
        assertTrue(equal.copy(sizeKb = 880_001, shownKb = 800_001).rejudge().pending)
        assertNull(equal.copy(shownKb = 800_001).rejudge().shownKb)
        assertTrue(equal.setThreshold("799").pending)
        assertThrows(IllegalArgumentException::class.java) { equal.setThreshold("0") }
    }

    private fun branch(): String = "{\"commit\":{\"sha\":\"$ref\",\"commit\":{\"tree\":{\"sha\":\"$tree\"}}}}"
    private fun client(server: MockWebServer) =
        GitHubClient(GitHubTarget("example", "storage", "release/first"), "fixture-token", OkHttpClient(), server.url("/"), {})
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun MockWebServer.takeAt(index: Int): okhttp3.mockwebserver.RecordedRequest {
        repeat(index - 1) { takeRequest() }
        return takeRequest()
    }
}
