package com.kitsuneapp.feature.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kitsuneapp.R
import com.kitsuneapp.data.LedgerPeriod
import com.kitsuneapp.ui.*
import java.time.YearMonth

@Composable
internal fun LedgerPeriodControl(period: LedgerPeriod, open: () -> Unit, set: (LedgerPeriod) -> Unit) {
    val month = YearMonth.parse(period.month)
    val monthly = period.start == null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (monthly) {
            SquareIcon(designIcon(R.drawable.ks_chevron_left), "上个月", month > YearMonth.of(1, 1)) {
                set(LedgerPeriod(month.minusMonths(1).toString()))
            }
        }
        Choice(period.label, open, Modifier.weight(1f).semantics { contentDescription = "选择日期范围" },
            icon = designIcon(R.drawable.ks_calendar_days))
        if (monthly) {
            SquareIcon(designIcon(R.drawable.ks_chevron_right), "下个月", month < YearMonth.of(9999, 12)) {
                set(LedgerPeriod(month.plusMonths(1).toString()))
            }
        }
    }
}

@Composable
internal fun LedgerPeriodDialog(applied: LedgerPeriod, dismiss: () -> Unit, apply: (LedgerPeriod) -> Unit) {
    val appliedMonth = YearMonth.parse(applied.month)
    var monthly by rememberSaveable { mutableStateOf(applied.start == null) }
    var year by rememberSaveable { mutableStateOf(appliedMonth.year.toString()) }
    var month by rememberSaveable { mutableIntStateOf(appliedMonth.monthValue) }
    var start by rememberSaveable { mutableStateOf(applied.start.orEmpty()) }
    var end by rememberSaveable { mutableStateOf(applied.end.orEmpty()) }
    val parsedYear = year.takeIf { it.length in 1..4 && it.all { digit -> digit in '0'..'9' } }
        ?.toIntOrNull()?.takeIf { it in 1..9999 }
    val datesReversed = end.isNotEmpty() && start > end
    val draft = if (monthly) {
        parsedYear?.let { LedgerPeriod(YearMonth.of(it, month).toString()) }
    } else {
        applied.copy(start = start, end = end).takeIf { start.isNotEmpty() && end.isNotEmpty() && !datesReversed }
    }

    AlertDialog(onDismissRequest = dismiss, title = { Text("日期范围") },
        shape = DialogShape, containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("按月", { monthly = true }, Modifier.weight(1f), selected = monthly)
                    Choice("自定义日期", { monthly = false }, Modifier.weight(1f), selected = !monthly)
                }
                if (monthly) {
                    Field("年份", year, { year = it }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                    if (parsedYear == null) Secondary("请输入 1 至 9999 的年份")
                    MonthChoices(month) { month = it }
                } else {
                    DateControl("开始日期", start, { start = it })
                    DateControl("结束日期", end, { end = it })
                    if (datesReversed) Text("开始日期不能晚于结束日期", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton({ draft?.let(apply) }, enabled = draft != null) { Text("应用") } },
        dismissButton = { TextButton(dismiss) { Text("取消") } })
}
