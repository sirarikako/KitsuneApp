package com.kitsuneapp.feature.ledger

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kitsuneapp.AppViewModel
import com.kitsuneapp.data.*
import com.kitsuneapp.ui.*
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WalletForm(records: Records, id: String?, model: AppViewModel, back: () -> Unit) {
    val wallet = records.wallets.find { it.id == id }
    val locked = records.entries.any { id != null && (it.walletId == id || it.toWalletId == id) }
    var name by rememberSaveable { mutableStateOf(wallet?.name.orEmpty()) }
    var code by rememberSaveable { mutableStateOf(wallet?.currencyCode) }
    var amount by rememberSaveable { mutableStateOf(wallet?.let { money(it.openingBalanceMinor, it.currencyScale) } ?: "0") }
    var order by rememberSaveable { mutableStateOf((wallet?.sortOrder ?: (records.wallets.maxOfOrNull { it.sortOrder }
        ?: -1).coerceAtMost(Int.MAX_VALUE - 1) + 1).toString()) }
    var archived by rememberSaveable { mutableStateOf(wallet?.archived ?: false) }
    var separate by rememberSaveable { mutableStateOf(wallet?.separateAccounting ?: false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    Column {
        Header(if (wallet == null) "新建钱包" else "编辑钱包", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("钱包名称", name, { name = it })
            Choice("币种 · " + (code ?: "请选择"), { pickCurrency = true }, Modifier.fillMaxWidth(), enabled = !locked)
            if (locked) Secondary("已有流水，币种和精度固定")
            Field("初始余额", amount, { amount = it }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Field("排序", order, { order = it }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            Toggle("独立核算", separate) { separate = it }
            Secondary("开启后默认排除本钱包的收支和流水。余额与历史记录保留，可在账本手动选择。")
            if (wallet != null) Toggle("归档钱包", archived) { archived = it }
            SaveActions(busy, error, {
                model.save(back) {
                    saveWallet(WalletDraft(id, name, code.orEmpty(), amount,
                        requireNotNull(order.toIntOrNull()) { "排序必须为非负整数" }, archived, separate))
                }
            }, wallet?.takeUnless { locked }?.let { "删除钱包" }, { delete = true })
        }
    }
    if (pickCurrency) {
        var query by rememberSaveable { mutableStateOf("") }
        val locale = Locale.SIMPLIFIED_CHINESE
        AlertDialog(onDismissRequest = { pickCurrency = false }, shape = DialogShape, title = { Text("选择币种") },
            text = {
                Column {
                    Field("搜索代码或名称", query, { query = it })
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(availableCurrencies.filter {
                            it.currencyCode.contains(query.trim(), true) || it.getDisplayName(locale).contains(query.trim(), true)
                        }, key = { it.currencyCode }) {
                            TextButton({ code = it.currencyCode; pickCurrency = false }) {
                                Text(it.currencyCode + " · " + it.getDisplayName(locale))
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton({ pickCurrency = false }) { Text("取消") } })
    }
    if (delete && wallet != null) ConfirmDelete(wallet.name + " · " + wallet.currencyCode, { delete = false },
        { delete = false; model.save(back) { removeWallet(wallet.id) } })
}

@Composable
fun EntryDetails(records: Records, id: String, model: AppViewModel, open: (String) -> Unit, back: () -> Unit) {
    val ledger = remember(records) { Ledger(records) }
    val entry = ledger.entry(id)
    var delete by rememberSaveable { mutableStateOf(false) }
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    Column {
        Header("账目详情", back = back) { if (entry != null) TextButton({ open("entryForm/$id") }) { Text("编辑") } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) detail@ {
            if (entry == null) {
                Paper { Text("这笔账目已不存在") }
                return@detail
            }
            val remaining = if (entry.type == EntryType.EXPENSE) ledger.refundRemaining(entry) else null
            Paper {
                Secondary(entry.type.label)
                Text(ledger.entryAmount(entry), style = MaterialTheme.typography.headlineSmall)
                Text("${entry.date} ${entry.time}")
                Text("钱包 · " + ledger.walletLabel(entry.walletId))
                entry.toWalletId?.let { Text("到账钱包 · " + ledger.walletLabel(it)) }
                ledger.categoryLabel(ledger.categoryId(entry)).takeIf(String::isNotBlank)?.let { Text("分类 · $it") }
                if (entry.note.isNotBlank()) Text(entry.note)
                if (remaining != null) {
                    val wallet = checkNotNull(ledger.wallet(entry.walletId))
                    Text("剩余可退 · " + money(remaining, wallet.currencyScale) + " " + wallet.currencyCode)
                }
            }
            if (remaining?.signum() == 1) Primary("发起退款", { open("refund/$id") }, Modifier.fillMaxWidth(), enabled = !busy)
            entry.originalExpenseId?.let { Choice("查看原支出", { open("entry/$it") }, Modifier.fillMaxWidth()) }
            val refunds = ledger.entries.filter { it.originalExpenseId == id }
            if (refunds.isNotEmpty()) {
                Section("关联退款")
                refunds.forEach { refund ->
                    Choice("${refund.date} · " + ledger.entryAmount(refund), { open("entry/" + refund.id) }, Modifier.fillMaxWidth())
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton({ delete = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("删除这笔账目")
            }
        }
    }
    if (delete && entry != null) ConfirmDelete(
        "${entry.date} ${entry.time} · ${entry.type.label}\n" +
            ledger.walletLabel(entry.walletId) + "\n" + ledger.categoryLabel(ledger.categoryId(entry)) + "\n" + ledger.entryAmount(entry),
        { delete = false }, { delete = false; model.save(back) { removeEntry(id) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryForm(records: Records, id: String?, expenseId: String?, model: AppViewModel, back: () -> Unit, finished: () -> Unit) {
    val ledger = remember(records) { Ledger(records) }
    val original = ledger.entry(id)
    val expense = ledger.entry(expenseId ?: original?.originalExpenseId)
    val now = remember { LocalDateTime.now() }
    val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf(original?.type ?: if (expenseId != null) EntryType.REFUND else EntryType.EXPENSE) }
    var date by rememberSaveable { mutableStateOf(original?.date ?: now.toLocalDate().toString()) }
    var time by rememberSaveable { mutableStateOf(original?.time ?: now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))) }
    var walletId by rememberSaveable { mutableStateOf(original?.walletId ?: expense?.walletId ?: ledger.preferredWallet()?.id) }
    var targetId by rememberSaveable { mutableStateOf(original?.toWalletId) }
    val wallet = ledger.wallet(walletId)
    val target = ledger.wallet(targetId)
    val sourceExpenseWallet = ledger.wallet(expense?.walletId)
    var categoryId by rememberSaveable { mutableStateOf(original?.categoryId ?:
        ledger.categories.find { it.scope == type.scope && it.isDefault }?.id.takeIf { wallet != null }) }
    var newCategory by rememberSaveable { mutableStateOf("") }
    var categoryCleared by rememberSaveable { mutableStateOf(false) }
    var editingCategory by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf(original?.let { ledger.wallet(it.walletId)?.let { w -> money(it.amountMinor, w.currencyScale) } }.orEmpty()) }
    var targetAmount by rememberSaveable { mutableStateOf(original?.toAmountMinor?.let { money(it, target?.currencyScale ?: 0) }.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var calculator by rememberSaveable { mutableIntStateOf(0) }
    var walletSheet by rememberSaveable { mutableIntStateOf(0) }
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    val scope = type.scope
    val options = if (scope != null && wallet != null) ledger.categories.filter { allowedCategory(it, wallet, scope) } else emptyList()
    fun unselectCategory(nextWallet: Wallet?, nextType: EntryType) {
        val nextScope = nextType.scope
        val retain = nextScope != null && nextWallet != null &&
            ledger.categories.any { it.id == categoryId && allowedCategory(it, nextWallet, nextScope) }
        if (!retain) {
            categoryCleared = categoryId != null && nextScope != null
            categoryId = if (categoryCleared || nextWallet == null || nextScope == null) null
                else ledger.categories.firstOrNull { it.scope == nextScope && it.isDefault }?.id
        }
        newCategory = ""
        editingCategory = false
    }

    Column {
        Header(when {
            original != null -> "编辑${type.label}"
            type == EntryType.REFUND -> "记录退款"
            else -> "记一笔"
        }, back = back, action = {
            if (original == null && expense == null) Select("类型", type, listOf(EntryType.EXPENSE, EntryType.INCOME, EntryType.TRANSFER),
                { it.label }, { value -> unselectCategory(wallet, value); type = value })
        })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateControl("日期", date, { date = it }, Modifier.weight(1f))
                Choice("时间 · $time", {
                    val value = LocalTime.parse(time)
                    TimePickerDialog(context, { _, hour, minute -> time = String.format(Locale.ROOT, "%02d:%02d", hour, minute) },
                        value.hour, value.minute, true).show()
                }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice((if (type == EntryType.TRANSFER) "转出 · " else "钱包 · ") + ledger.walletLabel(walletId),
                    { walletSheet = 1 }, Modifier.weight(1f))
                if (type == EntryType.TRANSFER) {
                    Choice("到账 · " + ledger.walletLabel(targetId), { walletSheet = 2 }, Modifier.weight(1f))
                } else if (scope != null) {
                    Select("分类", categoryId, options.map { it.id }, ledger::categoryLabel, {
                        categoryId = it
                        categoryCleared = false
                        editingCategory = false
                        newCategory = ""
                    }, Modifier.weight(1f), enabled = wallet != null)
                }
            }
            if (wallet == null) Secondary("请手动选择钱包")
            if (categoryCleared) Secondary("请重新选择该钱包的分类")
            if (scope != null && wallet != null) {
                TextButton({ editingCategory = !editingCategory; if (!editingCategory) newCategory = "" }) {
                    Text(if (editingCategory) "取消新增分类" else "新增分类")
                }
                if (editingCategory) Field("分类名称", newCategory, { newCategory = it })
            }
            if (expense != null) {
                val available = ledger.refundRemaining(expense, original?.id)
                Secondary("原支出 · ${expense.date} · " + ledger.categoryLabel(expense.categoryId))
                if (sourceExpenseWallet != null) {
                    TextButton({ amount = money(available, sourceExpenseWallet.currencyScale) }) {
                        Text("全额退款 · " + money(available, sourceExpenseWallet.currencyScale) + " " + sourceExpenseWallet.currencyCode)
                    }
                }
            }
            Field("备注", note, { note = it }, multiline = true)
            AmountField(if (type == EntryType.TRANSFER) "转出金额" else "金额", amount, { amount = it }, wallet) { calculator = 1 }
            if (type == EntryType.TRANSFER && wallet != null && target != null) {
                if (wallet.currencyCode == target.currencyCode) Secondary("到账金额与转出金额相同")
                else AmountField("到账金额", targetAmount, { targetAmount = it }, target) { calculator = 2 }
            }
            SaveActions(busy, error, {
                val draft = EntryDraft(id, date, time, type, walletId, amount, categoryId, newCategory,
                    targetId, targetAmount, expense?.id, note)
                model.save(finished) { saveEntry(draft) }
            })
        }
    }

    if (calculator != 0) {
        val first = calculator == 1
        val scale = (if (first) wallet else target)?.currencyScale
        if (scale != null) Calculator(scale, if (first) amount else targetAmount, { calculator = 0 }) {
            if (first) amount = it else targetAmount = it
            calculator = 0
        }
    }
    if (walletSheet != 0) {
        val source = walletSheet == 1
        val choices = ledger.wallets.filter { item ->
            val active = !item.archived || item.id == (if (source) original?.walletId ?: expense?.walletId else original?.toWalletId)
            active && (if (source) item.id != targetId || type != EntryType.TRANSFER else item.id != walletId) &&
                (!source || expense == null || item.currencyCode == sourceExpenseWallet?.currencyCode)
        }
        ModalBottomSheet(onDismissRequest = { walletSheet = 0 }) {
            Column(Modifier.padding(16.dp)) {
                Section(if (source) "选择钱包" else "选择到账钱包")
                if (choices.isEmpty()) Secondary("没有可选钱包")
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(choices, key = { it.id }) { item ->
                        val current = if (source) walletId else targetId
                        Choice(ledger.walletLabel(item.id) + if (item.separateAccounting) " · 独立核算" else "", {
                            if (source) {
                                if (walletId != item.id) unselectCategory(item, type)
                                walletId = item.id
                            } else targetId = item.id
                            walletSheet = 0
                        }, Modifier.fillMaxWidth(), selected = current == item.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountField(label: String, value: String, set: (String) -> Unit, wallet: Wallet?, calculator: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(label + (wallet?.let { " · " + it.currencyCode } ?: ""), value, set, Modifier.weight(1f),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End))
        Choice("计算器", calculator, enabled = wallet != null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Calculator(scale: Int, initial: String, dismiss: () -> Unit, apply: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun evaluate(): String? {
        return try {
            money(calculate(text, scale), scale).also { result = it; error = null }
        } catch (failure: IllegalArgumentException) {
            error = failure.message
            null
        }
    }
    ModalBottomSheet(onDismissRequest = dismiss, shape = DialogShape) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("金额计算器")
            Paper { Text(text.ifBlank { "0" }, style = MaterialTheme.typography.headlineSmall); result?.let { Text("= $it") } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            listOf(listOf("清空", "⌫", "÷", "×"), listOf("7", "8", "9", "−"), listOf("4", "5", "6", "+"),
                listOf("1", "2", "3", "="), listOf("0", ".")).forEach { keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    keys.forEach { key ->
                        Choice(key, {
                            if (key == "=") evaluate() else {
                                text = when (key) {
                                    "清空" -> ""
                                    "⌫" -> text.dropLast(1)
                                    else -> (text + key).take(512)
                                }
                                result = null
                                error = null
                            }
                        }, Modifier.weight(1f))
                    }
                }
            }
            Primary("使用此金额", { evaluate()?.let(apply) }, Modifier.fillMaxWidth())
        }
    }
}
