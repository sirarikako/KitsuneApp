package com.kitsuneapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapshotTest {
    private lateinit var database: KitsuneDatabase
    private lateinit var repository: Repository
    private lateinit var store: SnapshotStore
    private lateinit var context: Context
    private lateinit var sample: String

    @Before
    fun setUp() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        sample = instrumentation.context.assets.open("backup-v1.json").bufferedReader().use { it.readText() }
        database = Room.inMemoryDatabaseBuilder(context, KitsuneDatabase::class.java).build()
        repository = Repository(database, newId())
        repository.initialize(true)
        store = SnapshotStore(context, repository)
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun documentedFixtureRoundTripsWithHistoricalFieldsAndAccurateBalances() = runBlocking {
        store.stage(sample.byteInputStream()).use { staged ->
            assertEquals(SnapshotCounts(1, 2, 3), staged.counts)
            store.restore(staged) { fail("Fresh database should not require protection") }
            assertEquals(1L, repository.read().state.revision)
            assertEquals(staged.info.datasetId, repository.read().state.datasetId)
        }
        val records = repository.read()
        val ledger = Ledger(records)
        val wallets = records.wallets.associateBy { it.currencyCode }
        assertEquals("8000", ledger.balance()[wallets.getValue("CNY").id].toString())
        assertEquals("200", ledger.balance()[wallets.getValue("JPY").id].toString())
        assertTrue(wallets.getValue("JPY").separateAccounting)
        val output = ByteArrayOutputStream()
        store.export(BackupKind.MANUAL, output)
        assertTrue(output.toString("UTF-8").contains("\"openingBalanceMinor\": \"10000\""))
        store.stage(output.toByteArray().inputStream()).use { assertEquals(SnapshotCounts(1, 2, 3), it.counts) }
    }

    @Test
    fun folderSnapshotsStorePlusEightAndRestoreCanonicalUtc() = runBlocking {
        val sha = "ab".repeat(32)
        val fixture = sample.replace("\"importReceipts\": []", """"importReceipts": [
            {"kind":"LEDGER","sha256":"$sha","importedAt":"2026-11-16T20:30:00.456Z"}
        ]""")
        store.stage(fixture.byteInputStream()).use { store.restore(it) { error("Unexpected protection backup") } }
        val source = repository.read()
        val absoluteTime = Regex(""""(?:exportedAt|createdAt|updatedAt|importedAt)": "([^"]+)"""")

        for (kind in listOf(BackupKind.AUTO, BackupKind.PRE_RESTORE)) {
            val output = ByteArrayOutputStream()
            val info = store.export(kind, output, forFolder = true)
            val json = output.toString("UTF-8")
            val times = absoluteTime.findAll(json).map { it.groupValues[1] }.toList()
            assertEquals(24, times.size)
            assertEquals(info.exportedAt, times.first())
            assertTrue(times.all { it.endsWith("+08:00") })
            assertTrue(json.contains("\"importedAt\": \"2026-11-17T04:30:00.456+08:00\""))

            store.stage(output.toByteArray().inputStream()).use { staged ->
                val dao = staged.db.records()
                assertEquals(info, staged.info)
                assertEquals(source.categories, dao.categories())
                assertEquals(source.methods, dao.methods())
                assertEquals(source.meals, dao.meals())
                assertEquals(source.wallets, dao.wallets())
                assertEquals(source.entries, dao.entries())
                assertEquals(repository.dao.receipts(), dao.receipts())
            }
        }

        val output = ByteArrayOutputStream()
        store.export(BackupKind.AUTO, output)
        val times = absoluteTime.findAll(output.toString("UTF-8")).map { it.groupValues[1] }.toList()
        assertEquals(24, times.size)
        assertTrue(times.all { it.endsWith("Z") })
    }

    @Test
    fun oldV1DefaultsAndFailedProtectionPreserveCurrentDataset() = runBlocking {
        val previous = sample.replace(Regex("\"walletId\":null,"), "")
            .replace(Regex(",\"separateAccounting\":(?:true|false),\"incomeCategoryMode\":\"DEFAULT\",\"expenseCategoryMode\":\"DEFAULT\""), "")
        repository.applyTheme(ThemePreferences(themeMode = ThemeMode.DARK))
        val before = repository.read()
        store.stage(previous.byteInputStream()).use { staged ->
            assertTrue(runCatching { store.restore(staged) { error("无法保存保护文件") } }.isFailure)
            assertEquals(before, repository.read())
            assertTrue(staged.db.records().wallets().none { it.separateAccounting })
            assertTrue(staged.db.records().wallets().all { it.incomeCategoryMode == CategoryMode.DEFAULT })
        }
    }

    @Test
    fun rejectsDuplicateKeysUnknownVersionBadForeignKeysAndMissingTime() = runBlocking {
        val before = repository.read()
        val malformed = listOf(
            sample.replace("\"formatVersion\": 1", "\"formatVersion\": 9"),
            sample.replace("\"formatVersion\": 1", "\"formatVersion\": 1, \"formatVersion\": 1"),
            sample.replace("\"time\":\"18:30\",", ""),
            sample.replace("\"toWalletId\":\"40000000-0000-4000-8000-000000000002\"", "\"toWalletId\":\"40000000-0000-4000-8000-000000000099\""),
            sample.replace("\"amountMinor\":\"280\"", "\"amountMinor\":\"1281\""),
        )
        malformed.forEach { assertTrue(runCatching { store.stage(it.byteInputStream()).close() }.isFailure) }
        assertEquals(before, repository.read())
    }
}
