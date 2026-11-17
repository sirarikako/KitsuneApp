package com.sirarikako.kitsune.feature.meals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sirarikako.kitsune.core.data.*
import com.sirarikako.kitsune.ui.*

val MealDraftSaver = listSaver<MealDraft?, Any>(
    save = { draft -> if (draft == null) listOf(false) else listOf(true, draft.id.orEmpty(), draft.date, draft.mealSlot.name,
        draft.plannedAtHome?.let { if (it) 1 else 0 } ?: -1, draft.mealMethodId.orEmpty(),
        ArrayList(draft.categoryIds), ArrayList(draft.newCategoryNames), draft.dishes, draft.note) },
    restore = { values ->
        if (values[0] == false) null else {
            @Suppress("UNCHECKED_CAST")
            MealDraft((values[1] as String).ifEmpty { null }, values[2] as String, MealSlot.valueOf(values[3] as String),
                when (values[4] as Int) { 1 -> true; 0 -> false; else -> null }, (values[5] as String).ifEmpty { null },
                (values[6] as List<String>).toSet(), values[7] as List<String>, values[8] as String, values[9] as String)
        }
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealEditor(data: AppData, initial: MealDraft, busy: Boolean, onCancel: () -> Unit,
    onSave: (MealDraft) -> Unit, onDelete: (String) -> Unit) {
    var savedDraft by rememberSaveable(stateSaver = MealDraftSaver) { mutableStateOf<MealDraft?>(initial) }
    val draft = savedDraft ?: initial
    fun update(next: MealDraft) { savedDraft = next }
    var localError by remember { mutableStateOf<String?>(null) }
    var categoryMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    val method = data.methods.find { it.id == draft.mealMethodId }
    val original = data.meals.find { it.id == draft.id }
    val historical = method?.showPreparationCategories == false && draft.categoryIds.isNotEmpty()
    BackHandler { if (!busy) onCancel() }
    Scaffold(
        topBar = { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel, enabled = !busy) { Text("取消") }
            Text(if (draft.id == null) "记录一餐" else "编辑用餐", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = draft.id != null && !busy, onClick = { deleting = true }) { Text("删除") }
        } },
        bottomBar = { Surface(tonalElevation = 3.dp) {
            Button(onClick = {
                try {
                    MealRules.categoriesForSave(draft, original, data.methods)
                    localError = null; onSave(draft)
                } catch (e: IllegalArgumentException) { localError = e.message }
                  catch (e: IllegalStateException) { localError = e.message }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(if (busy) "正在保存…" else "保存到本地") }
        } }, modifier = Modifier.imePadding(),
    ) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).testTag("meal_form"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { OutlinedTextField(draft.date, { update(draft.copy(date = it)) }, label = { Text("日期 · YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { ChipRow { MealSlot.entries.forEach { slot -> FilterChip(draft.mealSlot == slot, onClick = { update(draft.copy(mealSlot = slot)) }, label = { Text(slot.label) }) } } }
            item { SectionCard("是否计划在家吃 · 必选") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(draft.plannedAtHome == true, onClick = { update(draft.copy(plannedAtHome = true)) }, label = { Text("是") })
                    FilterChip(draft.plannedAtHome == false, onClick = { update(draft.copy(plannedAtHome = false)) }, label = { Text("否") })
                }
                Text("记录原先的计划，与实际用餐方式独立。", style = MaterialTheme.typography.bodySmall)
            } }
            item { SectionCard(if (draft.plannedAtHome == true) "用餐方式 · 必选一项" else "用餐方式 · 可选一项") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.mealMethodId == null, onClick = { update(draft.copy(mealMethodId = null, categoryIds = emptySet(), newCategoryNames = emptyList())) }, label = { Text("不填写") })
                    data.methods.filter { !it.archived || it.id == original?.mealMethodId }.forEach { option ->
                        FilterChip(draft.mealMethodId == option.id, onClick = {
                            if (option.id != draft.mealMethodId) update(draft.copy(mealMethodId = option.id,
                                categoryIds = if (option.showPreparationCategories) draft.categoryIds else emptySet(),
                                newCategoryNames = if (option.showPreparationCategories) draft.newCategoryNames else emptyList()))
                        }, label = { Text(option.name + if (option.archived) "（停用）" else "") })
                    }
                }
            } }
            if (method?.showPreparationCategories == true || historical) item {
                SectionCard(if (historical) "历史制作分类" else "制作分类 · 多选") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.categories.filter { it.id in draft.categoryIds }.forEach { category ->
                            InputChip(selected = true, onClick = { update(draft.copy(categoryIds = draft.categoryIds - category.id)) }, label = { Text("${category.name} ×") })
                        }
                        draft.newCategoryNames.forEach { name -> InputChip(selected = true,
                            onClick = { update(draft.copy(newCategoryNames = draft.newCategoryNames - name)) }, label = { Text("$name（新） ×") }) }
                    }
                    if (method?.showPreparationCategories == true) {
                        Box {
                            OutlinedButton(onClick = { categoryMenu = true }) { Text("选择或新增分类 ▾") }
                            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }, modifier = Modifier.heightIn(max = 340.dp)) {
                                OutlinedTextField(query, onValueChange = { query = it }, label = { Text("搜索或输入新分类") }, modifier = Modifier.padding(8.dp), singleLine = true)
                                data.categories.filter { it.scope == CategoryScope.PREPARATION && !it.archived && it.name.contains(query.trim(), ignoreCase = true) }.forEach { category ->
                                    DropdownMenuItem(text = { Text((if (category.id in draft.categoryIds) "✓ " else "") + category.name) }, onClick = {
                                        update(draft.copy(categoryIds = if (category.id in draft.categoryIds) draft.categoryIds - category.id else draft.categoryIds + category.id))
                                    })
                                }
                                if (query.isNotBlank()) DropdownMenuItem(text = { Text("＋ 添加“${query.trim()}”") }, onClick = {
                                    val clean = query.trim()
                                    val existing = data.categories.find { it.scope == CategoryScope.PREPARATION && it.normalizedName == normalizedName(clean) }
                                    when {
                                        existing?.archived == true -> localError = "分类已停用，请先在设置中启用"
                                        existing != null -> update(draft.copy(categoryIds = draft.categoryIds + existing.id))
                                        draft.newCategoryNames.none { normalizedName(it) == normalizedName(clean) } -> update(draft.copy(newCategoryNames = draft.newCategoryNames + clean))
                                    }
                                    query = ""; categoryMenu = false
                                })
                            }
                        }
                        Text("新分类在保存此记录时创建；取消不会写入。", style = MaterialTheme.typography.bodySmall)
                    } else Text("方式设置已关闭分类。仅编辑其他内容时保留；切换方式或清空选择才移除。", style = MaterialTheme.typography.bodySmall)
                }
            }
            item { OutlinedTextField(draft.dishes, { update(draft.copy(dishes = it)) }, label = { Text("吃了什么？") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(draft.note, { update(draft.copy(note = it)) }, label = { Text("备注") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedButton(onClick = {
                try {
                    validatedDate(draft.date)
                    val previous = MealRules.previous(data, draft.date, draft.mealSlot)
                    if (previous == null) localError = "还没有更早的用餐记录"
                    else {
                        update(draft.copy(dishes = previous.dishes, note = previous.note,
                            categoryIds = if (method?.showPreparationCategories == true) data.categoryIds(previous.id).filter { id -> data.categories.any { it.id == id && !it.archived } }.toSet() else draft.categoryIds))
                        localError = null
                    }
                } catch (e: Exception) { localError = e.message }
            }) { Text("复制上一餐内容") } }
            if (localError != null) item { Text(localError!!, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("删除这一餐？") }, text = { Text("删除后将无法在当前设备中撤销。") },
        confirmButton = { TextButton(onClick = { draft.id?.let(onDelete); deleting = false }, enabled = !busy) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } })
}
