package com.kitsuneapp.feature.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    AlertDialog(onDismissRequest = dismiss, shape = DialogShape, title = { Text("流水筛选") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterGroupHeader("类型", { draft = draft.copy(types = null) }, { draft = draft.copy(types = emptySet()) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryType.entries.forEach { type ->
                        FilterChoice(type.label, type in types) {
                            draft = draft.copy(types = toggle(types, type).takeUnless { it == allTypes })
                        }
                    }
                }
                FilterGroupHeader("分类", { draft = draft.copy(categoryNames = null) }, { draft = draft.copy(categoryNames = emptySet()) })
                if (categories.isEmpty()) Secondary("当前钱包范围没有可选分类")
                LazyColumn(Modifier.heightIn(max = 216.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(categories, key = Category::normalizedName) { category ->
                        val name = category.normalizedName
                        FilterChoice(category.name, name in categoryNames, Modifier.fillMaxWidth()) {
                            draft = draft.copy(categoryNames = toggle(categoryNames, name).takeUnless { it == allCategories })
                        }
                    }
                }
                Field("备注关键词", draft.note, { draft = draft.copy(note = it) })
                TextButton({ draft = LedgerEntryFilters() }) { Text("重置条件") }
            }
        },
        confirmButton = { Primary("应用", { apply(draft) }) },
        dismissButton = { TextButton(dismiss) { Text("取消") } })
}

private fun <T> toggle(selection: Set<T>, value: T): Set<T> =
    if (value in selection) selection - value else selection + value

@Composable
private fun FilterChoice(label: String, checked: Boolean, modifier: Modifier = Modifier, change: () -> Unit) {
    Choice(label, change, modifier, selected = checked, icon = if (checked) designIcon(R.drawable.ks_check) else null)
}

@Composable
private fun FilterGroupHeader(label: String, selectAll: () -> Unit, clear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { FieldLabel(label) }
        TextButton(selectAll, Modifier.semantics { contentDescription = "全选$label" }) { Text("全选") }
        TextButton(clear, Modifier.semantics { contentDescription = "全不选$label" }) { Text("全不选") }
    }
}
