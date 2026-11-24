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

    @Test
    fun filtersCombineTypesAndMatchCategoryNamesAcrossWalletsAndScopes() {
        val income = Category("income", Scope.INCOME, null, "", "奖金", "奖金", 0, false, false, audit, audit)
        val expense = Category("expense", Scope.EXPENSE, "a", "a", "奖金", "奖金", 0, true, false, audit, audit)
        val other = expense.copy(id = "other", walletId = "b", ownerKey = "b", name = "日用", normalizedName = "日用", archived = false)
        val day = "2026-11-02"
        val entries = listOf(
            entry("income", EntryType.INCOME, "b", 100, day, category = income.id),
            entry("expense", EntryType.EXPENSE, "a", 100, day, category = expense.id),
            entry("refund", EntryType.REFUND, "b", 30, day, original = "expense"),
            entry("other", EntryType.EXPENSE, "b", 20, day, category = other.id),
            entry("transfer", EntryType.TRANSFER, "a", 20, day, target = "b"),
        )
        val wallets = listOf(wallet("a", false, 0), wallet("b", false, 0))
        val ledger = Ledger(Records(DataState(installationId = "install", datasetId = "data", arrayElements = 0),
            ThemePreferences(), emptyList(), listOf(income, expense, other), emptyList(), emptyMap(), wallets, entries))
        val period = LedgerPeriod("2026-11")
        val both = WalletRange()
        val onlyB = WalletRange(RangeMode.MANUAL, listOf("b"))
        val named = LedgerEntryFilters(categoryNames = setOf("奖金"))

        assertEquals(listOf("奖金", "日用"), ledger.filterCategories(wallets).map { it.name })
        assertEquals(listOf("奖金", "日用"), ledger.filterCategories(onlyB.selected(wallets)).map { it.name })
        assertEquals(listOf("income", "expense", "refund"), ledger.filtered(period, both, named).map { it.id })
        assertEquals(listOf("income", "refund"), ledger.filtered(period, onlyB, named).map { it.id })
        assertEquals(listOf("income", "expense"), ledger.filtered(period, both,
            named.copy(types = setOf(EntryType.INCOME, EntryType.EXPENSE))).map { it.id })
        assertTrue(ledger.filtered(period, both, named.copy(types = emptySet())).isEmpty())
        assertTrue(ledger.filtered(period, both, LedgerEntryFilters(categoryNames = emptySet())).isEmpty())
        assertEquals(entries, ledger.filtered(period, both))
        assertEquals(setOf(expense.id, other.id), ledger.statistics(period, both).single().expenseCategories.keys)
    }
}
