package com.kitsuneapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kitsuneapp.R
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

internal const val CalendarMonthCount = 9999 * 12
internal val YearMonth.calendarPage: Int get() = (year - 1) * 12 + monthValue - 1
private fun calendarMonth(page: Int) = YearMonth.of(page / 12 + 1, page % 12 + 1)

@Composable
internal fun DateCalendar(pager: PagerState, selected: LocalDate, expanded: Boolean,
                          setExpanded: (Boolean) -> Unit, select: (LocalDate) -> Unit) {
    val month = calendarMonth(pager.currentPage)
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var year by rememberSaveable { mutableStateOf(month.year.toString()) }
    val parsedYear = year.takeIf { it.length in 1..4 && it.all { digit -> digit in '0'..'9' } }
        ?.toIntOrNull()?.takeIf { it in 1..9999 }

    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(selected.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SquareIcon(designIcon(R.drawable.ks_chevron_left), "上个月", !expanded && pager.currentPage > 0) {
                scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
            }
            Choice("${month.year} 年 ${month.monthValue} 月", {
                if (expanded) focus.clearFocus() else year = month.year.toString()
                setExpanded(!expanded)
            }, Modifier.weight(1f).semantics { contentDescription = "选择年月" }, selected = expanded,
                icon = designIcon(R.drawable.ks_chevron_down))
            SquareIcon(designIcon(R.drawable.ks_chevron_right), "下个月",
                !expanded && pager.currentPage < CalendarMonthCount - 1) {
                scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(CalendarMonthCount - 1)) }
            }
        }
        if (expanded) {
            Field("年份", year, { year = it }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (parsedYear == null) Secondary("请输入 1 至 9999 的年份")
            MonthChoices(month.monthValue.takeIf { parsedYear == month.year }, enabled = parsedYear != null) {
                val target = YearMonth.of(checkNotNull(parsedYear), it)
                focus.clearFocus()
                pager.requestScrollToPage(target.calendarPage)
                setExpanded(false)
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                "一二三四五六日".forEach { day ->
                    Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                        Secondary(day.toString())
                    }
                }
            }
            HorizontalPager(pager) { page ->
                CalendarDays(calendarMonth(page), selected, select)
            }
        }
    }
}

@Composable
internal fun MonthChoices(selected: Int?, enabled: Boolean = true, select: (Int) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (first in 1..12 step 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (month in first..first + 2) {
                    Choice("$month 月", { select(month) }, Modifier.weight(1f), selected = month == selected, enabled = enabled)
                }
            }
        }
    }
}

@Composable
private fun CalendarDays(month: YearMonth, selected: LocalDate, select: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val offset = month.atDay(1).dayOfWeek.value - 1
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - offset + 1
                    Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val active = date == selected
                            Surface(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape)
                                    .selectable(active, role = Role.Button) { select(date) }
                                    .semantics { contentDescription = date.toString() },
                                shape = CircleShape,
                                color = if (active) colors.primary else colors.surface,
                                contentColor = if (active) colors.onPrimary else if (date == today) colors.primary else colors.onSurface,
                                border = if (date == today && !active) BorderStroke(1.dp, colors.primary) else null,
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(day.toString(), Modifier.padding(vertical = 6.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
