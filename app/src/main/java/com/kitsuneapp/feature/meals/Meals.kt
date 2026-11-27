package com.kitsuneapp.feature.meals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kitsuneapp.AppViewModel
import com.kitsuneapp.R
import com.kitsuneapp.data.*
import com.kitsuneapp.ui.*
import java.io.Serializable
import java.time.LocalDate
import java.time.YearMonth

data class MealFilter(
    val start: String = "",
    val end: String = "",
    val slots: List<MealSlot> = emptyList(),
    val atHome: List<Boolean> = emptyList(),
    val methods: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val query: String = "",
) : Serializable {
    fun matches(meal: MealRecord, records: Records): Boolean =
        (start.isEmpty() || meal.date >= start) && (end.isEmpty() || meal.date <= end) &&
            (slots.isEmpty() || meal.mealSlot in slots) && (atHome.isEmpty() || meal.plannedAtHome in atHome) &&
            (methods.isEmpty() || (meal.mealMethodId ?: NO_METHOD) in methods) &&
            (categories.isEmpty() || records.preparation[meal.id].orEmpty().any { it in categories }) &&
            (meal.dishes.contains(query.trim(), true) || meal.note.contains(query.trim(), true))

    fun labels(records: Records, includeDates: Boolean = true) = buildList {
        if (includeDates && (start.isNotEmpty() || end.isNotEmpty())) {
            add("${start.ifEmpty { "最早" }} ～ ${end.ifEmpty { "至今" }}")
        }
        MealSlot.entries.filter { it in slots }.forEach { add(it.label) }
        listOf(true, false).filter { it in atHome }.forEach { add("计划在家：${if (it) "是" else "否"}") }
        methods.forEach { method ->
            add(if (method == NO_METHOD) "方式未填写" else records.methodName(method))
        }
        addAll(categories.mapNotNull(records.categoryNames::get))
        if (query.isNotBlank()) add("关键词：${query.trim()}")
    }

    fun hasSameDates(other: MealFilter) = start == other.start && end == other.end

    /**
     * Drops conditions that reference meal methods or preparation categories no longer in [records].
     * Such stale IDs cannot match any record, cannot be shown as a label, and cannot be unchecked in the filter page.
     */
    fun validIn(records: Records): MealFilter {
        val validMethods = methods.filter { it == NO_METHOD || it in records.methodNames }
        val validCategories = categories.filter { it in records.preparationCategoryIds }
        return if (validMethods.size == methods.size && validCategories.size == categories.size) this
        else copy(methods = validMethods, categories = validCategories)
    }

    fun dateSummary(currentMonth: MealFilter): String {
        if (start.isEmpty() && end.isEmpty()) return "所有时间"
        if (start.isEmpty()) return "截至 $end"
        if (end.isEmpty()) return "从 $start 起"
        if (hasSameDates(currentMonth)) return "本月"
        val month = YearMonth.from(LocalDate.parse(start))
        return if (hasSameDates(forMonth(month))) "${month.year}年${month.monthValue}月" else "$start 至 $end"
    }

    fun withMonth(month: YearMonth) = copy(start = month.atDay(1).toString(), end = month.atEndOfMonth().toString())

    companion object {
        /** Method filter value for meals whose method was left empty. */
        const val NO_METHOD = "none"

        fun forMonth(month: YearMonth) = MealFilter().withMonth(month)
    }
}

private data class MealsContent(
    val date: String? = null,
    val filter: MealFilter? = null,
    val statsFilter: MealFilter? = null,
)

private fun MealSlot.glyph() = when (this) {
    MealSlot.BREAKFAST -> R.drawable.ks_sunrise
    MealSlot.LUNCH -> R.drawable.ks_sun
    MealSlot.DINNER -> R.drawable.ks_moon
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealsPage(
    records: Records,
    filter: MealFilter?,
    statsFilter: MealFilter,
    currentMonth: MealFilter,
    daily: () -> Unit,
    clearConditions: () -> Unit,
    openFilter: () -> Unit,
    openStatsFilter: () -> Unit,
    selectStatsMonth: (YearMonth) -> Unit,
    openMeal: (String, MealSlot, String?) -> Unit,
    openExisting: (MealRecord) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var statsMonthPicker by rememberSaveable { mutableStateOf(false) }
    val labels = filter?.labels(records).orEmpty()
    Column {
        Header("用餐")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tabs(listOf("记录", "统计"), tab) { tab = it }
            Spacer(Modifier.height(4.dp))
            if (tab == 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (filter == null) DateControl("用餐日期", date, { date = it }, Modifier.weight(1f), showLabel = false)
                    else Choice("按日浏览", daily, icon = designIcon(R.drawable.ks_chevron_left))
                    Choice(if (labels.isEmpty()) "筛选" else "筛选 (${labels.size})", openFilter,
                        selected = labels.isNotEmpty(), icon = designIcon(R.drawable.ks_list_filter))
                }
                if (filter == null) WeekStrip(LocalDate.parse(date)) { date = it.toString() }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedAction({ statsMonthPicker = true },
                        Modifier.weight(1f).semantics { contentDescription = "选择统计月份" }, spread = true) {
                        Text(statsFilter.dateSummary(currentMonth), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        Icon(designIcon(R.drawable.ks_chevron_down), null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Choice("筛选", openStatsFilter, icon = designIcon(R.drawable.ks_list_filter))
                }
            }
        }
        val content = when {
            tab == 1 -> MealsContent(statsFilter = statsFilter)
            filter != null -> MealsContent(filter = filter)
            else -> MealsContent(date = date)
        }
        ContentFade(content, Modifier.weight(1f)) { shown ->
            if (shown.statsFilter != null) MealStats(records, shown.statsFilter)
            else if (shown.date != null) {
                val today = records.meals.filter { it.date == shown.date }.associateBy { it.mealSlot }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Section("三餐记录") }
                    items(MealSlot.entries, key = { it.name }) { slot ->
                        val meal = today[slot]
                        if (meal == null) RecordRow(slot.label, "添加记录", icon = designIcon(slot.glyph()),
                            empty = true, onClick = { openMeal(shown.date, slot, null) })
                        else MealRow(records, meal, daily = true) { openExisting(meal) }
                    }
                }
            } else {
                val applied = requireNotNull(shown.filter)
                val matches = remember(records, applied) {
                    records.meals.filter { applied.matches(it, records) }
                        .sortedWith(compareByDescending<MealRecord> { it.date }.thenBy { it.mealSlot.ordinal })
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        val chips = applied.labels(records)
                        if (chips.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                chips.forEach { Tag(it) }
                            }
                        }
                        Section("筛选结果") {
                            Secondary("${matches.size} 条")
                            if (applied != MealFilter()) TextAction("清空条件", clearConditions)
                        }
                    }
                    if (matches.isEmpty()) item { EmptyState("没有符合条件的记录\n试试调整日期范围或筛选条件") }
                    items(matches, key = { it.id }) { meal ->
                        MealRow(records, meal) { openExisting(meal) }
                    }
                }
            }
        }
    }
    if (statsMonthPicker) {
        val dateInRange = statsFilter.start.ifEmpty { statsFilter.end }.ifEmpty { currentMonth.start }
        MonthPickerDialog(YearMonth.from(LocalDate.parse(dateInRange)), { statsMonthPicker = false }) { month ->
            statsMonthPicker = false
            selectStatsMonth(month)
        }
    }
}

@Composable
private fun WeekStrip(day: LocalDate, select: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
    val previous = day.minusWeeks(1)
    val next = day.plusWeeks(1)
    Paper(padding = 4) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextAction("上一周", { select(previous) }, leading = designIcon(R.drawable.ks_chevron_left),
                enabled = previous.year >= 1)
            TextAction("下一周", { select(next) }, trailing = designIcon(R.drawable.ks_chevron_right),
                enabled = next.year <= 9999)
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                val date = monday.plusDays(index.toLong())
                val selected = date == day
                val available = date.year in 1..9999
                val style = if (selected) Modifier.background(colors.primaryContainer)
                    .border(1.dp, colors.primary, ControlShape) else Modifier
                Column(Modifier.weight(1f).heightIn(min = 48.dp).clip(ControlShape).then(style)
                    .selectable(selected, enabled = available, role = Role.RadioButton, onClick = { select(date) })
                    .semantics { contentDescription = "${date.monthValue} 月 ${date.dayOfMonth} 日，星期$label" }
                    .padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                        color = when {
                            selected -> colors.onPrimaryContainer
                            available -> colors.onSurfaceVariant
                            else -> colors.onSurfaceVariant.copy(alpha = 0.6f)
                        })
                    Text(date.dayOfMonth.toString(), textAlign = TextAlign.Center,
                        color = when {
                            selected -> colors.onPrimaryContainer
                            available -> colors.onSurface
                            else -> colors.onSurfaceVariant.copy(alpha = 0.6f)
                        })
                }
            }
        }
    }
}

@Composable
private fun MealRow(records: Records, meal: MealRecord, daily: Boolean = false, onClick: () -> Unit) {
    val prep = records.preparationNames(meal.id)
    val plan = "计划在家：${if (meal.plannedAtHome) "是" else "否"}"
    val method = if (meal.mealMethodId == null) "方式未填写" else records.methodName(meal.mealMethodId)
    val detail = (listOf(plan) + prep + listOfNotNull(meal.note.takeIf(String::isNotBlank)?.let { "备注：$it" }))
        .filter(String::isNotBlank).joinToString(" · ")
    val dishes = meal.dishes.ifBlank { "未填写菜品" }
    val title = if (daily) meal.mealSlot.label else dishes
    val subtitle = if (daily) dishes else "${meal.date} · ${meal.mealSlot.label}"
    RecordRow(title, subtitle, if (daily) "" else detail, designIcon(meal.mealSlot.glyph()), onClick,
        badge = method, sharedId = meal.id, badgeInSubtitle = !daily)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealStats(records: Records, filter: MealFilter) {
    val matches = remember(records, filter) { records.meals.filter { filter.matches(it, records) } }
    val total = matches.size
    val home = matches.count { it.plannedAtHome }
    val methodGroups = matches.groupingBy { it.mealMethodId }.eachCount()
    val prep = matches.flatMap { records.preparation[it.id].orEmpty() }.groupingBy { it }.eachCount()
    val labels = filter.labels(records, includeDates = false)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (labels.isNotEmpty()) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                labels.forEach { Tag(it) }
            }
        }
        item {
            Section("用餐概览")
            Summary("已记录" to "$total 餐", "计划在家吃" to "$home 餐")
        }
        item {
            Paper {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("计划在家：是 $home 餐", Modifier.weight(1f))
                    Text("否 ${total - home} 餐")
                }
                if (total > 0) StatBar(home.toFloat() / total, segmented = true)
            }
        }
        item { Section("用餐方式") }
        if (methodGroups.isEmpty()) item { EmptyState("没有符合条件的用餐记录") }
        else item {
            Paper {
                methodGroups.forEach { (id, count) -> CountBar(records.methodName(id), count, total) }
            }
        }
        item { Section("制作分类") }
        if (prep.isEmpty()) item { EmptyState("暂无制作分类记录") }
        else item {
            Paper {
                prep.forEach { (id, count) -> CountBar(records.categoryNames[id].orEmpty(), count, total, "次") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealFilterPage(
    records: Records,
    initial: MealFilter,
    defaults: MealFilter,
    currentMonth: MealFilter,
    back: () -> Unit,
    apply: (MealFilter) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf(false) }
    var pickMethods by rememberSaveable { mutableStateOf(false) }
    val methodOptions = remember(records.methods) { listOf(MealFilter.NO_METHOD) + records.methods.map { it.id } }
    val methodById = remember(records.methods) { records.methods.associateBy { it.id } }
    fun methodOptionLabel(id: String): String = when (id) {
        MealFilter.NO_METHOD -> "未填写方式"
        else -> records.methodName(id) + if (methodById[id]?.archived == true) "（已停用）" else ""
    }
    val methodSummary = remember(draft.methods, methodById, records) {
        val chosen = methodOptions.filter { it in draft.methods }
        when {
            chosen.isEmpty() -> "全部方式"
            chosen.size <= 2 -> chosen.joinToString("、", transform = ::methodOptionLabel)
            else -> "已选 ${chosen.size} 项 · ${chosen.take(2).joinToString("、", transform = ::methodOptionLabel)}等"
        }
    }

    Column {
        Header("筛选用餐", back = back)
        Form {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("日期范围")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Choice("本月", {
                        draft = draft.copy(start = currentMonth.start, end = currentMonth.end)
                        error = false
                    }, selected = draft.hasSameDates(currentMonth))
                    Choice("所有时间", {
                        draft = draft.copy(start = "", end = "")
                        error = false
                    }, selected = draft.start.isEmpty() && draft.end.isEmpty())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateControl("开始日期", draft.start, { draft = draft.copy(start = it); error = false },
                        Modifier.weight(1f), allowEmpty = true)
                    DateControl("结束日期", draft.end, { draft = draft.copy(end = it); error = false },
                        Modifier.weight(1f), allowEmpty = true)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("餐次", "多选，不选不限")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealSlot.entries.forEach { slot ->
                        val checked = slot in draft.slots
                        Choice(slot.label, {
                            draft = draft.copy(slots = if (checked) draft.slots - slot else draft.slots + slot)
                        }, selected = checked, icon = if (checked) designIcon(R.drawable.ks_check) else null)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("计划在家吃", "多选，不选不限")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to "是", false to "否").forEach { (value, label) ->
                        val checked = value in draft.atHome
                        Choice(label, {
                            draft = draft.copy(atHome = if (checked) draft.atHome - value else draft.atHome + value)
                        }, selected = checked, icon = if (checked) designIcon(R.drawable.ks_check) else null)
                    }
                }
            }
            SelectionControl("用餐方式", methodSummary, { pickMethods = true })
            val prepCategories = remember(records.categories) {
                records.categories.filter { it.scope == Scope.PREPARATION }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("制作分类", "多选，不选不限")
                MultiCategories(prepCategories, draft.categories) {
                    draft = draft.copy(categories = it)
                }
            }
            Field("菜品／备注关键词", draft.query, { draft = draft.copy(query = it) }, placeholder = "例如：番茄、和朋友")
            Secondary("不同条件同时满足。未选择的条件不限制。")
            if (error) Text("开始日期不能晚于结束日期", color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("重置条件", { draft = defaults; error = false }, Modifier.weight(1f))
                Primary("应用筛选", {
                    error = draft.start.isNotEmpty() && draft.end.isNotEmpty() && draft.start > draft.end
                    if (!error) {
                        apply(draft.copy(
                            slots = MealSlot.entries.filter { it in draft.slots },
                            atHome = listOf(true, false).filter { it in draft.atHome },
                            methods = methodOptions.filter { it in draft.methods },
                            categories = prepCategories.map { it.id }.filter { it in draft.categories },
                            query = draft.query.trim(),
                        ))
                    }
                }, Modifier.weight(1f))
            }
        }
    }
    if (pickMethods) {
        MultiSelectSheet(
            title = "选择用餐方式",
            selected = draft.methods.toSet(),
            options = methodOptions,
            optionKey = { it },
            label = ::methodOptionLabel,
            onApply = { picked -> draft = draft.copy(methods = picked) },
            dismiss = { pickMethods = false },
            subtitle = "全不选表示不限",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiCategories(
    categories: List<Category>,
    selected: List<String>,
    trailing: @Composable () -> Unit = {},
    set: (List<String>) -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.take(8).forEach { category ->
            val checked = category.id in selected
            Choice(category.name + if (category.archived) "（已停用）" else "",
                { set(if (checked) selected - category.id else selected + category.id) }, selected = checked,
                icon = if (checked) designIcon(R.drawable.ks_check) else null)
        }
        if (categories.size > 8) Choice("更多分类", { menu = true }, icon = designIcon(R.drawable.ks_list_filter))
        trailing()
    }
    val extra = categories.drop(8).filter { it.id in selected }
    if (extra.isNotEmpty()) Secondary("已选更多分类 · " + extra.joinToString("、") { it.name })
    if (menu) {
        val selectedSet = remember(selected) { selected.toSet() }
        MultiSelectSheet(
            title = "选择制作分类",
            selected = categories.filter { it.id in selectedSet }.toSet(),
            options = categories,
            optionKey = { it.id },
            label = { it.name + if (it.archived) "（已停用）" else "" },
            onApply = { picked -> set(picked.map { it.id }) },
            dismiss = { menu = false },
            searchable = true,
            searchLabel = "搜索分类",
            empty = "没有匹配分类",
        )
    }
}

private val mealLineBreaks = Regex("[\r\n\u2028\u2029]")
private val singleLineMealText = VisualTransformation { text ->
    // Display stored line breaks as spaces, keeping the original text and cursor offsets.
    TransformedText(AnnotatedString(mealLineBreaks.replace(text.text, " ")), OffsetMapping.Identity)
}

private enum class MealOption(val label: String) {
    METHOD("用餐方式"), PREPARATION("制作分类")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealForm(records: Records, id: String?, initialDate: String, slot: MealSlot, model: AppViewModel, back: () -> Unit) {
    val original = records.meals.find { it.id == id }
    var date by rememberSaveable { mutableStateOf(original?.date ?: initialDate) }
    var selectedSlot by rememberSaveable { mutableStateOf(original?.mealSlot ?: slot) }
    var atHome by rememberSaveable { mutableStateOf(original?.plannedAtHome ?: true) }
    var methodId by rememberSaveable {
        mutableStateOf(if (original != null) original.mealMethodId
            else records.methods.firstOrNull { it.id == DEFAULT_COOKING_METHOD_ID && !it.archived }?.id)
    }
    var selected by rememberSaveable { mutableStateOf<List<String>>(ArrayList(records.preparation[id].orEmpty())) }
    var newName by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf<MealOption?>(null) }
    var dishes by rememberSaveable { mutableStateOf(original?.dishes.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var delete by rememberSaveable { mutableStateOf(false) }
    val method = records.methods.find { it.id == methodId }
    // Archived items already used by the record being edited stay selectable; new records only offer active items.
    val originalMethodId = original?.mealMethodId
    val originalPreparation = original?.let { records.preparation[it.id] }.orEmpty().toSet()
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    val earlier = records.meals.asSequence().filter {
        it.id != original?.id && (it.date < date || (it.date == date && it.mealSlot.ordinal < selectedSlot.ordinal))
    }.maxWithOrNull(compareBy<MealRecord> { it.date }.thenBy { it.mealSlot.ordinal })

    fun openAdd(option: MealOption) {
        model.clearError()
        newName = ""
        adding = option
    }

    fun closeAdd() {
        adding = null
        model.clearError()
    }

    Column {
        Header((if (original == null) "记录" else "编辑") + selectedSlot.label, back = back)
        Form {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateControl("日期", date, { date = it }, Modifier.weight(1f))
                Select("餐次", selectedSlot, MealSlot.entries, { it.label }, { selectedSlot = it }, Modifier.weight(1f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("是否计划在家吃", "必选")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("是", { atHome = true }, selected = atHome)
                    Choice("否", { atHome = false }, selected = !atHome)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("用餐方式", if (atHome) "必选" else "单选 · 可不填")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    records.methods.filter { !it.archived || it.id == originalMethodId || it.id == methodId }.forEach { option ->
                        // Keep the category draft while switching; saving with the final method decides what is stored.
                        Choice(option.name + if (option.archived) "（已停用）" else "",
                            { methodId = option.id.takeUnless { it == methodId } }, selected = methodId == option.id)
                    }
                    SquareIcon(designIcon(R.drawable.ks_plus), "新增用餐方式", enabled = !busy) {
                        openAdd(MealOption.METHOD)
                    }
                }
            }
            if (method?.showPreparationCategories == true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("制作分类", "多选 · 选填")
                    MultiCategories(records.categories.filter {
                        it.scope == Scope.PREPARATION && (!it.archived || it.id in originalPreparation || it.id in selected)
                    }, selected, trailing = {
                            SquareIcon(designIcon(R.drawable.ks_plus), "新增制作分类", enabled = !busy) {
                                openAdd(MealOption.PREPARATION)
                            }
                        },
                    ) { selected = ArrayList(it) }
                }
            } else if (method != null && method.id == original?.mealMethodId && records.preparationNames(original.id).isNotEmpty()) {
                Secondary("历史制作分类 · " + records.preparationNames(original.id).joinToString("、"))
            }
            if (earlier != null) TextButton({
                dishes = earlier.dishes
                note = earlier.note
                if (method?.showPreparationCategories == true) {
                    val valid = records.categories.filter { !it.archived || it.id in originalPreparation }
                        .map { it.id }.toSet()
                    selected = ArrayList(records.preparation[earlier.id].orEmpty().filter { it in valid })
                }
            }) { Text("复制上一餐内容") }
            Field("菜品内容", dishes, { dishes = it }, transformation = singleLineMealText, placeholder = "今天吃了什么？")
            Field("备注", note, { note = it }, transformation = singleLineMealText, placeholder = "还有什么想记下的？")
            SaveActions(busy, error.takeIf { adding == null }, {
                val draft = MealDraft(id, date, selectedSlot, atHome, methodId, selected,
                    emptyList(), dishes, note)
                model.save(back) { saveMeal(draft) }
            }, original?.let { "删除这条用餐记录" }, { delete = true }, "保存${selectedSlot.label}记录")
        }
    }
    adding?.let { option ->
        AlertDialog(
            onDismissRequest = { if (!busy) closeAdd() },
            shape = DialogShape,
            title = { Text("新增${option.label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("名称", newName, { newName = it })
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Primary("确定", {
                    val name = newName
                    model.save(::closeAdd) {
                        when (option) {
                            MealOption.METHOD -> saveMethod(null, name, order = null, archived = false, show = false)
                            MealOption.PREPARATION -> saveCategory(null, Scope.PREPARATION, null, name, order = null, archived = false)
                        }
                    }
                }, enabled = !busy && newName.isNotBlank())
            },
            dismissButton = { TextButton(::closeAdd, enabled = !busy) { Text("取消") } },
        )
    }
    if (delete && original != null) ConfirmDelete(
        "${original.date} · ${original.mealSlot.label}\n" + original.dishes.take(140),
        { delete = false }, { delete = false; model.save(back) { deleteMeal(original.id) } },
        title = "删除这条用餐记录？", note = "删除后，将更新今日进度、月度及全部记录统计。")
}
