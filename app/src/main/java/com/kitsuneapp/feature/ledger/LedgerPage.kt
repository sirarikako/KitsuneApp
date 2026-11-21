package com.kitsuneapp.feature.ledger

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsuneapp.data.*
import com.kitsuneapp.ui.*
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerPage(records: Records, open: (String) -> Unit) {
    val ledger = remember(records) { Ledger(records) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var period by rememberSaveable { mutableStateOf(LedgerPeriod(YearMonth.now().toString())) }
    var wallets by rememberSaveable { mutableStateOf(WalletRange()) }
    var type by rememberSaveable { mutableStateOf<EntryType?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var walletPicker by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }
    val selected = wallets.selected(ledger.wallets)
    val matching = remember(ledger, period, wallets) { ledger.filtered(period, wallets) }
    val balances = remember(ledger) { ledger.balance() }
    Column {
        Header("记账")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tabs(listOf("流水", "钱包", "统计"), tab) { tab = it }
            if (tab != 1) {
                if (period.start == null) MonthControl(period.month) { period = LedgerPeriod(it) }
                else Choice(period.label, { datePicker = true }, Modifier.fillMaxWidth(), selected = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("日期范围", { datePicker = true })
                    Choice(when (wallets.mode) {
                        RangeMode.REGULAR -> "常规钱包"
                        RangeMode.ALL -> "全部钱包"
                        RangeMode.MANUAL -> "已选 ${selected.size} 个钱包"
                    }, { walletPicker = true }, Modifier.weight(1f))
                    if (tab == 0) Choice("筛选", { filters = true }, selected = type != null || category != null || note.isNotBlank())
                }
                if (selected.isEmpty() && ledger.wallets.isNotEmpty()) Secondary("当前范围没有钱包，可手动选择")
            }
        }
        Crossfade(tab, modifier = Modifier.weight(1f), animationSpec = tween(150, easing = motion), label = "账本标签") { page ->
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (page) {
                    0 -> {
                        val entries = matching.filter {
                            (type == null || it.type == type) && (category == null || ledger.categoryId(it) == category) &&
                                it.note.contains(note.trim(), true)
                        }
                        if (entries.isEmpty()) item { Paper { Text(if (ledger.wallets.isEmpty()) "先创建一个钱包" else "这段时间没有符合条件的账目") } }
                        items(entries, key = { it.id }) { entry -> EntryRow(ledger, entry) { open("entry/" + entry.id) } }
                    }
                    1 -> {
                        item { Primary("新建钱包", { open("wallet/-") }, Modifier.fillMaxWidth()) }
                        items(ledger.wallets, key = { it.id }) { wallet ->
                            Paper {
                                RecordRow(wallet.name, listOfNotNull(wallet.currencyCode, "已归档".takeIf { wallet.archived }).joinToString(" · "),
                                    icon = Icons.AutoMirrored.Outlined.List, badge = "独立核算".takeIf { wallet.separateAccounting },
                                    amount = money(balances.getValue(wallet.id), wallet.currencyScale) + " " + wallet.currencyCode,
                                    onClick = { open("wallet/" + wallet.id) })
                                TextButton({ open("walletCategories/" + wallet.id) }) { Text("管理钱包专属分类") }
                            }
                        }
                    }
                    else -> {
                        val totals = ledger.statistics(period, wallets)
                        if (totals.isEmpty()) item { Paper { Text("暂无收支 · 收入 0 · 净支出 0") } }
                        items(totals, key = { it.currency }) { total ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Section(total.currency)
                                Summary("收入" to money(total.income, total.scale), "净支出" to money(total.expenses, total.scale))
                                Paper {
                                    CategoryTotals("收入分类", total.incomeCategories, total.scale, ledger)
                                    CategoryTotals("净支出分类", total.expenseCategories, total.scale, ledger)
                                }
                            }
                        }
                    }
                }
            }
        }
        Primary("记一笔", {
            open(if (ledger.availableWallets().isEmpty()) "wallet/-" else "entryForm/-")
        }, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
    }

    if (walletPicker) {
        var draft by rememberSaveable { mutableStateOf(wallets) }
        ModalBottomSheet(onDismissRequest = { walletPicker = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Section("钱包范围")
                Choice("常规钱包（恢复默认）", { draft = WalletRange() }, Modifier.fillMaxWidth(),
                    selected = draft.mode == RangeMode.REGULAR)
                Choice("全部钱包（含独立核算）", { draft = WalletRange(RangeMode.ALL) }, Modifier.fillMaxWidth(),
                    selected = draft.mode == RangeMode.ALL)
                LazyColumn(Modifier.heightIn(max = 270.dp)) {
                    items(ledger.wallets, key = { it.id }) { wallet ->
                        val ids = draft.selected(ledger.wallets).map { it.id }
                        val checked = wallet.id in ids
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, { select -> draft = WalletRange(RangeMode.MANUAL,
                                if (select) ids + wallet.id else ids - wallet.id) })
                            Text(ledger.walletLabel(wallet.id) + if (wallet.separateAccounting) " · 独立核算" else "")
                        }
                    }
                }
                Primary("应用钱包范围", { wallets = draft; walletPicker = false }, Modifier.fillMaxWidth())
            }
        }
    }
    if (datePicker) {
        val month = YearMonth.parse(period.month)
        var start by rememberSaveable { mutableStateOf(period.start ?: month.atDay(1).toString()) }
        var end by rememberSaveable { mutableStateOf(period.end ?: month.atEndOfMonth().toString()) }
        AlertDialog(onDismissRequest = { datePicker = false }, title = { Text("自定义日期范围") }, shape = DialogShape,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateControl("开始", start, { start = it })
                    DateControl("结束", end, { end = it })
                    if (start > end) Text("开始日期不能晚于结束日期", color = MaterialTheme.colorScheme.error)
                    TextButton({ period = LedgerPeriod(period.month); datePicker = false }) { Text("返回按月") }
                }
            }, confirmButton = { Primary("应用", { period = period.copy(start = start, end = end); datePicker = false }, enabled = start <= end) },
            dismissButton = { TextButton({ datePicker = false }) { Text("取消") } })
    }
    if (filters) {
        var draftType by rememberSaveable { mutableStateOf(type?.name.orEmpty()) }
        var draftCategory by rememberSaveable { mutableStateOf(category.orEmpty()) }
        var draftNote by rememberSaveable { mutableStateOf(note) }
        val ids = selected.map { it.id }.toSet()
        val historical = ledger.entries.filter { it.walletId in ids }.mapNotNull(ledger::categoryId).toSet()
        val options = ledger.categories.filter {
            it.scope != Scope.PREPARATION && ((it.walletId == null && !it.archived) || it.walletId in ids || it.id in historical)
        }
        AlertDialog(onDismissRequest = { filters = false }, shape = DialogShape, title = { Text("流水筛选") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Select("类型", draftType, listOf("") + EntryType.entries.map { it.name },
                        { if (it.isEmpty()) "不限" else EntryType.valueOf(it).label }, { draftType = it })
                    Select("分类", draftCategory, listOf("") + options.map { it.id },
                        { if (it.isEmpty()) "不限" else ledger.categoryLabel(it) }, { draftCategory = it })
                    Field("备注关键词", draftNote, { draftNote = it })
                    TextButton({ draftType = ""; draftCategory = ""; draftNote = "" }) { Text("重置条件") }
                }
            }, confirmButton = { Primary("应用", {
                type = draftType.takeIf(String::isNotEmpty)?.let(EntryType::valueOf)
                category = draftCategory.takeIf(String::isNotEmpty)
                note = draftNote
                filters = false
            }) }, dismissButton = { TextButton({ filters = false }) { Text("取消") } })
    }
}

@Composable
private fun CategoryTotals(title: String, totals: Map<String, java.math.BigInteger>, scale: Int, ledger: Ledger) {
    Section(title)
    if (totals.isEmpty()) Secondary("暂无账目")
    val max = totals.values.maxOfOrNull { it.abs() } ?: java.math.BigInteger.ZERO
    totals.entries.sortedByDescending { it.value }.forEach { (id, amount) ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ledger.categoryLabel(id), Modifier.weight(1f))
            Text(money(amount, scale))
        }
        if (max.signum() != 0) {
            val share = amount.abs().toBigDecimal().divide(max.toBigDecimal(), 4, java.math.RoundingMode.HALF_UP).toFloat()
            LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth())
        }
    }
}

fun Ledger.entryAmount(entry: FinancialEntry): String {
    val source = requireNotNull(wallet(entry.walletId))
    val value = money(entry.amountMinor, source.currencyScale) + " " + source.currencyCode
    return when (entry.type) {
        EntryType.EXPENSE -> "−$value"
        EntryType.INCOME, EntryType.REFUND -> "+$value"
        EntryType.TRANSFER -> {
            val target = requireNotNull(wallet(entry.toWalletId))
            value + " → " + money(requireNotNull(entry.toAmountMinor), target.currencyScale) + " " + target.currencyCode
        }
    }
}

@Composable
private fun EntryRow(ledger: Ledger, entry: FinancialEntry, onClick: () -> Unit) {
    val names = if (entry.type == EntryType.TRANSFER) ledger.walletLabel(entry.walletId) + " → " + ledger.walletLabel(entry.toWalletId)
        else ledger.walletLabel(entry.walletId)
    val category = ledger.categoryLabel(ledger.categoryId(entry)).ifBlank { entry.type.label }
    RecordRow(category, "${entry.date} ${entry.time} · $names", entry.note, Icons.AutoMirrored.Outlined.List, onClick,
        amount = ledger.entryAmount(entry), amountColor = when (entry.type) {
            EntryType.INCOME, EntryType.REFUND -> incomeColor
            EntryType.EXPENSE -> MaterialTheme.colorScheme.error
            EntryType.TRANSFER -> MaterialTheme.colorScheme.onSurface
        }, badge = entry.type.label)
}
