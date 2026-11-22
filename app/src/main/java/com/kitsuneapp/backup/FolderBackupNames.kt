package com.kitsuneapp.backup

import com.kitsuneapp.data.folderBackupOffset
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

private val autoName = Regex("""kitsune-auto-([0-9]{8}T[0-9]{6}(?:Z|\+0800))-([0-9]{4,})\.json""")
private val filenameTime = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssXX", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)

internal fun folderFilenameTime(time: Instant): String = filenameTime.withZone(folderBackupOffset).format(time)

internal data class AutoBackupName(val time: Instant, val sequence: Long) : Comparable<AutoBackupName> {
    fun filename() = "kitsune-auto-${folderFilenameTime(time)}-${sequence.toString().padStart(4, '0')}.json"

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
    names.mapNotNull(AutoBackupName::parse).filter { it.time == time }.maxOfOrNull { it.sequence } ?: 0L,
    1L,
)
