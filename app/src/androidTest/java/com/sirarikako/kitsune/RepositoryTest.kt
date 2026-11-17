package com.sirarikako.kitsune

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sirarikako.kitsune.core.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: KitsuneDatabase
    private lateinit var repo: KitsuneRepository
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KitsuneDatabase::class.java).build()
        repo = KitsuneRepository(db); repo.initialize()
    }
    @After fun close() { db.close() }
    @Test fun seedsOnceAndFailedConflictCreatesNeitherCategoriesNorRevision() = runBlocking {
        val initial = repo.data.first(); repo.initialize()
        assertEquals(initial, repo.data.first())
        repo.saveMeal(MealDraft(plannedAtHome = false))
        val saved = repo.data.first()
        assertTrue(runCatching { repo.saveMeal(MealDraft(plannedAtHome = true, mealMethodId = saved.methods.first().id, newCategoryNames = listOf("不应创建"))) }.isFailure)
        assertEquals(saved, repo.data.first())
        assertTrue(runCatching {
            repo.saveMeal(MealDraft(date = "2026-12-01", plannedAtHome = true, mealMethodId = saved.methods.first().id,
                categoryIds = setOf("missing"), newCategoryNames = listOf("事务应回滚的新分类")))
        }.isFailure)
        assertEquals(saved, repo.data.first())
    }
    @Test fun historicalCategoriesSurviveSwitchSettingAndRenameAndDeleteCascades() = runBlocking {
        val initial = repo.data.first(); val cook = initial.methods.first { it.showPreparationCategories }
        repo.saveMeal(MealDraft(plannedAtHome = true, mealMethodId = cook.id, newCategoryNames = listOf(" 新分类 ")))
        val saved = repo.data.first(); val meal = saved.meals.single(); val categoryId = saved.categoryIds(meal.id).single()
        assertEquals(1L, saved.state.revision)
        repo.saveMethod(cook.id, "改名后的方式", false)
        repo.archiveCategory(categoryId)
        repo.saveMeal(saved.draft(meal).copy(note = "多行\n备注"))
        val edited = repo.data.first()
        assertEquals(setOf(categoryId), edited.categoryIds(meal.id)); assertEquals("多行\n备注", edited.meals.single().note)
        val delivery = edited.methods.first { it.id != cook.id }
        repo.saveMeal(edited.draft(edited.meals.single()).copy(mealMethodId = delivery.id))
        assertTrue(repo.data.first().links.isEmpty())
        repo.deleteMeal(meal.id)
        assertTrue(repo.data.first().meals.isEmpty()); assertTrue(repo.data.first().links.isEmpty())
    }
    @Test fun sameDateSlotUniquenessAndForeignKeysAreEnforcedByRoom() = runBlocking {
        repo.saveMeal(MealDraft(plannedAtHome = false))
        val meal = repo.data.first().meals.single()
        assertTrue(runCatching { db.dao().insertMeal(meal.copy(id = "another")) }.isFailure)
        assertTrue(runCatching { db.dao().addLinks(listOf(MealPreparationCategory(meal.id, "missing"))) }.isFailure)
    }
    @Test fun noOpThemeDoesNotAdvanceRevisionAndOverflowRollsBackWholeCommit() = runBlocking {
        val initial = repo.data.first(); repo.applyTheme(initial.theme)
        assertEquals(initial.state, repo.data.first().state)
        db.dao().put(initial.state.copy(revision = Long.MAX_VALUE))
        assertTrue(runCatching { repo.saveMeal(MealDraft(plannedAtHome = false)) }.isFailure)
        assertTrue(repo.data.first().meals.isEmpty())
        assertEquals(Long.MAX_VALUE, repo.data.first().state.revision)
    }
    @Test fun changedThemeCommitsOnceAndNewCategoriesAreAtomic() = runBlocking {
        repo.applyTheme(ThemePreferences(customSeedColor = "#000000", colorSource = ColorSource.CUSTOM))
        val initial = repo.data.first(); assertEquals(1L, initial.state.revision)
        val cook = initial.methods.first { it.showPreparationCategories }
        repo.saveMeal(MealDraft(plannedAtHome = true, mealMethodId = cook.id, newCategoryNames = listOf(" 新分类 ", "新分类")))
        val saved = repo.data.first()
        assertEquals(2L, saved.state.revision)
        assertEquals(1, saved.categories.count { it.name == "新分类" })
        assertEquals(1, saved.links.size)
    }
}
