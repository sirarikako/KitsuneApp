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

internal fun backupFilenameTime(time: Instant): String = backupFilenameFormatter.format(time)

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
