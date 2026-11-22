package com.kitsuneapp.feature.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsuneapp.data.*
import com.kitsuneapp.ui.*
import java.time.YearMonth

private data class LedgerContent(
    val page: Int,
    val period: LedgerPeriod,
    val wallets: WalletRange,
    val type: EntryType? = null,
    val category: String? = null,
    val note: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerPage(records: Records, open: (String) -> Unit, openDetails: (String) -> Unit) {
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
        val content = if (tab == 0) LedgerContent(tab, period, wallets, type, category, note)
            else LedgerContent(tab, period, wallets)
        ContentFade(content, Modifier.weight(1f)) { shown ->
            val totals = remember(ledger, shown.period, shown.wallets, shown.page == 1) {
                if (shown.page == 1) emptyList() else ledger.statistics(shown.period, shown.wallets)
            }
            val balances = remember(ledger, shown.page == 1) { if (shown.page == 1) ledger.balance() else emptyMap() }
            val periodLabel = if (shown.period.start == null) "${YearMonth.parse(shown.period.month).monthValue} 月" else "所选期间"
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (shown.page) {
                    0 -> {
                        val entries = ledger.filtered(shown.period, shown.wallets).filter {
                            (shown.type == null || it.type == shown.type) &&
                                (shown.category == null || ledger.categoryId(it) == shown.category) &&
                                it.note.contains(shown.note.trim(), true)
                        }
                        items(totals, key = { "summary:${it.currency}" }) { total ->
                            Paper {
                                Text("$periodLabel 净支出 · ${total.currency}", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall)
                                Text(money(total.expenses, total.scale), style = MaterialTheme.typography.headlineSmall)
                                Secondary("收入 ${money(total.income, total.scale)}")
                            }
                        }
                        item { Section("$periodLabel 流水") { Secondary("${entries.size} 笔") } }
                        if (entries.isEmpty()) item { Paper { Text(if (ledger.wallets.isEmpty()) "先创建一个钱包" else "这段时间没有符合条件的账目") } }
                        items(entries, key = { it.id }) { entry -> EntryRow(ledger, entry) { openDetails(entry.id) } }
                    }
                    1 -> {
                        item { Section("我的钱包") { Secondary("每个钱包余额单独计算") } }
                        item { Primary("新建钱包", { open("wallet/-") }, Modifier.fillMaxWidth()) }
                        items(ledger.wallets, key = { it.id }) { wallet ->
                            Paper {
                                Text(ledger.walletLabel(wallet.id), color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium)
                                if (wallet.separateAccounting) Tag("独立核算")
                                if (wallet.archived) Tag("已归档")
                                Text(money(balances.getValue(wallet.id), wallet.currencyScale),
                                    style = MaterialTheme.typography.headlineSmall)
                                Secondary("初始余额 ${money(wallet.openingBalanceMinor, wallet.currencyScale)} · 全部流水")
                                Choice("编辑钱包", { open("wallet/" + wallet.id) }, Modifier.fillMaxWidth())
                                Choice("管理本钱包分类", { open("walletCategories/" + wallet.id) }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    else -> {
                        if (totals.isEmpty()) item { Paper { Text("暂无收支 · 收入 0 · 净支出 0") } }
                        items(totals, key = { it.currency }) { total ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Section("$periodLabel 收支 · ${total.currency}")
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
        }, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), icon = Icons.Outlined.Add)
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
                val canApply = draft.mode != RangeMode.MANUAL || draft.walletIds.isNotEmpty()
                if (!canApply) Secondary("请选择至少一个钱包，或恢复默认。")
                Primary("应用钱包范围", { wallets = draft; walletPicker = false }, Modifier.fillMaxWidth(), enabled = canApply)
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
    val value = signedAmount(entry, source.currencyScale) + " " + source.currencyCode
    if (entry.type != EntryType.TRANSFER) return value
    val target = requireNotNull(wallet(entry.toWalletId))
    return value + " → " + money(requireNotNull(entry.toAmountMinor), target.currencyScale) + " " + target.currencyCode
}

private fun signedAmount(entry: FinancialEntry, scale: Int): String {
    val sign = when (entry.type) {
        EntryType.EXPENSE -> "−"
        EntryType.INCOME, EntryType.REFUND -> "+"
        EntryType.TRANSFER -> ""
    }
    return sign + money(entry.amountMinor, scale)
}

@Composable
private fun EntryRow(ledger: Ledger, entry: FinancialEntry, onClick: () -> Unit) {
    val transfer = entry.type == EntryType.TRANSFER
    val wallet = requireNotNull(ledger.wallet(entry.walletId))
    val names = if (transfer) wallet.name + " → " + requireNotNull(ledger.wallet(entry.toWalletId)).name else wallet.name
    val category = ledger.categoryLabel(ledger.categoryId(entry)).ifBlank { entry.type.label }
    val subtitle = "${entry.date} ${entry.time}" + if (transfer) "" else " · ${entry.type.label}"
    RecordRow(category, subtitle, entry.note, Icons.AutoMirrored.Outlined.List, onClick,
        amount = if (transfer) ledger.entryAmount(entry) else signedAmount(entry, wallet.currencyScale),
        currency = wallet.currencyCode.takeUnless { transfer }, amountColor = when (entry.type) {
            EntryType.INCOME, EntryType.REFUND -> incomeColor
            EntryType.EXPENSE -> MaterialTheme.colorScheme.error
            EntryType.TRANSFER -> MaterialTheme.colorScheme.onSurface
        }, badge = names, sharedId = entry.id)
}
