package com.kitsuneapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsuneapp.data.CsvDocument
import com.kitsuneapp.data.CsvKind
import com.kitsuneapp.data.CsvMappings
import com.kitsuneapp.data.CsvPreview
import com.kitsuneapp.data.KitsuneDatabase
import com.kitsuneapp.data.Repository
import com.kitsuneapp.data.newId
import com.kitsuneapp.feature.settings.CsvImportPreviewDialog
import com.kitsuneapp.ui.KitsuneTheme
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LocalBackupAndCsvTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsEntriesOpenLocalBackupAndCsvPages() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("设置").performClick()

        compose.onNodeWithText("本地备份与恢复").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GitHub 备份").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("CSV 导入与导出").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("导入与导出").assertDoesNotExist()
        compose.onNodeWithText("本地文件夹备份").assertDoesNotExist()

        // Local Backup & Restore page
        compose.onNodeWithText("本地备份与恢复").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("自动备份"), 10_000)
        compose.onNodeWithText("备份与恢复").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("立即备份").assertDoesNotExist()
        compose.onNodeWithText("手动备份").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("恢复备份").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("选择其他备份文件"), 10_000)
        compose.onNodeWithText("选择其他备份文件").assertIsEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("返回").performClick()

        // CSV Import & Export page
        compose.onNodeWithText("CSV 导入与导出").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("选择 CSV 文件"), 10_000)
        compose.onNodeWithText("用餐").assertIsSelected()
        compose.onNodeWithText("记账").assertIsNotSelected()
        compose.onNodeWithText("导出 CSV 模板").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("导出全部用餐记录").performScrollTo().assertIsDisplayed()

        // Expand format guide for meals
        compose.onNodeWithText("CSV 格式说明").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("meal_slot", substring = true), 10_000)

        // Switch to ledger tab -> export & format guide switch synchronously
        compose.onNodeWithText("记账").performClick()
        compose.onNodeWithText("记账").assertIsSelected()
        compose.onNodeWithText("用餐").assertIsNotSelected()
        compose.onNodeWithText("导出全部记账记录").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("original_expense_id", substring = true).performScrollTo().assertIsDisplayed()
    }
}

@OptIn(ExperimentalTestApi::class)
class CsvPreviewDialogTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun confirmButtonShowsAdditionCountAndBlocksErrorsOrZeroAdditions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, KitsuneDatabase::class.java).build()
        val repository = Repository(database, newId())
        val records = runBlocking {
            repository.initialize(true)
            repository.read()
        }
        val csvText = "id,date,meal_slot,planned_at_home,meal_method,preparation_categories,dishes,note\n" +
            ",2026-11-18,BREAKFAST,true,下厨,[],燕麦粥,\n"
        val doc = runBlocking { CsvDocument.load(CsvKind.MEALS, ByteArrayInputStream(csvText.encodeToByteArray())) }
        var preview by mutableStateOf(CsvPreview(additions = 2, duplicates = 1, errors = listOf("记录 2（第 3 行）日期无效"), elements = 10))
        try {
            compose.setContent {
                KitsuneTheme(records.theme) {
                    CsvImportPreviewDialog(
                        records = records,
                        source = doc,
                        preview = preview,
                        mappings = CsvMappings(),
                        busy = false,
                        error = null,
                        onRecheck = {},
                        onCommit = {},
                        onDismiss = {},
                    )
                }
            }
            compose.waitUntilAtLeastOneExists(hasText("确认导入（2 条）"), 10_000)
            compose.onNodeWithText("新增").assertIsDisplayed()
            compose.onNodeWithText("重复").assertIsDisplayed()
            compose.onNodeWithText("错误").assertIsDisplayed()
            compose.onNodeWithText("确认导入（2 条）").assertIsNotEnabled()

            // Zero additions, no errors -> still disabled
            compose.runOnUiThread {
                preview = CsvPreview(additions = 0, duplicates = 3, errors = emptyList(), elements = 10)
            }
            compose.waitUntilAtLeastOneExists(hasText("确认导入（0 条）"), 10_000)
            compose.onNodeWithText("确认导入（0 条）").assertIsNotEnabled()

            // Positive additions, no errors -> enabled
            compose.runOnUiThread {
                preview = CsvPreview(additions = 3, duplicates = 1, errors = emptyList(), elements = 13)
            }
            compose.waitUntilAtLeastOneExists(hasText("确认导入（3 条）"), 10_000)
            compose.onNodeWithText("确认导入（3 条）").assertIsEnabled()
        } finally {
            database.close()
        }
    }
}
