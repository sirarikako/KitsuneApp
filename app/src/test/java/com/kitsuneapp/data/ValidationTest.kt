package com.kitsuneapp.data

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
}
