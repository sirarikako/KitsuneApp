package com.kitsuneapp.data

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test
    fun calculatorUsesExactFractionsAndRoundsOnce() {
        assertEquals(3333L, calculate("1÷3×100", 2))
        assertEquals(100L, calculate("1÷3×3", 2))
        assertEquals(2800L, calculate("12+8×2", 2))
        assertEquals(1L, calculate("0.005", 2))
        assertEquals(13L, calculate("12.5", 0))
        assertThrows(IllegalArgumentException::class.java) { calculate("1÷0", 2) }
        assertThrows(IllegalArgumentException::class.java) { calculate("0.001", 2) }
        assertThrows(IllegalArgumentException::class.java) { calculate("9223372036854775808", 0) }
    }

    @Test
    fun livePreviewUsesFinalCurrencyPrecisionAndLeavesIncompleteOrInvalidInputUnresolved() {
        assertEquals("164.50", previewCalculation("128.50+36", 2)?.let { money(it, 2) })
        assertEquals("28.00", previewCalculation("12+8×2", 2)?.let { money(it, 2) })
        assertEquals("33.33", previewCalculation("1÷3×100", 2)?.let { money(it, 2) })
        assertEquals("13", previewCalculation("12+0.5", 0)?.let { money(it, 0) })
        assertEquals("0.0003", previewCalculation("1÷3÷1000", 4)?.let { money(it, 4) })
        listOf("128+", "2..3+1", "1÷0", "1−2", "9223372036854775808+1", "1".repeat(513)).forEach {
            assertNull(it, previewCalculation(it, 2))
            assertThrows(IllegalArgumentException::class.java) { calculate(it, 2) }
        }
    }

    @Test
    fun inputRequiresCurrencyPrecisionAndSupportsLongBounds() {
        assertEquals(Long.MIN_VALUE, decimalMinor("-92233720368547758.08", 2, false))
        assertEquals(Long.MAX_VALUE, decimalMinor("92233720368547758.07", 2))
        assertThrows(IllegalArgumentException::class.java) { decimalMinor("1.0", 0) }
        assertThrows(IllegalArgumentException::class.java) { decimalMinor("1.001", 2) }
        assertEquals("92233720368547758.08", money(Long.MAX_VALUE.toBigInteger() + 1.toBigInteger(), 2))
        assertEquals("-92,233,720,368,547,758.08", displayMoney(Long.MIN_VALUE, 2))
        assertEquals("9,223,372,036,854,775,808", displayMoney(Long.MAX_VALUE.toBigInteger() + 1.toBigInteger(), 0))
    }
}
