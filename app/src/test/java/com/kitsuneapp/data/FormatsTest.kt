package com.kitsuneapp.data

import java.io.StringWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FormatsTest {
    @Test
    fun csvPreservesMultilineEscapingAndSourceLineNumbers() {
        val cells = listOf("", "2026-11-21", "LUNCH", "false", "外卖", "[]", "面,饭", "备注\"引号\"\n第二行")
        val output = StringWriter()
        output.writeCsvRow(mealColumns)
        output.writeCsvRow(cells)
        output.writeCsvRow(cells)
        val parsed = CsvReader(("\uFEFF" + output).reader()).read(mealColumns)
        assertEquals(cells, mealColumns.map { parsed[0][it] })
        assertEquals(2, parsed[0].line)
        assertEquals(4, parsed[1].line)
    }

    @Test
    fun csvRejectsMalformedQuotesAndUnknownHeader() {
        assertThrows(IllegalArgumentException::class.java) {
            CsvReader("date,id\nx,y".reader()).read(mealColumns)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CsvReader((mealColumns.joinToString(",") + "\n,\"unfinished").reader()).read(mealColumns)
        }
    }

    @Test
    fun backupParserRejectsDuplicateKeyAndOversizeRawToken() = runBlocking {
        val duplicate = "{\"id\":\"a\",\"id\":\"b\"}"
        assertTrue(runCatching {
            jsonReader(duplicate.byteInputStream()).use {
                val input = JsonInput(it)
                input.obj(setOf("id")) { input.string() }
            }
        }.isFailure)
        val tooLarge = "\"" + "\\u0061".repeat(10923) + "\""
        assertThrows(IllegalArgumentException::class.java) {
            jsonReader(tooLarge.byteInputStream()).use { JsonInput(it).string() }
        }
        Unit
    }
}
