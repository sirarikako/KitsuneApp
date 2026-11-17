package com.sirarikako.kitsune

import com.sirarikako.kitsune.core.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class MealRulesTest {
    private val time = "2026-11-17T00:00:00.000Z"
    private val cook = MealMethod("cook", "随意改名", "随意改名", 0, showPreparationCategories = true, createdAt = time, updatedAt = time)
    private val takeaway = cook.copy(id = "takeaway", name = "外卖", showPreparationCategories = false)
    private val original = MealRecord("meal", "2026-11-17", MealSlot.DINNER, true, cook.id, createdAt = time, updatedAt = time)
    private val methods = listOf(cook, takeaway)
    private fun fails(message: String, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertNotNull(message, error)
    }
    @Test fun newMealRequiresExplicitHomeChoiceAndConditionalMethod() {
        fails("尚未选择不能保存") { MealRules.categoriesForSave(MealDraft(), null, methods) }
        fails("在家须选择方式") { MealRules.categoriesForSave(MealDraft(plannedAtHome = true), null, methods) }
        assertEquals(emptySet<String>(), MealRules.categoriesForSave(MealDraft(plannedAtHome = false), null, methods))
        assertEquals(setOf("veg"), MealRules.categoriesForSave(MealDraft(plannedAtHome = false, mealMethodId = cook.id, categoryIds = setOf("veg")), null, methods))
    }
    @Test fun changingPlanDoesNotInferOrClearActualMethod() {
        val draft = MealDraft(plannedAtHome = false, mealMethodId = cook.id, categoryIds = setOf("veg"))
        assertEquals(setOf("veg"), MealRules.categoriesForSave(draft, original, methods))
    }
    @Test fun disabledGlobalSwitchPreservesHistoryButExplicitMethodChangeClearsIt() {
        val disabled = listOf(cook.copy(showPreparationCategories = false), takeaway)
        val draft = MealDraft(plannedAtHome = true, mealMethodId = cook.id, categoryIds = setOf("veg"))
        assertEquals(setOf("veg"), MealRules.categoriesForSave(draft, original, disabled))
        assertEquals(emptySet<String>(), MealRules.categoriesForSave(draft.copy(mealMethodId = takeaway.id), original, disabled))
        assertEquals(emptySet<String>(), MealRules.categoriesForSave(draft.copy(plannedAtHome = false, mealMethodId = null), original, disabled))
    }
    @Test fun archiveAllowsOriginalReferenceButNotNewSelection() {
        val archived = listOf(cook.copy(archived = true))
        val draft = MealDraft(plannedAtHome = true, mealMethodId = cook.id)
        MealRules.categoriesForSave(draft, original, archived)
        fails("新记录不得使用停用方式") { MealRules.categoriesForSave(draft, null, archived) }
    }
    @Test fun dateValidationRejectsImpossibleDatesAndLooseFormatting() {
        listOf("2026-02-29", "2026-13-01", "2026-1-1", "2026-11-17T00:00:00Z").forEach { date -> fails(date) { validatedDate(date) } }
        assertEquals("2028-02-29", validatedDate("2028-02-29").toString())
    }
    @Test fun normalizedNamesPreserveInternalSpaceAndUseNfc() {
        assertEquals(normalizedName(" ÉCLAIR "), normalizedName("e\u0301clair"))
        assertNotEquals(normalizedName("炒 菜"), normalizedName("炒菜"))
    }
    @Test fun statisticsCountEachCategoryAndUnspecifiedMethodsSeparately() {
        val breakfast = original.copy(id = "breakfast", mealSlot = MealSlot.BREAKFAST, mealMethodId = null, plannedAtHome = false)
        val outside = original.copy(id = "outside", date = "2026-10-31")
        val data = AppData(DataState(datasetId = "test"), methods, emptyList(), listOf(original, breakfast, outside),
            listOf(MealPreparationCategory("meal", "veg"), MealPreparationCategory("meal", "prepared")), ThemePreferences())
        val stats = mealStatistics(data, YearMonth.of(2026, 11))
        assertEquals(2, stats.total); assertEquals(1, stats.atHome); assertEquals(1, stats.away)
        assertEquals(1, stats.methods[null]); assertEquals(2, stats.categories.values.sum())
        assertEquals(breakfast, MealRules.previous(data, original.date, original.mealSlot))
        assertNull(MealRules.previous(data, "2026-01-01", MealSlot.BREAKFAST))
    }
}
