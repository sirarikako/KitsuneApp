package com.kitsuneapp.feature.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsuneapp.data.RangeMode
import com.kitsuneapp.data.Wallet
import com.kitsuneapp.data.WalletRange
import com.kitsuneapp.ui.MultiSelectionRow
import com.kitsuneapp.ui.Secondary
import com.kitsuneapp.ui.SelectionRow
import com.kitsuneapp.ui.SelectionSheet
import com.kitsuneapp.ui.walletSelectionDetails

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WalletRangeDialog(
    applied: WalletRange, wallets: List<Wallet>, dismiss: () -> Unit, apply: (WalletRange) -> Unit,
) {
    val regularIds = remember(wallets) { WalletRange().selected(wallets).map(Wallet::id) }
    var mode by rememberSaveable { mutableStateOf(applied.mode) }
    var manualIds by rememberSaveable {
        mutableStateOf(ArrayList(if (applied.mode == RangeMode.MANUAL) applied.walletIds else regularIds))
    }
    val checkedIds = remember(manualIds) { manualIds.toSet() }
    val canApply = mode != RangeMode.MANUAL || wallets.any { it.id in checkedIds }

    SelectionSheet("钱包范围", dismiss, footer = {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({
                mode = RangeMode.REGULAR
                manualIds = ArrayList(regularIds)
            }) { Text("恢复默认") }
            TextButton(dismiss) { Text("取消") }
            TextButton(
                { apply(WalletRange(mode, if (mode == RangeMode.MANUAL) manualIds else emptyList())) },
                enabled = canApply,
            ) { Text("应用") }
        }
    }) {
        item {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RangeMode.entries.forEach { option ->
                    val label = when (option) {
                        RangeMode.REGULAR -> "常规钱包"
                        RangeMode.ALL -> "全部钱包"
                        RangeMode.MANUAL -> "手动选择"
                    }
                    SelectionRow(label, option == mode, { mode = option })
                }
            }
        }
        item {
            Secondary(when (mode) {
                RangeMode.REGULAR -> "仅常规钱包（独立核算关闭），包括归档历史。"
                RangeMode.ALL -> "包括独立核算钱包和归档历史。"
                RangeMode.MANUAL -> "可勾选独立核算钱包和归档钱包。"
            })
        }
        if (mode == RangeMode.MANUAL) {
            items(wallets, key = Wallet::id) { wallet ->
                MultiSelectionRow(wallet.name, wallet.id in checkedIds, { checked ->
                    manualIds = ArrayList(if (checked) manualIds + wallet.id else manualIds - wallet.id)
                }, walletSelectionDetails(wallet))
            }
        }
        if (!canApply) item { Secondary("请选择至少一个钱包，或恢复默认。") }
    }
}
