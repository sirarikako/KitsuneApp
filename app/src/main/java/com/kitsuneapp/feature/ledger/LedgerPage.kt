package com.kitsuneapp.feature.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsuneapp.R
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

@OptIn(ExperimentalLayoutApi::class)
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
            Tabs(listOf("流水", "钱包", "统计"), tab) { tab = it; datePicker = false }
            Spacer(Modifier.height(4.dp))
            if (tab != 1) {
                LedgerPeriodControl(period, { datePicker = true }) { period = it }
                ScopeControl("钱包范围", when (wallets.mode) {
                    RangeMode.REGULAR -> "常规钱包"
                    RangeMode.ALL -> "全部钱包（含独立核算）"
                    RangeMode.MANUAL -> selected.singleOrNull()?.let { ledger.walletLabel(it.id) }
                        ?: "已选 ${selected.size} 个钱包"
                }, R.drawable.ks_wallet) { walletPicker = true }
                if (wallets.mode != RangeMode.REGULAR && selected.size > 1) {
                    Secondary(selected.joinToString("、") { ledger.walletLabel(it.id) }, lines = 2)
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
            val custom = shown.period.start != null
            val periodLabel = if (custom) "所选期间" else "本月"
            val summaryPeriod = if (custom) periodLabel else "${YearMonth.parse(shown.period.month).monthValue} 月"
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
                                Text("$summaryPeriod 净支出 · ${total.currency}", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall)
                                MoneyText(displayMoney(total.expenses, total.scale))
                                Text("收入 ${displayMoney(total.income, total.scale)}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item { Section("${periodLabel}流水") {
                            Secondary("${entries.size} 笔")
                            Spacer(Modifier.width(8.dp))
                            Choice("筛选", { filters = true }, selected = shown.type != null || shown.category != null || shown.note.isNotBlank(),
                                icon = designIcon(R.drawable.ks_list_filter))
                        } }
                        if (entries.isEmpty()) item {
                            EmptyState(if (ledger.wallets.isEmpty()) "先创建一个钱包" else "所选日期和钱包范围内没有符合条件的账目")
                        }
                        val includeYear = custom && shown.period.start?.take(4) != shown.period.end?.take(4)
                        items(entries, key = { it.id }) { entry -> EntryRow(ledger, entry, includeYear) { openDetails(entry.id) } }
                    }
                    1 -> {
                        item { Section("我的钱包") {
                            Secondary("每个钱包余额单独计算")
                            Spacer(Modifier.width(8.dp))
                            TextAction("新建钱包", { open("wallet/-") }, designIcon(R.drawable.ks_plus))
                        } }
                        items(ledger.wallets, key = { it.id }) { wallet ->
                            Paper {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(ledger.walletLabel(wallet.id), color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall)
                                    if (wallet.separateAccounting) Tag("独立核算")
                                    if (wallet.archived) Tag("已归档")
                                }
                                MoneyText(displayMoney(balances.getValue(wallet.id), wallet.currencyScale))
                                Secondary("初始余额 ${displayMoney(wallet.openingBalanceMinor, wallet.currencyScale)} · 全部流水")
                                Choice("编辑钱包", { open("wallet/" + wallet.id) }, Modifier.fillMaxWidth())
                                Choice("管理本钱包分类", { open("walletCategories/" + wallet.id) }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    else -> {
                        if (totals.isEmpty()) item { Paper { Text("暂无收支 · 收入 0 · 净支出 0") } }
                        items(totals, key = { it.currency }) { total ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Section("${periodLabel}收支 · ${total.currency}")
                                Summary("收入 · ${total.currency}" to displayMoney(total.income, total.scale),
                                    "净支出 · ${total.currency}" to displayMoney(total.expenses, total.scale))
                                CategoryTotals("收入分类 · ${total.currency}", total.incomeCategories, total.scale, ledger)
                                CategoryTotals("净支出分类 · ${total.currency}", total.expenseCategories, total.scale, ledger)
                                if (total.incomeCategories.isEmpty() && total.expenseCategories.isEmpty()) {
                                    EmptyState("所选日期和钱包范围内没有账目")
                                }
                            }
                        }
                    }
                }
            }
        }
        Primary("记一笔", {
            open(if (ledger.availableWallets().isEmpty()) "wallet/-" else "entryForm/-")
        }, Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
            icon = designIcon(R.drawable.ks_plus))
    }

    if (datePicker && tab != 1) {
        LedgerPeriodDialog(period, dismiss = { datePicker = false }) {
            period = it
            datePicker = false
        }
    }
    if (walletPicker) {
        WalletRangeDialog(wallets, ledger.wallets, dismiss = { walletPicker = false }) {
            wallets = it
            walletPicker = false
        }
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
private fun ScopeControl(label: String, value: String, icon: Int, onClick: () -> Unit) {
    OutlinedAction(onClick, Modifier.fillMaxWidth(), spread = true) {
        val colors = MaterialTheme.colorScheme
        Icon(designIcon(icon), null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        Text(label, color = colors.onSurfaceVariant)
        Text(value, Modifier.weight(1f), color = colors.primary, textAlign = TextAlign.End)
        Icon(designIcon(R.drawable.ks_chevron_down), null, Modifier.size(14.dp), tint = colors.primary)
    }
}

@Composable
private fun CategoryTotals(title: String, totals: Map<String, java.math.BigInteger>, scale: Int, ledger: Ledger) {
    if (totals.isEmpty()) return
    Section(title)
    Paper(padding = 0) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            totals.entries.sortedByDescending { it.value }.forEachIndexed { index, (id, amount) ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ledger.categoryLabel(id), Modifier.weight(1f), fontSize = 13.sp)
                    Text(displayMoney(amount, scale), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun MoneyText(value: String, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(value, modifier, fontSize = 30.sp, lineHeight = 39.sp, letterSpacing = (-1).sp,
        fontWeight = FontWeight.Medium, textAlign = align)
}

fun Ledger.entryAmount(entry: FinancialEntry, includeCurrency: Boolean = true): String {
    val source = requireNotNull(wallet(entry.walletId))
    val value = signedAmount(entry, source.currencyScale)
    if (entry.type != EntryType.TRANSFER) return value + if (includeCurrency) " ${source.currencyCode}" else ""
    val target = requireNotNull(wallet(entry.toWalletId))
    return "$value ${source.currencyCode} → " +
        displayMoney(requireNotNull(entry.toAmountMinor), target.currencyScale) + " " + target.currencyCode
}

private fun signedAmount(entry: FinancialEntry, scale: Int): String {
    val sign = when (entry.type) {
        EntryType.EXPENSE -> "−"
        EntryType.INCOME, EntryType.REFUND -> "+"
        EntryType.TRANSFER -> ""
    }
    return sign + displayMoney(entry.amountMinor, scale)
}

@Composable
private fun EntryRow(ledger: Ledger, entry: FinancialEntry, includeYear: Boolean, onClick: () -> Unit) {
    val transfer = entry.type == EntryType.TRANSFER
    val wallet = requireNotNull(ledger.wallet(entry.walletId))
    val names = if (transfer) wallet.name + " → " + requireNotNull(ledger.wallet(entry.toWalletId)).name else wallet.name
    val category = ledger.categoryLabel(ledger.categoryId(entry)).ifBlank { entry.type.label }
    val date = if (includeYear) entry.date else entry.date.drop(5)
    val subtitle = listOf("$date ${entry.time}", entry.note).filter(String::isNotBlank).joinToString(" · ")
    val title = if (entry.type == EntryType.REFUND) "退款 · $category" else category
    val icon = when {
        transfer -> R.drawable.ks_arrow_right_left
        entry.type == EntryType.REFUND -> R.drawable.ks_undo2
        category == "交通" -> R.drawable.ks_train_front
        category == "工资" -> R.drawable.ks_briefcase_business
        else -> R.drawable.ks_receipt_text
    }
    RecordRow(title, subtitle, icon = designIcon(icon), onClick = onClick,
        amount = if (transfer) ledger.entryAmount(entry) else signedAmount(entry, wallet.currencyScale),
        currency = wallet.currencyCode.takeUnless { transfer }, amountColor = when (entry.type) {
            EntryType.INCOME, EntryType.REFUND -> incomeColor
            EntryType.EXPENSE -> MaterialTheme.colorScheme.error
            EntryType.TRANSFER -> MaterialTheme.colorScheme.onSurface
        }, badge = names, sharedId = entry.id)
}
