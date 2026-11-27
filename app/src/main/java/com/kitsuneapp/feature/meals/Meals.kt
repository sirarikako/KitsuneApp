package com.kitsuneapp.feature.meals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsuneapp.AppViewModel
import com.kitsuneapp.R
import com.kitsuneapp.data.*
import com.kitsuneapp.ui.*
import java.io.Serializable
import java.time.LocalDate
import java.time.YearMonth

data class MealFilterChip(
    val key: String,
    val label: String,
    val cleared: MealFilter,
)

data class MealFilter(
    val start: String = "",
    val end: String = "",
    val slots: List<MealSlot> = emptyList(),
    val atHome: List<Boolean> = emptyList(),
    val methods: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val query: String = "",
) : Serializable {
    val isEmpty: Boolean
        get() = start.isEmpty() && end.isEmpty() && slots.isEmpty() && atHome.isEmpty() &&
            methods.isEmpty() && categories.isEmpty() && query.isBlank()

    fun matches(meal: MealRecord, records: Records): Boolean =
        (start.isEmpty() || meal.date >= start) && (end.isEmpty() || meal.date <= end) &&
            (slots.isEmpty() || meal.mealSlot in slots) && (atHome.isEmpty() || meal.plannedAtHome in atHome) &&
            (methods.isEmpty() || (meal.mealMethodId ?: NO_METHOD) in methods) &&
            (categories.isEmpty() || records.preparation[meal.id].orEmpty().any { it in categories }) &&
            (meal.dishes.contains(query.trim(), true) || meal.note.contains(query.trim(), true))

    fun chips(records: Records, includeDates: Boolean = true): List<MealFilterChip> = buildList {
        if (includeDates && (start.isNotEmpty() || end.isNotEmpty())) {
            add(MealFilterChip("dates", "${start.ifEmpty { "最早" }} ～ ${end.ifEmpty { "至今" }}", copy(start = "", end = "")))
        }
        MealSlot.entries.filter { it in slots }.forEach { slot ->
            add(MealFilterChip("slot:${slot.name}", slot.label, copy(slots = slots - slot)))
        }
        listOf(true, false).filter { it in atHome }.forEach { value ->
            add(MealFilterChip("atHome:$value", "计划在家：${if (value) "是" else "否"}", copy(atHome = atHome - value)))
        }
        methods.forEach { method ->
            val label = if (method == NO_METHOD) "方式未填写" else records.methodName(method)
            add(MealFilterChip("method:$method", label, copy(methods = methods - method)))
        }
        categories.forEach { id ->
            records.categoryNames[id]?.let { name ->
                add(MealFilterChip("category:$id", name, copy(categories = categories - id)))
            }
        }
        if (query.isNotBlank()) {
            add(MealFilterChip("query", "关键词：${query.trim()}", copy(query = "")))
        }
    }

    fun labels(records: Records, includeDates: Boolean = true): List<String> =
        chips(records, includeDates).map { it.label }

    fun hasSameDates(other: MealFilter) = start == other.start && end == other.end

    /**
     * Drops conditions that reference meal methods or preparation categories no longer in [records].
     * Such stale IDs cannot match any record, cannot be shown as a label, and cannot be unchecked in the filter sheet.
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

private enum class MealFilterTarget {
    RECORDS, STATS
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
    date: String,
    onDateChange: (String) -> Unit,
    filter: MealFilter?,
    statsFilter: MealFilter,
    currentMonth: MealFilter,
    active: Boolean = true,
    updateFilter: (MealFilter?) -> Unit,
    updateStatsFilter: (MealFilter) -> Unit,
    selectStatsMonth: (YearMonth) -> Unit,
    openMeal: (String, MealSlot, String?) -> Unit,
    openExisting: (MealRecord) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var statsMonthPicker by rememberSaveable { mutableStateOf(false) }
    var filterTarget by rememberSaveable { mutableStateOf<MealFilterTarget?>(null) }
    val activeFilter = filter?.takeUnless { it.isEmpty }
    val chips = activeFilter?.chips(records).orEmpty()
    val statsChips = statsFilter.chips(records, includeDates = false)

    BackHandler(active && tab == 0 && activeFilter != null && filterTarget == null && !statsMonthPicker) {
        updateFilter(null)
    }

    Column {
        Header("用餐")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tabs(listOf("记录", "统计"), tab) { tab = it }
            Spacer(Modifier.height(4.dp))
            if (tab == 0) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (activeFilter == null) {
                        DateControl("用餐日期", date, onDateChange, Modifier.weight(1f), showLabel = false)
                    } else {
                        OutlinedAction(
                            { filterTarget = MealFilterTarget.RECORDS },
                            Modifier.weight(1f).semantics { contentDescription = "选择筛选范围" },
                            spread = true,
                        ) {
                            Text(
                                "范围：${activeFilter.dateSummary(currentMonth)}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                designIcon(R.drawable.ks_chevron_down),
                                null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Choice(
                        if (chips.isEmpty()) "筛选" else "筛选 (${chips.size})",
                        { filterTarget = MealFilterTarget.RECORDS },
                        selected = chips.isNotEmpty(),
                        icon = designIcon(R.drawable.ks_list_filter),
                    )
                }
                if (activeFilter == null) WeekStrip(LocalDate.parse(date)) { onDateChange(it.toString()) }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedAction(
                        { statsMonthPicker = true },
                        Modifier.weight(1f).semantics { contentDescription = "选择统计月份" },
                        spread = true,
                    ) {
                        Text(
                            statsFilter.dateSummary(currentMonth),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            designIcon(R.drawable.ks_chevron_down),
                            null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Choice(
                        if (statsChips.isEmpty()) "筛选" else "筛选 (${statsChips.size})",
                        { filterTarget = MealFilterTarget.STATS },
                        selected = statsChips.isNotEmpty(),
                        icon = designIcon(R.drawable.ks_list_filter),
                    )
                }
            }
        }
        val content = when {
            tab == 1 -> MealsContent(statsFilter = statsFilter)
            activeFilter != null -> MealsContent(filter = activeFilter)
            else -> MealsContent(date = date)
        }
        ContentFade(content, Modifier.weight(1f)) { shown ->
            if (shown.statsFilter != null) {
                MealStats(records, shown.statsFilter) { next -> updateStatsFilter(next) }
            } else if (shown.date != null) {
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
                val resultChips = applied.chips(records)
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        if (resultChips.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                resultChips.forEach { chip ->
                                    FilterChipTag(chip.label) { updateFilter(chip.cleared) }
                                }
                            }
                        }
                        Section("筛选结果") {
                            Secondary("${matches.size} 条")
                            Spacer(Modifier.width(8.dp))
                            TextAction("清除筛选", { updateFilter(null) })
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
    filterTarget?.let { target ->
        val statistics = target == MealFilterTarget.STATS
        val defaults = if (statistics) currentMonth else MealFilter()
        val initial = if (statistics) statsFilter else activeFilter ?: MealFilter()
        MealFilterDialog(
            records = records,
            initial = initial,
            defaults = defaults,
            currentMonth = currentMonth,
            dismiss = { filterTarget = null },
        ) { applied ->
            if (statistics) updateStatsFilter(applied)
            else updateFilter(applied)
            filterTarget = null
        }
    }
}

@Composable
private fun FilterChipTag(label: String, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onRemove,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .semantics { contentDescription = "移除筛选条件：$label" },
        shape = RoundedCornerShape(8.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        border = BorderStroke(1.dp, colors.primary),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Icon(designIcon(R.drawable.ks_x), contentDescription = null, modifier = Modifier.size(14.dp))
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
    val detailParts = (if (daily) emptyList() else listOf(plan)) + prep +
        listOfNotNull(meal.note.takeIf(String::isNotBlank)?.let { "备注：${mealLineBreaks.replace(it, " ").trim()}" })
    val detail = detailParts.filter(String::isNotBlank).joinToString(" · ")
    val dishes = meal.dishes.ifBlank { "未填写菜品" }
    val title = if (daily) meal.mealSlot.label else dishes
    val subtitle = if (daily) dishes else "${meal.date} · ${meal.mealSlot.label}"
    RecordRow(title, subtitle, detail, designIcon(meal.mealSlot.glyph()), onClick,
        badge = method, sharedId = meal.id, badgeInSubtitle = !daily)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealStats(records: Records, filter: MealFilter, updateFilter: (MealFilter) -> Unit) {
    val matches = remember(records, filter) { records.meals.filter { filter.matches(it, records) } }
    val total = matches.size
    val home = matches.count { it.plannedAtHome }
    val methodGroups = matches.groupingBy { it.mealMethodId }.eachCount()
    val prep = matches.flatMap { records.preparation[it.id].orEmpty() }.groupingBy { it }.eachCount()
    val chips = filter.chips(records, includeDates = false)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (chips.isNotEmpty()) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { chip -> FilterChipTag(chip.label) { updateFilter(chip.cleared) } }
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
internal fun MealFilterDialog(
    records: Records,
    initial: MealFilter,
    defaults: MealFilter,
    currentMonth: MealFilter,
    dismiss: () -> Unit,
    apply: (MealFilter) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf(false) }
    val methodOptions = remember(records.methods) { records.methods.map { it.id } + MealFilter.NO_METHOD }
    val preparationCategories = remember(records.categories) {
        records.categories.filter { it.scope == Scope.PREPARATION }
    }

    LaunchedEffect(records) {
        val pruned = draft.validIn(records)
        if (pruned != draft) draft = pruned
    }

    SelectionSheet(
        title = "筛选用餐",
        dismiss = dismiss,
        footer = {
            if (error) {
                Text(
                    "开始日期不能晚于结束日期",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("重置条件", { draft = defaults; error = false }, Modifier.weight(1f))
                Primary(
                    "应用筛选",
                    {
                        val reversed = draft.start.isNotEmpty() && draft.end.isNotEmpty() && draft.start > draft.end
                        error = reversed
                        if (!reversed) {
                            apply(
                                draft.copy(
                                    slots = MealSlot.entries.filter { it in draft.slots },
                                    atHome = listOf(true, false).filter { it in draft.atHome },
                                    methods = methodOptions.filter { it in draft.methods },
                                    categories = preparationCategories.map { it.id }.filter { it in draft.categories },
                                    query = draft.query.trim(),
                                ),
                            )
                        }
                    },
                    Modifier.weight(1f),
                )
            }
        },
    ) {
        item(key = "query") {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldLabel("菜品／备注关键词")
                    if (draft.query.isNotEmpty()) {
                        TextAction(
                            "清空",
                            { draft = draft.copy(query = "") },
                            modifier = Modifier.semantics { contentDescription = "清空菜品／备注关键词" },
                        )
                    }
                }
                Field(
                    "菜品／备注关键词",
                    draft.query,
                    { draft = draft.copy(query = it) },
                    placeholder = "例如：番茄、和朋友",
                    showLabel = false,
                )
            }
        }
        item(key = "dates") {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(2.dp))
                FieldLabel("日期范围")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterSheetChip("本月", draft.hasSameDates(currentMonth)) {
                        draft = draft.copy(start = currentMonth.start, end = currentMonth.end)
                        error = false
                    }
                    FilterSheetChip("所有时间", draft.start.isEmpty() && draft.end.isEmpty()) {
                        draft = draft.copy(start = "", end = "")
                        error = false
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateControl(
                        "开始日期",
                        draft.start,
                        { draft = draft.copy(start = it); error = false },
                        Modifier.weight(1f),
                        allowEmpty = true,
                    )
                    DateControl(
                        "结束日期",
                        draft.end,
                        { draft = draft.copy(end = it); error = false },
                        Modifier.weight(1f),
                        allowEmpty = true,
                    )
                }
            }
        }
        item(key = "slot") {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(2.dp))
                FieldLabel("餐次", "多选 · 不选不限")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealSlot.entries.forEach { slot ->
                        val checked = slot in draft.slots
                        FilterSheetChip(slot.label, checked) {
                            draft = draft.copy(slots = if (checked) draft.slots - slot else draft.slots + slot)
                        }
                    }
                }
            }
        }
        item(key = "atHome") {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(2.dp))
                FieldLabel("是否计划在家吃", "多选 · 不选不限")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to "是", false to "否").forEach { (value, label) ->
                        val checked = value in draft.atHome
                        FilterSheetChip(label, checked) {
                            draft = draft.copy(atHome = if (checked) draft.atHome - value else draft.atHome + value)
                        }
                    }
                }
            }
        }
        item(key = "method") {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(2.dp))
                FieldLabel("用餐方式", "多选 · 不选不限")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    records.methods.forEach { method ->
                        val checked = method.id in draft.methods
                        val label = method.name + if (method.archived) "（已停用）" else ""
                        FilterSheetChip(label, checked) {
                            draft = draft.copy(methods = if (checked) draft.methods - method.id else draft.methods + method.id)
                        }
                    }
                    val noMethodSelected = MealFilter.NO_METHOD in draft.methods
                    FilterSheetChip("未填写方式", noMethodSelected) {
                        draft = draft.copy(
                            methods = if (noMethodSelected) draft.methods - MealFilter.NO_METHOD else draft.methods + MealFilter.NO_METHOD
                        )
                    }
                }
            }
        }
        item(key = "categories") {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(2.dp))
                FieldLabel("制作分类", "多选 · 不选不限")
                if (preparationCategories.isEmpty()) {
                    Secondary("暂无制作分类")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        preparationCategories.forEach { category ->
                            val checked = category.id in draft.categories
                            val label = category.name + if (category.archived) "（已停用）" else ""
                            FilterSheetChip(label, checked) {
                                draft = draft.copy(
                                    categories = if (checked) draft.categories - category.id else draft.categories + category.id
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "note") {
            Secondary("不同条件同时满足。未选择的条件不限制。", Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun FilterSheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.primaryContainer else colors.surface,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = 36.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) Icon(designIcon(R.drawable.ks_check), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
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
fun MealForm(
    records: Records,
    id: String?,
    initialDate: String,
    slot: MealSlot,
    model: AppViewModel,
    back: () -> Unit,
    viewDay: (() -> Unit)? = null,
) {
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
        Header(
            (if (original == null) "记录" else "编辑") + selectedSlot.label,
            back = back,
            action = {
                if (original != null && viewDay != null) {
                    OutlinedAction(
                        onClick = viewDay,
                        modifier = Modifier.semantics { contentDescription = "查看当天" },
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(designIcon(R.drawable.ks_calendar_days), null, Modifier.size(16.dp))
                        Text("查看当天", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            },
        )
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
