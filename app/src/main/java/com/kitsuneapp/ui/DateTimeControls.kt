package com.kitsuneapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsuneapp.R
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import kotlin.math.sqrt

@Composable
fun DateControl(label: String, value: String, set: (String) -> Unit, modifier: Modifier = Modifier,
                allowEmpty: Boolean = false, showLabel: Boolean = true) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel) FieldLabel(label)
        OutlinedAction({ open = true }, Modifier.fillMaxWidth().semantics { contentDescription = label }, spread = true) {
            Text(value.ifEmpty { if (allowEmpty) "不限" else "请选择" }, Modifier.weight(1f))
            Icon(designIcon(R.drawable.ks_calendar_days), null, Modifier.size(16.dp))
        }
        if (allowEmpty && value.isNotEmpty()) TextAction("清空", { set("") })
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
        var editingText by rememberSaveable { mutableStateOf(true) }
        var hourText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(value.take(2)))
        }
        var minuteText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(value.takeLast(2)))
        }
        val hour = timePart(hourText.text, 23)
        val minute = timePart(minuteText.text, 59)
        val valid = !editingText || (hour != null && minute != null)
        val confirm: () -> Unit = {
            if (valid) {
                val time = if (editingText) LocalTime.of(checkNotNull(hour), checkNotNull(minute))
                    else LocalTime.of(state.hour, state.minute)
                set(time.hhmm())
                open = false
            }
        }
        PickerCard("选择时间", { open = false }, confirm, valid,
            maxWidth = if (editingText) 280.dp else 320.dp, sideMargin = 12.dp, leadingAction = {
                val keyboard = LocalSoftwareKeyboardController.current
                IconButton({
                    if (editingText) {
                        keyboard?.hide()
                        hour?.let { state.hour = it }
                        minute?.let { state.minute = it }
                    } else {
                        val time = LocalTime.of(state.hour, state.minute).hhmm()
                        hourText = TextFieldValue(time.take(2))
                        minuteText = TextFieldValue(time.takeLast(2))
                    }
                    editingText = !editingText
                }) {
                    val icon = if (editingText) R.drawable.ks_clock else R.drawable.ks_keyboard
                    Icon(designIcon(icon), if (editingText) "使用时钟" else "输入时间")
                }
            }) {
            if (editingText) {
                val hourFocus = remember { FocusRequester() }
                val minuteFocus = remember { FocusRequester() }
                val window = LocalWindowInfo.current
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    snapshotFlow { window.isWindowFocused }.first { it }
                    hourFocus.requestFocus()
                    keyboard?.show()
                }
                Column(Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        TimeInputField("小时", hourText, 23, hourFocus, ImeAction.Next,
                            { minuteFocus.requestFocus() }, Modifier.weight(1f)) { next ->
                            val typedHour = next.text != hourText.text && timePart(next.text, 23) != null
                            hourText = next
                            if (typedHour) minuteFocus.requestFocus()
                        }
                        Box(Modifier.height(timeFieldHeight()).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            Text(":", style = MaterialTheme.typography.displayLarge.copy(fontSize = timeDigitSize()))
                        }
                        TimeInputField("分钟", minuteText, 59, minuteFocus, ImeAction.Done, confirm,
                            Modifier.weight(1f)) { minuteText = it }
                    }
                }
            } else {
                TimePicker(state, Modifier.fillMaxWidth(), layoutType = TimePickerLayoutType.Vertical)
            }
        }
    }
}

@Composable
private fun TimeInputField(label: String, value: TextFieldValue, max: Int, focus: FocusRequester,
                           action: ImeAction, onAction: () -> Unit, modifier: Modifier,
                           set: (TextFieldValue) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val invalid = value.text.length == 2 && timePart(value.text, max) == null
    val fieldColor = when {
        invalid -> colors.error
        focused -> colors.primary
        else -> colors.onSurface
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicTextField(
            value = value,
            onValueChange = { next -> if (next.text.length <= 2 && next.text.all(Char::isDigit)) set(next) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged {
                if (it.isFocused && !focused) set(value.copy(selection = TextRange(0, value.text.length)))
                focused = it.isFocused
            }.semantics {
                contentDescription = label
                if (invalid) error("请输入 00 至 $max")
            },
            textStyle = MaterialTheme.typography.displayLarge.copy(
                color = fieldColor, fontSize = timeDigitSize(), lineHeight = timeDigitSize(), textAlign = TextAlign.Center,
            ),
            singleLine = true,
            cursorBrush = SolidColor(fieldColor),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = action),
            keyboardActions = KeyboardActions(onNext = { onAction() }, onDone = { onAction() }),
            decorationBox = { input ->
                val background = if (focused) colors.primaryContainer else colors.surfaceContainerHighest
                val border = if (focused || invalid) Modifier.border(2.dp, fieldColor, ControlShape) else Modifier
                Box(Modifier.fillMaxWidth().height(timeFieldHeight()).background(background, ControlShape).then(border),
                    contentAlignment = Alignment.Center) { input() }
            },
        )
        Text(label, color = if (invalid) colors.error else colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun timeDigitSize() = (54f / sqrt(LocalDensity.current.fontScale.coerceAtLeast(1f))).sp

@Composable
private fun timeFieldHeight() = 80.dp * sqrt(LocalDensity.current.fontScale.coerceAtLeast(1f))

private fun timePart(text: String, max: Int) = text.toIntOrNull()?.takeIf { text.length == 2 && it in 0..max }

@Composable
private fun PickerCard(title: String, dismiss: () -> Unit, confirm: () -> Unit, enabled: Boolean,
                       maxWidth: Dp = 360.dp, sideMargin: Dp = 0.dp,
                       leadingAction: (@Composable () -> Unit)? = null,
                       content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(horizontal = sideMargin).widthIn(max = maxWidth).fillMaxWidth(), shape = DialogShape, color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant)) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val titleStyle = if (leadingAction == null) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.bodyMedium
                Text(title, Modifier.padding(horizontal = 20.dp), style = titleStyle)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    leadingAction?.invoke()
                    Spacer(Modifier.weight(1f))
                    TextButton(dismiss) { Text("取消") }
                    TextButton(confirm, enabled = enabled) { Text("确定") }
                }
            }
        }
    }
}

private fun readDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()?.takeIf { it.year in 1..9999 && it.toString() == value }

private fun LocalTime.hhmm() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
