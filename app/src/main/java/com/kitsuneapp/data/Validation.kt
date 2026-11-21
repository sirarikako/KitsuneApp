package com.kitsuneapp.data

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

const val MAX_FIELD_BYTES = 65_536
const val MAX_ARRAY_ELEMENTS = 200_000
const val GLOBAL_OWNER = ""
val presets = linkedMapOf(
    "apricot" to ("杏橙" to "#C47A4A"),
    "sage" to ("茶绿" to "#5F7F64"),
    "mist_blue" to ("雾蓝" to "#587C9B"),
    "lavender" to ("薰衣草" to "#8A729F"),
    "rose" to ("豆沙粉" to "#A76F82"),
)
private val datePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val timePattern = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
private val idPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
private val integerPattern = Regex("0|-?[1-9][0-9]*")
private val colorPattern = Regex("#[0-9A-F]{6}")
private val currencyPattern = Regex("[A-Z]{3}")

fun normalizedName(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)

fun validateField(field: String, value: String) {
    var bytes = 0
    var index = 0
    while (index < value.length) {
        val char = value[index++]
        bytes += when {
            char == '"' || char == '\\' || char in "\t\n\b\r\u000C" -> 2
            char < ' ' || char == '\u2028' || char == '\u2029' -> 6
            char < '\u0080' -> 1
            char < '\u0800' -> 2
            Character.isHighSurrogate(char) -> {
                require(index < value.length && Character.isLowSurrogate(value[index++])) { "$field 包含无效字符" }
                4
            }
            Character.isLowSurrogate(char) -> throw IllegalArgumentException("$field 包含无效字符")
            else -> 3
        }
        require(bytes <= MAX_FIELD_BYTES) { "$field 超过 64 KiB" }
    }
}

fun cleanName(value: String): String = value.trim().also {
    require(it.isNotEmpty()) { "请填写名称" }
    validateField("名称", it)
}

fun validateDate(value: String): String {
    require(datePattern.matches(value) && runCatching { LocalDate.parse(value).year >= 1 }.getOrDefault(false)) {
        "请输入有效日期（YYYY-MM-DD）"
    }
    return value
}

fun validateTime(value: String): String {
    require(timePattern.matches(value)) { "请输入有效时间（HH:mm）" }
    return value
}

fun validateId(value: String): String {
    require(idPattern.matches(value)) { "无效的 UUID" }
    return value
}

fun parseLongString(value: String): Long {
    require(integerPattern.matches(value)) { "无效的最小货币单位或修订号" }
    return requireNotNull(value.toLongOrNull()) { "数值超出 64 位范围" }
}

fun validateCurrency(code: String, scale: Int) {
    require(currencyPattern.matches(code) && scale in 0..4) { "无效币种或货币精度" }
}

fun validateTheme(theme: ThemePreferences) {
    require(theme.presetId in presets && colorPattern.matches(theme.customSeedColor)) { "无效的外观设置" }
}

fun normalizedColor(value: String): String? =
    ("#" + value.trim().removePrefix("#").uppercase(Locale.ROOT)).takeIf(colorPattern::matches)

fun allowedCategory(category: Category, wallet: Wallet, scope: Scope): Boolean {
    if (category.scope != scope || category.archived) return false
    if (category.isDefault) return category.walletId == null
    return category.walletId == wallet.id.takeIf { wallet.categoryMode(scope) == CategoryMode.CUSTOM }
}
