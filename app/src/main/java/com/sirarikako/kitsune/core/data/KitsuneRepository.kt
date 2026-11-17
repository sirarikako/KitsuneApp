package com.sirarikako.kitsune.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class KitsuneRepository(private val db: KitsuneDatabase) {
    private val dao = db.dao()
    private fun id() = UUID.randomUUID().toString()
    private fun now() = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()
    val data = dao.observeState().filterNotNull().map { db.withTransaction { read() } }.flowOn(Dispatchers.IO)
    private suspend fun read() = AppData(dao.state() ?: error("数据库尚未初始化"), dao.methods(), dao.categories(), dao.meals(), dao.links(), dao.theme())
    suspend fun initialize() = db.withTransaction {
        if (dao.state() == null) {
            val time = now()
            listOf("下厨" to true, "外卖" to false).forEachIndexed { order, (name, show) ->
                dao.put(MealMethod(id(), name, normalizedName(name), order, showPreparationCategories = show, createdAt = time, updatedAt = time))
            }
            listOf("炒菜", "预制菜").forEachIndexed { order, name ->
                dao.put(Category(id = id(), name = name, normalizedName = normalizedName(name), sortOrder = order, createdAt = time, updatedAt = time))
            }
            listOf(CategoryScope.INCOME, CategoryScope.EXPENSE).forEach { scope ->
                dao.put(Category(id = id(), scope = scope, name = "未分类", normalizedName = "未分类", sortOrder = 0, isDefault = true, createdAt = time, updatedAt = time))
            }
            dao.put(ThemePreferences())
            dao.put(DataState(datasetId = id()))
        }
    }
    /** Only successful, changed business transactions advance the local revision.
     * No backup targets exist in stage 1, so no fictitious backup jobs are accumulated. */
    private suspend fun change(block: suspend () -> Boolean) = db.withTransaction {
        if (block()) {
            val state = dao.state() ?: error("数据库尚未初始化")
            dao.put(state.copy(revision = Math.addExact(state.revision, 1)))
        }
    }
    suspend fun saveMeal(draft: MealDraft) = change {
        val data = read()
        val original = draft.id?.let { mealId -> data.meals.find { it.id == mealId } ?: error("记录已不存在，请重新打开") }
        val selected = MealRules.categoriesForSave(draft, original, data.methods).toMutableSet()
        require(data.meals.none { it.date == draft.date && it.mealSlot == draft.mealSlot && it.id != draft.id }) { "这一天的${draft.mealSlot.label}已有记录，请编辑已有记录" }
        val method = data.methods.find { it.id == draft.mealMethodId }
        val time = now()
        if (method?.showPreparationCategories == true) {
            val categories = data.categories.toMutableList()
            draft.newCategoryNames.map(::validatedName).distinctBy(::normalizedName).forEach { name ->
                val existing = categories.find { it.scope == CategoryScope.PREPARATION && it.normalizedName == normalizedName(name) }
                require(existing?.archived != true) { "分类“$name”已停用，请在设置中启用" }
                val category = existing ?: Category(id = id(), name = name, normalizedName = normalizedName(name),
                    sortOrder = categories.filter { it.scope == CategoryScope.PREPARATION }.size, createdAt = time, updatedAt = time)
                if (existing == null) { dao.put(category); categories.add(category) }
                selected.add(category.id)
            }
        }
        val originalIds = original?.let { data.categoryIds(it.id) }.orEmpty()
        val allCategories = dao.categories()
        selected.forEach { categoryId ->
            val category = allCategories.find { it.id == categoryId } ?: error("制作分类不存在")
            require(category.scope == CategoryScope.PREPARATION && (!category.archived || categoryId in originalIds)) { "所选制作分类已停用或类型不符" }
        }
        val meal = MealRecord(original?.id ?: id(), draft.date, draft.mealSlot, draft.plannedAtHome!!,
            draft.mealMethodId, draft.dishes, draft.note, original?.createdAt ?: time, time)
        if (original != null && meal.copy(updatedAt = original.updatedAt) == original && selected == originalIds) return@change false
        if (original == null) dao.insertMeal(meal) else dao.updateMeal(meal)
        dao.clearLinks(meal.id)
        dao.addLinks(selected.map { MealPreparationCategory(meal.id, it) })
        true
    }
    suspend fun deleteMeal(id: String) = change {
        if (dao.meals().none { it.id == id }) false else { dao.deleteMeal(id); true }
    }
    suspend fun saveMethod(originalId: String?, name: String, show: Boolean) = change {
        val clean = validatedName(name)
        val methods = dao.methods()
        require(methods.none { it.id != originalId && it.normalizedName == normalizedName(clean) }) { "用餐方式名称已存在（包括停用项）" }
        val old = originalId?.let { id -> methods.find { it.id == id } ?: error("方式不存在") }
        if (old?.name == clean && old.showPreparationCategories == show) return@change false
        val time = now()
        dao.put(old?.copy(name = clean, normalizedName = normalizedName(clean), showPreparationCategories = show, updatedAt = time)
            ?: MealMethod(id(), clean, normalizedName(clean), methods.size, showPreparationCategories = show, createdAt = time, updatedAt = time))
        true
    }
    suspend fun saveCategory(originalId: String?, name: String) = change {
        val clean = validatedName(name)
        val categories = dao.categories().filter { it.scope == CategoryScope.PREPARATION }
        require(categories.none { it.id != originalId && it.normalizedName == normalizedName(clean) }) { "制作分类名称已存在（包括停用项）" }
        val old = originalId?.let { id -> categories.find { it.id == id } ?: error("分类不存在") }
        if (old?.name == clean) return@change false
        val time = now()
        dao.put(old?.copy(name = clean, normalizedName = normalizedName(clean), updatedAt = time)
            ?: Category(id = id(), name = clean, normalizedName = normalizedName(clean), sortOrder = categories.size, createdAt = time, updatedAt = time))
        true
    }
    suspend fun archiveMethod(id: String) = change {
        val method = dao.methods().first { it.id == id }
        dao.put(method.copy(archived = !method.archived, updatedAt = now())); true
    }
    suspend fun archiveCategory(id: String) = change {
        val category = dao.categories().first { it.id == id }
        require(!category.isDefault) { "兜底分类不可停用" }
        dao.put(category.copy(archived = !category.archived, updatedAt = now())); true
    }
    suspend fun moveMethod(id: String, direction: Int) = change {
        val items = dao.methods().toMutableList(); val index = items.indexOfFirst { it.id == id }; val next = index + direction
        if (index < 0 || next !in items.indices) return@change false
        java.util.Collections.swap(items, index, next)
        val time = now(); items.forEachIndexed { order, item -> dao.put(item.copy(sortOrder = order, updatedAt = time)) }; true
    }
    suspend fun moveCategory(id: String, direction: Int) = change {
        val items = dao.categories().filter { it.scope == CategoryScope.PREPARATION }.toMutableList()
        val index = items.indexOfFirst { it.id == id }; val next = index + direction
        if (index < 0 || next !in items.indices) return@change false
        java.util.Collections.swap(items, index, next)
        val time = now(); items.forEachIndexed { order, item -> dao.put(item.copy(sortOrder = order, updatedAt = time)) }; true
    }
    suspend fun applyTheme(theme: ThemePreferences) = change {
        require(theme.presetId in setOf("apricot", "sage", "mist_blue", "lavender", "rose")) { "未知调色板" }
        require(Regex("#[0-9A-F]{6}").matches(theme.customSeedColor)) { "请输入六位 HEX 颜色" }
        if (dao.theme() == theme) false else { dao.put(theme); true }
    }
}
