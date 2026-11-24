package com.kitsuneapp.feature.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kitsuneapp.R
import com.kitsuneapp.data.Category
import com.kitsuneapp.data.EntryType
import com.kitsuneapp.data.LedgerEntryFilters
import com.kitsuneapp.ui.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerEntryFilterDialog(
    applied: LedgerEntryFilters,
    categories: List<Category>,
    dismiss: () -> Unit,
    apply: (LedgerEntryFilters) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(applied) }
    val allTypes = remember { EntryType.entries.toSet() }
    val allCategories = remember(categories) { categories.mapTo(mutableSetOf()) { it.normalizedName } }
    val types = draft.types ?: allTypes
    val categoryNames = draft.categoryNames ?: allCategories
    val categoryCount = remember(allCategories, categoryNames) { allCategories.count { it in categoryNames } }

    SelectionSheet("流水筛选", dismiss, footer = {
        Primary("应用", { apply(draft) }, Modifier.fillMaxWidth().heightIn(min = 48.dp))
    }) {
        item {
            Column {
                FilterGroupHeader("类型", null, { draft = draft.copy(types = null) },
                    { draft = draft.copy(types = emptySet()) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EntryType.entries.forEach { type ->
                        TypeFilterChip(type.label, type in types) {
                            draft = draft.copy(types = toggle(types, type).takeUnless { it == allTypes })
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(top = 12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FilterGroupHeader("分类", "$categoryCount/${allCategories.size}",
                    { draft = draft.copy(categoryNames = null) },
                    { draft = draft.copy(categoryNames = emptySet()) })
            }
        }
        if (categories.isEmpty()) item { Secondary("当前钱包范围没有可选分类") }
        items(categories, key = { "category:${it.normalizedName}" }) { category ->
            val name = category.normalizedName
            MultiSelectionRow(category.name, name in categoryNames, {
                draft = draft.copy(categoryNames = toggle(categoryNames, name).takeUnless { it == allCategories })
            }, icon = categoryGlyph(category.icon))
        }
        item {
            Field("备注关键词", draft.note, { draft = draft.copy(note = it) }, Modifier.padding(top = 12.dp))
        }
    }
}

private fun <T> toggle(selection: Set<T>, value: T): Set<T> =
    if (value in selection) selection - value else selection + value

@Composable
private fun TypeFilterChip(label: String, selected: Boolean, toggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = toggle,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = {
            Box(Modifier.size(16.dp)) {
                if (selected) Icon(designIcon(R.drawable.ks_check), contentDescription = null)
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = colors.onSurface,
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer,
            selectedLeadingIconColor = colors.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = colors.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroupHeader(label: String, count: String?, selectAll: () -> Unit, clear: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
        Row(Modifier.weight(1f).align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SelectionGroupTitle(label)
            count?.let { Secondary(it) }
        }
        TextButton(selectAll, Modifier.semantics { contentDescription = "全选$label" },
            contentPadding = PaddingValues(horizontal = 8.dp)) { Text("全选") }
        TextButton(clear, Modifier.semantics { contentDescription = "全不选$label" },
            contentPadding = PaddingValues(horizontal = 8.dp)) { Text("全不选") }
    }
}
