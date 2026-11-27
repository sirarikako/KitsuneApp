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
        val filter = MealFilter(methods = listOf(cooking.id), categories = listOf(stirFry.id), query = "番茄")
        assertSame(filter, filter.validIn(records))
        val unfilled = MealFilter(methods = listOf(MealFilter.NO_METHOD))
        assertSame(unfilled, unfilled.validIn(records))
    }

    @Test
    fun multiSelectSlotsAtHomeAndMethodsMatchAndProduceLabels() {
        val takeout = MealMethod("method-takeout", "外卖", "外卖", 1, false, false, now, now)
        val breakfast = MealRecord("meal-b", "2026-11-24", MealSlot.BREAKFAST, false, takeout.id, "包子", "", now, now)
        val dinner = MealRecord("meal-d", "2026-11-24", MealSlot.DINNER, false, null, "面条", "", now, now)
        val rich = records.copy(methods = listOf(cooking, takeout), meals = listOf(breakfast, meal, dinner))

        val slotFilter = MealFilter(slots = listOf(MealSlot.BREAKFAST, MealSlot.DINNER))
        assertTrue(slotFilter.matches(breakfast, rich))
        assertTrue(!slotFilter.matches(meal, rich))
        assertTrue(slotFilter.matches(dinner, rich))
        assertEquals(listOf("早餐", "晚餐"), slotFilter.labels(rich))

        val allSlots = MealFilter(slots = MealSlot.entries)
        assertTrue(allSlots.matches(meal, rich))
        assertTrue(allSlots.labels(rich).isEmpty())

        val homeOnly = MealFilter(atHome = listOf(true))
        assertTrue(homeOnly.matches(meal, rich))
        assertTrue(!homeOnly.matches(breakfast, rich))
        assertEquals(listOf("计划在家：是"), homeOnly.labels(rich))

        val bothHome = MealFilter(atHome = listOf(true, false))
        assertTrue(bothHome.matches(meal, rich))
        assertTrue(bothHome.matches(breakfast, rich))
        assertTrue(bothHome.labels(rich).isEmpty())

        val methodFilter = MealFilter(methods = listOf(MealFilter.NO_METHOD, takeout.id))
        assertTrue(methodFilter.matches(breakfast, rich))
        assertTrue(!methodFilter.matches(meal, rich))
        assertTrue(methodFilter.matches(dinner, rich))
        assertEquals(listOf("方式未填写", "外卖"), methodFilter.labels(rich))
    }

    @Test
    fun staleMethodAndCategoryConditionsAreDroppedAndOtherConditionsKept() {
        val stale = MealFilter(start = "2026-11-01", end = "2026-11-30", slots = listOf(MealSlot.LUNCH),
            methods = listOf("method-removed", cooking.id),
            categories = listOf("prep-removed", stirFry.id, expense.id), query = "番茄")
        val valid = stale.validIn(records)
        assertEquals(MealFilter(start = "2026-11-01", end = "2026-11-30", slots = listOf(MealSlot.LUNCH),
            methods = listOf(cooking.id), categories = listOf(stirFry.id), query = "番茄"), valid)
        assertEquals(listOf("2026-11-01 ～ 2026-11-30", "午餐", "下厨", "炒菜", "关键词：番茄"), valid.labels(records))
        assertTrue(valid.matches(meal, records))
    }

    @Test
    fun filterWithOnlyStaleConditionsBecomesUnrestricted() {
        val valid = MealFilter(methods = listOf("method-removed"), categories = listOf("prep-removed")).validIn(records)
        assertEquals(MealFilter(), valid)
        assertTrue(valid.labels(records).isEmpty())
    }

    @Test
    fun writingBackPrunedFiltersPreventsRevivalWhenOlderBackupRestoresDeletedItems() {
        val removedMethod = MealMethod("method-removed", "旧方式", "旧方式", 1, false, false, now, now)
        val removedPrep = category("prep-removed", Scope.PREPARATION, "旧分类")
        val beforeDelete = records.copy(methods = records.methods + removedMethod, categories = records.categories + removedPrep)
        val currentMonth = MealFilter.forMonth(java.time.YearMonth.of(2026, 11))

        var storedRecordFilter: MealFilter? = MealFilter(methods = listOf(removedMethod.id), categories = listOf(removedPrep.id), query = "番茄")
        var storedStatsFilter: MealFilter? = currentMonth.copy(methods = listOf(removedMethod.id))

        // Delete items -> prune and write back to stored state (matching MainActivity LaunchedEffect).
        storedRecordFilter = storedRecordFilter?.validIn(records)
        storedStatsFilter = storedStatsFilter?.validIn(records)?.takeUnless { it == currentMonth }
        assertEquals(MealFilter(query = "番茄"), storedRecordFilter)
        assertEquals(null, storedStatsFilter)

        // Restore older backup containing removedMethod and removedPrep -> cleared conditions stay cleared.
        assertEquals(MealFilter(query = "番茄"), storedRecordFilter?.validIn(beforeDelete))
        assertEquals(currentMonth, (storedStatsFilter?.validIn(beforeDelete) ?: currentMonth))
    }
}
