package com.sirarikako.kitsune

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sirarikako.kitsune.core.data.*
import com.sirarikako.kitsune.core.theme.KitsuneTheme
import com.sirarikako.kitsune.feature.meals.MealEditor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealEditorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun requiredChoiceIsExplicitAndAwayAllowsNoMethod() {
        val data = AppData(DataState(datasetId = "test"), emptyList(), emptyList(), emptyList(), emptyList(), ThemePreferences())
        var saved: MealDraft? = null
        var cancelled = false
        compose.setContent { KitsuneTheme(data.theme) { MealEditor(data, MealDraft(), false, onCancel = { cancelled = true }, onSave = { saved = it }, onDelete = {}) } }
        compose.onNodeWithText("保存到本地").performClick()
        compose.onNodeWithTag("meal_form").performScrollToNode(hasText("请选择是否计划在家吃"))
        compose.onNodeWithText("请选择是否计划在家吃").assertIsDisplayed()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("meal_form").performScrollToNode(hasText("是"))
        compose.onNodeWithText("是").performClick()
        compose.onNodeWithText("保存到本地").performClick()
        compose.onNodeWithTag("meal_form").performScrollToNode(hasText("计划在家吃时，请选择用餐方式"))
        compose.onNodeWithText("计划在家吃时，请选择用餐方式").assertIsDisplayed()
        compose.onNodeWithTag("meal_form").performScrollToNode(hasText("否"))
        compose.onNodeWithText("否").performClick()
        compose.onNodeWithText("保存到本地").performClick()
        compose.runOnIdle { assertEquals(false, saved?.plannedAtHome); assertNull(saved?.mealMethodId) }
        compose.onNodeWithText("取消", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }
}
