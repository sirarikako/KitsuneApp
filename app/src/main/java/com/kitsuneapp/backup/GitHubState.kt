package com.kitsuneapp.backup

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kitsuneapp.data.DataState
import com.kitsuneapp.data.newId
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class DelayGate(val boot: Int, val elapsed: Long, val delay: Long) {
    fun rebase(currentBoot: Int, now: Long): DelayGate =
        if (boot != currentBoot || now < elapsed) DelayGate(currentBoot, now, delay) else this
    fun remaining(now: Long): Long = (delay - (now - elapsed)).coerceAtLeast(0L)
}

internal class BackupClock(private val context: Context) {
    val boot get() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    val now get() = SystemClock.elapsedRealtime()
    fun after(delay: Long) = DelayGate(boot, now, delay)
    fun refreshed(gate: DelayGate?) = gate?.rebase(boot, now)
}

internal data class Capacity(
    val thresholdMb: Long = 800,
    val sizeKb: Long? = null,
    val checkedAt: String? = null,
    val failed: Boolean = false,
    val shownKb: Long? = null,
    val pending: Boolean = false,
) {
    fun rejudge(): Capacity {
        val size = sizeKb ?: return copy(pending = false)
        val threshold = thresholdMb * 1000
        if (size <= threshold) return copy(shownKb = null, pending = false)
        return copy(pending = shownKb == null || size - shownKb >= threshold / 10)
    }

    fun setThreshold(text: String): Capacity {
        val value = text.trim().toLongOrNull()
        require(value != null && value in 1L..Long.MAX_VALUE / 1000) { "阈值必须是可换算为 KB 的正整数 MB" }
        return copy(thresholdMb = value).rejudge()
    }
}

internal data class UploadAttempt(
    val generation: Long,
    val datasetId: String,
    val revision: Long,
    val snapshotId: String,
    val condition: String,
    val sha256: String,
    val gitSha: String,
    val automatic: Boolean,
)

internal enum class GitHubPauseReason(val description: String, val action: String) {
    CONFLICT("GitHub 备份发生变化，需要确认后才能继续自动备份。", "处理备份冲突"),
    CONNECTION("GitHub 连接不可用，请检查 Token、仓库权限及分支。", "重新配置连接"),
    RESTORED("完整恢复已完成，需要授权后才能更新 GitHub 备份。", "确认更新 GitHub 备份"),
    REVIEW("需要确认当前远端版本后才能开始自动备份。", "确认 GitHub 备份");
}

internal data class GitHubPause(val reason: GitHubPauseReason, val id: String, val detail: String? = null)

internal enum class GitHubBackupStatus(val label: String) {
    NORMAL("自动备份正常"),
    WIFI("等待 Wi-Fi"),
    PENDING("等待自动备份"),
    RETRY("等待重试"),
    RUNNING("正在备份"),
    FAILED("备份失败");
}

private const val INITIAL_REVIEW = "请核对并确认当前远端版本"
private const val RESTORED_PAUSE = "完整恢复后等待确认更新远端"
private const val CONFLICT_PAUSE = "远端版本已变更，请核对后选择恢复或覆盖"
private const val PREVIOUS_UPLOAD_PAUSE = "上一代备份已发布，请确认当前远端版本"

internal data class GitHubState(
    val installation: String,
    val target: GitHubTarget,
    val repositoryId: Long,
    val encryptedToken: String,
    val wifiOnly: Boolean,
    val generation: Long,
    val binding: String = newId(),
    val accepted: String? = null,
    val consent: String? = null,
    val bypassFrequency: Boolean = false,
    val lastRevision: Long = -1,
    val succeededAt: String? = null,
    val paused: String? = INITIAL_REVIEW,
    val attempts: List<UploadAttempt> = emptyList(),
    val lastWrite: DelayGate? = null,
    val pending: DelayGate? = null,
    val retry: DelayGate? = null,
    val failures: Int = 0,
    val failedRevision: Long = -1,
    val permanent: Boolean = false,
    val status: String? = null,
    val capacity: Capacity = Capacity(),
    val pauseReason: GitHubPauseReason? = null,
    val pauseId: String? = null,
    val acknowledgedPause: String? = null,
) {
    fun belongsTo(local: DataState): Boolean = installation == local.installationId &&
        (generation != local.generation || lastRevision <= local.revision)

    // Existing installations stored only the pause text. Keep their block and recover its action.
    private fun storedPause(): GitHubPauseReason? = pauseReason ?: when (paused) {
        null -> null
        INITIAL_REVIEW -> GitHubPauseReason.REVIEW
        RESTORED_PAUSE -> GitHubPauseReason.RESTORED
        CONFLICT_PAUSE, PREVIOUS_UPLOAD_PAUSE -> GitHubPauseReason.CONFLICT
        else -> GitHubPauseReason.CONNECTION
    }

    fun pause(local: DataState): GitHubPause? {
        if (!belongsTo(local)) return GitHubPause(GitHubPauseReason.CONNECTION, "$binding:ownership:${local.installationId}")
        val stored = storedPause()
        val reason = when {
            stored == GitHubPauseReason.CONNECTION -> stored
            generation != local.generation -> GitHubPauseReason.RESTORED
            else -> stored ?: return null
        }
        val id = when (reason) {
            GitHubPauseReason.RESTORED -> "$binding:restore:${local.generation}"
            else -> pauseId ?: "$binding:$generation:$reason"
        }
        return GitHubPause(reason, id, paused.takeIf { reason == GitHubPauseReason.CONNECTION })
    }

    fun withPause(reason: GitHubPauseReason, message: String = reason.description): GitHubState =
        copy(paused = message, pauseReason = reason,
            pauseId = if (storedPause() == reason) pauseId else newId(), consent = null, bypassFrequency = false)

    fun status(revision: Long, running: Boolean, wifiAvailable: Boolean, retryWaiting: Boolean = false): GitHubBackupStatus {
        val pending = lastRevision != revision || consent != null || attempts.isNotEmpty() || this.pending != null
        return when {
            running -> GitHubBackupStatus.RUNNING
            failedRevision == revision && permanent -> GitHubBackupStatus.FAILED
            pending && wifiOnly && !wifiAvailable -> GitHubBackupStatus.WIFI
            pending && (retryWaiting || failedRevision == revision && retry != null) -> GitHubBackupStatus.RETRY
            pending -> GitHubBackupStatus.PENDING
            else -> GitHubBackupStatus.NORMAL
        }
    }
}

internal class BackupSecret(private val installation: String) {
    private val alias = "kitsune-github-$installation"
    private val store get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun encrypt(token: String): String {
        val key = store.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build())
            }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(installation.encodeToByteArray())
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(token.encodeToByteArray()))
    }

    fun decrypt(encrypted: String): String {
        val key = requireNotNull(store.getKey(alias, null) as? SecretKey) { "备份凭据需重新配置" }
        val bytes = Base64.getDecoder().decode(encrypted)
        require(bytes.size > 12) { "备份凭据需重新配置" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(installation.encodeToByteArray())
        return cipher.doFinal(bytes, 12, bytes.size - 12).decodeToString()
    }
}
