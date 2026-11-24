package com.kitsuneapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CsvTransfersTest {
    private lateinit var database: KitsuneDatabase
    private lateinit var repository: Repository
    private lateinit var csv: CsvTransfers

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, KitsuneDatabase::class.java).build()
        repository = Repository(database, newId())
        repository.initialize(true)
        csv = CsvTransfers(repository, SnapshotStore(context, repository))
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun mealPreviewRollsBackAndReceiptStopsRepeatOfIdlessFile() = runBlocking {
        val source = mealColumns.joinToString(",") + "\n,2026-11-21,LUNCH,true,下厨,\"[\"\"凉菜\"\"]\",面,\n"
        val document = CsvDocument.load(CsvKind.MEALS, source.byteInputStream())
        val before = repository.read()
        val preview = csv.preview(document, CsvMappings(), estimateCloud = true)
        assertEquals(1, preview.additions)
        assertTrue(checkNotNull(preview.snapshotBytes) > 0L)
        assertEquals(before, repository.read())
        csv.commit(document, CsvMappings())
        val applied = repository.read()
        assertEquals(1L, applied.state.revision)
        assertEquals(listOf("凉菜"), applied.preparationNames(applied.meals.single().id))
        assertEquals(1, csv.commit(document, CsvMappings()).duplicates)
        assertEquals(applied, repository.read())
    }

    @Test
    fun explicitMappingsResolveUnknownMethodAndSameCurrencyWallet() = runBlocking {
        repository.saveWallet(WalletDraft(null, "常规", "CNY", "0", 0, false, false))
        repository.saveWallet(WalletDraft(null, "旅行", "JPY", "0", 1, false, true))
        val before = repository.read()
        val method = before.methods.single { it.name == "外卖" }
        val regular = before.wallets.single { it.currencyCode == "CNY" }
        val travel = before.wallets.single { it.currencyCode == "JPY" }
        val source = mealColumns.joinToString(",") + "\n,2026-11-23,LUNCH,true,外送,[],汤,\n"
        val meal = CsvDocument.load(CsvKind.MEALS, source.byteInputStream())
        assertTrue(csv.preview(meal, CsvMappings()).errors.isNotEmpty())
        assertEquals(1, csv.commit(meal, CsvMappings(methods = mapOf("外送" to method.id))).additions)
        assertEquals(method.id, repository.read().meals.single().mealMethodId)

        val ledgerSource = ledgerColumns.joinToString(",") + "\n,2026-11-23,10:20,EXPENSE,另一钱包,CNY,2,早餐,,,,,\n"
        val entry = CsvDocument.load(CsvKind.LEDGER, ledgerSource.byteInputStream())
        val key = "另一钱包" to "CNY"
        assertTrue(csv.preview(entry, CsvMappings(wallets = mapOf(key to travel.id))).errors.isNotEmpty())
        assertEquals(1, csv.commit(entry, CsvMappings(wallets = mapOf(key to regular.id))).additions)
        assertEquals(regular.id, repository.read().entries.single().walletId)
    }

    @Test
    fun ledgerImportResolvesSameBatchRefundsAndRejectsTheWholeIncorrectBatch() = runBlocking {
        repository.saveWallet(WalletDraft(null, "常规", "CNY", "0", 0, false, false))
        val id = newId()
        val source = ledgerColumns.joinToString(",") + "\n,2026-11-21,10:20,REFUND,常规,CNY,2,早餐,,,,$id,\n" +
            "$id,2026-11-21,09:30,EXPENSE,常规,CNY,3,早餐,,,,,\n"
        val file = CsvDocument.load(CsvKind.LEDGER, source.byteInputStream())
        val invalid = CsvDocument.load(CsvKind.LEDGER, source.replace(",2,早餐", ",4,早餐").byteInputStream())
        val before = repository.read()
        assertTrue(csv.preview(invalid, CsvMappings()).errors.isNotEmpty())
        assertTrue(runCatching { csv.commit(invalid, CsvMappings()) }.isFailure)
        assertEquals(before, repository.read())
        assertEquals(2, csv.commit(file, CsvMappings()).additions)
        assertEquals(2, repository.read().entries.size)
    }
}
