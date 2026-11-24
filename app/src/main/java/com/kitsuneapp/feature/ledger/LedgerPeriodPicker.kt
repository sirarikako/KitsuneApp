package com.kitsuneapp.feature.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsuneapp.R
import com.kitsuneapp.data.LedgerPeriod
import com.kitsuneapp.ui.*
import java.time.YearMonth

@Composable
internal fun LedgerPeriodControl(period: LedgerPeriod, open: () -> Unit, modifier: Modifier) {
    val month = YearMonth.parse(period.month)
    val start = period.start
    val end = period.end
    val value = if (start != null && end != null) {
        "${start.replace('-', '/')} ～ ${end.replace('-', '/')}"
    } else {
        "${month.year}年${month.monthValue}月"
    }
    LedgerFilterControl(value, "选择日期范围", open, modifier)
}

@Composable
internal fun LedgerFilterControl(
    value: String,
    description: String,
    open: () -> Unit,
    modifier: Modifier,
    ellipsize: Boolean = false,
) {
    OutlinedAction(open, modifier.semantics { contentDescription = description }, spread = true) {
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            maxLines = if (ellipsize) 1 else Int.MAX_VALUE,
            overflow = if (ellipsize) TextOverflow.Ellipsis else TextOverflow.Clip)
        Icon(designIcon(R.drawable.ks_chevron_down), null, Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun LedgerPeriodDialog(applied: LedgerPeriod, dismiss: () -> Unit, apply: (LedgerPeriod) -> Unit) {
    val appliedMonth = YearMonth.parse(applied.month)
    var monthly by rememberSaveable { mutableStateOf(applied.start == null) }
    var monthDraft by rememberSaveable { mutableStateOf(MonthDraft(appliedMonth)) }
    var start by rememberSaveable { mutableStateOf(applied.start.orEmpty()) }
    var end by rememberSaveable { mutableStateOf(applied.end.orEmpty()) }
    val datesReversed = end.isNotEmpty() && start > end
    val draft = if (monthly) {
        monthDraft.value?.let { LedgerPeriod(it.toString()) }
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
                    MonthPickerFields(monthDraft) { monthDraft = it }
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
