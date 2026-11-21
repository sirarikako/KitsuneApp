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
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, KitsuneDatabase::class.java).build()
        repository = Repository(database, newId())
        repository.initialize(true)
        csv = CsvTransfers(repository)
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun mealPreviewRollsBackAndReceiptStopsRepeatOfIdlessFile() = runBlocking {
        val source = mealColumns.joinToString(",") + "\n,2026-11-21,LUNCH,true,下厨,\"[\"\"凉菜\"\"]\",面,\n"
        val document = CsvDocument.load(CsvKind.MEALS, source.byteInputStream())
        val before = repository.read()
        assertEquals(1, csv.preview(document, CsvMappings()).additions)
        assertEquals(before, repository.read())
        csv.commit(document, CsvMappings())
        val applied = repository.read()
        assertEquals(1L, applied.state.revision)
        assertEquals(listOf("凉菜"), applied.preparationNames(applied.meals.single().id))
        assertEquals(1, csv.commit(document, CsvMappings()).duplicates)
        assertEquals(applied, repository.read())
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
