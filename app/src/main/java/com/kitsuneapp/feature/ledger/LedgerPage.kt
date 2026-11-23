package com.kitsuneapp.feature.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsuneapp.R
import com.kitsuneapp.data.*
import com.kitsuneapp.ui.*
import java.math.BigInteger
import java.math.MathContext
import java.time.YearMonth
import kotlin.math.roundToInt

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
fun LedgerPage(records: Records, open: (String) -> Unit, openDetails: (String) -> Unit, active: Boolean = true) {
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
    var titleHeight by remember { mutableIntStateOf(0) }
    var filterHeight by remember { mutableIntStateOf(0) }
    var collapsed by remember(tab) { mutableFloatStateOf(0f) }
    var compactFab by remember(tab) { mutableStateOf(false) }
    val fullyCollapsed by remember(tab) { derivedStateOf { collapsed >= 1f } }

    LaunchedEffect(active) {
        if (active) {
            collapsed = 0f
            compactFab = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column {
            Box(Modifier.ledgerHeaderPart(fullyCollapsed, { collapsed }) { titleHeight = it }) {
                Header("记账")
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Tabs(listOf("流水", "钱包", "统计"), tab) { tab = it; datePicker = false }
            }
            Box(Modifier.ledgerHeaderPart(fullyCollapsed, { collapsed }) { filterHeight = it }) {
                if (tab != 1) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val singleWallet = selected.singleOrNull()?.takeIf { wallets.mode == RangeMode.MANUAL }
                        val walletLabel = when (wallets.mode) {
                            RangeMode.REGULAR -> "常规钱包"
                            RangeMode.ALL -> "全部钱包"
                            RangeMode.MANUAL -> singleWallet?.let { ledger.walletLabel(it.id) }
                                ?: "已选 ${selected.size} 个钱包"
                        }
                        if (period.start == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LedgerPeriodControl(period, { datePicker = true }, Modifier.weight(1f))
                                LedgerFilterControl(walletLabel, "钱包范围", { walletPicker = true },
                                    Modifier.weight(1f), ellipsize = singleWallet != null)
                            }
                        } else {
                            LedgerPeriodControl(period, { datePicker = true }, Modifier.fillMaxWidth())
                            LedgerFilterControl(walletLabel, "钱包范围", { walletPicker = true },
                                Modifier.fillMaxWidth(), ellipsize = singleWallet != null)
                        }
                        if (selected.isEmpty() && ledger.wallets.isNotEmpty()) {
                            Secondary("当前范围没有钱包，可手动选择")
                        }
                    }
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
                val list = rememberLazyListState()
                val visibleContent = active && shown == content
                val scroll = remember(list, tab, active) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (available.y <= 0f || collapsed == 0f) return Offset.Zero
                            val height = titleHeight + filterHeight
                            if (height == 0) return Offset.Zero
                            // Give the first part of a reverse gesture to the header, keeping the current list item anchored.
                            val expanded = minOf(available.y, height * collapsed)
                            collapsed = (collapsed - expanded / height).coerceAtLeast(0f)
                            return Offset(0f, expanded)
                        }

                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            if (!list.canScrollBackward) {
                                collapsed = 0f
                                compactFab = false
                            } else if (consumed.y < 0f) {
                                val height = titleHeight + filterHeight
                                if (height > 0) collapsed = (collapsed - consumed.y / height).coerceAtMost(1f)
                                compactFab = true
                            }
                            return Offset.Zero
                        }
                    }
                }
                if (visibleContent) {
                    LaunchedEffect(list) {
                        snapshotFlow { list.canScrollBackward }.collect { awayFromTop ->
                            if (!awayFromTop) {
                                collapsed = 0f
                                compactFab = false
                            }
                        }
                    }
                }
                LazyColumn(
                    modifier = if (visibleContent) Modifier.nestedScroll(scroll) else Modifier,
                    state = list,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp,
                        bottom = if (shown.page == 0) 84.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                                val label = ledger.walletLabel(wallet.id)
                                Paper {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(label, color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.bodySmall)
                                            if (wallet.separateAccounting) Tag("独立核算")
                                            if (wallet.archived) Tag("已归档")
                                        }
                                        SquareIcon(designIcon(R.drawable.ks_pencil), "编辑钱包：$label") {
                                            open("wallet/" + wallet.id)
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        MoneyText(displayMoney(balances.getValue(wallet.id), wallet.currencyScale))
                                        Secondary("初始余额 ${displayMoney(wallet.openingBalanceMinor, wallet.currencyScale)} · 全部流水")
                                    }
                                    TextAction("管理本钱包分类", { open("walletCategories/" + wallet.id) },
                                        designIcon(R.drawable.ks_chevron_right))
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
                                    CategoryTotals("收入分类 · ${total.currency}", total.incomeCategories, total.scale, ledger, incomeColor)
                                    CategoryTotals("净支出分类 · ${total.currency}", total.expenseCategories, total.scale, ledger, expenseColor)
                                    if (total.incomeCategories.isEmpty() && total.expenseCategories.isEmpty()) {
                                        EmptyState("所选日期和钱包范围内没有账目")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (tab == 0) {
            LedgerFab(!compactFab, { open(newEntryRoute(ledger)) },
                Modifier.align(Alignment.BottomEnd).padding(16.dp))
        }
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

internal fun newEntryRoute(ledger: Ledger): String =
    if (ledger.availableWallets().isEmpty()) "wallet/-" else "entryForm/-"

private fun Modifier.ledgerHeaderPart(
    hidden: Boolean,
    collapsed: () -> Float,
    onHeight: (Int) -> Unit,
): Modifier = then(if (hidden) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier)
    .clipToBounds()
    .layout { measurable, constraints ->
        val part = measurable.measure(constraints.copy(minHeight = 0))
        val visible = (part.height * (1f - collapsed())).roundToInt()
        layout(part.width, visible) {
            if (visible > 0) part.placeRelative(0, visible - part.height)
        }
    }
    .onSizeChanged { onHeight(it.height) }

@Composable
private fun LedgerFab(expanded: Boolean, addEntry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(addEntry, modifier.semantics { contentDescription = "记一笔" }, shape = CircleShape,
        color = colors.primary, contentColor = colors.onPrimary, shadowElevation = 4.dp) {
        Row(Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(designIcon(R.drawable.ks_plus), null, Modifier.size(24.dp))
            AnimatedVisibility(expanded, modifier = Modifier.clearAndSetSemantics {},
                enter = expandHorizontally(tween(180, easing = motion), expandFrom = Alignment.Start) + fadeIn(tween(140)),
                exit = shrinkHorizontally(tween(180, easing = motion), shrinkTowards = Alignment.Start) + fadeOut(tween(140))) {
                Text("记一笔", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CategoryTotals(title: String, totals: Map<String, BigInteger>, scale: Int, ledger: Ledger, color: Color) {
    if (totals.isEmpty()) return
    val magnitude = remember(totals) { totals.values.sumOf(BigInteger::abs).toBigDecimal() }
    val sorted = remember(totals) { totals.entries.sortedByDescending { it.value } }
    Section(title) {
        if (magnitude.signum() > 0) {
            Secondary(if (sorted.last().value.signum() < 0) "条长按绝对值占比" else "金额占比")
        }
    }
    Paper(padding = 0) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            sorted.forEachIndexed { index, (id, amount) ->
                if (index > 0) HorizontalDivider()
                val label = ledger.categoryLabel(id)
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label, Modifier.weight(1f), fontSize = 13.sp)
                        Text(displayMoney(amount, scale), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    if (amount.signum() != 0) {
                        val ratio = amount.abs().toBigDecimal().divide(magnitude, MathContext.DECIMAL64).toFloat()
                        StatBar(ratio, color = color, modifier = Modifier.semantics {
                            contentDescription = "${label}金额占比"
                            progressBarRangeInfo = ProgressBarRangeInfo(ratio, 0f..1f)
                        })
                    }
                }
            }
        }
    }
}

@Composable
internal fun MoneyText(value: String, modifier: Modifier = Modifier, align: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(value, modifier, fontSize = 30.sp, lineHeight = 39.sp, letterSpacing = (-1).sp,
        fontWeight = FontWeight.Medium, textAlign = align, color = color)
}

@Composable
internal fun EntryType.amountColor(): Color = when (this) {
    EntryType.INCOME, EntryType.REFUND -> incomeColor
    EntryType.EXPENSE -> expenseColor
    EntryType.TRANSFER -> MaterialTheme.colorScheme.onSurface
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
        currency = wallet.currencyCode.takeUnless { transfer }, amountColor = entry.type.amountColor(),
        badge = names, sharedId = entry.id)
}
