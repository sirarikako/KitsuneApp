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
    fun inputRequiresCurrencyPrecisionAndSupportsLongBounds() {
        assertEquals(Long.MIN_VALUE, decimalMinor("-92233720368547758.08", 2, false))
        assertEquals(Long.MAX_VALUE, decimalMinor("92233720368547758.07", 2))
        assertThrows(IllegalArgumentException::class.java) { decimalMinor("1.0", 0) }
        assertThrows(IllegalArgumentException::class.java) { decimalMinor("1.001", 2) }
        assertEquals("92233720368547758.08", money(Long.MAX_VALUE.toBigInteger() + 1.toBigInteger(), 2))
    }
}
