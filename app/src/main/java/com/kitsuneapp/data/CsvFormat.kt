package com.kitsuneapp.data

import java.io.PushbackReader
import java.io.Reader
import java.io.Writer

internal val mealColumns = listOf("id", "date", "meal_slot", "planned_at_home", "meal_method", "preparation_categories", "dishes", "note")
internal val ledgerColumns = listOf("id", "date", "time", "type", "wallet", "currency", "amount", "category",
    "to_wallet", "to_currency", "to_amount", "original_expense_id", "note")

internal data class CsvRow(val number: Int, val line: Int, val values: Map<String, String>) {
    operator fun get(column: String): String = values.getValue(column)
    val position get() = "记录 $number（第 $line 行）"
}

internal class CsvReader(source: Reader) {
    private val reader = PushbackReader(source.buffered(), 1)
    private var line = 1
    private var row = 0

    fun read(required: List<String>): List<CsvRow> {
        val first = reader.read()
        if (first >= 0 && first != 0xFEFF) reader.unread(first)
        val header = next() ?: throw IllegalArgumentException("CSV 文件为空")
        require(header.size == required.size && header.toSet() == required.toSet()) { "CSV 表头列与模板不符" }
        val result = ArrayList<CsvRow>()
        while (true) {
            val start = line
            val values = next() ?: break
            require(values.size == header.size) { "记录 $row（第 $start 行）列数不符" }
            require(result.size < MAX_ARRAY_ELEMENTS) { "CSV 超过 200,000 条记录" }
            result += CsvRow(row - 1, start, header.zip(values).toMap())
        }
        return result
    }

    private fun next(): List<String>? {
        val values = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var closed = false
        var any = false
        val start = line
        fun endField() {
            require(values.size < ledgerColumns.size) { "第 $start 行列数过多" }
            values.add(value.toString())
            value.clear()
            closed = false
        }
        while (true) {
            val code = reader.read()
            if (code == -1) {
                require(!quoted) { "第 $start 行有未结束的引用字段" }
                if (!any) return null
                endField()
                row++
                return values
            }
            any = true
            val char = code.toChar()
            when {
                quoted && char == '"' -> {
                    val following = reader.read()
                    if (following == 34) value.append('"')
                    else {
                        if (following >= 0) reader.unread(following)
                        quoted = false
                        closed = true
                    }
                }
                quoted -> {
                    value.append(char)
                    if (char == '\n') line++
                }
                char == ',' -> endField()
                char == '\n' || char == '\r' -> {
                    if (char == '\r') require(reader.read() == '\n'.code) { "第 $line 行有无效的换行" }
                    line++
                    row++
                    endField()
                    return values
                }
                closed -> throw IllegalArgumentException("第 $line 行的引号字段末尾有多余文本")
                char == '"' -> {
                    require(value.isEmpty()) { "第 $line 行有无效的双引号" }
                    quoted = true
                }
                else -> value.append(char)
            }
            require(value.length <= MAX_FIELD_BYTES * 8) { "第 $start 行字段过长" }
        }
    }
}

internal fun Writer.writeCsvRow(values: List<String>) {
    values.forEachIndexed { index, value ->
        if (index > 0) write(",")
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            write("\"")
            write(value.replace("\"", "\"\""))
            write("\"")
        } else write(value)
    }
    write("\r\n")
}
