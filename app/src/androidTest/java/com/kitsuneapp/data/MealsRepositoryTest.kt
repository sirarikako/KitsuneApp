package com.kitsuneapp.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class MealsRepositoryTest {
    private lateinit var database: KitsuneDatabase
    private lateinit var repository: Repository
    private var commits = 0

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, KitsuneDatabase::class.java).build()
        repository = Repository(database, newId()) { commits++ }
        repository.initialize(true)
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun addsPreparationAndMealInOneRevisionAndRetainsHistoryWhenMethodDisabled() = runBlocking {
        val seed = repository.read()
        val cooking = seed.methods.first()
        val draft = MealDraft(date = "2026-11-21", slot = MealSlot.DINNER, atHome = true, methodId = cooking.id,
            preparationIds = emptyList(), newPreparations = listOf("  沙拉  "), dishes = "汤\n饭", note = "")
        assertTrue(repository.saveMeal(draft))
        val added = repository.read()
        val meal = added.meals.single()
        assertEquals(1L, added.state.revision)
        assertEquals(1, commits)
        assertEquals(listOf("沙拉"), added.preparationNames(meal.id))
        repository.saveMethod(cooking.id, cooking.name, cooking.sortOrder, false, false)
        repository.saveMeal(draft.copy(id = meal.id, atHome = false, newPreparations = emptyList(), note = "追加"))
        assertEquals(listOf("沙拉"), repository.read().preparationNames(meal.id))
        repository.saveMeal(draft.copy(id = meal.id, atHome = false, methodId = null, newPreparations = emptyList()))
        assertTrue(repository.read().preparation.isEmpty())
    }

    @Test
    fun rejectsUnselectedPlanAndOccupiedSlotWithoutChangingRevision() = runBlocking {
        val draft = MealDraft(date = "2026-11-21", slot = MealSlot.LUNCH, atHome = null, methodId = null,
            preparationIds = emptyList(), newPreparations = emptyList(), dishes = "", note = "")
        assertTrue(runCatching { repository.saveMeal(draft) }.isFailure)
        assertTrue(runCatching { repository.saveMeal(draft.copy(atHome = true)) }.isFailure)
        assertEquals(0L, repository.read().state.revision)
        repository.saveMeal(draft.copy(atHome = false))
        assertTrue(runCatching { repository.saveMeal(draft.copy(atHome = false)) }.isFailure)
        assertEquals(1L, repository.read().state.revision)
        assertEquals(1, commits)
    }

    @Test
    fun checksOwnershipAndDoesNotReseedExistingDatabase() = runBlocking {
        val previous = repository.read()
        repository.initialize(false)
        assertEquals(previous, repository.read())
        val elsewhere = Repository(database, newId())
        assertTrue(runCatching { elsewhere.initialize(false) }.exceptionOrNull() is OwnershipException)
    }

    @Test
    fun themeApplyOnlyRevisesActualChanges() = runBlocking {
        assertFalse(repository.applyTheme(ThemePreferences()))
        assertTrue(repository.applyTheme(ThemePreferences(themeMode = ThemeMode.DARK)))
        assertFalse(repository.applyTheme(ThemePreferences(themeMode = ThemeMode.DARK)))
        assertEquals(1L, repository.read().state.revision)
    }
}
