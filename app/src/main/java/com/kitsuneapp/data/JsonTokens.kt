package com.kitsuneapp.data

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private class BoundedTokens(input: InputStream) : FilterInputStream(input) {
    private var quoted = false
    private var escaped = false
    private var length = 0

    override fun read(): Int = super.read().also { if (it >= 0) scan(it) }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int =
        super.read(buffer, offset, count).also { size ->
            for (i in offset until offset + size) scan(buffer[i].toInt() and 0xff)
        }

    private fun scan(value: Int) {
        if (quoted) {
            if (value == 34 && !escaped) {
                quoted = false
                length = 0
                return
            }
            escaped = value == 92 && !escaped
            length++
        } else {
            when (value) {
                34 -> { quoted = true; length = 0 }
                9, 10, 13, 32, 44, 58, 91, 93, 123, 125 -> length = 0
                else -> length++
            }
        }
        require(length <= MAX_FIELD_BYTES) { "备份字段超过 64 KiB" }
    }
}

internal fun jsonReader(input: InputStream) = JsonReader(InputStreamReader(BoundedTokens(input), UTF_8.newDecoder()))
    .also { it.strictness = Strictness.STRICT; it.nestingLimit = 16 }

internal fun jsonWriter(output: OutputStream) = JsonWriter(OutputStreamWriter(output, UTF_8)).also {
    it.strictness = Strictness.STRICT
    it.serializeNulls = true
    it.setIndent("  ")
}

internal class JsonInput(val reader: JsonReader) {
    var count = 0
        private set

    suspend fun obj(required: Set<String>, optional: Set<String> = emptySet(), field: suspend (String) -> Unit) {
        reader.beginObject()
        val seen = HashSet<String>()
        while (reader.hasNext()) {
            currentCoroutineContext().ensureActive()
            val key = reader.nextName()
            require(key in required || key in optional) { "未知备份字段 $key" }
            require(seen.add(key)) { "重复的备份字段 $key" }
            field(key)
        }
        reader.endObject()
        require(seen.containsAll(required)) { "缺少备份字段 " + (required - seen).joinToString() }
    }

    suspend fun list(item: suspend () -> Unit) {
        reader.beginArray()
        while (reader.hasNext()) {
            currentCoroutineContext().ensureActive()
            require(++count <= MAX_ARRAY_ELEMENTS) { "备份记录和关联总数超过 200,000" }
            item()
        }
        reader.endArray()
    }

    fun string(): String {
        require(reader.peek() == JsonToken.STRING) { "备份字段必须是字符串" }
        return reader.nextString()
    }

    fun bool(): Boolean {
        require(reader.peek() == JsonToken.BOOLEAN) { "备份字段必须是布尔值" }
        return reader.nextBoolean()
    }

    fun int(): Int {
        require(reader.peek() == JsonToken.NUMBER) { "备份字段必须是整数" }
        return reader.nextString().toIntOrNull() ?: throw IllegalArgumentException("备份字段必须是整数")
    }

    fun <T> nullable(read: () -> T): T? {
        if (reader.peek() != JsonToken.NULL) return read()
        reader.nextNull()
        return null
    }
}

internal fun JsonWriter.field(name: String, value: Any?) {
    name(name)
    when (value) {
        null -> nullValue()
        is String -> value(value)
        is Boolean -> value(value)
        is Int -> value(value.toLong())
        is Enum<*> -> value(value.name)
        else -> error("Invalid JSON field")
    }
}
