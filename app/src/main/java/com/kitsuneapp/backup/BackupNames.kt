package com.kitsuneapp.backup

import com.kitsuneapp.data.BackupKind
import com.kitsuneapp.data.backupOffset
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

private val autoName = Regex("""kitsune-auto-([0-9]{8}T[0-9]{6}(?:Z|\+0800))-([0-9]{4,})\.json""")
private val filenameTime = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssXX", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)
private val backupFilenameFormatter = filenameTime.withZone(backupOffset)
private val readableBackupTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
    .withZone(backupOffset)

internal fun backupFilenameTime(time: Instant): String = backupFilenameFormatter.format(time)

internal fun backupReadableTime(time: Instant): String = "${readableBackupTimeFormatter.format(time)} (+08:00)"

internal fun formatBackupSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < 1024L -> "$safe B"
        safe < 1024L * 1024L -> String.format(Locale.ROOT, "%.1f KB", safe / 1024.0)
        else -> String.format(Locale.ROOT, "%.2f MB", safe / (1024.0 * 1024.0))
    }
}

internal fun readableTreePath(treeId: String): String {
    val trimmed = treeId.trim()
    if (trimmed.isEmpty()) return "已授权目录"
    if (trimmed.startsWith("primary:")) {
        val path = trimmed.removePrefix("primary:").trimStart('/')
        return if (path.isEmpty()) "内部存储根目录" else path
    }
    if (':' in trimmed) {
        val volume = trimmed.substringBefore(':').trim()
        val path = trimmed.substringAfter(':').trimStart('/')
        return when {
            volume.isEmpty() && path.isEmpty() -> "已授权目录"
            volume.isEmpty() -> path
            path.isEmpty() -> volume
            else -> "$volume/$path"
        }
    }
    return trimmed
}

internal fun suggestedBackupFilename(kind: BackupKind, time: Instant = Instant.now()): String {
    val prefix = when (kind) {
        BackupKind.MANUAL -> "kitsune-manual"
        BackupKind.PRE_RESTORE -> "kitsune-pre-restore"
        BackupKind.AUTO -> error("Automatic backups require a sequence number")
    }
    return "$prefix-${backupFilenameTime(time)}.json"
}

internal data class AutoBackupName(val time: Instant, val sequence: Long) : Comparable<AutoBackupName> {
    fun filename() = "kitsune-auto-${backupFilenameTime(time)}-${sequence.toString().padStart(4, '0')}.json"

    override fun compareTo(other: AutoBackupName) = compareValuesBy(this, other, AutoBackupName::time, AutoBackupName::sequence)

    companion object {
        fun parse(name: String): AutoBackupName? {
            val (timestamp, suffix) = autoName.matchEntire(name)?.destructured ?: return null
            val sequence = suffix.toLongOrNull() ?: return null
            val time = try {
                OffsetDateTime.parse(timestamp, filenameTime).toInstant()
            } catch (_: DateTimeParseException) {
                return null
            }
            return AutoBackupName(time, sequence)
        }
    }
}

internal fun nextAutoBackupSequence(names: Sequence<String>, time: Instant): Long = Math.addExact(
    names.mapNotNull { AutoBackupName.parse(it.removeSuffix(".ok")) }
        .filter { it.time == time }.maxOfOrNull { it.sequence } ?: 0L,
    1L,
)
