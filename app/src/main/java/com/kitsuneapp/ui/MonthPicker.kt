package com.kitsuneapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kitsuneapp.R
import java.io.Serializable
import java.time.YearMonth

internal data class MonthDraft(val year: String, val month: Int) : Serializable {
    constructor(month: YearMonth) : this(month.year.toString(), month.monthValue)

    val value: YearMonth? get() = year.takeIf { it.length in 1..4 && it.all { digit -> digit in '0'..'9' } }
        ?.toIntOrNull()?.takeIf { it in 1..9999 }?.let { YearMonth.of(it, month) }
}

@Composable
internal fun MonthPickerFields(draft: MonthDraft, update: (MonthDraft) -> Unit) {
    val selected = draft.value
    Field("年份", draft.year, { update(draft.copy(year = it)) },
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
    if (selected == null) Secondary("请输入 1 至 9999 的年份")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        SquareIcon(designIcon(R.drawable.ks_chevron_left), "上个月",
            selected != null && selected > YearMonth.of(1, 1)) {
            selected?.minusMonths(1)?.let { update(MonthDraft(it)) }
        }
        selected?.let { Text("${it.year}年${it.monthValue}月", Modifier.weight(1f).padding(horizontal = 4.dp),
            textAlign = TextAlign.Center) }
        SquareIcon(designIcon(R.drawable.ks_chevron_right), "下个月",
            selected != null && selected < YearMonth.of(9999, 12)) {
            selected?.plusMonths(1)?.let { update(MonthDraft(it)) }
        }
    }
    MonthChoices(draft.month) { update(draft.copy(month = it)) }
}

@Composable
internal fun MonthPickerDialog(initial: YearMonth, dismiss: () -> Unit, apply: (YearMonth) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(MonthDraft(initial)) }
    val month = draft.value
    AlertDialog(onDismissRequest = dismiss, title = { Text("选择统计月份") },
        shape = DialogShape, containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthPickerFields(draft) { draft = it }
            }
        },
        confirmButton = { TextButton({ month?.let(apply) }, enabled = month != null) { Text("应用") } },
        dismissButton = { TextButton(dismiss) { Text("取消") } })
}
