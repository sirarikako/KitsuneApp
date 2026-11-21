package com.kitsuneapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensAppearanceFromSettings() {
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasText("日常记录"), 15_000)
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("外观与调色板").performClick()
        compose.onNodeWithText("恢复默认").assertIsDisplayed()
    }
}
