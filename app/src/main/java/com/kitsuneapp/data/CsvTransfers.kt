package com.kitsuneapp.data

import androidx.room.withTransaction
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class CsvPreview(val additions: Int, val duplicates: Int, val errors: List<String>, val elements: Int)
data class CsvMappings(val methods: Map<String, String> = emptyMap(), val wallets: Map<Pair<String, String>, String> = emptyMap())

class CsvDocument internal constructor(val kind: CsvKind, val sha256: String, internal val rows: List<CsvRow>) {
    fun methods() = if (kind == CsvKind.MEALS) rows.map { it["meal_method"] }.filter(String::isNotBlank).distinct() else emptyList()
    fun wallets() = if (kind == CsvKind.LEDGER) rows.flatMap {
        listOf(it["wallet"] to it["currency"], it["to_wallet"] to it["to_currency"])
    }.filter { it.first.isNotBlank() }.distinct() else emptyList()

    companion object {
        suspend fun load(kind: CsvKind, input: InputStream): CsvDocument = withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            val rows = DigestInputStream(input, digest).use { stream ->
                InputStreamReader(stream, UTF_8.newDecoder()).use { CsvReader(it).read(kind.columns) }
            }
            CsvDocument(kind, digest.digest().hex(), rows)
        }
    }
}

internal fun ByteArray.hex() = joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
private val CsvKind.columns get() = if (this == CsvKind.MEALS) mealColumns else ledgerColumns
private class PreviewComplete(val preview: CsvPreview) : RuntimeException()

class CsvTransfers(private val repository: Repository) {
    suspend fun preview(file: CsvDocument, mappings: CsvMappings): CsvPreview = try {
        repository.change { throw PreviewComplete(apply(file, mappings)) }
        error("Preview did not roll back")
    } catch (result: PreviewComplete) {
        result.preview
    }

    suspend fun commit(file: CsvDocument, mappings: CsvMappings): CsvPreview {
        lateinit var result: CsvPreview
        repository.change {
            result = apply(file, mappings)
            require(result.errors.isEmpty()) { result.errors.first() }
            result.additions > 0
        }
        return result
    }

    private suspend fun apply(file: CsvDocument, mappings: CsvMappings): CsvPreview {
        val dao = repository.dao
        val baseline = repository.ownedState()
        if (dao.receipt(file.kind, file.sha256) != null && file.rows.any { it["id"].isEmpty() }) {
            return CsvPreview(0, file.rows.size, emptyList(), baseline.arrayElements)
        }
        val errors = mutableListOf<String>()
        val duplicateIds = file.rows.map { it["id"] }.filter(String::isNotEmpty).groupingBy { it }.eachCount()
        var added = 0
        var duplicates = 0
        val rows = if (file.kind == CsvKind.MEALS) file.rows else file.rows.sortedBy { it["type"] == "REFUND" }
        for (row in rows) {
            currentCoroutineContext().ensureActive()
            try {
                require((duplicateIds[row["id"]] ?: 0) < 2) { "同一文件中 ID 重复" }
                val changed = if (file.kind == CsvKind.MEALS) writeMeal(row, mappings) else writeEntry(row, mappings)
                if (changed) added++ else duplicates++
            } catch (failure: IllegalArgumentException) {
                errors += row.position + " · " + (failure.message ?: "字段无效")
            }
        }
        if (added > 0 && dao.receipt(file.kind, file.sha256) == null) dao.insert(ImportReceipt(file.kind, file.sha256, utcNow()))
        val elements = dao.countElements()
        if (elements > MAX_ARRAY_ELEMENTS && elements >= baseline.arrayElements) errors += "完整备份的记录和关联总数超过 200,000"
        return CsvPreview(added, duplicates, errors, elements)
    }

    private suspend fun writeMeal(row: CsvRow, mappings: CsvMappings): Boolean {
        val dao = repository.dao
        val id = row.id()
        val previous = dao.meal(id)
        val date = validateDate(row["date"])
        val slot = csvEnum<MealSlot>(row["meal_slot"])
        val home = requireNotNull(row["planned_at_home"].toBooleanStrictOrNull()) { "计划在家吃必须是 true 或 false" }
        val method = row["meal_method"].takeIf(String::isNotBlank)?.let { name ->
            val mapped = mappings.methods[name]
            requireNotNull(if (mapped != null) dao.method(mapped) else dao.methodByName(normalizedName(name))) { "请映射用餐方式“$name”" }
        }
        val names = preparationNames(row["preparation_categories"])
        require(method != null || (!home && names.isEmpty())) { "请提供用餐方式，未填方式时不能包含制作分类" }
        require(dao.mealAt(date, slot)?.id.let { it == null || it == id }) { "日期和餐次已被另一条记录占用" }
        val preparation = names.map { repository.resolveCategory(Scope.PREPARATION, null, it, allowArchived = true).id }.toSet()
        require(preparation.size == names.size) { "制作分类重复" }
        val now = utcNow()
        val meal = MealRecord(id, date, slot, home, method?.id, row["dishes"], row["note"], previous?.createdAt ?: now, previous?.updatedAt ?: now)
        validateField("菜品", meal.dishes)
        validateField("备注", meal.note)
        if (previous != null) {
            require(previous == meal && dao.preparationIds(id).toSet() == preparation) { "相同 ID 的用餐内容不同" }
            return false
        }
        dao.insertMeal(meal)
        dao.insertPreparation(preparation.map { MealPreparationCategory(id, it) })
        return true
    }

    private suspend fun writeEntry(row: CsvRow, mappings: CsvMappings): Boolean {
        val dao = repository.dao
        val id = row.id()
        val previous = dao.entry(id)
        val type = csvEnum<EntryType>(row["type"])
        val wallet = lookupWallet(row["wallet"], row["currency"], mappings)
        val amount = decimalMinor(row["amount"], wallet.currencyScale)
        val scope = type.scope
        val name = row["category"].ifBlank { "未分类" }
        val category = if (scope != null) {
            val prior = previous?.categoryId?.let { dao.category(it) }?.takeIf {
                previous.walletId == wallet.id && it.scope == scope && it.normalizedName == normalizedName(name)
            }
            val owner = wallet.id.takeIf { wallet.categoryMode(scope) == CategoryMode.CUSTOM }
            prior ?: repository.resolveCategory(scope, owner, name, allowArchived = true)
        } else null
        val target = if (type == EntryType.TRANSFER) lookupWallet(row["to_wallet"], row["to_currency"], mappings) else null
        val targetAmount = if (target != null) decimalMinor(row["to_amount"], target.currencyScale) else null
        if (target != null) {
            require(row["category"].isEmpty() && wallet.id != target.id) { "转账不得填分类，且须使用两个不同的钱包" }
            require(wallet.currencyCode != target.currencyCode || amount == targetAmount) { "同币种转账两侧金额必须相等" }
        } else require(listOf("to_wallet", "to_currency", "to_amount").all { row[it].isEmpty() }) { "非转账不得填写到账列" }
        val originalId = row["original_expense_id"]
        val original = if (type == EntryType.REFUND) {
            val expense = requireNotNull(dao.entry(validateId(originalId))) { "退款引用的原支出不存在" }
            require(expense.type == EntryType.EXPENSE) { "退款引用的原支出不存在" }
            require(dao.wallet(expense.walletId)?.currencyCode == wallet.currencyCode) { "退款钱包与原支出币种不同" }
            if (row["category"].isNotEmpty()) {
                require(expense.categoryId?.let { dao.category(it)?.normalizedName } == normalizedName(row["category"])) {
                    "退款分类与原支出不匹配"
                }
            }
            val otherRefunds = dao.refunds(expense.id).filter { it.id != id }
                .fold(0.toBigInteger()) { sum, entry -> sum + entry.amountMinor.toBigInteger() }
            require(otherRefunds + amount.toBigInteger() <= expense.amountMinor.toBigInteger()) { "退款累计超过原支出" }
            expense
        } else {
            require(originalId.isEmpty()) { "仅退款可填写原支出 ID" }
            null
        }
        val now = utcNow()
        validateField("备注", row["note"])
        val entry = FinancialEntry(id, validateDate(row["date"]), validateTime(row["time"]), type, wallet.id, amount,
            category?.id, target?.id, targetAmount, original?.id, row["note"], previous?.createdAt ?: now, previous?.updatedAt ?: now)
        if (previous != null) {
            require(entry == previous) { "相同 ID 的账目内容不同" }
            return false
        }
        dao.insertEntry(entry)
        return true
    }

    private suspend fun lookupWallet(name: String, code: String, mappings: CsvMappings): Wallet {
        val mapped = mappings.wallets[name to code]
        val wallet = if (mapped != null) repository.dao.wallet(mapped) else repository.dao.walletByName(normalizedName(name), code)
        require(wallet != null && wallet.currencyCode == code) { "请映射币种为 $code 的钱包“$name”" }
        return wallet
    }

    suspend fun export(kind: CsvKind, output: OutputStream, template: Boolean = false) = withContext(Dispatchers.IO) {
        OutputStreamWriter(output, UTF_8).buffered().use { writer ->
            writer.writeCsvRow(kind.columns)
            if (template) return@use
            val dao = repository.dao
            repository.database.withTransaction {
                repository.ownedState()
                if (kind == CsvKind.MEALS) walk(dao::mealPage, MealRecord::id) { meal ->
                    val method = meal.mealMethodId?.let { dao.method(it)?.name }.orEmpty()
                    val names = dao.preparationIds(meal.id).map { checkNotNull(dao.category(it)).name }
                    writer.writeCsvRow(listOf(meal.id, meal.date, meal.mealSlot.name, meal.plannedAtHome.toString(), method,
                        com.google.gson.Gson().toJson(names), meal.dishes, meal.note))
                } else walk(dao::entryPage, FinancialEntry::id) { entry ->
                    val wallet = checkNotNull(dao.wallet(entry.walletId))
                    val target = entry.toWalletId?.let { dao.wallet(it) }
                    val categoryId = entry.categoryId ?: entry.originalExpenseId?.let { dao.entry(it)?.categoryId }
                    val category = categoryId?.let { dao.category(it)?.name }.orEmpty()
                    writer.writeCsvRow(listOf(entry.id, entry.date, entry.time, entry.type.name, wallet.name, wallet.currencyCode,
                        money(entry.amountMinor, wallet.currencyScale), category, target?.name.orEmpty(), target?.currencyCode.orEmpty(),
                        entry.toAmountMinor?.let { money(it, checkNotNull(target).currencyScale) }.orEmpty(), entry.originalExpenseId.orEmpty(), entry.note))
                }
            }
        }
    }

    private suspend fun preparationNames(field: String): List<String> {
        if (field.isEmpty()) return emptyList()
        return jsonReader(ByteArrayInputStream(field.toByteArray(UTF_8))).use { reader ->
            val items = mutableListOf<String>()
            val input = JsonInput(reader)
            input.list { items += cleanName(input.string()) }
            require(reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT) { "制作分类数组末尾有多余文本" }
            items
        }
    }
}

private fun CsvRow.id() = get("id").takeIf(String::isNotEmpty)?.let(::validateId) ?: newId()
private inline fun <reified T : Enum<T>> csvEnum(value: String): T =
    enumValues<T>().find { it.name == value } ?: throw IllegalArgumentException("无效枚举值“$value”")
