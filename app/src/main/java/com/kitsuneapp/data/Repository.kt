package com.kitsuneapp.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val DEFAULT_COOKING_METHOD_ID = "10000000-0000-4000-8000-000000000001"

data class Records(
    val state: DataState,
    val theme: ThemePreferences,
    val methods: List<MealMethod>,
    val categories: List<Category>,
    val meals: List<MealRecord>,
    val preparation: Map<String, List<String>>,
    val wallets: List<Wallet>,
    val entries: List<FinancialEntry>,
) {
    val methodNames = methods.associate { it.id to it.name }
    val categoryNames = categories.associate { it.id to it.name }
    fun methodName(id: String?) = methodNames[id] ?: "未填写"
    fun preparationNames(id: String) = preparation[id].orEmpty().mapNotNull(categoryNames::get)
}

data class MealDraft(
    val id: String? = null,
    val date: String,
    val slot: MealSlot,
    val atHome: Boolean?,
    val methodId: String?,
    val preparationIds: List<String>,
    val newPreparations: List<String>,
    val dishes: String,
    val note: String,
    val clearHistoricalPreparation: Boolean = false,
)

class OwnershipException : IllegalStateException("此数据库需要重新确认设备归属")

class Repository internal constructor(
    internal val database: KitsuneDatabase,
    private val installationId: String,
    private val onChanged: () -> Unit = {},
) {
    internal val dao = database.records()
    internal val writes = Mutex()

    suspend fun initialize(newDatabase: Boolean) = database.withTransaction {
        val state = dao.state()
        if (state != null) {
            if (state.installationId != installationId) throw OwnershipException()
            return@withTransaction
        }
        if (!newDatabase) throw OwnershipException()
        val now = utcNow()
        dao.insert(listOf(
            MealMethod(DEFAULT_COOKING_METHOD_ID, "下厨", "下厨", 0, false, true, now, now),
            MealMethod("10000000-0000-4000-8000-000000000002", "外卖", "外卖", 1, false, false, now, now),
        ))
        dao.insertCategories(listOf(
            seedCategory("1", Scope.PREPARATION, "炒菜", now, sort = 0),
            seedCategory("4", Scope.PREPARATION, "预制菜", now, sort = 1),
            seedCategory("2", Scope.INCOME, "未分类", now, default = true, sort = 0),
            seedCategory("3", Scope.EXPENSE, "未分类", now, default = true, sort = 0),
        ))
        dao.put(ThemePreferences())
        dao.put(DataState(installationId = installationId, datasetId = newId(), arrayElements = 6))
    }

    private fun seedCategory(suffix: String, scope: Scope, name: String, now: String, sort: Int, default: Boolean = false) =
        Category("20000000-0000-4000-8000-00000000000$suffix", scope, null, GLOBAL_OWNER,
            name, normalizedName(name), sort, false, default, now, now)

    internal suspend fun ownedState(): DataState = checkNotNull(dao.state()).also {
        if (it.installationId != installationId) throw OwnershipException()
    }

    internal fun notifyChanged() = onChanged()

    fun observe(): Flow<Records> = database.invalidationTracker
        .createFlow("DataState").map { read() }.distinctUntilChanged()

    suspend fun read(): Records = database.withTransaction {
        Records(ownedState(), checkNotNull(dao.theme()), dao.methods(), dao.categories(), dao.meals(),
            dao.preparation().groupBy({ it.mealId }, { it.categoryId }), dao.wallets(), dao.entries())
    }

    internal suspend fun change(block: suspend () -> Boolean): Boolean = writes.withLock {
        val changed = database.withTransaction {
            val state = ownedState()
            if (!block()) return@withTransaction false
            val count = dao.countElements()
            require(count <= MAX_ARRAY_ELEMENTS || count < state.arrayElements) { "完整备份的记录和关联总数超过 200,000" }
            dao.put(state.copy(revision = Math.addExact(state.revision, 1L), arrayElements = count))
            true
        }
        if (changed) notifyChanged()
        changed
    }

    suspend fun saveMeal(draft: MealDraft) = change {
        val original = draft.id?.let { requireNotNull(dao.meal(it)) { "这条用餐记录已不存在" } }
        val date = validateDate(draft.date)
        val atHome = requireNotNull(draft.atHome) { "请选择是否计划在家吃" }
        require(!atHome || draft.methodId != null) { "请选择用餐方式" }
        val method = draft.methodId?.let { requireNotNull(dao.method(it)) { "用餐方式已不存在" } }
        require(method?.archived != true || method.id == original?.mealMethodId) { "请选择有效的用餐方式" }
        require(dao.mealAt(date, draft.slot)?.id.let { it == null || it == original?.id }) { "该日期与餐次已有记录" }
        validateField("菜品", draft.dishes)
        validateField("备注", draft.note)
        val previous = original?.let { dao.preparationIds(it.id) }.orEmpty().toSet()
        val selected = when {
            method == null -> emptySet()
            method.showPreparationCategories -> {
                val existing = draft.preparationIds.toSet()
                existing.forEach { id ->
                    val category = requireNotNull(dao.category(id)) { "制作分类不存在" }
                    require(category.scope == Scope.PREPARATION && (!category.archived || id in previous)) { "制作分类不可用" }
                }
                existing + draft.newPreparations.map { resolveCategory(Scope.PREPARATION, null, it).id }
            }
            original?.mealMethodId == method.id && !draft.clearHistoricalPreparation -> previous
            else -> emptySet()
        }
        val now = utcNow()
        val record = MealRecord(original?.id ?: newId(), date, draft.slot, atHome, method?.id,
            draft.dishes, draft.note, original?.createdAt ?: now, now)
        if (original != null && record.copy(updatedAt = original.updatedAt) == original && selected == previous) return@change false
        dao.put(record)
        dao.deletePreparation(record.id)
        dao.insertPreparation(selected.map { MealPreparationCategory(record.id, it) })
        true
    }

    suspend fun deleteMeal(id: String) = change { dao.deleteMeal(id) > 0 }

    suspend fun saveMethod(id: String?, name: String, order: Int?, archived: Boolean, show: Boolean) = change {
        val original = id?.let { requireNotNull(dao.method(it)) { "用餐方式已不存在" } }
        val sortOrder = order ?: original?.sortOrder ?: nextSortOrder(dao.maxMethodOrder())
        require(sortOrder >= 0) { "排序必须为非负整数" }
        val clean = cleanName(name)
        val normalized = normalizedName(clean)
        require(dao.methodByName(normalized)?.id.let { it == null || it == id }) { "已有同名用餐方式" }
        val now = utcNow()
        val method = MealMethod(id ?: newId(), clean, normalized, sortOrder, archived, show, original?.createdAt ?: now, now)
        if (original != null && method.copy(updatedAt = original.updatedAt) == original) return@change false
        dao.put(method)
        true
    }

    suspend fun deleteMethod(id: String) = change {
        require(dao.methodUses(id) == 0) { "已有记录使用此方式，请停用" }
        dao.deleteMethod(id) > 0
    }

    internal suspend fun resolveCategory(scope: Scope, walletId: String?, name: String, allowArchived: Boolean = false): Category {
        val clean = cleanName(name)
        if (scope != Scope.PREPARATION && clean == "未分类") {
            return checkNotNull(dao.categoryByName(scope, GLOBAL_OWNER, clean)).also { check(it.isDefault) }
        }
        val existing = dao.categoryByName(scope, walletId ?: GLOBAL_OWNER, normalizedName(clean))
        if (existing != null) {
            require(!existing.archived || allowArchived) { "分类已停用，请在设置中启用" }
            return existing
        }
        val nextOrder = nextSortOrder(dao.maxCategoryOrder(scope, walletId ?: GLOBAL_OWNER))
        return buildCategory(null, scope, walletId, clean, nextOrder, false).also { dao.put(it) }
    }

    suspend fun saveCategory(id: String?, scope: Scope, walletId: String?, name: String, order: Int?, archived: Boolean) = change {
        val original = id?.let { requireNotNull(dao.category(it)) { "分类已不存在" } }
        val sortOrder = order ?: original?.sortOrder ?: nextSortOrder(dao.maxCategoryOrder(scope, walletId ?: GLOBAL_OWNER))
        val category = buildCategory(original, scope, walletId, name, sortOrder, archived)
        if (original != null && category.copy(updatedAt = original.updatedAt) == original) return@change false
        dao.put(category)
        true
    }

    private suspend fun buildCategory(original: Category?, scope: Scope, walletId: String?, name: String, order: Int, archived: Boolean): Category {
        require(order >= 0) { "排序必须为非负整数" }
        require(walletId == null || (scope != Scope.PREPARATION && dao.wallet(walletId) != null)) { "分类归属无效" }
        val clean = cleanName(name)
        require(scope == Scope.PREPARATION || clean != "未分类") { "“未分类”是保留名称" }
        require(original == null || (!original.isDefault && original.scope == scope && original.walletId == walletId)) {
            "不能更改兜底分类或分类归属"
        }
        val owner = walletId ?: GLOBAL_OWNER
        val normalized = normalizedName(clean)
        require(dao.categoryByName(scope, owner, normalized)?.id.let { it == null || it == original?.id }) {
            "此范围已有同名分类"
        }
        val now = utcNow()
        return Category(original?.id ?: newId(), scope, walletId, owner, clean, normalized, order, archived,
            false, original?.createdAt ?: now, now)
    }

    suspend fun deleteCategory(id: String) = change {
        require(dao.category(id)?.isDefault != true) { "不能删除兜底分类" }
        require(dao.categoryUses(id) == 0) { "已有记录使用此分类，请停用" }
        dao.deleteCategory(id) > 0
    }

    suspend fun applyTheme(theme: ThemePreferences) = change {
        validateTheme(theme)
        if (theme == dao.theme()) return@change false
        dao.put(theme)
        true
    }
}

private fun nextSortOrder(lastOrder: Int?): Int = lastOrder?.let { if (it == Int.MAX_VALUE) it else it + 1 } ?: 0
