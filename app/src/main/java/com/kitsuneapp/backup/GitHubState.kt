package com.kitsuneapp.backup

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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

internal data class GitHubState(
    val installation: String,
    val target: GitHubTarget,
    val repositoryId: Long,
    val encryptedToken: String,
    val wifiOnly: Boolean,
    val generation: Long,
    val binding: String = com.kitsuneapp.data.newId(),
    val accepted: String? = null,
    val consent: String? = null,
    val bypassFrequency: Boolean = false,
    val lastRevision: Long = -1,
    val succeededAt: String? = null,
    val paused: String? = "请核对并确认当前远端版本",
    val attempts: List<UploadAttempt> = emptyList(),
    val lastWrite: DelayGate? = null,
    val pending: DelayGate? = null,
    val retry: DelayGate? = null,
    val failures: Int = 0,
    val failedRevision: Long = -1,
    val permanent: Boolean = false,
    val status: String? = null,
    val capacity: Capacity = Capacity(),
)

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
