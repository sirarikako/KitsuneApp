package com.kitsuneapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@Composable
fun Header(title: String, subtitle: String? = null, back: (() -> Unit)? = null, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (back != null) SquareIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", back)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) Secondary(subtitle)
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
fun Paper(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(padding.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun RecordRow(
    title: String,
    subtitle: String,
    detail: String = "",
    icon: ImageVector,
    onClick: () -> Unit,
    amount: String? = null,
    amountColor: Color = Color.Unspecified,
    empty: Boolean = false,
    badge: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp), shape = CardShape,
        color = if (empty) scheme.background else scheme.surface, border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(ControlShape)
                .background(if (empty) scheme.primaryContainer else scheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = if (empty) scheme.primary else scheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) Secondary(subtitle, lines = 2)
                if (detail.isNotEmpty()) Secondary(detail, lines = 1)
                if (badge != null) Tag(badge)
                if (amount != null) {
                    Text(amount, Modifier.align(Alignment.End), color = amountColor, fontWeight = FontWeight.SemiBold)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Tag(text: String) {
    Surface(shape = RoundedTag, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

private val RoundedTag = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)

@Composable
fun Primary(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 46.dp), enabled = enabled, shape = ControlShape) {
        Text(label)
    }
}

@Composable
fun Choice(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 46.dp), shape = ControlShape,
        enabled = enabled, border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) scheme.primaryContainer else scheme.surface,
            contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        ), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label)
    }
}

@Composable
fun SquareIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedIconButton(onClick = onClick, modifier = Modifier.size(44.dp), shape = ControlShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Icon(icon, label, Modifier.size(18.dp))
    }
}

@Composable
fun Field(label: String, value: String, set: (String) -> Unit, modifier: Modifier = Modifier, multiline: Boolean = false,
          keyboard: KeyboardOptions = KeyboardOptions.Default, transformation: androidx.compose.ui.text.input.VisualTransformation =
              androidx.compose.ui.text.input.VisualTransformation.None) {
    OutlinedTextField(value, set, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = !multiline,
        shape = ControlShape, minLines = if (multiline) 2 else 1, maxLines = if (multiline) 6 else 1,
        keyboardOptions = keyboard, visualTransformation = transformation,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
}

@Composable
fun <T> Select(
    title: String, value: T?, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, placeholder: String = "请选择", enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Choice("$title · " + (value?.let(label) ?: placeholder), { open = true }, Modifier.fillMaxWidth(), enabled = enabled)
        DropdownMenu(open, { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

@Composable
fun Tabs(options: List<String>, selected: Int, set: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        options.forEachIndexed { index, option ->
            Column(Modifier.clickable { set(index) }.heightIn(min = 46.dp).padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(option, fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(30.dp).height(2.dp).background(
                    if (index == selected) MaterialTheme.colorScheme.primary else Color.Transparent))
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun DateControl(label: String, value: String, set: (String) -> Unit, modifier: Modifier = Modifier, allowEmpty: Boolean = false) {
    val context = LocalContext.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Choice("$label · " + value.ifEmpty { "不限" }, {
            val date = runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
            android.app.DatePickerDialog(context,
                { _, y, m, d -> set(LocalDate.of(y, m + 1, d).toString()) },
                date.year, date.monthValue - 1, date.dayOfMonth).show()
        }, Modifier.weight(1f))
        if (allowEmpty && value.isNotEmpty()) TextButton({ set("") }) { Text("清空") }
    }
}

@Composable
fun MonthControl(value: String, set: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.parse(value)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SquareIcon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月") { set(month.minusMonths(1).toString()) }
        Choice("${month.year} 年 ${month.monthValue} 月", { open = true }, Modifier.weight(1f))
        SquareIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月") { set(month.plusMonths(1).toString()) }
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
fun SaveActions(busy: Boolean, error: String?, onSave: () -> Unit, deleteLabel: String? = null, onDelete: () -> Unit = {}) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Primary(if (busy) "正在保存…" else "保存", onSave, Modifier.fillMaxWidth(), enabled = !busy)
    if (deleteLabel != null) {
        TextButton(onDelete, Modifier.fillMaxWidth().heightIn(min = 46.dp), enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(deleteLabel) }
    }
}

@Composable
fun ConfirmDelete(description: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, shape = DialogShape, title = { Text("确认删除") },
        text = { Text(description) },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
        confirmButton = {
            Button(onClick = confirm, shape = ControlShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError)) { Text("删除") }
        })
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
        Text(label, Modifier.weight(1f))
        Secondary("$count $unit · " + String.format(Locale.ROOT, "%.0f%%", ratio * 100))
    }
    if (ratio > 0) LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
}

@Composable
fun CalendarIcon() = Icon(Icons.Outlined.DateRange, null, Modifier.size(18.dp))
