package com.kitsuneapp.feature.ledger

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsuneapp.AppViewModel
import com.kitsuneapp.R
import com.kitsuneapp.data.*
import com.kitsuneapp.ui.*
import kotlinx.coroutines.launch
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
        Form {
            Field("钱包名称", name, { name = it })
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("币种")
                OutlinedAction({ pickCurrency = true }, Modifier.fillMaxWidth(), enabled = !locked, spread = true) {
                    Text(code ?: "请选择", Modifier.weight(1f))
                    Icon(designIcon(R.drawable.ks_chevron_down), null, Modifier.size(14.dp))
                }
                if (locked) Secondary("已有流水，币种和精度固定")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("初始余额", amount, { amount = it }, Modifier.weight(1f),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Field("排序", order, { order = it }, Modifier.weight(1f),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Paper {
                Toggle("独立核算", separate) { separate = it }
                Secondary("开启后默认排除本钱包的收支和流水。余额与历史记录保留，可在账本手动选择。")
                if (wallet != null) {
                    HorizontalDivider()
                    Toggle("归档钱包", archived) { archived = it }
                }
            }
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
        Header("账目详情", back = back) {
            if (entry != null) TextAction("编辑", { open("entryForm/$id") }, designIcon(R.drawable.ks_pencil))
        }
        Form detail@{
            if (entry == null) {
                EmptyState("这笔账目已不存在")
                return@detail
            }
            val remaining = if (entry.type == EntryType.EXPENSE) ledger.refundRemaining(entry) else null
            Paper {
                val wallet = checkNotNull(ledger.wallet(entry.walletId))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Secondary("${entry.type.label} · ${wallet.currencyCode}")
                    MoneyText(ledger.entryAmount(entry, includeCurrency = entry.type == EntryType.TRANSFER), align = TextAlign.Center)
                }
            }
            Paper(padding = 0) {
                val fields = buildList {
                    add("日期时间" to "${entry.date} ${entry.time}")
                    add("钱包" to ledger.walletLabel(entry.walletId))
                    entry.toWalletId?.let { add("到账钱包" to ledger.walletLabel(it)) }
                    if (entry.type != EntryType.TRANSFER) {
                        add("分类" to ledger.categoryLabel(ledger.categoryId(entry)).ifBlank { "未分类" })
                    }
                    add("备注" to entry.note.ifBlank { "无" })
                    if (remaining != null) {
                        val wallet = checkNotNull(ledger.wallet(entry.walletId))
                        add("剩余可退" to "${displayMoney(remaining, wallet.currencyScale)} ${wallet.currencyCode}")
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    fields.forEachIndexed { index, (label, value) ->
                        if (index > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, Modifier.weight(1f), textAlign = TextAlign.End)
                        }
                    }
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
            DeleteAction("删除这笔账目", { delete = true }, enabled = !busy)
        }
    }
    if (delete && entry != null) ConfirmDelete(
        "${entry.date} ${entry.time} · ${entry.type.label}\n" +
            ledger.walletLabel(entry.walletId) + "\n" + ledger.categoryLabel(ledger.categoryId(entry)) + "\n" + ledger.entryAmount(entry),
        { delete = false }, { delete = false; model.save(back) { removeEntry(id) } },
        title = "删除这笔账目？", note = "删除后，将更新流水、收支统计和钱包余额。")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryForm(records: Records, id: String?, expenseId: String?, model: AppViewModel, back: () -> Unit, finished: () -> Unit) {
    val ledger = remember(records) { Ledger(records) }
    val original = ledger.entry(id)
    val expense = ledger.entry(expenseId ?: original?.originalExpenseId)
    val now = remember { LocalDateTime.now() }
    val context = LocalContext.current
    val pickerTheme = platformPickerTheme()
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
        }, back = back)
        Form {
            if (original == null && expense == null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel("收支类型")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(EntryType.EXPENSE, EntryType.INCOME, EntryType.TRANSFER).forEach { option ->
                            Choice(option.label, {
                                if (option != type) { unselectCategory(wallet, option); type = option }
                            }, selected = type == option)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("日期时间")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateControl("日期", date, { date = it }, Modifier.weight(1.6f), showLabel = false)
                    Choice(time, {
                        val value = LocalTime.parse(time)
                        TimePickerDialog(context, pickerTheme, { _, hour, minute ->
                            time = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                        }, value.hour, value.minute, true).show()
                    }, Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WalletControl(if (type == EntryType.TRANSFER) "转出钱包" else "钱包", ledger.walletLabel(walletId),
                    Modifier.weight(1f)) { walletSheet = 1 }
                if (type == EntryType.TRANSFER) {
                    WalletControl("到账钱包", ledger.walletLabel(targetId), Modifier.weight(1f)) { walletSheet = 2 }
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
            if (scope != null && wallet != null) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val categorySource = if (wallet.categoryMode(scope) == CategoryMode.CUSTOM) "本钱包专属分类" else "通用分类"
                Secondary("分类来源：$categorySource。切换钱包后如无对应分类，请重新选择。")
                if (categoryCleared) Secondary("请重新选择该钱包的分类")
                TextAction(if (editingCategory) "取消新增分类" else "新增分类", {
                    editingCategory = !editingCategory
                    if (!editingCategory) newCategory = ""
                }, designIcon(R.drawable.ks_plus))
                if (editingCategory) {
                    Field("分类名称", newCategory, { newCategory = it }, placeholder = "输入分类名称")
                }
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
            Field("备注", note, { note = it }, multiline = true, minLines = 1, placeholder = "这笔钱用在了哪里？")
            AmountField(if (type == EntryType.TRANSFER) "转出金额" else "金额", amount, { amount = it }, wallet) { calculator = 1 }
            if (type == EntryType.TRANSFER && wallet != null && target != null) {
                if (wallet.currencyCode == target.currencyCode) Secondary("到账金额与转出金额相同")
                else AmountField("到账金额", targetAmount, { targetAmount = it }, target) { calculator = 2 }
            }
            SaveActions(busy, error, {
                val draft = EntryDraft(id, date, time, type, walletId, amount, categoryId, newCategory,
                    targetId, targetAmount, expense?.id, note)
                model.save(finished) { saveEntry(draft) }
            }, saveLabel = "保存${type.label}")
        }
    }

    if (calculator != 0) {
        val first = calculator == 1
        val scale = (if (first) wallet else target)?.currencyScale
        val currency = if (first) wallet?.currencyCode else target?.currencyCode
        if (scale != null && currency != null) Calculator(scale, currency, if (first) amount else targetAmount, { calculator = 0 }) {
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
        DesignSheet({ walletSheet = 0 }) {
            Text(if (source) "选择钱包" else "选择到账钱包", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (choices.isEmpty()) EmptyState("没有可选钱包")
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
            Choice("取消", { walletSheet = 0 }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WalletControl(title: String, value: String, modifier: Modifier, select: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(title)
        OutlinedAction(select, Modifier.fillMaxWidth(), spread = true) {
            Text(value, Modifier.weight(1f))
            Icon(designIcon(R.drawable.ks_chevron_down), null, Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AmountField(label: String, value: String, set: (String) -> Unit, wallet: Wallet?, calculator: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("$label · ${wallet?.currencyCode ?: "请选择钱包"}")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(label, value, set, Modifier.weight(1f), keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp, lineHeight = 40.sp, textAlign = TextAlign.End),
                placeholder = if (wallet?.currencyScale == 0) "0" else "0.00", showLabel = false)
            OutlinedAction(calculator, enabled = wallet != null, contentColor = MaterialTheme.colorScheme.primary) {
                Icon(designIcon(R.drawable.ks_calculator), null, Modifier.size(18.dp))
                Text("计算器")
            }
        }
    }
}

@Composable
internal fun Calculator(scale: Int, currency: String, initial: String, dismiss: () -> Unit, apply: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    fun evaluate(): String? {
        return try {
            money(calculate(text, scale), scale).also { error = null }
        } catch (failure: IllegalArgumentException) {
            error = failure.message
            null
        }
    }
    fun update(input: String) {
        text = input.take(512)
        error = null
    }
    fun press(key: String) {
        when (key) {
            "C" -> update("")
            "⌫" -> update(text.dropLast(1))
            else -> update(text + key)
        }
    }
    DesignSheet(dismiss, scroll = scroll, footer = {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("取消", dismiss, Modifier.weight(1f))
            Primary("使用此金额", { evaluate()?.let(apply) }, Modifier.weight(1f))
        }
    }) {
        val focus = LocalFocusManager.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("金额计算器", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            TextAction("关闭", dismiss)
        }
        Secondary("$currency · 支持加减乘除")
        Field("计算表达式", text, ::update, textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.End),
            placeholder = if (scale == 0) "0" else "0.00", showLabel = false)
        listOf(listOf("C", "⌫", "÷", "×"), listOf("7", "8", "9", "−"), listOf("4", "5", "6", "+")).forEach { keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { key -> CalculatorKey(key, Modifier.weight(1f)) { press(key) } }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val keyWidth = (maxWidth - 24.dp) / 4
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.width(keyWidth * 3 + 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1", "2", "3").forEach { key -> CalculatorKey(key, Modifier.width(keyWidth)) { press(key) } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CalculatorKey("0", Modifier.width(keyWidth * 2 + 8.dp)) { press("0") }
                        CalculatorKey(".", Modifier.width(keyWidth)) { press(".") }
                    }
                }
                Primary("=", {
                    evaluate()?.let {
                        update(it)
                        focus.clearFocus()
                        scope.launch { scroll.scrollTo(0) }
                    }
                }, Modifier.width(keyWidth).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun CalculatorKey(key: String, modifier: Modifier, press: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedAction(press, modifier, contentColor = if (key in listOf("÷", "×", "−", "+")) colors.primary else colors.onSurface) {
        Text(key, fontSize = 20.sp, lineHeight = 24.sp)
    }
}
