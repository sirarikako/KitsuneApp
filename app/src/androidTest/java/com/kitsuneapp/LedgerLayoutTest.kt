package com.kitsuneapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsuneapp.data.*
import com.kitsuneapp.feature.ledger.EntryForm
import com.kitsuneapp.feature.ledger.LedgerPage
import com.kitsuneapp.feature.ledger.Calculator
import com.kitsuneapp.ui.DateControl
import com.kitsuneapp.ui.KitsuneTheme
import com.kitsuneapp.ui.TimeControl
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LedgerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun incomeWalletTotalsAndGroupedFiltersUseTheSelectedWalletRange() {
        val now = "2026-11-17T12:00:00Z"
        val source = Wallet("first", "收款钱包", "收款钱包", "CNY", 2, 0, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, now, now, defaultToIncome = true)
        val second = source.copy(id = "second", name = "另一钱包")
        val income = Category("income", Scope.INCOME, null, "", "奖金", "奖金", 0, false, false, now, now)
        val expense = income.copy(id = "expense", scope = Scope.EXPENSE, walletId = second.id, ownerKey = second.id)
        val other = expense.copy(id = "other", name = "日用", normalizedName = "日用")
        fun entry(id: String, wallet: Wallet, type: EntryType, amount: Long, category: Category) =
            FinancialEntry(id, LocalDate.now().toString(), "12:00", type, wallet.id, amount, category.id,
                null, null, null, "", now, now)
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), listOf(income, expense, other), emptyList(), emptyMap(), listOf(source, second),
            listOf(entry("one", source, EntryType.INCOME, 800, income),
                entry("two", second, EntryType.EXPENSE, 200, expense),
                entry("three", second, EntryType.EXPENSE, 100, other)))
        compose.setContent {
            KitsuneTheme(records.theme, forceDark = true) {
                Surface(Modifier.width(320.dp).fillMaxHeight()) { LedgerPage(records, open = {}, openDetails = {}) }
            }
        }
        val month = YearMonth.now().monthValue
        compose.onNodeWithText("$month 月 收入 · CNY").assertIsDisplayed()
        compose.onNodeWithText("8.00").assertIsDisplayed()
        compose.onNodeWithText("净支出 3.00").assertIsDisplayed()

        val inDialog = hasAnyAncestor(isDialog())
        fun openFilters() = compose.onNodeWithText("筛选").performClick()
        fun apply() = compose.onNode(hasText("应用") and inDialog).performClick()
        fun bulk(description: String) = compose.onNodeWithContentDescription(description).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        fun choose(name: String) = compose.onNode(hasText(name) and inDialog).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        openFilters()
        bulk("全不选类型")
        choose("收入")
        choose("支出")
        bulk("全不选分类")
        choose("奖金")
        apply()
        compose.onNodeWithText("2 笔").assertExists()
        compose.onNodeWithText("净支出 3.00").assertIsDisplayed()

        openFilters()
        bulk("全不选分类")
        apply()
        compose.onNodeWithText("0 笔").assertExists()

        openFilters()
        bulk("全选类型")
        bulk("全选分类")
        apply()
        compose.onNodeWithText("3 笔").assertExists()
    }

    @Test
    fun showsExpenseColorAndCompactWalletActionsOnNarrowDarkLayout() {
        val now = "2026-11-17T12:00:00Z"
        val regular = Wallet("regular", "日常钱包", "日常钱包", "CNY", 2, 10_000, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, now, now)
        val separate = regular.copy(id = "separate", name = "旅行钱包", currencyCode = "JPY", currencyScale = 0,
            separateAccounting = true, archived = true)
        val entry = FinancialEntry("expense", LocalDate.now().toString(), "12:00", EntryType.EXPENSE,
            regular.id, 1_250, "expense-category", null, null, null, "", now, now)
        val category = Category("expense-category", Scope.EXPENSE, null, "", "餐饮", "餐饮", 0, false, false, now, now)
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0, defaultWalletId = regular.id),
            ThemePreferences(), emptyList(), listOf(category), emptyList(), emptyMap(), listOf(regular, separate), listOf(entry))
        var detail: String? = null
        var route: String? = null
        compose.setContent {
            KitsuneTheme(records.theme, forceDark = true) {
                Surface(Modifier.width(320.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
                    Box { LedgerPage(records, open = { route = it }, openDetails = { detail = it }) }
                }
            }
        }
        val title = compose.onNodeWithText("餐饮", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val amount = compose.onNodeWithText("−12.50", useUnmergedTree = true).assertIsDisplayed()
        val value = amount.fetchSemanticsNode().boundsInRoot
        val currency = compose.onNodeWithText("CNY", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(value.left > title.right)
        assertTrue(currency.top >= value.bottom)
        val layouts = mutableListOf<TextLayoutResult>()
        amount.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(0xFFE8B276.toInt(), layouts.single().layoutInput.style.color.toArgb())
        compose.onNodeWithText("旅行钱包", substring = true).assertDoesNotExist()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(0xFF271F1A.toInt(), bitmap.getPixel(8, 8))
        File(compose.activity.cacheDir, "kitsune-ledger-narrow-dark.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        compose.onNodeWithText("餐饮").performClick()
        compose.runOnIdle { assertEquals(entry.id, detail) }
        compose.onNodeWithText("钱包").performClick()
        compose.onNodeWithText("我的钱包").assertIsDisplayed()
        val name = compose.onNodeWithText("日常钱包 · CNY").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val balance = compose.onNodeWithText("87.50").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val edit = compose.onNodeWithContentDescription("编辑钱包：日常钱包 · CNY").assertIsDisplayed()
        val categories = compose.onNodeWithContentDescription("管理本钱包分类：日常钱包 · CNY").assertIsDisplayed()
        val editBounds = edit.fetchSemanticsNode().boundsInRoot
        val categoryBounds = categories.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("默认钱包").assertIsDisplayed()
        assertTrue(editBounds.left > name.right && editBounds.bottom <= balance.top)
        assertTrue(categoryBounds.left > name.right && categoryBounds.right <= editBounds.left)
        assertEquals(editBounds.top, categoryBounds.top, 0.1f)
        edit.performClick()
        compose.runOnIdle { assertEquals("wallet/regular", route) }
        categories.performClick()
        compose.runOnIdle { assertEquals("walletCategories/regular", route) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("旅行钱包 · JPY"))
        compose.onNodeWithText("旅行钱包 · JPY").assertIsDisplayed()
        compose.onNodeWithText("独立核算").assertIsDisplayed()
        compose.onNodeWithText("已归档").assertIsDisplayed()
        saveScreenshot("kitsune-wallets-narrow-dark")
    }

    @Test
    fun walletRangeDialogAppliesModesAndKeepsIndependentWalletsOptIn() {
        val now = "2026-11-17T12:00:00Z"
        val regular = Wallet("regular", "日常钱包", "日常钱包", "CNY", 2, 0, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, now, now)
        val separate = regular.copy(id = "separate", name = "省钱钱包", currencyCode = "JPY", currencyScale = 0,
            separateAccounting = true)
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), emptyList(), emptyList(), emptyMap(), listOf(regular, separate), emptyList())
        compose.setContent { KitsuneTheme(records.theme, forceDark = false) { LedgerPage(records, {}, {}) } }

        fun inDialog(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isDialog()))
        val range = compose.onNodeWithContentDescription("钱包范围")
        val daily = inDialog("日常钱包 · CNY")
        val savings = inDialog("省钱钱包 · JPY")
        val apply = inDialog("应用")
        val manual = inDialog("手动选择")
        val all = inDialog("全部钱包")
        compose.onNodeWithText("统计").performClick()
        range.performClick()
        inDialog("常规钱包").assertIsSelected()
        daily.assertDoesNotExist()
        savings.assertDoesNotExist()
        saveScreenshot("kitsune-wallet-range-regular")

        manual.performClick().assertIsSelected()
        daily.assertIsOn()
        savings.assertIsOff()
        saveScreenshot("kitsune-wallet-range-manual")
        daily.performClick().assertIsOff()
        apply.assertIsNotEnabled()
        savings.performClick().assertIsOn()
        all.performClick().assertIsSelected()
        savings.assertDoesNotExist()
        manual.performClick()
        daily.assertIsOff()
        savings.assertIsOn()
        apply.performClick()
        compose.onNodeWithText("本月收支 · JPY").assertIsDisplayed()

        range.performClick()
        savings.assertIsOn()
        all.performClick()
        inDialog("取消").performClick()
        compose.onNodeWithText("本月收支 · CNY").assertDoesNotExist()
        range.performClick()
        all.performClick()
        apply.performClick()
        compose.onNodeWithText("本月收支 · CNY").assertIsDisplayed()

        range.performClick()
        all.assertIsSelected()
        manual.performClick()
        daily.assertIsOn()
        savings.assertIsOff()
        inDialog("恢复默认").performClick()
        compose.onNodeWithText("常规钱包").assertIsDisplayed()
        compose.onNodeWithText("本月收支 · JPY").assertDoesNotExist()
    }

    @Test
    fun sharesMonthlyAndCustomPeriodsBetweenLedgerTabs() {
        val current = YearMonth.now()
        val previous = current.minusMonths(1)
        val from = previous.atEndOfMonth()
        val to = current.atDay(1)
        val timestamp = "2026-11-17T12:00:00Z"
        val wallet = Wallet("regular", "日常钱包", "日常钱包", "CNY", 2, 0, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, timestamp, timestamp)
        val category = Category("food", Scope.EXPENSE, null, "", "餐饮", "餐饮", 0, false, false, timestamp, timestamp)
        val entries = listOf(from.minusDays(1) to 300L, from to 700L, to to 1_200L).map { (date, amount) ->
            FinancialEntry(date.toString(), date.toString(), "12:00", EntryType.EXPENSE,
                wallet.id, amount, category.id, null, null, null, "", timestamp, timestamp)
        }
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), listOf(category), emptyList(), emptyMap(), listOf(wallet), entries)
        compose.setContent { KitsuneTheme(records.theme, forceDark = false) { LedgerPage(records, {}, {}) } }

        fun monthLabel(month: YearMonth) = "${month.year}年${month.monthValue}月"
        fun inDialog(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isDialog()))
        val picker = compose.onNodeWithContentDescription("选择日期范围")
        val monthly = inDialog("按月")
        val custom = inDialog("自定义日期")
        val apply = inDialog("应用")

        compose.onNodeWithText(monthLabel(current)).assertIsDisplayed()
        val walletPicker = compose.onNodeWithContentDescription("钱包范围")
        assertTrue(picker.fetchSemanticsNode().boundsInRoot.right <= walletPicker.fetchSemanticsNode().boundsInRoot.left)

        picker.performClick()
        monthly.assertIsSelected()
        compose.onNodeWithContentDescription("上个月").performScrollTo().performClick()
        inDialog("${previous.monthValue} 月").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("下个月").performScrollTo().performClick()
        inDialog("${current.monthValue} 月").performScrollTo().assertIsSelected()
        val year = compose.onNodeWithContentDescription("年份")
        year.performTextReplacement("0")
        apply.assertIsNotEnabled()
        year.performTextReplacement(previous.year.toString())
        inDialog("${previous.monthValue} 月").performScrollTo().performClick().assertIsSelected()
        apply.performClick()
        compose.onNodeWithText(monthLabel(previous)).assertIsDisplayed()
        compose.onNodeWithText("10.00").assertIsDisplayed()

        picker.performClick()
        custom.performClick().assertIsSelected()
        apply.assertIsNotEnabled()
        pickLedgerDate("开始日期", from)
        pickLedgerDate("结束日期", from.minusDays(1))
        inDialog("开始日期不能晚于结束日期").assertIsDisplayed()
        monthly.performClick()
        custom.performClick()
        inDialog("开始日期不能晚于结束日期").assertIsDisplayed()
        apply.assertIsNotEnabled()
        pickLedgerDate("结束日期", to)
        apply.performClick()

        val range = compose.onNodeWithText("${from.toString().replace('-', '/')} ～ ${to.toString().replace('-', '/')}")
        range.assertIsDisplayed()
        assertTrue(picker.fetchSemanticsNode().boundsInRoot.bottom <= walletPicker.fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText("19.00").assertIsDisplayed()
        compose.onNodeWithText("统计").performClick()
        range.assertIsDisplayed()
        compose.onNodeWithText("所选期间收支 · CNY").assertIsDisplayed()
        compose.onAllNodesWithText("19.00")[0].assertIsDisplayed()

        picker.performClick()
        custom.assertIsSelected()
        monthly.performClick()
        inDialog("${previous.monthValue} 月").assertIsSelected()
        inDialog("取消").performClick()
        range.assertIsDisplayed()
        picker.performClick()
        monthly.performClick()
        apply.performClick()
        compose.onNodeWithText(monthLabel(previous)).assertIsDisplayed()
        compose.onNodeWithText("流水").performClick()
        compose.onNodeWithText(monthLabel(previous)).assertIsDisplayed()
    }

    private fun pickLedgerDate(label: String, date: LocalDate) {
        compose.onNodeWithContentDescription(label).performClick()
        val inputMode = compose.onNodeWithText("输入日期")
        if (inputMode.isDisplayed()) inputMode.performClick()
        compose.onNodeWithContentDescription("日期（YYYY-MM-DD）").performTextReplacement(date.toString())
        compose.onNodeWithText("确定").performClick()
    }

    @Test
    fun jumpsDirectlyToYearAndMonthAndCommitsOnlyConfirmedDates() {
        var date by mutableStateOf("2026-11-17")
        compose.setContent { KitsuneTheme(ThemePreferences(), forceDark = true) { DateControl("日期", date, { date = it }) } }
        val trigger = compose.onNodeWithContentDescription("日期")
        val jump = compose.onNodeWithContentDescription("选择年月")
        val year = compose.onNodeWithContentDescription("年份")
        val february = compose.onNodeWithText("2 月", substring = false)
        val confirm = compose.onNodeWithText("确定")
        val leapDay = compose.onNodeWithContentDescription("2024-02-29")
        trigger.performClick()
        jump.performClick()
        year.performTextReplacement("0")
        february.assertIsNotEnabled()
        confirm.assertIsNotEnabled()
        year.performTextReplacement("2024")
        saveScreenshot("kitsune-calendar-year-month")
        february.performScrollTo().performClick()
        compose.onNodeWithText("2024 年 2 月").assertIsDisplayed()
        compose.onNodeWithContentDescription("下个月").performClick()
        compose.onNodeWithText("2024 年 3 月").assertIsDisplayed()
        compose.onNodeWithContentDescription("上个月").performClick()
        leapDay.performScrollTo().performClick().assertIsSelected()
        saveScreenshot("kitsune-calendar-leap-day")
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals("2026-11-17", date) }

        trigger.performClick()
        jump.performClick()
        year.performTextReplacement("2024")
        february.performScrollTo().performClick()
        leapDay.performScrollTo().performClick()
        confirm.performClick()
        compose.runOnIdle { assertEquals("2024-02-29", date) }
    }

    @Test
    fun replacesTheExistingTimeWithFourDigitsAndSupportsTheClock() {
        var time by mutableStateOf("09:41")
        compose.setContent { KitsuneTheme(ThemePreferences(), forceDark = true) { TimeControl(time, { time = it }) } }
        val trigger = compose.onNodeWithContentDescription("时间")
        val hour = compose.onNodeWithContentDescription("小时")
        val minute = compose.onNodeWithContentDescription("分钟")
        val confirm = compose.onNodeWithText("确定")
        fun waitForFocus(label: String) =
            compose.waitUntilExactlyOneExists(hasContentDescription(label) and isFocused(), 10_000)
        fun openInput() {
            trigger.performClick()
            waitForFocus("小时")
        }
        openInput()
        hour.assertIsFocused().assertTextEquals("09")
        assertEquals(TextRange(0, 2), hour.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        saveScreenshot("kitsune-time-input")
        hour.performTextInput("2")
        confirm.assertIsNotEnabled()
        hour.performTextInput("4")
        hour.assertIsFocused().assertTextEquals("24")
        confirm.assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals("09:41", time) }

        openInput()
        hour.performTextInput("0")
        hour.performTextInput("0")
        waitForFocus("分钟")
        minute.assertIsFocused().assertTextEquals("41")
        assertEquals(TextRange(0, 2), minute.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        minute.performTextInput("0")
        minute.performTextInput("0")
        confirm.performClick()
        compose.runOnIdle { assertEquals("00:00", time) }

        openInput()
        hour.performTextInput("2")
        hour.performTextInput("3")
        waitForFocus("分钟")
        minute.performTextInput("6")
        minute.performTextInput("0")
        confirm.assertIsNotEnabled()
        minute.performTextReplacement("59")
        compose.onNodeWithContentDescription("使用时钟").performClick()
        saveScreenshot("kitsune-time-clock")
        compose.onNodeWithContentDescription("输入时间").performClick()
        waitForFocus("小时")
        hour.assertIsFocused().assertTextEquals("23")
        minute.assertTextEquals("59")
        confirm.performClick()
        compose.runOnIdle { assertEquals("23:59", time) }

        openInput()
        compose.onNodeWithContentDescription("使用时钟").performClick()
        val hourOnDial = compose.activity.getString(androidx.compose.material3.R.string.m3c_time_picker_hour_24h_suffix, 17)
        val minuteOnDial = compose.activity.getString(androidx.compose.material3.R.string.m3c_time_picker_minute_suffix, 15)
        compose.onNodeWithContentDescription(hourOnDial).performScrollTo().performTouchInput { click() }
        compose.waitUntilAtLeastOneExists(hasText("17"), 5_000)
        compose.onNodeWithText("59").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription(minuteOnDial), 5_000)
        compose.onNodeWithContentDescription(minuteOnDial).performScrollTo().performTouchInput { click() }
        compose.waitUntilAtLeastOneExists(hasText("15"), 5_000)
        confirm.performClick()
        compose.runOnIdle { assertEquals("17:15", time) }
    }

    @Test
    fun rendersCategorySharesByDirectionAndCurrencyWithRefunds() {
        val month = YearMonth.now()
        val date = month.atDay(1).toString()
        val earlier = month.minusMonths(1).atEndOfMonth().toString()
        val now = "2026-11-17T12:00:00Z"
        val daily = Wallet("daily", "日常钱包", "日常钱包", "CNY", 2, 0, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, now, now)
        val travel = daily.copy(id = "travel", name = "旅行钱包", currencyCode = "JPY", currencyScale = 0)
        val categories = listOf("food" to "食材", "refund" to "交通", "zero" to "日用", "salary" to "工资", "tickets" to "门票")
            .map { (id, name) -> Category(id, if (id == "salary") Scope.INCOME else Scope.EXPENSE, null, "",
                name, name, 0, false, false, now, now) }
        fun entry(id: String, amount: Long, category: String?, type: EntryType = EntryType.EXPENSE,
                  day: String = date, wallet: Wallet = daily, original: String? = null) =
            FinancialEntry(id, day, "12:00", type, wallet.id, amount, category, null, null, original, "", now, now)
        val entries = listOf(
            entry("food", 700, "food"),
            entry("old-ride", 300, "refund", day = earlier),
            entry("ride-refund", 300, null, EntryType.REFUND, original = "old-ride"),
            entry("purchase", 200, "zero"),
            entry("purchase-refund", 200, null, EntryType.REFUND, original = "purchase"),
            entry("salary", 1_200, "salary", EntryType.INCOME),
            entry("tickets", 900, "tickets", wallet = travel),
        )
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), categories, emptyList(), emptyMap(), listOf(daily, travel), entries)
        compose.setContent { KitsuneTheme(records.theme) { LedgerPage(records, {}, {}) } }
        compose.onNodeWithText("统计").performClick()
        val list = compose.onNode(hasScrollToNodeAction())
        fun assertShare(label: String, ratio: Float) {
            val bar = hasContentDescription("${label}金额占比")
            list.performScrollToNode(bar)
            val progress = compose.onNode(bar).assertIsDisplayed().fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(ratio, progress.current, 0.0001f)
        }

        assertShare("工资", 1f)
        assertShare("食材", 0.7f)
        assertShare("交通", 0.3f)
        compose.onNodeWithText("-3.00").assertExists()
        compose.onNodeWithText("条长按绝对值占比").assertExists()
        compose.onNodeWithText("日用").assertExists()
        compose.onNodeWithContentDescription("日用金额占比").assertDoesNotExist()
        assertShare("门票", 1f)
        compose.onNodeWithText("净支出分类 · JPY").assertExists()
    }

    @Test
    fun ledgerHeaderTracksScrollAndFabStaysCompactUntilTheTop() {
        val now = "2026-11-17T12:00:00Z"
        val wallet = Wallet("regular", "日常钱包", "日常钱包", "CNY", 2, 0, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, now, now)
        val category = Category("food", Scope.EXPENSE, null, "", "餐饮", "餐饮", 0, false, false, now, now)
        val entries = (28 downTo 1).map { index ->
            FinancialEntry("entry-$index", YearMonth.now().atDay(index).toString(), "12:00", EntryType.EXPENSE,
                wallet.id, 100, category.id, null, null, null, "第 $index 笔", now, now)
        }
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), listOf(category), emptyList(), emptyMap(), listOf(wallet), entries)
        var route: String? = null
        compose.setContent {
            KitsuneTheme(records.theme) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                    Box(Modifier.width(320.dp).height(630.dp)) { LedgerPage(records, { route = it }, {}) }
                }
            }
        }

        val title = compose.onNodeWithText("记账")
        val tabs = compose.onNodeWithText("流水")
        val date = compose.onNodeWithContentDescription("选择日期范围")
        val fab = compose.onNodeWithContentDescription("记一笔")
        val list = compose.onNode(hasScrollToNodeAction())
        fun fabWidth() = fab.fetchSemanticsNode().boundsInRoot.width
        fun listPosition() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun drag(from: Float, to: Float) = list.performTouchInput {
            swipe(Offset(width * 0.2f, height * from), Offset(width * 0.2f, height * to), durationMillis = 900)
        }

        title.assertIsDisplayed()
        date.assertIsDisplayed()
        val expandedWidth = fabWidth()
        repeat(3) { drag(0.90f, 0.10f) }
        title.assertIsNotDisplayed()
        date.assertIsNotDisplayed()
        tabs.assertIsDisplayed()
        val compactWidth = fabWidth()
        assertTrue(compactWidth < expandedWidth)
        assertTrue(listPosition() > 0f)
        drag(0.10f, 0.55f)
        title.assertIsDisplayed()
        date.assertIsDisplayed()
        assertTrue(listPosition() > 0f)
        assertEquals(compactWidth, fabWidth())
        fab.performClick()
        compose.runOnIdle { assertEquals("entryForm/-", route) }

        val lastEntry = hasText("第 1 笔", substring = true)
        list.performScrollToNode(lastEntry)
        // Scroll to the end so the last record can come fully above the overlay.
        drag(0.85f, 0.15f)
        val lastAtEnd = compose.onNode(lastEntry).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(lastAtEnd.bottom < fab.fetchSemanticsNode().boundsInRoot.top)
        list.performScrollToIndex(0)
        assertEquals(0f, listPosition(), 0f)
        title.assertIsDisplayed()
        assertEquals(expandedWidth, fabWidth())
        compose.onNodeWithText("钱包").performClick()
        fab.assertDoesNotExist()
        compose.onNodeWithText("新建钱包").assertIsDisplayed()
        compose.onNodeWithText("统计").performClick()
        fab.assertDoesNotExist()
        date.assertIsDisplayed()
        tabs.performClick()
        assertEquals(expandedWidth, fabWidth())
    }

    @Test
    fun calculatorUsesKeypadAndRestoresExpressionBeforeApplying() {
        var amount: String? = null
        var dismissed = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            KitsuneTheme(ThemePreferences(), forceDark = false) {
                Calculator(2, "CNY", "12.50", dismiss = { dismissed = true }, apply = { amount = it })
            }
        }
        val expression = compose.onNodeWithContentDescription("计算表达式")
        val clear = compose.onNodeWithContentDescription("清空")
        val apply = compose.onNodeWithText("使用此金额")
        fun press(vararg keys: String) {
            keys.forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        }

        compose.onNodeWithText("12.50").assertIsDisplayed()
        assertTrue(!expression.fetchSemanticsNode().config.contains(SemanticsActions.SetText))
        clear.performClick()
        press("1", "÷", "0", "=")
        compose.onNodeWithText("除数不能为零").assertIsDisplayed()
        compose.onNodeWithContentDescription("退格").performScrollTo().performClick()
        press("3", "×", "1", "0", "0")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("1÷3×100").performScrollTo().assertIsDisplayed()
        press("=")
        compose.waitUntil(5_000) { compose.onNodeWithText("33.33").isDisplayed() }
        apply.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("33.33", amount) }
        saveScreenshot("kitsune-calculator")

        clear.performScrollTo().performClick()
        press("2", "×", "2", "5")
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertEquals("33.33", amount)
        }
    }

    @Test
    fun transferFormRestoresDraftAndUsesTheSelectedCurrencyForEachAmount() {
        val timestamp = "2026-11-17T12:00:00Z"
        val source = Wallet("source", "日常钱包", "日常钱包", "CNY", 2, 0, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, timestamp, timestamp)
        val target = source.copy(id = "target", name = "旅行钱包", currencyCode = "JPY", currencyScale = 0)
        val entry = FinancialEntry("transfer", LocalDate.now().toString(), "12:00", EntryType.TRANSFER,
            source.id, 1_250, null, target.id, 300, null, "原备注", timestamp, timestamp)
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), emptyList(), emptyList(), emptyMap(), listOf(source, target), listOf(entry))
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java] }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            KitsuneTheme(records.theme) { EntryForm(records, entry.id, null, model, back = {}, finished = {}) }
        }

        val sourceAmount = hasContentDescription("转出金额 · CNY")
        val targetAmount = hasContentDescription("到账金额 · JPY")
        val expression = hasContentDescription("计算表达式")
        val note = compose.onNodeWithContentDescription("备注")
        val apply = compose.onNodeWithText("使用此金额")
        note.performScrollTo().performTextReplacement("未保存备注")
        val initial = compose.onNode(sourceAmount and hasText("12.50"))
        assertTrue(!initial.fetchSemanticsNode().config.contains(SemanticsActions.SetText))
        initial.performScrollTo().performClick()
        compose.onNode(expression and hasText("12.50")).assertIsDisplayed()
        compose.onNodeWithText("+").performScrollTo().performClick()
        compose.onNodeWithText("1").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(expression and hasText("12.50+1")).performScrollTo().assertIsDisplayed()
        apply.performClick()
        compose.onNode(sourceAmount and hasText("13.50")).assertExists()
        note.assertTextEquals("未保存备注")

        compose.onNode(targetAmount and hasText("300")).performScrollTo().performClick()
        compose.onNodeWithText("JPY · 支持加减乘除").assertIsDisplayed()
        compose.onNode(expression and hasText("300")).assertIsDisplayed()
        compose.onNodeWithContentDescription("清空").performScrollTo().performClick()
        listOf("2", ".", "5").forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        apply.performClick()
        compose.onNode(targetAmount and hasText("3")).assertExists()
        compose.onNode(sourceAmount and hasText("13.50")).assertExists()
    }

    private fun saveScreenshot(name: String) {
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
