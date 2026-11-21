package com.kitsuneapp.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class BackupKind { AUTO, MANUAL, PRE_RESTORE }

data class SnapshotInfo(val datasetId: String, val snapshotId: String, val revision: Long, val exportedAt: String, val kind: BackupKind)
data class SnapshotCounts(val meals: Int, val wallets: Int, val entries: Int)
class StagedSnapshot internal constructor(
    val info: SnapshotInfo,
    val counts: SnapshotCounts,
    internal val db: KitsuneDatabase,
    private val context: Context,
    private val name: String,
) : Closeable {
    override fun close() {
        db.close()
        context.deleteDatabase(name)
    }
}

class SnapshotStore(private val context: Context, private val repository: Repository) {
    suspend fun export(kind: BackupKind, output: OutputStream): SnapshotInfo = withContext(Dispatchers.IO) {
        repository.database.withTransaction {
            val data = repository.ownedState()
            require(data.arrayElements <= MAX_ARRAY_ELEMENTS) { "当前数据超过完整备份的记录上限" }
            val info = SnapshotInfo(data.datasetId, newId(), data.revision, utcNow(), kind)
            val dao = repository.dao
            jsonWriter(output).use { writer ->
                writer.beginObject()
                writer.field("format", "kitsune-backup")
                writer.field("formatVersion", 1)
                writer.field("datasetId", info.datasetId)
                writer.field("snapshotId", info.snapshotId)
                writer.field("revision", info.revision.toString())
                writer.field("exportedAt", info.exportedAt)
                writer.field("backupKind", info.kind)
                writer.name("data").beginObject()
                writer.array("categories", dao::categoryPage, Category::id) {
                    row(categoryFields, it.id, it.scope, it.walletId, it.name, it.sortOrder, it.archived, it.isDefault, it.createdAt, it.updatedAt)
                }
                writer.name("meals").beginObject()
                writer.array("methods", dao::methodPage, MealMethod::id) {
                    row(methodFields, it.id, it.name, it.sortOrder, it.archived, it.showPreparationCategories, it.createdAt, it.updatedAt)
                }
                writer.array("records", dao::mealPage, MealRecord::id) {
                    row(mealFields, it.id, it.date, it.mealSlot, it.plannedAtHome, it.mealMethodId, dao.preparationIds(it.id),
                        it.dishes, it.note, it.createdAt, it.updatedAt)
                }
                writer.endObject()
                writer.name("ledger").beginObject()
                writer.array("wallets", dao::walletPage, Wallet::id) {
                    row(walletFields, it.id, it.name, it.currencyCode, it.currencyScale, it.openingBalanceMinor.toString(),
                        it.sortOrder, it.archived, it.separateAccounting, it.incomeCategoryMode, it.expenseCategoryMode, it.createdAt, it.updatedAt)
                }
                writer.array("entries", dao::entryPage, FinancialEntry::id) {
                    row(entryFields, it.id, it.date, it.time, it.type, it.walletId, it.amountMinor.toString(), it.categoryId,
                        it.toWalletId, it.toAmountMinor?.toString(), it.originalExpenseId, it.note, it.createdAt, it.updatedAt)
                }
                writer.endObject()
                writer.name("settings").beginObject()
                writer.name("theme")
                val theme = checkNotNull(dao.theme())
                writer.row(themeFields, theme.themeMode, theme.colorSource, theme.presetId, theme.customSeedColor)
                writer.endObject()
                writer.name("importReceipts").beginArray()
                walkReceipts(dao) { receipt ->
                    writer.row(receiptFields, receipt.kind, receipt.sha256, receipt.importedAt)
                }
                writer.endArray()
                writer.endObject().endObject()
            }
            info
        }
    }

    suspend fun stage(input: InputStream): StagedSnapshot = withContext(Dispatchers.IO) {
        val name = File(context.cacheDir, "stage-" + newId() + ".db").path
        val stage = Room.databaseBuilder(context, KitsuneDatabase::class.java, name)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
        try {
            val info = stage.withTransaction {
                val parsed = jsonReader(input).use { parse(JsonInput(it), stage.records()) }
                stage.records().validateStaged()
                parsed
            }
            val dao = stage.records()
            currentCoroutineContext().ensureActive()
            StagedSnapshot(info, SnapshotCounts(dao.mealCount(), dao.walletCount(), dao.entryCount()), stage, context, name)
        } catch (error: Throwable) {
            stage.close()
            context.deleteDatabase(name)
            throw error
        }
    }

    suspend fun restore(staged: StagedSnapshot, protect: suspend (DataState) -> Unit) = withContext(Dispatchers.IO) {
        repository.writes.withLock {
            val state = repository.ownedState()
            if (state.revision != 0L) protect(state)
            val source = staged.db.records()
            val target = repository.dao
            repository.database.withTransaction {
                check(repository.ownedState() == state) { "本机数据已变化，请重新确认恢复" }
                target.clearEntries()
                target.clearPreparation()
                target.clearMeals()
                target.clearMethods()
                target.clearCategories()
                target.clearWallets()
                target.clearReceipts()
                walk(source::walletPage, Wallet::id) { target.insertWallet(it) }
                walk(source::categoryPage, Category::id) { target.insertCategory(it) }
                walk(source::methodPage, MealMethod::id) { target.insertMethod(it) }
                walk(source::mealPage, MealRecord::id) { meal ->
                    target.insertMeal(meal)
                    source.preparationIds(meal.id).chunked(128).forEach { ids ->
                        target.insertPreparation(ids.map { MealPreparationCategory(meal.id, it) })
                    }
                }
                walk(source::entryPage, FinancialEntry::id) { target.insertEntry(it) }
                walkReceipts(source) { target.insert(it) }
                target.put(checkNotNull(source.theme()))
                target.put(state.copy(datasetId = staged.info.datasetId,
                    revision = Math.addExact(state.revision, 1L), generation = Math.addExact(state.generation, 1L),
                    arrayElements = target.countElements()))
            }
            repository.notifyChanged()
        }
    }

    private suspend fun parse(input: JsonInput, dao: RecordsDao): SnapshotInfo = with(input) {
        val values = mutableMapOf<String, String>()
        val envelope = setOf("format", "formatVersion", "datasetId", "snapshotId", "revision", "exportedAt", "backupKind", "data")
        obj(envelope) { key ->
            when (key) {
                "format" -> require(string() == "kitsune-backup") { "文件不是 Kitsune 完整备份" }
                "formatVersion" -> require(int() == 1) { "不支持此备份格式版本" }
                "data" -> readData(this, dao)
                else -> values[key] = string()
            }
        }
        require(reader.peek() == JsonToken.END_DOCUMENT) { "备份文件末尾包含多余内容" }
        SnapshotInfo(validateId(values.getValue("datasetId")), validateId(values.getValue("snapshotId")),
            parseLongString(values.getValue("revision")).also { require(it >= 0) { "修订号不能为负数" } },
            canonicalTimestamp(values.getValue("exportedAt")), BackupKind.valueOf(values.getValue("backupKind")))
    }

    private suspend fun readData(input: JsonInput, dao: RecordsDao): Unit = with(input) {
        obj(setOf("categories", "meals", "ledger", "settings", "importReceipts")) { key ->
            when (key) {
                "categories" -> list { dao.insertCategory(row(categoryFields).category()) }
                "meals" -> obj(setOf("methods", "records")) { field ->
                    if (field == "methods") list { dao.insertMethod(row(methodFields).method()) }
                    else list {
                        val row = row(mealFields)
                        val meal = row.meal()
                        dao.insertMeal(meal)
                        row.idList("preparationCategoryIds").chunked(128).forEach { ids ->
                            dao.insertPreparation(ids.map { MealPreparationCategory(meal.id, it) })
                        }
                    }
                }
                "ledger" -> obj(setOf("wallets", "entries")) { field ->
                    if (field == "wallets") list { dao.insertWallet(row(walletFields).wallet()) }
                    else list { dao.insertEntry(row(entryFields).entry()) }
                }
                "settings" -> obj(setOf("theme")) { dao.put(row(themeFields).theme()) }
                "importReceipts" -> list { dao.insert(row(receiptFields).receipt()) }
            }
        }
    }
}

internal suspend fun <T> walk(page: suspend (String) -> List<T>, id: (T) -> String, action: suspend (T) -> Unit) {
    var cursor = ""
    while (true) {
        currentCoroutineContext().ensureActive()
        val batch = page(cursor)
        if (batch.isEmpty()) return
        for (value in batch) {
            currentCoroutineContext().ensureActive()
            action(value)
        }
        cursor = id(batch.last())
    }
}

private suspend fun walkReceipts(dao: RecordsDao, action: suspend (ImportReceipt) -> Unit) {
    for (kind in CsvKind.entries.sortedBy { it.name }) walk({ dao.receiptPage(kind, it) }, ImportReceipt::sha256, action)
}

private suspend fun <T> JsonWriter.array(name: String, page: suspend (String) -> List<T>, id: (T) -> String,
                                          write: suspend JsonWriter.(T) -> Unit) {
    name(name).beginArray()
    walk(page, id) { write(it) }
    endArray()
}

private suspend fun RecordsDao.validateStaged() {
    require(defaultCount(Scope.INCOME) == 1 && defaultCount(Scope.EXPENSE) == 1) { "缺少全局兜底分类" }
    require(inconsistentCurrency() == 0) { "同一币种钱包使用了不同精度" }
    require(invalidPreparation() == 0) { "制作分类关联无效" }
    require(invalidEntryCategory() == 0) { "账目引用了不属于该钱包或类型的分类" }
    require(invalidTransfer() == 0) { "同币种转账两侧金额不一致" }
    require(invalidRefund() == 0) { "退款原支出或币种无效" }
    var expense = ""
    var remaining = 0L
    var lastId = ""
    while (true) {
        currentCoroutineContext().ensureActive()
        val page = refundPage(expense, lastId)
        if (page.isEmpty()) break
        for (row in page) {
            if (expense != row.expenseId) {
                expense = row.expenseId
                remaining = row.originalAmount
            }
            require(row.refundAmount <= remaining) { "退款累计超过原支出金额" }
            remaining -= row.refundAmount
            lastId = row.refundId
        }
    }
}
