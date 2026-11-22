package com.kitsuneapp.data

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class AccountingTest {
    private val audit = "2026-11-21T00:00:00.000Z"
    private fun wallet(id: String, separate: Boolean, opening: Long) = Wallet(
        id, id, id, "CNY", 2, opening, 0, false, separate, CategoryMode.DEFAULT, CategoryMode.DEFAULT, audit, audit,
    )
    private fun entry(id: String, type: EntryType, wallet: String, amount: Long, day: String,
                      category: String? = null, original: String? = null, target: String? = null) = FinancialEntry(
        id, day, "20:21", type, wallet, amount, category, target, amount.takeIf { target != null }, original, "", audit, audit,
    )

    @Test
    fun balanceAndStatisticsUseSeparateRulesAndArbitraryPrecision() {
        val category = Category("c", Scope.EXPENSE, "a", "a", "餐饮", "餐饮", 0, true, false, audit, audit)
        val entries = listOf(
            entry("e", EntryType.EXPENSE, "a", 100, "2026-10-20", category = "c"),
            entry("r", EntryType.REFUND, "b", 30, "2026-11-01", original = "e"),
            entry("t", EntryType.TRANSFER, "b", 5, "2026-11-30", target = "a"),
            entry("i", EntryType.INCOME, "a", 100, "2026-11-15", category = "income"),
        )
        val data = Records(DataState(installationId = "install", datasetId = "data", arrayElements = 7), ThemePreferences(),
            emptyList(), listOf(category), emptyList(), emptyMap(), listOf(wallet("a", false, Long.MAX_VALUE), wallet("b", true, 0)), entries)
        val ledger = Ledger(data)
        val period = LedgerPeriod("2026-11")
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE) + 5.toBigInteger(), ledger.balance()["a"])
        assertEquals(25.toBigInteger(), ledger.balance()["b"])
        assertEquals(100.toBigInteger(), ledger.statistics(period, WalletRange()).single().income)
        assertEquals(BigInteger.ZERO, ledger.statistics(period, WalletRange()).single().expenses)
        assertEquals((-30).toBigInteger(), ledger.statistics(period, WalletRange(RangeMode.ALL)).single().expenseCategories["c"])
        assertEquals(listOf("t", "i"), ledger.filtered(period, WalletRange()).map { it.id })
        assertEquals(listOf("r", "t"), ledger.filtered(period, WalletRange(RangeMode.MANUAL, listOf("b"))).map { it.id })
    }
}
