package com.kitsuneapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kitsuneapp.R
import com.kitsuneapp.data.Wallet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionSheet(
    title: String,
    dismiss: () -> Unit,
    subtitle: String? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    singleChoice: Boolean = false,
    listStateKey: String = "",
    content: LazyListScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val listState = key(listStateKey) { rememberLazyListState() }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = .33f),
        dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Surface(Modifier.fillMaxWidth().padding(9.dp), shape = DialogShape, color = colors.surface) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)
                    .size(32.dp, 4.dp).clip(ControlShape).background(colors.outlineVariant))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = dismiss) {
                        Icon(designIcon(R.drawable.ks_x), contentDescription = "关闭")
                    }
                }
                header?.invoke()
                LazyColumn(
                    Modifier.weight(1f, fill = false).heightIn(max = 420.dp)
                        .then(if (singleChoice) Modifier.selectableGroup() else Modifier),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (subtitle != null) item { Secondary(subtitle) }
                    content()
                }
                footer?.invoke(this)
            }
        }
    }
}

@Composable
internal fun SelectionRow(
    label: String, selected: Boolean, onSelect: () -> Unit,
    secondary: String? = null, icon: ImageVector? = null,
) = SelectionOption(label, selected, Modifier.selectable(selected, role = Role.RadioButton, onClick = onSelect),
    secondary, icon) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (selected) Icon(designIcon(R.drawable.ks_check), contentDescription = null)
    }
}

@Composable
internal fun MultiSelectionRow(
    label: String, checked: Boolean, change: (Boolean) -> Unit,
    secondary: String? = null, icon: ImageVector? = null,
) = SelectionOption(label, checked, Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = change),
    secondary, icon) {
    Checkbox(checked, onCheckedChange = null)
}

@Composable
private fun SelectionOption(
    label: String, selected: Boolean, interaction: Modifier,
    secondary: String?, icon: ImageVector?, indicator: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val foreground = if (selected) colors.onPrimaryContainer else colors.onSurface
    val supporting = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (secondary == null) 48.dp else 64.dp)
            .clip(ControlShape).background(if (selected) colors.primaryContainer else Color.Transparent)
            .then(interaction).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = supporting)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = foreground)
            secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = supporting) }
        }
        CompositionLocalProvider(LocalContentColor provides foreground, content = indicator)
    }
}

@Composable
internal fun <T> SingleSelectSheet(
    title: String, value: T?, options: List<T>, optionKey: (T) -> String,
    label: (T) -> String, onSelect: (T) -> Unit, dismiss: () -> Unit,
    subtitle: String? = null, secondary: ((T) -> String?)? = null,
    optionIcon: (@Composable (T) -> ImageVector)? = null, empty: String = "没有可选项目",
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    listStateKey: String = "",
) {
    SelectionSheet(title, dismiss, subtitle, header, footer, singleChoice = true, listStateKey = listStateKey) {
        if (options.isEmpty()) item { EmptyState(empty) }
        items(options, key = optionKey) { option ->
            SelectionRow(label(option), option == value, {
                dismiss()
                onSelect(option)
            }, secondary?.invoke(option), optionIcon?.invoke(option))
        }
    }
}

@Composable
internal fun SelectionControl(
    title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: ImageVector? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(title)
        OutlinedAction(onClick, Modifier.fillMaxWidth().semantics { contentDescription = title },
            enabled = enabled, spread = true) {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp))
            Text(value, Modifier.weight(1f), color =
                if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(designIcon(R.drawable.ks_chevron_down), null, Modifier.size(14.dp))
        }
    }
}

@Composable
internal fun SheetSelect(
    title: String, value: String?, options: List<String>, label: (String) -> String,
    onSelect: (String) -> Unit, enabled: Boolean = true, secondary: ((String) -> String?)? = null,
    currentLabel: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SelectionControl(title, currentLabel ?: value?.let(label) ?: "请选择", { open = true }, enabled = enabled)
    if (open && enabled) {
        SingleSelectSheet("选择$title", value, options, { it }, label, onSelect, { open = false },
            secondary = secondary)
    }
}

internal fun walletSelectionDetails(wallet: Wallet): String = buildString {
    append(wallet.currencyCode)
    if (wallet.separateAccounting) append(" · 独立核算")
    if (wallet.archived) append(" · 已归档")
}
