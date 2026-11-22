package com.kitsuneapp.data

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

internal val backupOffset = ZoneOffset.ofHours(8)
private val utcMillis = DateTimeFormatterBuilder().appendInstant(3).toFormatter()
private val backupMillis = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSxxx", Locale.ROOT)
    .withZone(backupOffset)
// The last eight hours of UTC year 9999 are already in year 10000 at UTC+8.
private val timestampPattern = Regex(
    """(?:[0-9]{4}|\+10000)-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:\.[0-9]{1,9})?(?:Z|\+08:00)""",
)

fun utcNow(): String = utcMillis.format(Instant.now())

fun canonicalTimestamp(value: String): String = utcMillis.format(parseTimestamp(value).toInstant())

internal fun backupTimestamp(): String = backupMillis.format(Instant.now())

internal fun backupTimestamp(value: String): String = backupMillis.format(parseTimestamp(value).toInstant())

internal fun backupStatusTime(value: String?): String? =
    value?.let { runCatching { backupTimestamp(it) }.getOrNull() }

private fun parseTimestamp(value: String): OffsetDateTime {
    require(timestampPattern.matches(value)) { "无效的备份时间" }
    val time = OffsetDateTime.parse(value)
    require(time.withOffsetSameInstant(ZoneOffset.UTC).year in 0..9999) { "备份时间超出范围" }
    return time
}
