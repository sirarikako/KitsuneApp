package com.kitsuneapp.data

import java.time.DateTimeException
import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    @Test
    fun fieldLimitsMeasureEscapedUtf8() {
        validateField("备注", "中".repeat(21_845))
        validateField("备注", "\n".repeat(32_768))
        assertThrows(IllegalArgumentException::class.java) { validateField("备注", "中".repeat(21_846)) }
        assertThrows(IllegalArgumentException::class.java) { validateField("备注", "\u0001".repeat(10_923)) }
        assertThrows(IllegalArgumentException::class.java) { validateField("备注", "\uD800") }
    }

    @Test
    fun dateTimeAndNames() {
        assertEquals("café", normalizedName(" CAFE\u0301 "))
        assertEquals("2024-02-29", validateDate("2024-02-29"))
        assertThrows(IllegalArgumentException::class.java) { validateDate("2025-02-29") }
        listOf("24:00", "12:60", "12:04:00", "8:03").forEach {
            assertThrows(IllegalArgumentException::class.java) { validateTime(it) }
        }
        assertEquals("00:00", validateTime("00:00"))
        assertEquals("23:59", validateTime("23:59"))
    }

    @Test
    fun backupTimestampsPreserveInstantsAcrossUtcPlusEight() {
        val utc = "2026-11-17T16:30:15.456Z"
        val local = "2026-11-18T00:30:15.456+08:00"
        assertEquals(local, folderTimestamp(utc))
        assertEquals(utc, canonicalTimestamp(local))
        assertEquals(local, snapshotTimestamp(local))
        assertEquals("2026-11-17T16:30:15.000Z", snapshotTimestamp("2026-11-17T16:30:15Z"))
        assertEquals("2026-11-18T00:30:15.456+08:00", snapshotTimestamp("2026-11-18T00:30:15.456789+08:00"))
        val boundary = "9999-12-31T23:00:00.000Z"
        assertEquals(boundary, canonicalTimestamp(folderTimestamp(boundary)))
        assertThrows(IllegalArgumentException::class.java) { canonicalTimestamp("0000-01-01T01:00:00+08:00") }
        assertThrows(IllegalArgumentException::class.java) { canonicalTimestamp("2026-11-18T00:30:15+07:00") }
        assertThrows(IllegalArgumentException::class.java) { canonicalTimestamp("2026-11-18T00:30:15") }
        assertThrows(DateTimeException::class.java) { canonicalTimestamp("2026-11-31T00:30:15+08:00") }
    }
}
