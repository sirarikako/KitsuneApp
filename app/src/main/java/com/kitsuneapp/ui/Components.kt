package com.kitsuneapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsuneapp.R
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@Composable
internal fun designIcon(@DrawableRes icon: Int) = ImageVector.vectorResource(icon)

internal fun Modifier.inert(key: Any?) = clearAndSetSemantics { hideFromAccessibility() }
    .pointerInput(key) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

@Composable
internal fun <T> ContentFade(state: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(state, modifier, transitionSpec = {
        fadeIn(tween(150, easing = motion)) togetherWith fadeOut(tween(150, easing = motion))
    }, label = "局部内容") { shown ->
        val active = shown == state
        val recordMotion = LocalRecordMotion.current.takeIf { active }
        CompositionLocalProvider(LocalRecordMotion provides recordMotion) {
            Box(if (active) Modifier else Modifier.inert(shown)) { content(shown) }
        }
    }
}

@Composable
fun Header(title: String, subtitle: String? = null, back: (() -> Unit)? = null,
           leading: @Composable RowScope.() -> Unit = {}, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = if (back == null) 16.dp else 12.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (back != null) SquareIcon(designIcon(R.drawable.ks_arrow_left), "返回", onClick = back)
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) Secondary(subtitle, Modifier.padding(top = 3.dp))
        }
        action()
    }
}

@Composable
fun Section(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        trailing()
    }
}

@Composable
fun Secondary(text: String, modifier: Modifier = Modifier, lines: Int = Int.MAX_VALUE) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = lines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun EmptyState(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
}

@Composable
fun Paper(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(padding.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun Form(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordRow(
    title: String,
    subtitle: String,
    detail: String = "",
    icon: ImageVector,
    onClick: (() -> Unit)?,
    amount: String? = null,
    currency: String? = null,
    amountColor: Color = Color.Unspecified,
    empty: Boolean = false,
    badge: String? = null,
    sharedId: String? = null,
    badgeInSubtitle: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interactive = if (onClick == null) Modifier else Modifier.clip(CardShape).clickable(onClick = onClick)
    Surface(modifier = recordBounds(Modifier, sharedId).fillMaxWidth().heightIn(min = 70.dp).then(interactive),
        shape = CardShape, color = if (empty) Color.Transparent else scheme.surface,
        border = if (empty) null else BorderStroke(1.dp, scheme.outlineVariant)) {
        val outline = if (empty) Modifier.drawWithCache {
            val line = 1.dp.toPx()
            val stroke = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            onDrawBehind {
                drawRoundRect(scheme.outlineVariant, topLeft = Offset(line / 2, line / 2),
                    size = Size(size.width - line, size.height - line),
                    cornerRadius = CornerRadius(CardRadius.toPx() - line / 2), style = stroke)
            }
        } else Modifier
        BoxWithConstraints(outline.padding(12.dp), propagateMinConstraints = true) {
            val amountWidth = maxWidth * 0.46f
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(ControlShape)
                    .background(if (empty) scheme.primaryContainer else scheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = if (empty) scheme.primary else scheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (badge != null && !badgeInSubtitle) Tag(badge)
                    }
                    if (subtitle.isNotEmpty() || (badge != null && badgeInSubtitle)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (subtitle.isNotEmpty()) Secondary(subtitle, lines = 2)
                            if (badge != null && badgeInSubtitle) Tag(badge)
                        }
                    }
                    if (detail.isNotEmpty()) Secondary(detail, lines = 1)
                }
                if (amount != null) {
                    Column(Modifier.widthIn(max = amountWidth), horizontalAlignment = Alignment.End) {
                        Text(amount, color = amountColor, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                        if (currency != null) Text(currency, color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                    }
                }
                if (onClick != null) {
                    Icon(designIcon(if (empty) R.drawable.ks_plus else R.drawable.ks_chevron_right), null,
                        Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun Tag(text: String) {
    Surface(shape = RoundedTag, color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

private val RoundedTag = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)

@Composable
fun Primary(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
            icon: ImageVector? = null, danger: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val fill = if (danger) colors.error else colors.primary
    val ink = if (danger) colors.onError else colors.onPrimary
    Row(modifier.heightIn(min = 46.dp).clip(ControlShape).background(if (enabled) fill else fill.copy(alpha = 0.45f))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = ink)
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink, textAlign = TextAlign.Center)
    }
}

@Composable
fun Choice(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean? = null,
           enabled: Boolean = true, icon: ImageVector? = null) {
    val scheme = MaterialTheme.colorScheme
    OutlinedAction(onClick, modifier, enabled = enabled, selected = selected) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = if (!enabled) scheme.onSurfaceVariant.copy(alpha = 0.6f) else LocalContentColor.current)
    }
}

@Composable
internal fun OutlinedAction(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean? = null,
    contentColor: Color = Color.Unspecified, spread: Boolean = false, content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val selectedStyle = if (selected == null) Modifier else Modifier.semantics { this.selected = selected }
    val ink = contentColor.takeOrElse { if (selected == true) colors.onPrimaryContainer else colors.onSurface }
    Row(modifier.heightIn(min = 46.dp).clip(ControlShape)
        .background(if (selected == true) colors.primaryContainer else colors.surface)
        .border(1.dp, if (selected == true) colors.primary else colors.outlineVariant, ControlShape)
        .then(selectedStyle).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, if (spread) Alignment.Start else Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides ink) { content() }
    }
}

@Composable
fun SquareIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(44.dp).clip(ControlShape).background(colors.surface)
        .border(1.dp, colors.outlineVariant, ControlShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(18.dp),
            tint = if (enabled) LocalContentColor.current else colors.onSurfaceVariant.copy(alpha = 0.38f))
    }
}

@Composable
internal fun FieldLabel(label: String, hint: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun Field(label: String, value: String, set: (String) -> Unit, modifier: Modifier = Modifier, multiline: Boolean = false,
          keyboard: KeyboardOptions = KeyboardOptions.Default, transformation: androidx.compose.ui.text.input.VisualTransformation =
              androidx.compose.ui.text.input.VisualTransformation.None,
          textStyle: TextStyle = MaterialTheme.typography.bodyLarge, placeholder: String? = null,
          minLines: Int = if (multiline) 2 else 1, showLabel: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel) FieldLabel(label)
        BasicTextField(
            value, set, Modifier.fillMaxWidth().semantics { contentDescription = label }, singleLine = !multiline,
            minLines = minLines, maxLines = if (multiline) 6 else 1, interactionSource = interaction,
            keyboardOptions = keyboard, visualTransformation = transformation,
            textStyle = textStyle.copy(color = colors.onSurface), cursorBrush = SolidColor(colors.primary),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(ControlShape).background(colors.surface)
                    .border(1.dp, if (focused) colors.primary else colors.outlineVariant, ControlShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, Modifier.fillMaxWidth(),
                        style = textStyle, color = colors.onSurfaceVariant)
                    inner()
                }
            })
    }
}

@Composable
fun <T> Select(
    title: String, value: T?, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, placeholder: String = "请选择", enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(title)
        Box {
            OutlinedAction({ open = true }, Modifier.fillMaxWidth().semantics { contentDescription = title },
                enabled = enabled, spread = true) {
                Text(value?.let(label) ?: placeholder, Modifier.weight(1f), color =
                    if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(designIcon(R.drawable.ks_chevron_down), null, Modifier.size(14.dp))
            }
            DropdownMenu(open, { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(label(option)) }, onClick = { open = false; onSelect(option) })
                }
            }
        }
    }
}

@Composable
fun Tabs(options: List<String>, selected: Int, set: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(23.dp)) {
        options.forEachIndexed { index, option ->
            val active = index == selected
            Column(Modifier.width(IntrinsicSize.Max).heightIn(min = 44.dp)
                .selectable(active, role = Role.Tab, onClick = { set(index) }),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(option, Modifier.padding(vertical = 10.dp),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.fillMaxWidth().height(2.dp)
                    .background(if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent))
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun platformPickerTheme() = if (LocalDarkMode.current) android.R.style.Theme_Material_Dialog_Alert
else android.R.style.Theme_Material_Light_Dialog_Alert

@Composable
fun DateControl(label: String, value: String, set: (String) -> Unit, modifier: Modifier = Modifier,
                allowEmpty: Boolean = false, showLabel: Boolean = true) {
    val context = LocalContext.current
    val pickerTheme = platformPickerTheme()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel) FieldLabel(label)
        OutlinedAction({
                val date = runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
                android.app.DatePickerDialog(context, pickerTheme,
                    { _, y, m, d -> set(LocalDate.of(y, m + 1, d).toString()) },
                    date.year, date.monthValue - 1, date.dayOfMonth).show()
            }, Modifier.fillMaxWidth().semantics { contentDescription = label }, spread = true) {
            Text(value.ifEmpty { if (allowEmpty) "不限" else "请选择" }, Modifier.weight(1f))
            Icon(designIcon(R.drawable.ks_calendar_days), null, Modifier.size(16.dp))
        }
        if (allowEmpty && value.isNotEmpty()) TextAction("清空", { set("") })
    }
}

@Composable
fun MonthControl(value: String, set: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.parse(value)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SquareIcon(designIcon(R.drawable.ks_chevron_left), "上个月") { set(month.minusMonths(1).toString()) }
        Choice("${month.year} 年 ${month.monthValue} 月", { open = true }, Modifier.weight(1f))
        SquareIcon(designIcon(R.drawable.ks_chevron_right), "下个月") { set(month.plusMonths(1).toString()) }
    }
    if (open) {
        var year by rememberSaveable { mutableStateOf(month.year.toString()) }
        var selected by rememberSaveable { mutableIntStateOf(month.monthValue) }
        val parsed = year.toIntOrNull()?.takeIf { it in 1..9999 }
        AlertDialog(onDismissRequest = { open = false }, shape = DialogShape, title = { Text("选择月份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("年份", year, { year = it }, keyboard = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    Select("月份", selected, (1..12).toList(), { "$it 月" }, { selected = it })
                }
            },
            confirmButton = { Primary("应用", { if (parsed != null) { set(YearMonth.of(parsed, selected).toString()); open = false } }, enabled = parsed != null) },
            dismissButton = { TextButton({ open = false }) { Text("取消") } })
    }
}

@Composable
fun SaveActions(busy: Boolean, error: String?, onSave: () -> Unit, deleteLabel: String? = null,
                onDelete: () -> Unit = {}, saveLabel: String = "保存") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Primary(if (busy) "正在保存…" else saveLabel, onSave, Modifier.fillMaxWidth(), enabled = !busy)
        if (deleteLabel != null) DeleteAction(deleteLabel, onDelete, enabled = !busy)
    }
}

@Composable
internal fun DeleteAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedAction(onClick, Modifier.fillMaxWidth(), enabled = enabled, contentColor = MaterialTheme.colorScheme.error) {
        Icon(designIcon(R.drawable.ks_trash2), null, Modifier.size(18.dp))
        Text(label)
    }
}

@Composable
internal fun TextAction(label: String, onClick: () -> Unit, trailing: ImageVector? = null,
                        leading: ImageVector? = null, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val ink = if (enabled) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f)
    Row(Modifier.heightIn(min = 44.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 2.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (leading != null) Icon(leading, null, Modifier.size(14.dp), tint = ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = ink)
        if (trailing != null) Icon(trailing, null, Modifier.size(14.dp), tint = ink)
    }
}

@Composable
fun ConfirmDelete(description: String, dismiss: () -> Unit, confirm: () -> Unit,
                  title: String = "确认删除", note: String? = null) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.widthIn(max = 540.dp).fillMaxWidth().padding(18.dp), shape = DialogShape, color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant), shadowElevation = 8.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(description)
                if (note != null) Secondary(note)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("取消", dismiss, Modifier.weight(1f))
                    Primary("确认删除", confirm, Modifier.weight(1f), danger = true)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesignSheet(
    dismiss: () -> Unit,
    scroll: ScrollState = rememberScrollState(),
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Color.Transparent, scrimColor = Color.Black.copy(alpha = .33f),
        dragHandle = null, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Surface(Modifier.fillMaxWidth().padding(9.dp), shape = DialogShape,
            color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val body = if (footer == null) Modifier else Modifier.weight(1f, fill = false)
                Column(body.verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                footer?.invoke(this)
            }
        }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, set)
    }
}

@Composable
fun Summary(first: Pair<String, String>, second: Pair<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(first, second).forEach { (label, value) ->
            Paper(Modifier.weight(1f), padding = 12) {
                Secondary(label)
                Text(value, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
fun CountBar(label: String, count: Int, total: Int, unit: String = "餐") {
    val ratio = if (total > 0) count.toFloat() / total else 0f
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$count $unit", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Secondary(String.format(Locale.ROOT, "%.0f%%", ratio * 100), Modifier.widthIn(min = 36.dp))
        }
    }
    if (ratio > 0) StatBar(ratio)
}

@Composable
internal fun StatBar(ratio: Float, segmented: Boolean = false, color: Color = MaterialTheme.colorScheme.primary,
                     modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().height(if (segmented) 8.dp else 9.dp)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)).background(colors.surfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(if (segmented && ratio in 0.001f..0.999f) 3.dp else 0.dp)) {
        if (ratio > 0f) Spacer(Modifier.weight(ratio.coerceAtMost(1f)).fillMaxHeight().background(color))
        if (ratio < 1f) Spacer(Modifier.weight((1f - ratio).coerceAtMost(1f)).fillMaxHeight()
            .background(if (segmented) colors.onSurfaceVariant.copy(alpha = .45f) else Color.Transparent))
    }
}

@Composable
fun CalendarIcon() = Icon(designIcon(R.drawable.ks_calendar_days), null, Modifier.size(18.dp))
