package com.kitsuneapp.data

import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

internal enum class FieldType { TEXT, TIMESTAMP, OPTIONAL_TEXT, FLAG, INT, IDS }
internal data class BackupField(val key: String, val type: FieldType = FieldType.TEXT, val absent: Boolean = false)
internal fun text(key: String) = BackupField(key)
internal fun reference(key: String, absent: Boolean = false) = BackupField(key, FieldType.OPTIONAL_TEXT, absent)
internal fun flag(key: String, absent: Boolean = false) = BackupField(key, FieldType.FLAG, absent)
internal fun number(key: String) = BackupField(key, FieldType.INT)
internal fun ids(key: String) = BackupField(key, FieldType.IDS)
internal fun legacy(key: String) = BackupField(key, absent = true)
private fun timestamp(key: String) = BackupField(key, FieldType.TIMESTAMP)
private val audit = listOf(timestamp("createdAt"), timestamp("updatedAt"))

internal val methodFields = listOf(text("id"), text("name"), number("sortOrder"), flag("archived"), flag("showPreparationCategories")) + audit
internal val categoryFields = listOf(text("id"), text("scope"), reference("walletId", true), text("name"),
    number("sortOrder"), flag("archived"), flag("isDefault"), reference("icon", true)) + audit
internal val mealFields = listOf(text("id"), text("date"), text("mealSlot"), flag("plannedAtHome"), reference("mealMethodId"),
    ids("preparationCategoryIds"), text("dishes"), text("note")) + audit
internal val walletFields = listOf(text("id"), text("name"), text("currencyCode"), number("currencyScale"), text("openingBalanceMinor"),
    number("sortOrder"), flag("archived"), flag("separateAccounting", true),
    legacy("incomeCategoryMode"), legacy("expenseCategoryMode")) + audit
internal val entryFields = listOf(text("id"), text("date"), text("time"), text("type"), text("walletId"), text("amountMinor"),
    reference("categoryId"), reference("toWalletId"), reference("toAmountMinor"), reference("originalExpenseId"), text("note")) + audit
internal val themeFields = listOf(text("themeMode"), text("colorSource"), text("presetId"), text("customSeedColor"))
internal val receiptFields = listOf(text("kind"), text("sha256"), timestamp("importedAt"))

internal class RowReader(private val fields: Map<String, Any?>) {
    fun has(key: String) = fields.containsKey(key)
    fun text(key: String) = fields.getValue(key) as String
    fun optional(key: String) = fields[key] as String?
    fun flag(key: String) = fields[key] as? Boolean ?: false
    fun int(key: String) = fields.getValue(key) as Int
    fun id(key: String = "id") = validateId(text(key))
    fun ref(key: String) = optional(key)?.let(::validateId)
    fun name() = cleanName(text("name"))
    fun time(key: String) = canonicalTimestamp(text(key))
    fun order() = int("sortOrder").also { require(it >= 0) { "排序必须为非负整数" } }
    @Suppress("UNCHECKED_CAST")
    fun idList(key: String) = fields.getValue(key) as List<String>
    inline fun <reified T : Enum<T>> enum(key: String, default: T? = null): T {
        val value = optional(key)
        if (value == null && default != null) return default
        return enumValues<T>().find { it.name == value } ?: throw IllegalArgumentException("无效的枚举字段 $key")
    }
}

internal suspend fun JsonInput.row(specification: List<BackupField>): RowReader {
    val spec = specification.associateBy { it.key }
    val required = specification.filterNot { it.absent }.mapTo(mutableSetOf()) { it.key }
    val values = mutableMapOf<String, Any?>()
    obj(required, spec.keys - required) { key ->
        values[key] = when (spec.getValue(key).type) {
            FieldType.TEXT, FieldType.TIMESTAMP -> string().also { validateField(key, it) }
            FieldType.OPTIONAL_TEXT -> nullable { string().also { validateField(key, it) } }
            FieldType.FLAG -> bool()
            FieldType.INT -> int()
            FieldType.IDS -> {
                val ids = linkedSetOf<String>()
                list { require(ids.add(validateId(string()))) { "制作分类关联重复" } }
                ids.toList()
            }
        }
    }
    return RowReader(values)
}

internal fun JsonWriter.row(spec: List<BackupField>, vararg values: Any?) {
    check(values.size == spec.size)
    beginObject()
    spec.zip(values.toList()).forEach { (field, value) ->
        if (value is List<*>) {
            name(field.key).beginArray()
            value.forEach { value(it as String) }
            endArray()
        } else {
            if (value is String) validateField(field.key, value)
            field(field.key, if (field.type == FieldType.TIMESTAMP) backupTimestamp(value as String) else value)
        }
    }
    endObject()
}

internal fun RowReader.method(): MealMethod = name().let {
    MealMethod(id(), it, normalizedName(it), order(), flag("archived"), flag("showPreparationCategories"), time("createdAt"), time("updatedAt"))
}

internal fun RowReader.category(): Category {
    val scope = enum<Scope>("scope")
    val wallet = ref("walletId")
    val name = name()
    val default = flag("isDefault")
    val archived = flag("archived")
    val icon = optional("icon")?.let { enum<CategoryIcon>("icon") }
    require(wallet == null || scope != Scope.PREPARATION) { "制作分类必须是通用分类" }
    require(!default || (scope != Scope.PREPARATION && name == "未分类" && !archived && wallet == null)) { "兜底分类无效" }
    require(scope == Scope.PREPARATION || name != "未分类" || default) { "分类名称“未分类”已经保留" }
    require(icon == null || (!default && scope != Scope.PREPARATION)) { "分类图标无效" }
    return Category(id(), scope, wallet, wallet ?: GLOBAL_OWNER, name, normalizedName(name), order(), archived,
        default, time("createdAt"), time("updatedAt"), icon)
}

internal fun RowReader.meal(): MealRecord {
    val method = ref("mealMethodId")
    val home = flag("plannedAtHome")
    require(method != null || (!home && idList("preparationCategoryIds").isEmpty())) { "用餐方式和制作分类不匹配" }
    return MealRecord(id(), validateDate(text("date")), enum("mealSlot"), home, method, text("dishes"), text("note"),
        time("createdAt"), time("updatedAt"))
}

internal fun RowReader.wallet(): Wallet {
    val code = text("currencyCode")
    val scale = int("currencyScale")
    val name = name()
    validateCurrency(code, scale)
    return Wallet(id(), name, normalizedName(name), code, scale, parseLongString(text("openingBalanceMinor")), order(),
        flag("archived"), flag("separateAccounting"), enum("incomeCategoryMode", CategoryMode.DEFAULT),
        enum("expenseCategoryMode", CategoryMode.DEFAULT), time("createdAt"), time("updatedAt"))
}

internal fun RowReader.entry(): FinancialEntry {
    val type = enum<EntryType>("type")
    val walletId = id("walletId")
    val amount = parseLongString(text("amountMinor"))
    val to = ref("toWalletId")
    val toAmount = optional("toAmountMinor")?.let(::parseLongString)
    val category = ref("categoryId")
    val original = ref("originalExpenseId")
    require(amount > 0) { "账目金额必须大于零" }
    require((category != null) == (type.scope != null)) { "账目分类字段不匹配" }
    require((original != null) == (type == EntryType.REFUND)) { "退款的原支出字段不匹配" }
    require(if (type == EntryType.TRANSFER) to != null && to != walletId && toAmount != null && toAmount > 0
        else to == null && toAmount == null) { "转账两侧字段不匹配" }
    return FinancialEntry(id(), validateDate(text("date")), validateTime(text("time")), type, walletId, amount, category,
        to, toAmount, original, text("note"), time("createdAt"), time("updatedAt"))
}

internal fun RowReader.theme(): ThemePreferences = ThemePreferences(themeMode = enum("themeMode"), colorSource = enum("colorSource"),
    presetId = text("presetId"), customSeedColor = text("customSeedColor")).also(::validateTheme)

internal fun RowReader.receipt() = ImportReceipt(enum("kind"), text("sha256"), time("importedAt")).also(::validateReceipt)
