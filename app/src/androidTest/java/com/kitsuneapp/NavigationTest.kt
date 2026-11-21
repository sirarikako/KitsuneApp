package com.kitsuneapp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import java.io.File
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensAppearanceFromSettings() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        screenshot("kitsune-home")
        compose.onNodeWithText("设置").performClick()
        screenshot("kitsune-settings")
        compose.onNodeWithText("外观与调色板").performClick()
        compose.onNodeWithText("恢复默认").assertIsDisplayed()
    }

    @Test
    fun checksRequiredMealPlanAndReturnsAfterSave() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("用餐").performClick()
        compose.onNodeWithText("早餐").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntilAtLeastOneExists(hasText("请选择是否计划在家吃"), 15_000)
        compose.onNodeWithText("否").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntilAtLeastOneExists(hasText("三餐记录"), 15_000)
        compose.onNodeWithText("早餐").performClick()
        compose.onNodeWithText("删除这条用餐记录").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.waitUntilAtLeastOneExists(hasText("待记录"), 15_000)
    }

    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
