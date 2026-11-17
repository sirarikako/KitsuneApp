package com.sirarikako.kitsune.core.data

import androidx.room.*
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

enum class MealSlot(val label: String) { BREAKFAST("早餐"), LUNCH("午餐"), DINNER("晚餐") }
enum class CategoryScope { PREPARATION, INCOME, EXPENSE }
enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
enum class ColorSource(val label: String) { PRESET("预设调色板"), CUSTOM("自选主色"), SYSTEM("系统动态取色") }

fun normalizedName(name: String): String = Normalizer.normalize(name.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)
fun validatedName(name: String): String = name.trim().also { require(it.isNotEmpty()) { "名称不能为空" } }
fun validatedDate(date: String): LocalDate {
    require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) { "日期格式应为 YYYY-MM-DD" }
    return try { LocalDate.parse(date) } catch (_: java.time.DateTimeException) { error("日期无效") }
}

@Entity(tableName = "meal_methods", indices = [Index(value = ["normalizedName"], unique = true)])
data class MealMethod(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val archived: Boolean = false,
    val showPreparationCategories: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
)
@Entity(tableName = "categories", indices = [Index(value = ["scope", "normalizedName"], unique = true)])
data class Category(
    @PrimaryKey val id: String,
    val scope: CategoryScope = CategoryScope.PREPARATION,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val archived: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
)
@Entity(tableName = "meals", indices = [Index(value = ["date", "mealSlot"], unique = true), Index("mealMethodId")],
    foreignKeys = [ForeignKey(entity = MealMethod::class, parentColumns = ["id"], childColumns = ["mealMethodId"], onDelete = ForeignKey.RESTRICT)])
data class MealRecord(
    @PrimaryKey val id: String,
    val date: String,
    val mealSlot: MealSlot,
    val plannedAtHome: Boolean,
    val mealMethodId: String?,
    val dishes: String = "",
    val note: String = "",
    val createdAt: String,
    val updatedAt: String,
)
@Entity(tableName = "meal_preparation_categories", primaryKeys = ["mealId", "categoryId"], indices = [Index("categoryId")],
    foreignKeys = [
        ForeignKey(entity = MealRecord::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ])
data class MealPreparationCategory(val mealId: String, val categoryId: String)
@Entity(tableName = "theme_preferences")
data class ThemePreferences(
    @PrimaryKey val singleton: Int = 1,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.PRESET,
    val presetId: String = "apricot",
    val customSeedColor: String = "#C47A4A",
)
@Entity(tableName = "data_state")
data class DataState(@PrimaryKey val singleton: Int = 1, val datasetId: String, val revision: Long = 0)

data class MealDraft(
    val id: String? = null,
    val date: String = LocalDate.now().toString(),
    val mealSlot: MealSlot = MealSlot.BREAKFAST,
    val plannedAtHome: Boolean? = null,
    val mealMethodId: String? = null,
    val categoryIds: Set<String> = emptySet(),
    val newCategoryNames: List<String> = emptyList(),
    val dishes: String = "",
    val note: String = "",
)
data class AppData(
    val state: DataState,
    val methods: List<MealMethod>,
    val categories: List<Category>,
    val meals: List<MealRecord>,
    val links: List<MealPreparationCategory>,
    val theme: ThemePreferences,
) {
    fun categoryIds(mealId: String) = links.filter { it.mealId == mealId }.map { it.categoryId }.toSet()
    fun draft(meal: MealRecord) = MealDraft(meal.id, meal.date, meal.mealSlot, meal.plannedAtHome,
        meal.mealMethodId, categoryIds(meal.id), dishes = meal.dishes, note = meal.note)
}

/** Business validation runs inside the repository transaction and is shared with JVM tests. */
object MealRules {
    fun categoriesForSave(draft: MealDraft, original: MealRecord?, methods: List<MealMethod>): Set<String> {
        validatedDate(draft.date)
        require(draft.plannedAtHome != null) { "请选择是否计划在家吃" }
        require(draft.plannedAtHome != true || draft.mealMethodId != null) { "计划在家吃时，请选择用餐方式" }
        val method = draft.mealMethodId?.let { id -> methods.find { it.id == id } ?: error("用餐方式不存在") }
        require(method == null || !method.archived || original?.mealMethodId == method.id) { "该方式已停用，请重新选择" }
        return when {
            method == null -> emptySet()
            !method.showPreparationCategories && original?.mealMethodId != method.id -> emptySet()
            else -> draft.categoryIds
        }
    }
    fun previous(data: AppData, date: String, slot: MealSlot): MealRecord? = data.meals
        .filter { it.date < date || (it.date == date && it.mealSlot.ordinal < slot.ordinal) }
        .maxWithOrNull(compareBy<MealRecord> { it.date }.thenBy { it.mealSlot.ordinal })
}

data class MealStatistics(val total: Int, val atHome: Int, val away: Int,
    val methods: Map<String?, Int>, val categories: Map<String, Int>)
fun mealStatistics(data: AppData, month: YearMonth): MealStatistics {
    val meals = data.meals.filter { YearMonth.from(LocalDate.parse(it.date)) == month }
    val ids = meals.map { it.id }.toSet()
    return MealStatistics(meals.size, meals.count { it.plannedAtHome }, meals.count { !it.plannedAtHome },
        meals.groupingBy { it.mealMethodId }.eachCount(), data.links.filter { it.mealId in ids }.groupingBy { it.categoryId }.eachCount())
}
