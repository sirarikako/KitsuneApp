package com.kitsuneapp.feature.meals

import com.kitsuneapp.data.Category
import com.kitsuneapp.data.DataState
import com.kitsuneapp.data.GLOBAL_OWNER
import com.kitsuneapp.data.MealMethod
import com.kitsuneapp.data.MealRecord
import com.kitsuneapp.data.MealSlot
import com.kitsuneapp.data.Records
import com.kitsuneapp.data.Scope
import com.kitsuneapp.data.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MealFilterTest {
    private val now = "2026-11-25T00:00:00.000Z"
    private val cooking = MealMethod("method-cooking", "下厨", "下厨", 0, false, true, now, now)
    private val stirFry = category("prep-stir-fry", Scope.PREPARATION, "炒菜")
    private val expense = category("expense-food", Scope.EXPENSE, "餐饮")
    private val meal = MealRecord("meal-1", "2026-11-24", MealSlot.LUNCH, true, cooking.id, "番茄炒蛋", "", now, now)
    private val records = Records(
        DataState(installationId = "device", datasetId = "dataset", arrayElements = 0), ThemePreferences(),
        listOf(cooking), listOf(stirFry, expense), listOf(meal), mapOf(meal.id to listOf(stirFry.id)), emptyList(), emptyList(),
    )

    private fun category(id: String, scope: Scope, name: String) =
        Category(id, scope, null, GLOBAL_OWNER, name, name, 0, false, false, now, now)

    @Test
    fun methodNameSeparatesUnfilledFromDeletedMethods() {
        assertEquals("未填写", records.methodName(null))
        assertEquals("下厨", records.methodName(cooking.id))
        assertEquals("已删除的方式", records.methodName("method-removed"))
    }

    @Test
    fun validFilterIsReturnedUnchanged() {
        val filter = MealFilter(method = cooking.id, categories = listOf(stirFry.id), query = "番茄")
        assertSame(filter, filter.validIn(records))
        val unfilled = MealFilter(method = MealFilter.NO_METHOD)
        assertSame(unfilled, unfilled.validIn(records))
    }

    @Test
    fun staleMethodAndCategoryConditionsAreDroppedAndOtherConditionsKept() {
        val stale = MealFilter(start = "2026-11-01", end = "2026-11-30", slot = MealSlot.LUNCH, method = "method-removed",
            categories = listOf("prep-removed", stirFry.id, expense.id), query = "番茄")
        val valid = stale.validIn(records)
        assertEquals(MealFilter(start = "2026-11-01", end = "2026-11-30", slot = MealSlot.LUNCH,
            categories = listOf(stirFry.id), query = "番茄"), valid)
        assertEquals(listOf("2026-11-01 ～ 2026-11-30", "午餐", "炒菜", "关键词：番茄"), valid.labels(records))
        assertTrue(valid.matches(meal, records))
    }

    @Test
    fun filterWithOnlyStaleConditionsBecomesUnrestricted() {
        val valid = MealFilter(method = "method-removed", categories = listOf("prep-removed")).validIn(records)
        assertEquals(MealFilter(), valid)
        assertTrue(valid.labels(records).isEmpty())
    }
}
