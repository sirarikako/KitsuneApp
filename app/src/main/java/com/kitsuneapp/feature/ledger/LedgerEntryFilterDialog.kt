package com.kitsuneapp.feature.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kitsuneapp.data.Category
import com.kitsuneapp.data.EntryType
import com.kitsuneapp.data.LedgerEntryFilters
import com.kitsuneapp.ui.*

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
    SelectionSheet("流水筛选", dismiss, footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(dismiss) { Text("取消") }
            Primary("应用", { apply(draft) }, Modifier.weight(1f).heightIn(min = 48.dp))
        }
    }) {
        item {
            FilterGroupHeader("类型", { draft = draft.copy(types = null) }, { draft = draft.copy(types = emptySet()) })
        }
        items(EntryType.entries, key = { it.name }) { type ->
            MultiSelectionRow(type.label, type in types, {
                draft = draft.copy(types = toggle(types, type).takeUnless { it == allTypes })
            })
        }
        item {
            FilterGroupHeader("分类", { draft = draft.copy(categoryNames = null) },
                { draft = draft.copy(categoryNames = emptySet()) })
        }
        if (categories.isEmpty()) item { Secondary("当前钱包范围没有可选分类") }
        items(categories, key = { "category:${it.normalizedName}" }) { category ->
            val name = category.normalizedName
            MultiSelectionRow(category.name, name in categoryNames, {
                draft = draft.copy(categoryNames = toggle(categoryNames, name).takeUnless { it == allCategories })
            }, icon = categoryGlyph(category.icon))
        }
        item {
            Field("备注关键词", draft.note, { draft = draft.copy(note = it) })
        }
    }
}

private fun <T> toggle(selection: Set<T>, value: T): Set<T> =
    if (value in selection) selection - value else selection + value

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroupHeader(label: String, selectAll: () -> Unit, clear: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.align(Alignment.CenterVertically)) { FieldLabel(label) }
        TextButton(selectAll, Modifier.semantics { contentDescription = "全选$label" }) { Text("全选") }
        TextButton(clear, Modifier.semantics { contentDescription = "全不选$label" }) { Text("全不选") }
    }
}
