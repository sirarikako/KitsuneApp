package com.sirarikako.kitsune.core.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun slot(value: String): MealSlot = MealSlot.valueOf(value)
    @TypeConverter fun slot(value: MealSlot): String = value.name
    @TypeConverter fun scope(value: String): CategoryScope = CategoryScope.valueOf(value)
    @TypeConverter fun scope(value: CategoryScope): String = value.name
    @TypeConverter fun mode(value: String): ThemeMode = ThemeMode.valueOf(value)
    @TypeConverter fun mode(value: ThemeMode): String = value.name
    @TypeConverter fun source(value: String): ColorSource = ColorSource.valueOf(value)
    @TypeConverter fun source(value: ColorSource): String = value.name
}
@Dao
interface KitsuneDao {
    @Query("SELECT * FROM data_state WHERE singleton = 1") fun observeState(): Flow<DataState?>
    @Query("SELECT * FROM data_state WHERE singleton = 1") suspend fun state(): DataState?
    @Query("SELECT * FROM meal_methods ORDER BY sortOrder, id") suspend fun methods(): List<MealMethod>
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") suspend fun categories(): List<Category>
    @Query("SELECT * FROM meals ORDER BY date DESC, mealSlot") suspend fun meals(): List<MealRecord>
    @Query("SELECT * FROM meal_preparation_categories") suspend fun links(): List<MealPreparationCategory>
    @Query("SELECT * FROM theme_preferences WHERE singleton = 1") suspend fun theme(): ThemePreferences
    @Upsert suspend fun put(method: MealMethod)
    @Upsert suspend fun put(category: Category)
    @Insert suspend fun insertMeal(meal: MealRecord)
    @Update suspend fun updateMeal(meal: MealRecord)
    @Upsert suspend fun put(theme: ThemePreferences)
    @Upsert suspend fun put(state: DataState)
    @Insert suspend fun addLinks(links: List<MealPreparationCategory>)
    @Query("DELETE FROM meal_preparation_categories WHERE mealId = :mealId") suspend fun clearLinks(mealId: String)
    @Query("DELETE FROM meals WHERE id = :id") suspend fun deleteMeal(id: String)
}
@Database(entities = [MealMethod::class, Category::class, MealRecord::class, MealPreparationCategory::class,
    ThemePreferences::class, DataState::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class KitsuneDatabase : RoomDatabase() {
    abstract fun dao(): KitsuneDao
    // Published schemas are committed; upgrades must supply explicit Migration objects.
    // Never enable destructive migration for the user's local records.
}
