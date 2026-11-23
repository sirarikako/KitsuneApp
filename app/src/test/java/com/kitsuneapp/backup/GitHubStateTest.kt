package com.kitsuneapp.backup

import com.google.gson.Gson
import com.kitsuneapp.data.DataState
import org.junit.Assert.*
import org.junit.Test

class GitHubStateTest {
    private val local = DataState(installationId = "install", datasetId = "data", revision = 5, arrayElements = 0)
    private val config = GitHubState(
        local.installationId, GitHubTarget("owner", "repo", "branch"), 7L, "encrypted", true, local.generation,
        paused = null, lastRevision = local.revision,
    )

    @Test
    fun differentiatesNormalPendingWifiRetryRunningAndTerminalFailure() {
        assertEquals(GitHubBackupStatus.NORMAL, config.status(5, false, false))
        assertEquals(GitHubBackupStatus.PENDING, config.status(6, false, true))
        assertEquals(GitHubBackupStatus.WIFI, config.status(6, false, false))
        assertEquals(GitHubBackupStatus.PENDING, config.copy(wifiOnly = false).status(6, false, false))
        assertEquals(GitHubBackupStatus.RUNNING, config.status(6, true, true))

        val retrying = config.copy(failedRevision = 6, retry = DelayGate(1, 100, 60_000))
        assertEquals(GitHubBackupStatus.RETRY, retrying.status(6, false, true))
        assertEquals(GitHubBackupStatus.WIFI, retrying.status(6, false, false))
        assertEquals(GitHubBackupStatus.FAILED, retrying.copy(permanent = true).status(6, false, true))
        assertEquals(GitHubBackupStatus.RETRY, retrying.status(7, false, true, retryWaiting = true))
        assertEquals(GitHubBackupStatus.PENDING, retrying.status(7, false, true, retryWaiting = false))
        assertNull(retrying.pause(local.copy(revision = 6)))
    }

    @Test
    fun oldStoredConflictsAndConnectionFailuresRetainTheirPause() {
        val legacy = config.copy(paused = "远端版本已变更，请核对后选择恢复或覆盖")
        assertEquals(GitHubPauseReason.CONFLICT, legacy.pause(local)?.reason)
        assertEquals(GitHubPauseReason.CONNECTION, config.copy(paused = "GitHub Token 已失效，请重新配置").pause(local)?.reason)
        assertEquals(GitHubPauseReason.REVIEW, config.copy(paused = "请核对并确认当前远端版本").pause(local)?.reason)
        assertEquals(GitHubPauseReason.CONNECTION, config.pause(local.copy(revision = 1))?.reason)
    }

    @Test
    fun acknowledgementSurvivesReloadWhileConsentAndRetriesKeepTheSameEvent() {
        val paused = config.withPause(GitHubPauseReason.CONFLICT)
        val id = checkNotNull(paused.pause(local)).id
        val gson = Gson()
        val reloaded = gson.fromJson(gson.toJson(paused.copy(acknowledgedPause = id, consent = "sha")), GitHubState::class.java)

        assertEquals(id, reloaded.acknowledgedPause)
        assertEquals(id, reloaded.pause(local)?.id)
        assertEquals(id, reloaded.withPause(GitHubPauseReason.CONFLICT).pause(local)?.id)
        assertNotEquals(id, reloaded.withPause(GitHubPauseReason.CONNECTION).pause(local)?.id)
        assertNotEquals(id, config.withPause(GitHubPauseReason.CONFLICT).pause(local)?.id)
    }

    @Test
    fun restoreDetectedFromGenerationKeepsItsEventDuringExplicitAuthorization() {
        val restored = local.copy(generation = 1, revision = 0)
        val inferred = checkNotNull(config.pause(restored))
        val confirmed = config.withPause(GitHubPauseReason.RESTORED).copy(generation = 1, lastRevision = -1, consent = "")
        assertEquals(GitHubPauseReason.RESTORED, inferred.reason)
        assertEquals(inferred.id, confirmed.pause(restored)?.id)
        assertNotEquals(inferred.id, confirmed.pause(restored.copy(generation = 2))?.id)
    }

    @Test
    fun authorizationErrorsPauseWhileRateLimitsAndTemporaryErrorsWait() {
        assertTrue(GitHubFailure(401, null, "").requiresConnection)
        assertTrue(GitHubFailure(403, null, "").requiresConnection)
        assertTrue(GitHubFailure(404, null, "").requiresConnection)
        assertFalse(GitHubFailure(403, 60_000, "").requiresConnection)
        assertFalse(GitHubFailure(429, 60_000, "").requiresConnection)
        assertFalse(GitHubFailure(503, 60_000, "").requiresConnection)
    }
}
