package com.kitsuneapp

import android.graphics.Bitmap
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

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
    fun keepsPageViewportAndMainNavigationFixedDuringSubpageSlide() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("设置").performClick()
        val pageViewport = compose.onNodeWithTag("navigation-pages")
        val mainNav = compose.onNodeWithTag("main-navigation", useUnmergedTree = true)
        val appearance = compose.onNodeWithText("外观与调色板")
        val viewportBounds = pageViewport.fetchSemanticsNode().boundsInRoot
        val navPosition = mainNav.fetchSemanticsNode().positionInRoot

        compose.mainClock.autoAdvance = false
        try {
            appearance.performClick()
            compose.mainClock.advanceTimeBy(96)
            assertEquals(viewportBounds, pageViewport.fetchSemanticsNode().boundsInRoot)
            assertEquals(navPosition, mainNav.fetchSemanticsNode().positionInRoot)
            val slidingTitle = appearance.fetchSemanticsNode().positionInRoot

            compose.mainClock.advanceTimeBy(280)
            val settledTitle = appearance.fetchSemanticsNode().positionInRoot
            assertTrue("The child page should enter from the right", slidingTitle.x > settledTitle.x)
            assertEquals(settledTitle.y, slidingTitle.y, 1f)
            assertEquals(viewportBounds, pageViewport.fetchSemanticsNode().boundsInRoot)
            mainNav.assertDoesNotExist()

            compose.onNodeWithContentDescription("返回").performClick()
            compose.mainClock.advanceTimeBy(96)
            assertEquals(viewportBounds, pageViewport.fetchSemanticsNode().boundsInRoot)
            assertEquals(navPosition, mainNav.fetchSemanticsNode().positionInRoot)
            compose.mainClock.advanceTimeBy(260)
            appearance.assertIsDisplayed()
            assertEquals(navPosition, mainNav.fetchSemanticsNode().positionInRoot)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun checksRequiredMealPlanAndReturnsAfterSave() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("用餐").performClick()
        screenshot("kitsune-meals")
        compose.onNodeWithText("早餐").performClick()
        screenshot("kitsune-meal-form")
        compose.onNodeWithText("保存早餐记录").performClick()
        compose.waitUntilAtLeastOneExists(hasText("请选择是否计划在家吃"), 15_000)
        compose.onNodeWithText("否").performClick()
        compose.onNodeWithText("保存早餐记录").performClick()
        compose.waitUntilAtLeastOneExists(hasText("三餐记录"), 15_000)
        compose.onNodeWithText("早餐").performClick()
        compose.onNodeWithText("删除这条用餐记录").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntilAtLeastOneExists(hasText("添加记录"), 15_000)
    }

    @Test
    fun selectsAnotherDayWithinVisibleWeek() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("用餐").performClick()
        val today = LocalDate.now()
        val other = if (today.dayOfWeek.value == 7) today.minusDays(1) else today.plusDays(1)
        fun dayLabel(date: LocalDate): String {
            val weekday = "一二三四五六日"[date.dayOfWeek.value - 1]
            return "${date.monthValue} 月 ${date.dayOfMonth} 日，星期$weekday"
        }
        compose.onNodeWithContentDescription(dayLabel(today)).assertIsSelected()
        compose.onNodeWithContentDescription(dayLabel(other)).assertIsNotSelected().performClick().assertIsSelected()
        compose.onNodeWithContentDescription(dayLabel(today)).assertIsNotSelected()
        compose.onNodeWithText("三餐记录").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
