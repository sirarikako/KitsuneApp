package com.kitsuneapp.feature.meals

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsuneapp.AppViewModel
import com.kitsuneapp.data.*
import com.kitsuneapp.ui.*
import java.io.Serializable
import java.time.LocalDate
import java.time.YearMonth

data class MealFilter(
    val start: String = "",
    val end: String = "",
    val slot: MealSlot? = null,
    val atHome: Boolean? = null,
    val method: String = "",
    val categories: List<String> = emptyList(),
    val query: String = "",
) : Serializable {
    fun matches(meal: MealRecord, records: Records): Boolean =
        (start.isEmpty() || meal.date >= start) && (end.isEmpty() || meal.date <= end) &&
            (slot == null || meal.mealSlot == slot) && (atHome == null || meal.plannedAtHome == atHome) &&
            (method.isEmpty() || method == (meal.mealMethodId ?: "none")) &&
            (categories.isEmpty() || records.preparation[meal.id].orEmpty().any { it in categories }) &&
            (meal.dishes.contains(query.trim(), true) || meal.note.contains(query.trim(), true))

    fun labels(records: Records) = listOfNotNull(
        start.takeIf(String::isNotEmpty)?.let { "从 $it" },
        end.takeIf(String::isNotEmpty)?.let { "至 $it" },
        slot?.label, atHome?.let { if (it) "计划在家吃" else "计划不在家吃" },
        method.takeIf(String::isNotEmpty)?.let(records::methodName),
        categories.takeIf(List<String>::isNotEmpty)?.joinToString("、") { records.categoryNames[it].orEmpty() },
        query.takeIf(String::isNotBlank),
    )
}

@Composable
fun MealsPage(records: Records, filter: MealFilter?, clearFilter: () -> Unit, openFilter: () -> Unit,
              openMeal: (String, MealSlot, String?) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    Column {
        Header("用餐")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tabs(listOf("记录", "统计"), tab) { tab = it }
            if (tab == 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (filter == null) DateControl("日期", date, { date = it }, Modifier.weight(1f))
                    else Choice("按日浏览", clearFilter, Modifier.weight(1f))
                    Choice("筛选", openFilter)
                }
                if (filter == null) {
                    val day = LocalDate.parse(date)
                    Paper(padding = 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
                            (0L..6L).forEach { offset ->
                                val weekday = monday.plusDays(offset)
                                Box(Modifier.weight(1f)) {
                                    Choice(weekday.dayOfMonth.toString(), { date = weekday.toString() },
                                        Modifier.fillMaxWidth(), selected = weekday == day)
                                }
                            }
                        }
                    }
                }
            } else MonthControl(month) { month = it }
        }
        Crossfade(tab, animationSpec = tween(150, easing = motion), label = "用餐标签") { section ->
            if (section == 1) MealStats(records, month)
            else if (filter == null) {
                val today = records.meals.filter { it.date == date }.associateBy { it.mealSlot }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Section("三餐记录") }
                    items(MealSlot.entries, key = { it.name }) { slot ->
                        val meal = today[slot]
                        if (meal == null) RecordRow(slot.label, "待记录", icon = Icons.Outlined.Add,
                            empty = true, onClick = { openMeal(date, slot, null) })
                        else MealRow(records, meal) { openMeal(date, slot, meal.id) }
                    }
                }
            } else {
                val matches = remember(records, filter) {
                    records.meals.filter { filter.matches(it, records) }
                        .sortedWith(compareByDescending<MealRecord> { it.date }.thenBy { it.mealSlot.ordinal })
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        filter.labels(records).takeIf(List<String>::isNotEmpty)?.let { Secondary(it.joinToString(" · ")) }
                        Section("筛选结果") {
                            Secondary("${matches.size} 条")
                            if (filter != MealFilter()) TextButton(clearFilter) { Text("清空条件") }
                        }
                    }
                    if (matches.isEmpty()) item { Paper { Text("没有符合条件的记录") } }
                    items(matches, key = { it.id }) { meal ->
                        MealRow(records, meal) { openMeal(meal.date, meal.mealSlot, meal.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealRow(records: Records, meal: MealRecord, onClick: () -> Unit) {
    val prep = records.preparationNames(meal.id)
    val detail = (listOf(if (meal.plannedAtHome) "计划在家吃" else "计划不在家吃") + prep + meal.note)
        .filter(String::isNotBlank).joinToString(" · ")
    RecordRow(meal.dishes.ifBlank { meal.mealSlot.label },
        "${meal.date} · ${meal.mealSlot.label} · ${records.methodName(meal.mealMethodId)}",
        detail, Icons.Outlined.DateRange, onClick)
}

@Composable
private fun MealStats(records: Records, month: String) {
    val all = records.meals
    val monthly = remember(all, month) { all.filter { it.date.startsWith(month) } }
    val home = monthly.count { it.plannedAtHome }
    val methodGroups = monthly.groupingBy { it.mealMethodId }.eachCount()
    val prep = monthly.flatMap { records.preparation[it.id].orEmpty() }.groupingBy { it }.eachCount()
    val allGroups = all.groupingBy { it.mealMethodId }.eachCount()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Section("本月概览")
            Summary("已记录" to "${monthly.size} 餐", "计划在家吃" to "$home 餐")
        }
        item {
            Paper {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("计划在家 · 是 $home 餐")
                    Text("否 ${monthly.size - home} 餐")
                }
                if (monthly.isNotEmpty()) LinearProgressIndicator(
                    progress = { home.toFloat() / monthly.size }, modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.secondaryContainer)
            }
        }
        item { Section("本月 · 用餐方式") }
        if (methodGroups.isEmpty()) item { Paper { Text("这个月还没有用餐记录") } }
        else item {
            Paper {
                methodGroups.forEach { (id, count) -> CountBar(records.methodName(id), count, monthly.size) }
            }
        }
        item { Section("本月 · 制作分类") }
        item {
            Paper {
                if (prep.isEmpty()) Text("暂无制作分类记录")
                prep.forEach { (id, count) -> CountBar(records.categoryNames[id].orEmpty(), count, monthly.size, "次") }
            }
        }
        item { Section("全部记录 · 用餐方式") { Secondary("共 ${all.size} 餐") } }
        item {
            Paper {
                if (allGroups.isEmpty()) Text("暂无用餐记录")
                allGroups.forEach { (id, count) -> CountBar(records.methodName(id), count, all.size) }
            }
        }
    }
}

@Composable
fun MealFilterPage(records: Records, initial: MealFilter, back: () -> Unit, apply: (MealFilter) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf(false) }
    Column {
        Header("用餐筛选", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DateControl("开始日期", draft.start, { draft = draft.copy(start = it) }, allowEmpty = true)
            DateControl("结束日期", draft.end, { draft = draft.copy(end = it) }, allowEmpty = true)
            Select("餐次", draft.slot?.name ?: "", listOf("") + MealSlot.entries.map { it.name },
                { if (it.isEmpty()) "不限" else MealSlot.valueOf(it).label },
                { draft = draft.copy(slot = if (it.isEmpty()) null else MealSlot.valueOf(it)) })
            Select("计划在家吃", draft.atHome?.toString() ?: "", listOf("", "true", "false"),
                { if (it.isEmpty()) "不限" else if (it == "true") "是" else "否" },
                { draft = draft.copy(atHome = it.toBooleanStrictOrNull()) })
            Select("用餐方式", draft.method, listOf("", "none") + records.methods.map { it.id },
                { if (it.isEmpty()) "不限" else records.methodName(it) }, { draft = draft.copy(method = it) })
            Section("制作分类")
            MultiCategories(records.categories.filter { it.scope == Scope.PREPARATION }, draft.categories) {
                draft = draft.copy(categories = it)
            }
            Field("菜品或备注关键词", draft.query, { draft = draft.copy(query = it) })
            if (error) Text("开始日期不能晚于结束日期", color = MaterialTheme.colorScheme.error)
            Primary("应用筛选", {
                error = draft.start.isNotEmpty() && draft.end.isNotEmpty() && draft.start > draft.end
                if (!error) apply(draft)
            }, Modifier.fillMaxWidth())
            Choice("重置条件", { draft = MealFilter(); error = false }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MultiCategories(categories: List<Category>, selected: List<String>, set: (List<String>) -> Unit) {
    var menu by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    Choice(if (selected.isEmpty()) "选择制作分类" else "制作分类 · 已选 ${selected.size} 项", { menu = true }, Modifier.fillMaxWidth())
    if (selected.isNotEmpty()) Secondary(categories.filter { it.id in selected }.joinToString("、") { it.name })
    if (menu) {
        AlertDialog(onDismissRequest = { menu = false }, title = { Text("选择制作分类") }, shape = DialogShape,
            text = {
                Column {
                    Field("搜索分类", search, { search = it })
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(categories.filter { it.name.contains(search, true) }, key = { it.id }) { category ->
                            val checked = category.id in selected
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, { set(if (checked) selected - category.id else selected + category.id) })
                                Text(category.name + if (category.archived) "（已停用）" else "")
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton({ menu = false; search = "" }) { Text("完成") } })
    }
}

@Composable
fun MealForm(records: Records, id: String?, initialDate: String, slot: MealSlot, model: AppViewModel, back: () -> Unit) {
    val original = records.meals.find { it.id == id }
    var date by rememberSaveable { mutableStateOf(original?.date ?: initialDate) }
    var selectedSlot by rememberSaveable { mutableStateOf(original?.mealSlot ?: slot) }
    var atHome by rememberSaveable { mutableStateOf(original?.plannedAtHome) }
    var methodId by rememberSaveable { mutableStateOf(original?.mealMethodId) }
    var selected by rememberSaveable { mutableStateOf(ArrayList(records.preparation[id].orEmpty())) }
    var added by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var newName by rememberSaveable { mutableStateOf("") }
    var dishes by rememberSaveable { mutableStateOf(original?.dishes.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var delete by rememberSaveable { mutableStateOf(false) }
    val method = records.methods.find { it.id == methodId }
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    val earlier = records.meals.asSequence().filter {
        it.id != original?.id && (it.date < date || (it.date == date && it.mealSlot.ordinal < selectedSlot.ordinal))
    }.maxWithOrNull(compareBy<MealRecord> { it.date }.thenBy { it.mealSlot.ordinal })

    Column {
        Header(if (original == null) "记录用餐" else "编辑用餐", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateControl("日期", date, { date = it }, Modifier.weight(1f))
                Select("餐次", selectedSlot, MealSlot.entries, { it.label }, { selectedSlot = it }, Modifier.weight(1f))
            }
            Section("是否计划在家吃")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("是", { atHome = true }, Modifier.weight(1f), selected = atHome == true)
                Choice("否", { atHome = false }, Modifier.weight(1f), selected = atHome == false)
            }
            Select("用餐方式", methodId ?: "", listOf("") + records.methods.filter { !it.archived || it.id == methodId }.map { it.id },
                { if (it.isEmpty()) "未选择" else records.methodName(it) },
                { value ->
                    methodId = value.ifEmpty { null }
                    if (records.methods.find { it.id == methodId }?.showPreparationCategories != true) {
                        selected = arrayListOf()
                        added = arrayListOf()
                        newName = ""
                    }
                })
            if (method?.showPreparationCategories == true) {
                MultiCategories(records.categories.filter { it.scope == Scope.PREPARATION && (!it.archived || it.id in selected) },
                    selected, { selected = ArrayList(it) })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("新制作分类", newName, { newName = it }, Modifier.weight(1f))
                    Choice("添加", {
                        val name = newName.trim()
                        if (added.none { normalizedName(it) == normalizedName(name) }) added = ArrayList(added + name)
                        newName = ""
                    }, enabled = newName.isNotBlank())
                }
                added.forEach { name -> Choice("$name · 移除", { added = ArrayList(added - name) }) }
            } else if (method != null && method.id == original?.mealMethodId && records.preparationNames(original.id).isNotEmpty()) {
                Secondary("历史制作分类 · " + records.preparationNames(original.id).joinToString("、"))
            }
            if (earlier != null) TextButton({
                dishes = earlier.dishes
                note = earlier.note
                if (method?.showPreparationCategories == true) {
                    val valid = records.categories.filter { !it.archived || it.id in records.preparation[id].orEmpty() }
                        .map { it.id }.toSet()
                    selected = ArrayList(records.preparation[earlier.id].orEmpty().filter { it in valid })
                    added = arrayListOf()
                }
            }) { Text("复制上一餐内容") }
            Field("菜品内容", dishes, { dishes = it }, multiline = true)
            Field("备注", note, { note = it }, multiline = true)
            SaveActions(busy, error, {
                val draft = MealDraft(id, date, selectedSlot, atHome, methodId, selected,
                    if (newName.isBlank()) added else added + newName, dishes, note)
                model.save(back) { saveMeal(draft) }
            }, original?.let { "删除这条用餐记录" }, { delete = true })
        }
    }
    if (delete && original != null) ConfirmDelete(
        "${original.date} · ${original.mealSlot.label}\n" + original.dishes.take(140),
        { delete = false }, { delete = false; model.save(back) { deleteMeal(original.id) } })
}
