package com.kitsuneapp.data

import java.io.Serializable
import java.math.BigInteger
import java.time.YearMonth

enum class RangeMode { REGULAR, ALL, MANUAL }
data class WalletRange(val mode: RangeMode = RangeMode.REGULAR, val walletIds: List<String> = emptyList()) : Serializable {
    fun selected(wallets: List<Wallet>): List<Wallet> = when (mode) {
        RangeMode.REGULAR -> wallets.filterNot { it.separateAccounting }
        RangeMode.ALL -> wallets
        RangeMode.MANUAL -> wallets.filter { it.id in walletIds }
    }
}

data class LedgerPeriod(val month: String, val start: String? = null, val end: String? = null) : Serializable {
    fun matches(date: String) = if (start != null && end != null) date in start..end else date.startsWith(month)
    val label get() = if (start != null && end != null) "$start 至 $end" else YearMonth.parse(month).let { "${it.year} 年 ${it.monthValue} 月" }
}

data class LedgerEntryFilters(
    val types: Set<EntryType>? = null,
    val categoryNames: Set<String>? = null,
    val note: String = "",
) : Serializable {
    val isActive get() = types != null || categoryNames != null || note.isNotBlank()
}

data class Totals(
    val currency: String,
    val scale: Int,
    val income: BigInteger,
    val expenses: BigInteger,
    val incomeCategories: Map<String, BigInteger>,
    val expenseCategories: Map<String, BigInteger>,
)

class Ledger(records: Records) {
    val wallets = records.wallets
    val entries = records.entries
    val categories = records.categories
    private val walletById = wallets.associateBy { it.id }
    private val entriesById = entries.associateBy { it.id }
    private val categoryById = categories.associateBy { it.id }
    private val categoryNameCounts = categories.groupingBy { it.scope to it.normalizedName }.eachCount()

    fun wallet(id: String?) = walletById[id]
    fun entry(id: String?) = entriesById[id]
    fun walletLabel(id: String?) = wallet(id)?.let { it.name + " · " + it.currencyCode } ?: "未选择"
    fun categoryId(entry: FinancialEntry) = entry.categoryId ?: entry.originalExpenseId?.let { entriesById[it]?.categoryId }
    fun categoryLabel(id: String?): String = categoryById[id]?.let { category ->
        if (categoryNameCounts.getValue(category.scope to category.normalizedName) > 1) {
            category.name + " · " + (wallet(category.walletId)?.name ?: "通用")
        } else category.name
    } ?: ""
    fun filterCategories(wallets: List<Wallet>): List<Category> {
        val walletIds = wallets.mapTo(mutableSetOf()) { it.id }
        val historical = entries.asSequence().filter { it.walletId in walletIds }.mapNotNull(::categoryId).toSet()
        return categories.filter {
            it.scope != Scope.PREPARATION &&
                ((it.walletId == null && !it.archived) || it.walletId in walletIds || it.id in historical)
        }.distinctBy(Category::normalizedName)
    }

    fun availableWallets() = wallets.filterNot { it.archived }
    val defaultWallet = wallet(records.state.defaultWalletId)?.takeUnless { it.archived }

    fun preferredWallet(): Wallet? {
        defaultWallet?.let { return it }
        val regular = availableWallets().filterNot { it.separateAccounting }.associateBy { it.id }
        val latest = entries.asSequence().filter { it.walletId in regular }.maxByOrNull { it.updatedAt }
        return regular[latest?.walletId] ?: regular.values.firstOrNull()
    }

    fun balance(): Map<String, BigInteger> {
        val sums = wallets.associate { it.id to it.openingBalanceMinor.toBigInteger() }.toMutableMap()
        for (entry in entries) {
            val amount = entry.amountMinor.toBigInteger()
            val debit = entry.type == EntryType.TRANSFER || entry.type == EntryType.EXPENSE
            sums[entry.walletId] = sums.getValue(entry.walletId) + if (debit) -amount else amount
            if (entry.type == EntryType.TRANSFER) {
                val id = checkNotNull(entry.toWalletId)
                sums[id] = sums.getValue(id) + checkNotNull(entry.toAmountMinor).toBigInteger()
            }
        }
        return sums
    }

    fun filtered(period: LedgerPeriod, range: WalletRange, filters: LedgerEntryFilters = LedgerEntryFilters()): List<FinancialEntry> {
        val ids = range.selected(wallets).map { it.id }.toSet()
        val query = filters.note.trim()
        return entries.filter {
            period.matches(it.date) && (it.walletId in ids || it.toWalletId in ids) &&
                (filters.types == null || it.type in filters.types) &&
                (filters.categoryNames == null || categoryById[categoryId(it)]?.normalizedName
                    ?.let { name -> name in filters.categoryNames } == true) &&
                it.note.contains(query, ignoreCase = true)
        }
    }

    fun statistics(period: LedgerPeriod, range: WalletRange): List<Totals> {
        val selected = range.selected(wallets)
        val ids = selected.map { it.id }.toSet()
        val grouped = entries.asSequence().filter {
            it.type != EntryType.TRANSFER && it.walletId in ids && period.matches(it.date)
        }.groupBy { walletById.getValue(it.walletId).currencyCode }
        return selected.groupBy { it.currencyCode }.toSortedMap().map { (currency, wallets) ->
            val income = mutableMapOf<String, BigInteger>()
            val expenses = mutableMapOf<String, BigInteger>()
            for (entry in grouped[currency].orEmpty()) {
                val id = checkNotNull(categoryId(entry))
                val amount = entry.amountMinor.toBigInteger()
                val destination = if (entry.type == EntryType.INCOME) income else expenses
                destination[id] = (destination[id] ?: BigInteger.ZERO) + if (entry.type == EntryType.REFUND) -amount else amount
            }
            Totals(currency, wallets.first().currencyScale,
                income.values.fold(BigInteger.ZERO, BigInteger::add), expenses.values.fold(BigInteger.ZERO, BigInteger::add),
                income.toMap(), expenses.toMap())
        }
    }

    fun refundRemaining(expense: FinancialEntry, except: String? = null): BigInteger =
        entries.asSequence().filter { it.originalExpenseId == expense.id && it.id != except }
            .fold(expense.amountMinor.toBigInteger()) { remaining, refund -> remaining - refund.amountMinor.toBigInteger() }
}
