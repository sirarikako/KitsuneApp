package com.kitsuneapp.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryTest {
    private lateinit var database: KitsuneDatabase
    private lateinit var repo: Repository
    private lateinit var a: Wallet
    private lateinit var b: Wallet
    private lateinit var jpy: Wallet
    private lateinit var expenseCategory: Category
    private val month = LedgerPeriod("2026-11")

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, KitsuneDatabase::class.java).build()
        repo = Repository(database, newId())
        repo.initialize(true)
        listOf(Triple("常规", "CNY", false), Triple("旅行", "CNY", true), Triple("日元", "JPY", true))
            .forEachIndexed { index, (name, code, separate) -> repo.saveWallet(WalletDraft(null, name, code, "0", index, false, separate)) }
        val data = repo.read()
        a = data.wallets[0]
        b = data.wallets[1]
        jpy = data.wallets[2]
        expenseCategory = data.categories.first { it.scope == Scope.EXPENSE && it.isDefault }
    }

    @After
    fun tearDown() { database.close() }

    private fun entry(type: EntryType, wallet: Wallet, amount: String, other: Wallet? = null, toAmount: String = "",
                      original: String? = null, category: String? = null, date: String = "2026-11-21") =
        EntryDraft(null, date, "18:30", type, wallet.id, amount, category, "",
            other?.id, toAmount, original, "")

    @Test
    fun olderDatabasesKeepWalletsAndDefaultsWhenAddingIncomePreference() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val state = repo.read().state
        val migration = MigrationTestHelper(instrumentation, KitsuneDatabase::class.java)
        for (version in 1..2) {
            val name = "wallet-migration-" + newId()
            val defaultWalletId = a.id.takeIf { version == 2 }
            try {
                migration.createDatabase(name, version).use { old ->
                    old.execSQL(
                        "INSERT INTO DataState (`key`, installationId, datasetId, revision, generation, arrayElements) VALUES (0, ?, ?, ?, ?, ?)",
                        arrayOf<Any>(state.installationId, state.datasetId, state.revision, state.generation, state.arrayElements),
                    )
                    old.execSQL(
                        """INSERT INTO Wallet (id, name, normalizedName, currencyCode, currencyScale, openingBalanceMinor,
                            sortOrder, archived, separateAccounting, incomeCategoryMode, expenseCategoryMode, createdAt, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?)""",
                        arrayOf<Any>(a.id, a.name, a.normalizedName, a.currencyCode, a.currencyScale, a.openingBalanceMinor,
                            a.sortOrder, a.incomeCategoryMode.name, a.expenseCategoryMode.name, a.createdAt, a.updatedAt),
                    )
                    if (defaultWalletId != null) old.execSQL("UPDATE DataState SET defaultWalletId = ?", arrayOf(defaultWalletId))
                }
                val upgraded = Room.databaseBuilder(context, KitsuneDatabase::class.java, name).build()
                try {
                    assertEquals(state.copy(defaultWalletId = defaultWalletId), upgraded.records().state())
                    assertEquals(a, upgraded.records().wallet(a.id))
                } finally {
                    upgraded.close()
                }
            } finally {
                context.deleteDatabase(name)
            }
        }
    }

    @Test
    fun explicitDefaultTakesPrecedenceAndClearsWhenArchivedOrDeleted() = runBlocking {
        fun draft(wallet: Wallet, default: Boolean? = null, archived: Boolean = false) =
            WalletDraft(wallet.id, wallet.name, wallet.currencyCode, "0", wallet.sortOrder, archived,
                wallet.separateAccounting, setAsDefault = default)

        repo.saveEntry(entry(EntryType.EXPENSE, a, "1", category = expenseCategory.id))
        assertEquals(a.id, Ledger(repo.read()).preferredWallet()?.id)
        repo.saveWallet(draft(b, default = true))
        assertEquals(b.id, Ledger(repo.read()).preferredWallet()?.id)
        assertEquals(listOf(a.id), WalletRange().selected(repo.read().wallets).map { it.id })

        repo.saveWallet(draft(jpy, default = true))
        repo.saveWallet(draft(b, default = false))
        assertEquals(jpy.id, Ledger(repo.read()).defaultWallet?.id)
        repo.saveWallet(draft(jpy, archived = true))
        assertNull(repo.read().state.defaultWalletId)
        repo.saveWallet(draft(jpy))
        assertEquals(a.id, Ledger(repo.read()).preferredWallet()?.id)

        repo.saveWallet(draft(b, default = true))
        repo.removeWallet(b.id)
        assertNull(repo.read().state.defaultWalletId)
        assertEquals(a.id, Ledger(repo.read()).preferredWallet()?.id)

        repo.saveWallet(draft(a, archived = true))
        assertNull(Ledger(repo.read()).preferredWallet())
        repo.saveWallet(WalletDraft(null, "新默认钱包", "JPY", "0", 3,
            archived = false, separateAccounting = true, setAsDefault = true))
        assertEquals("新默认钱包", Ledger(repo.read()).preferredWallet()?.name)
    }

    @Test
    fun refundsFollowDestinationAcrossWalletAndMonthWhileTransfersDoNotCount() = runBlocking {
        repo.saveEntry(entry(EntryType.EXPENSE, a, "100", category = expenseCategory.id, date = "2026-10-21"))
        val out = repo.read().entries.single()
        repo.saveEntry(entry(EntryType.REFUND, b, "30", original = out.id))
        repo.saveEntry(entry(EntryType.TRANSFER, a, "10", other = jpy, toAmount = "200"))
        val ledger = Ledger(repo.read())
        assertEquals("-11000", ledger.balance()[a.id].toString())
        assertEquals("3000", ledger.balance()[b.id].toString())
        assertEquals("200", ledger.balance()[jpy.id].toString())
        assertEquals("0", ledger.statistics(month, WalletRange()).single().expenses.toString())
        assertEquals("-3000", ledger.statistics(month, WalletRange(RangeMode.MANUAL, listOf(b.id))).single()
            .expenseCategories[expenseCategory.id].toString())
        assertEquals(1, ledger.filtered(month, WalletRange(RangeMode.MANUAL, listOf(jpy.id))).size)
    }

    @Test
    fun invalidOrConcurrentOverRefundCannotChangeData() = runBlocking {
        repo.saveEntry(entry(EntryType.EXPENSE, a, "100", category = expenseCategory.id))
        val original = repo.read().entries.single()
        val outcomes = supervisorScope {
            List(2) { async { runCatching { repo.saveEntry(entry(EntryType.REFUND, b, "70", original = original.id)) } } }.awaitAll()
        }
        assertEquals(1, outcomes.count { it.isSuccess })
        val before = repo.read()
        assertTrue(runCatching { repo.removeEntry(original.id) }.isFailure)
        assertTrue(runCatching { repo.saveEntry(entry(EntryType.REFUND, jpy, "20", original = original.id)) }.isFailure)
        assertTrue(runCatching {
            repo.saveEntry(entry(EntryType.EXPENSE, a, "69", category = expenseCategory.id).copy(id = original.id))
        }.isFailure)
        assertTrue(runCatching { repo.saveEntry(entry(EntryType.TRANSFER, a, "1", other = jpy, toAmount = "1.5")) }.isFailure)
        assertEquals(before, repo.read())
    }

    @Test
    fun customCategoriesStayScopedAndHistoricalEditsKeepIds() = runBlocking {
        repo.saveCategory(null, Scope.EXPENSE, null, "餐饮", 1, false)
        repo.setCategoryMode(a.id, Scope.EXPENSE, CategoryMode.CUSTOM)
        repo.setCategoryMode(b.id, Scope.EXPENSE, CategoryMode.CUSTOM)
        val empty = repo.read()
        val walletA = empty.wallets.first { it.id == a.id }
        assertEquals(listOf(expenseCategory.id), empty.categories.filter { allowedCategory(it, walletA, Scope.EXPENSE) }.map { it.id })
        repo.saveCategory(null, Scope.EXPENSE, a.id, "餐饮", 0, false)
        repo.saveCategory(null, Scope.EXPENSE, b.id, "餐饮", 0, false)
        val categories = repo.read().categories
        val privateA = categories.first { it.walletId == a.id }
        val privateB = categories.first { it.walletId == b.id }
        assertTrue(runCatching { repo.saveEntry(entry(EntryType.EXPENSE, a, "3", category = privateB.id)) }.isFailure)
        val draft = entry(EntryType.EXPENSE, a, "3", category = privateA.id)
        repo.saveEntry(draft)
        val saved = repo.read().entries.single()
        repo.setCategoryMode(a.id, Scope.EXPENSE, CategoryMode.DEFAULT)
        repo.saveEntry(draft.copy(id = saved.id, note = "保留"))
        assertEquals(privateA.id, repo.read().entries.single().categoryId)
    }
}
