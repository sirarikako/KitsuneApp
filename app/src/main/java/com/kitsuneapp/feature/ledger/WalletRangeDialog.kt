package com.kitsuneapp.feature.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kitsuneapp.data.RangeMode
import com.kitsuneapp.data.Wallet
import com.kitsuneapp.data.WalletRange
import com.kitsuneapp.ui.MultiSelectionRow
import com.kitsuneapp.ui.Primary
import com.kitsuneapp.ui.Secondary
import com.kitsuneapp.ui.SelectionGroupTitle
import com.kitsuneapp.ui.SelectionSheet
import com.kitsuneapp.ui.walletSelectionDetails

@Composable
internal fun WalletRangeDialog(
    applied: WalletRange, wallets: List<Wallet>, dismiss: () -> Unit, apply: (WalletRange) -> Unit,
) {
    val regularIds = remember(wallets) { WalletRange().selected(wallets).map(Wallet::id) }
    var draft by rememberSaveable {
        val initialIds = if (applied.mode == RangeMode.MANUAL) applied.walletIds else regularIds
        mutableStateOf(WalletRange(applied.mode, initialIds))
    }
    val mode = draft.mode
    val checkedIds = remember(draft.walletIds) { draft.walletIds.toSet() }
    val checkedCount = remember(wallets, checkedIds) { wallets.count { it.id in checkedIds } }
    val canApply = mode != RangeMode.MANUAL || checkedCount > 0

    SelectionSheet("钱包范围", dismiss, footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ draft = WalletRange(walletIds = regularIds) }) { Text("恢复默认") }
            Primary("应用", { apply(if (mode == RangeMode.MANUAL) draft else WalletRange(mode)) },
                Modifier.weight(1f).heightIn(min = 48.dp), enabled = canApply)
        }
    }) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WalletRangeModes(mode) { draft = draft.copy(mode = it) }
                when (mode) {
                    RangeMode.REGULAR -> Secondary("排除独立核算钱包，保留归档历史。")
                    RangeMode.ALL -> Secondary("包含独立核算钱包及归档历史。")
                    RangeMode.MANUAL -> Unit
                }
            }
        }
        if (mode == RangeMode.MANUAL) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        SelectionGroupTitle("选择钱包", Modifier.weight(1f))
                        Secondary("已选 $checkedCount/${wallets.size}")
                    }
                    if (!canApply) Secondary("至少选择一个钱包。")
                }
            }
            items(wallets, key = Wallet::id) { wallet ->
                MultiSelectionRow(wallet.name, wallet.id in checkedIds, { checked ->
                    val ids = draft.walletIds
                    draft = draft.copy(walletIds = if (checked) ids + wallet.id else ids - wallet.id)
                }, walletSelectionDetails(wallet))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletRangeModes(selected: RangeMode, select: (RangeMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val segmentColors = SegmentedButtonDefaults.colors(
        activeContainerColor = colors.primaryContainer,
        activeContentColor = colors.onPrimaryContainer,
        activeBorderColor = colors.outlineVariant,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = colors.onSurface,
        inactiveBorderColor = colors.outlineVariant,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        RangeMode.entries.forEachIndexed { index, mode ->
            val label = when (mode) {
                RangeMode.REGULAR -> "常规钱包"
                RangeMode.ALL -> "全部钱包"
                RangeMode.MANUAL -> "手动选择"
            }
            SegmentedButton(
                selected = mode == selected,
                onClick = { select(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, RangeMode.entries.size),
                modifier = Modifier.heightIn(min = 48.dp),
                colors = segmentColors,
                icon = {},
            ) {
                Text(label, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge.copy(lineBreak = LineBreak.Heading))
            }
        }
    }
}
