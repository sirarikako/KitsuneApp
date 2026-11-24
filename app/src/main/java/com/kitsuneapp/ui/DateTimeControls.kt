package com.kitsuneapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsuneapp.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

@Composable
fun DateControl(label: String, value: String, set: (String) -> Unit, modifier: Modifier = Modifier,
                allowEmpty: Boolean = false, showLabel: Boolean = true) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (allowEmpty) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (showLabel) FieldLabel(label)
                }
                if (value.isNotEmpty()) {
                    TextAction("清空", { set("") }, modifier = Modifier.semantics { contentDescription = "清空$label" })
                }
            }
        } else if (showLabel) {
            FieldLabel(label)
        }
        OutlinedAction({ open = true }, Modifier.fillMaxWidth().semantics { contentDescription = label }, spread = true) {
            Text(value.ifEmpty { if (allowEmpty) "不限" else "请选择" }, Modifier.weight(1f))
            Icon(designIcon(R.drawable.ks_calendar_days), null, Modifier.size(16.dp))
        }
    }
    if (open) {
        DateSelectionDialog(label, readDate(value) ?: LocalDate.now(), dismiss = { open = false }) {
            set(it.toString())
            open = false
        }
    }
}

@Composable
private fun DateSelectionDialog(label: String, initial: LocalDate, dismiss: () -> Unit, select: (LocalDate) -> Unit) {
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val canShowCalendar = with(LocalDensity.current) { windowWidth.toDp() >= 360.dp }
    val calendar = rememberPagerState(initialPage = YearMonth.from(initial).calendarPage) { CalendarMonthCount }
    var calendarDate by rememberSaveable { mutableStateOf(initial.toString()) }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    var editingText by rememberSaveable { mutableStateOf(!canShowCalendar) }
    var draft by rememberSaveable { mutableStateOf(initial.toString()) }
    val useText = editingText || !canShowCalendar
    val selected = if (useText) readDate(draft) else LocalDate.parse(calendarDate)
    LaunchedEffect(canShowCalendar) {
        if (!canShowCalendar && !editingText) {
            draft = calendarDate
            editingText = true
        }
    }
    PickerCard("选择$label", dismiss, { selected?.let(select) }, selected != null && (useText || !choosingMonth)) {
        if (canShowCalendar) TextButton({
            if (useText) {
                selected?.let {
                    calendarDate = it.toString()
                    calendar.requestScrollToPage(YearMonth.from(it).calendarPage)
                }
            } else {
                draft = calendarDate
                choosingMonth = false
            }
            editingText = !useText
        }, Modifier.align(Alignment.End).padding(horizontal = 12.dp)) { Text(if (useText) "使用日历" else "输入日期") }
        if (useText) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Field("日期（YYYY-MM-DD）", draft, { draft = it }, placeholder = "例如 2026-11-17",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                if (draft.isNotBlank() && selected == null) Secondary("请输入有效日期")
            }
        } else {
            DateCalendar(calendar, LocalDate.parse(calendarDate), choosingMonth, { choosingMonth = it }) {
                calendarDate = it.toString()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeControl(value: String, set: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Choice(value, { open = true }, modifier.semantics { contentDescription = "时间" })
    if (open) {
        val initial = LocalTime.parse(value)
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        var editingText by rememberSaveable { mutableStateOf(false) }
        var draft by rememberSaveable { mutableStateOf(value) }
        val selected = if (editingText) readTime(draft) else LocalTime.of(state.hour, state.minute)
        PickerCard("选择时间", { open = false }, {
            selected?.let { set(it.hhmm()); open = false }
        }, selected != null) {
            TextButton({
                if (editingText) {
                    selected?.let { state.hour = it.hour; state.minute = it.minute }
                } else {
                    draft = LocalTime.of(state.hour, state.minute).hhmm()
                }
                editingText = !editingText
            }, Modifier.align(Alignment.End).padding(horizontal = 12.dp)) { Text(if (editingText) "使用时钟" else "输入时间") }
            if (editingText) {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Field("时间（HH:mm）", draft, { draft = it }, placeholder = "例如 09:30",
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                    if (draft.isNotBlank() && selected == null) Secondary("请输入有效的 24 小时时间")
                }
            } else {
                TimePicker(state, Modifier.align(Alignment.CenterHorizontally), layoutType = TimePickerLayoutType.Vertical)
            }
        }
    }
}

@Composable
private fun PickerCard(title: String, dismiss: () -> Unit, confirm: () -> Unit, enabled: Boolean,
                       content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 360.dp).fillMaxWidth(), shape = DialogShape, color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant)) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
                Row(Modifier.align(Alignment.End).padding(horizontal = 12.dp)) {
                    TextButton(dismiss) { Text("取消") }
                    TextButton(confirm, enabled = enabled) { Text("确定") }
                }
            }
        }
    }
}

private fun readDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()?.takeIf { it.year in 1..9999 && it.toString() == value }

private fun readTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value) }.getOrNull()?.takeIf { it.hhmm() == value }

private fun LocalTime.hhmm() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
