package com.kitsuneapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kitsuneapp.R

/** Selection-only sheet; keep calculators and destructive confirmations unchanged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionSheet(
    title: String, dismiss: () -> Unit, subtitle: String? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = .33f), dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Surface(Modifier.fillMaxWidth().padding(9.dp), shape = DialogShape, color = colors.surface) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.padding(vertical = 4.dp).size(32.dp, 4.dp).clip(ControlShape)
                        .background(colors.outlineVariant))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = dismiss) {
                        Icon(designIcon(R.drawable.ks_x), contentDescription = "关闭")
                    }
                }
                subtitle?.let { Secondary(it) }
                header?.invoke(this)
                Column(Modifier.weight(1f, fill = false).heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp),
                    content = content)
                footer?.invoke(this)
            }
        }
    }
}

@Composable
internal fun SelectionRow(
    label: String, selected: Boolean, onSelect: () -> Unit,
    secondary: String? = null, icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = if (secondary == null) 48.dp else 64.dp)
        .clip(ControlShape).background(if (selected) colors.primaryContainer else Color.Transparent)
        .selectable(selected, role = Role.RadioButton, onClick = onSelect)
        .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp),
            tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface)
            secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant) }
        }
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(designIcon(R.drawable.ks_check), null, tint = colors.onPrimaryContainer)
        }
    }
}

@Composable
internal fun MultiSelectionRow(label: String, checked: Boolean, change: (Boolean) -> Unit,
    secondary: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = if (secondary == null) 48.dp else 64.dp)
        .clip(ControlShape).background(if (checked) colors.primaryContainer else Color.Transparent)
        .toggleable(checked, role = Role.Checkbox, onValueChange = change)
        .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(label, color = if (checked) colors.onPrimaryContainer else colors.onSurface)
            secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (checked) colors.onPrimaryContainer else colors.onSurfaceVariant) }
        }
    }
}

@Composable
internal fun <T> SingleSelectSheet(
    title: String, value: T?, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit,
    dismiss: () -> Unit, subtitle: String? = null, secondary: ((T) -> String?)? = null,
    optionIcon: (@Composable (T) -> ImageVector)? = null, empty: String = "没有可选项目",
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    SelectionSheet(title, dismiss, subtitle, header, footer) {
        if (options.isEmpty()) EmptyState(empty)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { option ->
                SelectionRow(label(option), option == value, {
                    dismiss()
                    onSelect(option)
                }, secondary?.invoke(option), optionIcon?.invoke(option))
            }
        }
    }
}

@Composable
internal fun <T> SheetSelect(
    title: String, value: T?, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, placeholder: String = "请选择", enabled: Boolean = true,
    showLabel: Boolean = true, sheetTitle: String = "选择$title", subtitle: String? = null,
    secondary: ((T) -> String?)? = null, optionIcon: (@Composable (T) -> ImageVector)? = null,
 ) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel) FieldLabel(title)
        OutlinedAction({ open = true }, Modifier.fillMaxWidth().semantics { contentDescription = title },
            enabled = enabled, spread = true) {
            if (value != null && optionIcon != null) Icon(optionIcon(value), null, Modifier.size(16.dp))
            Text(value?.let(label) ?: placeholder, Modifier.weight(1f))
            Icon(designIcon(R.drawable.ks_chevron_down), null, Modifier.size(14.dp))
        }
    }
    if (open && enabled) SingleSelectSheet(sheetTitle, value, options, label, onSelect, { open = false },
        subtitle, secondary, optionIcon)
}
