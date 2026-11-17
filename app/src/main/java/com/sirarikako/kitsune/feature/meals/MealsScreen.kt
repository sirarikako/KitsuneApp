package com.sirarikako.kitsune.feature.meals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sirarikako.kitsune.core.data.*
import com.sirarikako.kitsune.ui.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MealCard(data: AppData, meal: MealRecord?, slot: MealSlot, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(slot.label, style = MaterialTheme.typography.titleLarge)
            if (meal == null) {
                Text("还没记录这一餐", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("＋ 添加记录", color = MaterialTheme.colorScheme.primary)
            } else {
                Text("计划在家：${if (meal.plannedAtHome) "是" else "否"} · ${data.methods.find { it.id == meal.mealMethodId }?.name ?: "方式未填写"}")
                if (meal.dishes.isNotEmpty()) Text(meal.dishes, style = MaterialTheme.typography.bodyLarge)
                val categories = data.categories.filter { it.id in data.categoryIds(meal.id) }
                if (categories.isNotEmpty()) Text(categories.joinToString(" · ") { it.name }, color = MaterialTheme.colorScheme.primary)
                if (meal.note.isNotEmpty()) Text(meal.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("点击编辑", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
@Composable
fun MealsScreen(data: AppData, onEdit: (MealDraft) -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var slotFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var methodFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val date = LocalDate.parse(dateText)
    val records = data.meals.filter { meal ->
        (page == 1 || meal.date == dateText) && (slotFilter == null || meal.mealSlot.name == slotFilter) &&
            (methodFilter == null || (if (methodFilter == "EMPTY") meal.mealMethodId == null else meal.mealMethodId == methodFilter)) &&
            (categoryFilter == null || categoryFilter in data.categoryIds(meal.id)) &&
            (search.isEmpty() || meal.dishes.contains(search, ignoreCase = true) || meal.note.contains(search, ignoreCase = true))
    }.sortedWith(compareByDescending<MealRecord> { it.date }.thenBy { it.mealSlot.ordinal })
    val stats = remember(data, dateText) { mealStatistics(data, YearMonth.from(date)) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("用餐手记", style = MaterialTheme.typography.headlineLarge) }
        item { ChipRow { listOf("按日", "历史搜索", "月度统计").forEachIndexed { index, label -> FilterChip(page == index, onClick = { page = index }, label = { Text(label) }) } } }
        item { DateSelector(date) { dateText = it.toString() } }
        if (page == 2) {
            item { SectionCard("${YearMonth.from(date)}") {
                Text("已记录 ${stats.total} 餐")
                Text("计划在家：是 ${stats.atHome} · 否 ${stats.away}")
            } }
            item { SectionCard("用餐方式") {
                if (stats.total == 0) Text("这个月还没有记录")
                stats.methods.forEach { (id, count) -> Text("${data.methods.find { it.id == id }?.name ?: "未填写"}：$count 餐") }
            } }
            item { SectionCard("制作分类") {
                if (stats.categories.isEmpty()) Text("这个月没有制作分类记录")
                stats.categories.forEach { (id, count) -> Text("${data.categories.find { it.id == id }?.name}：$count 次") }
                Text("多选分类分别计数，包含历史分类。", style = MaterialTheme.typography.bodySmall)
            } }
        } else {
            item { OutlinedTextField(search, onValueChange = { search = it }, label = { Text("搜索菜品或备注") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { ChipRow {
                FilterChip(slotFilter == null, onClick = { slotFilter = null }, label = { Text("全部餐次") })
                MealSlot.entries.forEach { slot -> FilterChip(slotFilter == slot.name, onClick = { slotFilter = if (slotFilter == slot.name) null else slot.name }, label = { Text(slot.label) }) }
            } }
            item { ChipRow {
                FilterChip(methodFilter == null, onClick = { methodFilter = null }, label = { Text("全部方式") })
                FilterChip(methodFilter == "EMPTY", onClick = { methodFilter = if (methodFilter == "EMPTY") null else "EMPTY" }, label = { Text("未填写") })
                data.methods.forEach { method -> FilterChip(methodFilter == method.id, onClick = { methodFilter = if (methodFilter == method.id) null else method.id }, label = { Text(method.name + if (method.archived) "（停用）" else "") }) }
            } }
            item { ChipRow {
                FilterChip(categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("全部制作分类") })
                data.categories.filter { it.scope == CategoryScope.PREPARATION }.forEach { category -> FilterChip(categoryFilter == category.id, onClick = { categoryFilter = if (categoryFilter == category.id) null else category.id }, label = { Text(category.name) }) }
            } }
            item { FilledTonalButton(onClick = { onEdit(MealDraft(date = dateText)) }) { Text("＋ 记录一餐") } }
            if (records.isEmpty()) item { Text("没有符合条件的记录。可以添加用餐，或调整筛选。") }
            items(records, key = { it.id }) { meal ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (page == 1) Text(meal.date, style = MaterialTheme.typography.labelLarge)
                    MealCard(data, meal, meal.mealSlot) { onEdit(data.draft(meal)) }
                }
            }
        }
    }
}
