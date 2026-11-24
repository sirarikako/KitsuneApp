package com.kitsuneapp.feature.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kitsuneapp.data.RangeMode
import com.kitsuneapp.data.Wallet
import com.kitsuneapp.data.WalletRange
import com.kitsuneapp.ui.ControlShape
import com.kitsuneapp.ui.DialogShape
import com.kitsuneapp.ui.Secondary
import com.kitsuneapp.ui.SelectionSheet
import com.kitsuneapp.ui.MultiSelectionRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WalletRangeDialog(applied: WalletRange, wallets: List<Wallet>, dismiss: () -> Unit, apply: (WalletRange) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(applied.mode) }
    var manualIds by rememberSaveable {
        val initial = if (applied.mode == RangeMode.MANUAL) applied.walletIds else WalletRange().selected(wallets).map(Wallet::id)
        mutableStateOf(initial.toList())
    }
    val checkedIds = remember(manualIds) { manualIds.toSet() }
    val canApply = mode != RangeMode.MANUAL || wallets.any { it.id in checkedIds }
    SelectionSheet("钱包范围", dismiss, footer = {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton({ apply(WalletRange()) }) { Text("恢复默认") }
            TextButton(dismiss) { Text("取消") }
            TextButton({ apply(WalletRange(mode, if (mode == RangeMode.MANUAL) manualIds else emptyList())) },
                enabled = canApply) { Text("应用") }
        }
    }) {
        Secondary("默认只统计常规钱包；独立核算钱包可手动加入。")
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RangeMode.entries.forEach { option ->
                RangeChoice(option, selected = option == mode) { mode = option }
            }
        }
        Secondary(when (mode) {
            RangeMode.REGULAR -> "仅常规钱包（独立核算关闭），包括归档历史。"
            RangeMode.ALL -> "包括独立核算钱包和归档历史。"
            RangeMode.MANUAL -> "独立核算钱包在下方列表中默认不勾选。"
        })
        if (mode == RangeMode.MANUAL) {
            wallets.forEach { wallet ->
                MultiSelectionRow("${wallet.name} · ${wallet.currencyCode}", wallet.id in checkedIds, { checked ->
                    manualIds = if (checked) manualIds + wallet.id else manualIds - wallet.id
                }, listOfNotNull("独立核算".takeIf { wallet.separateAccounting },
                    "已归档".takeIf { wallet.archived }).joinToString(" · ").ifEmpty { null })
            }
        }
        if (!canApply) Secondary("请选择至少一个钱包，或恢复默认。")
    }
}

@Composable
private fun RangeChoice(mode: RangeMode, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = when (mode) {
        RangeMode.REGULAR -> "常规钱包"
        RangeMode.ALL -> "全部钱包"
        RangeMode.MANUAL -> "手动选择"
    }
    OutlinedButton(onClick, Modifier.heightIn(min = 46.dp).semantics { this.selected = selected },
        shape = ControlShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) colors.primaryContainer else colors.surface,
            contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        ),
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

