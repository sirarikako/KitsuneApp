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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.kitsuneapp.data.*
import com.kitsuneapp.feature.ledger.EntryForm
import com.kitsuneapp.feature.ledger.LedgerPage
import com.kitsuneapp.feature.ledger.Calculator
import com.kitsuneapp.ui.DateControl
import com.kitsuneapp.ui.KitsuneTheme
import com.kitsuneapp.ui.TimeControl
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LedgerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun incomeWalletTotalsAndGroupedFiltersUseTheSelectedWalletRange() {
        val today = LocalDate.now()
        val now = "${today}T12:00:00Z"
        val source = Wallet("first", "收款钱包", "收款钱包", "CNY", 2, 0, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, now, now, defaultToIncome = true)
        val second = source.copy(id = "second", name = "另一钱包", normalizedName = "另一钱包")
        val separate = source.copy(id = "separate", name = "独立钱包", normalizedName = "独立钱包",
            separateAccounting = true)
        val income = Category("income", Scope.INCOME, null, "", "奖金", "奖金", 0, false, false, now, now)
        val expense = income.copy(id = "expense", scope = Scope.EXPENSE, walletId = second.id, ownerKey = second.id)
        val other = expense.copy(id = "other", name = "日用", normalizedName = "日用")
        val outsideRange = expense.copy(id = "outside", walletId = separate.id, ownerKey = separate.id,
            name = "范围外分类", normalizedName = "范围外分类")
        fun entry(id: String, wallet: Wallet, type: EntryType, amount: Long, category: Category) =
            FinancialEntry(id, today.toString(), "12:00", type, wallet.id, amount, category.id,
                null, null, null, if (id == "one") "独有备注" else "", now, now)
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), listOf(income, expense, other, outsideRange), emptyList(), emptyMap(),
            listOf(source, second, separate),
            listOf(entry("one", source, EntryType.INCOME, 800, income),
                entry("two", second, EntryType.EXPENSE, 200, expense),
                entry("three", second, EntryType.EXPENSE, 100, other),
                entry("four", separate, EntryType.EXPENSE, 500, outsideRange)))
        compose.setContent {
            KitsuneTheme(records.theme, forceDark = true) {
                Surface(Modifier.width(320.dp).fillMaxHeight()) { LedgerPage(records, open = {}, openDetails = {}) }
            }
        }
        val month = today.monthValue
        compose.onNodeWithText("$month 月 收入 · CNY").assertIsDisplayed()
        compose.onNodeWithText("8.00").assertIsDisplayed()
        compose.onNodeWithText("净支出 3.00").assertIsDisplayed()

        val inDialog = hasAnyAncestor(isDialog())
        val filterTitle = hasText("流水筛选") and inDialog
        val bodyMatcher = hasScrollToNodeAction() and inDialog
        val body = compose.onNode(bodyMatcher)
        val applyButton = compose.onNode(hasText("应用") and inDialog)
        fun openFilters() {
            compose.onNode(hasText("筛选") and !inDialog).assertIsDisplayed().performClick()
            compose.waitUntil("ledger filter sheet and footer visible", 10_000) {
                body.isDisplayed() && applyButton.isDisplayed()
            }
        }
        fun assertEntryCount(count: Int) {
            compose.waitUntilDoesNotExist(filterTitle, 10_000)
            val displayedCount = hasText("$count 笔") and !inDialog
            compose.waitUntilExactlyOneExists(displayedCount, 10_000)
            compose.onNode(displayedCount).assertExists()
        }
        fun bulk(description: String) {
            body.performScrollToNode(hasContentDescription(description))
            compose.onNode(hasContentDescription(description) and inDialog)
                .performSemanticsAction(SemanticsActions.OnClick)
        }
        fun choose(name: String) {
            body.performScrollToNode(hasText(name))
            compose.onNode(hasText(name) and inDialog).performSemanticsAction(SemanticsActions.OnClick)
        }
        openFilters()
        bulk("全不选类型")
        EntryType.entries.forEach { type -> compose.onNode(hasText(type.label) and inDialog).assertIsNotSelected() }
        choose("收入")
        choose("支出")
        bulk("全不选分类")
        compose.onNode(hasText("0/2") and inDialog).assertExists()
        compose.onNode(hasText("范围外分类") and inDialog).assertDoesNotExist()
        choose("奖金")
        compose.onNode(hasText("1/2") and inDialog).assertExists()
        applyButton.performSemanticsAction(SemanticsActions.OnClick)
        assertEntryCount(2)
        compose.onNodeWithText("净支出 3.00").assertIsDisplayed()

        openFilters()
        bulk("全不选分类")
        applyButton.performSemanticsAction(SemanticsActions.OnClick)
        assertEntryCount(0)

        openFilters()
        bulk("全选类型")
        bulk("全选分类")
        applyButton.performSemanticsAction(SemanticsActions.OnClick)
        assertEntryCount(3)

        openFilters()
        val note = hasContentDescription("备注关键词") and inDialog
        body.performScrollToNode(note)
        compose.onNode(note).performTextReplacement("独有备注")
        applyButton.assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        assertEntryCount(1)
        openFilters()
        bulk("全不选类型")
        compose.onNode(hasContentDescription("关闭") and inDialog).performSemanticsAction(SemanticsActions.OnClick)
        assertEntryCount(1)
    }

    @Test
    fun ledgerRowsRefreshTheirCategoryGlyphsById() {
        val now = "2026-11-17T12:00:00Z"
        val date = LocalDate.now().toString()
        val wallet = Wallet("regular", "日常钱包", "日常钱包", "CNY", 2, 0, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, now, now)
        val other = wallet.copy(id = "other", name = "出行钱包")
        val shared = Category("shared", Scope.EXPENSE, null, "", "交通", "交通", 0,
            false, false, now, now, CategoryIcon.TRANSPORT)
        val custom = shared.copy(id = "custom", walletId = other.id, ownerKey = other.id,
            archived = true, icon = CategoryIcon.GIFT)
        val expense = FinancialEntry("expense", date, "12:02", EntryType.EXPENSE, other.id,
            500, custom.id, null, null, null, "专属支出", now, now)
        val refund = FinancialEntry("refund", date, "12:03", EntryType.REFUND, wallet.id,
            200, null, null, null, expense.id, "关联退款", now, now)
        val unrelated = expense.copy(id = "unrelated", time = "12:01", walletId = wallet.id,
            categoryId = shared.id, note = "同名通用支出")
        var records by mutableStateOf(Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), listOf(shared, custom), emptyList(), emptyMap(),
            listOf(wallet, other), listOf(expense, refund, unrelated)))
        compose.setContent { KitsuneTheme(records.theme, forceDark = false) { LedgerPage(records, {}, {}) } }

        fun rowImage(note: String): Bitmap {
            val row = hasText(note, substring = true)
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(row)
            return compose.onNode(row).assertIsDisplayed().captureToImage().asAndroidBitmap()
        }

        val expenseBefore = rowImage(expense.note)
        val refundBefore = rowImage(refund.note)
        val sharedBefore = rowImage(unrelated.note)
        compose.runOnIdle { records = records.copy(categories = listOf(shared, custom.copy(icon = CategoryIcon.HOME))) }
        assertFalse("The historical expense uses its updated category glyph", expenseBefore.sameAs(rowImage(expense.note)))
        assertFalse("The refund uses the original expense's updated category glyph", refundBefore.sameAs(rowImage(refund.note)))
        assertTrue("The separate category with the same name keeps its glyph", sharedBefore.sameAs(rowImage(unrelated.note)))
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
        val category = Category("expense-category", Scope.EXPENSE, null, "", "餐饮", "餐饮", 0,
            false, false, now, now, CategoryIcon.FOOD)
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
    }

    @Test
    fun walletRangeDialogAppliesModesAndKeepsIndependentWalletsOptIn() {
        val now = "2026-11-17T12:00:00Z"
        val regular = Wallet("regular", "日常钱包", "日常钱包", "CNY", 2, 0, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, now, now)
        val separate = regular.copy(id = "separate", name = "省钱钱包", currencyCode = "JPY", currencyScale = 0,
            separateAccounting = true)
        val archived = regular.copy(id = "archived", name = "旧钱包", archived = true)
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), emptyList(), emptyList(), emptyMap(), listOf(regular, separate, archived), emptyList())
        compose.setContent { KitsuneTheme(records.theme, forceDark = false) { LedgerPage(records, {}, {}) } }

        fun inDialog(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isDialog()))
        val range = compose.onNodeWithContentDescription("钱包范围")
        val daily = compose.onNode(hasText("日常钱包") and hasText("CNY") and hasAnyAncestor(isDialog()))
        val savings = compose.onNode(hasText("省钱钱包") and hasText("JPY · 独立核算") and hasAnyAncestor(isDialog()))
        val history = compose.onNode(hasText("旧钱包") and hasText("CNY · 已归档") and hasAnyAncestor(isDialog()))
        val apply = inDialog("应用")
        val manual = inDialog("手动选择")
        val all = inDialog("全部钱包")
        compose.onNodeWithText("统计").performClick()
        range.performClick()
        val regularMode = inDialog("常规钱包").assertIsSelected()
        val modes = listOf(regularMode, all, manual).map { it.fetchSemanticsNode().boundsInRoot }
        assertTrue(modes.zipWithNext().all { (first, next) -> first.center.x < next.center.x && first.top == next.top })
        assertTrue(modes.zipWithNext().all { (first, next) -> kotlin.math.abs(first.width - next.width) <= 1f })
        daily.assertDoesNotExist()
        savings.assertDoesNotExist()

        manual.performClick().assertIsSelected()
        inDialog("选择钱包").assertIsDisplayed()
        inDialog("已选 2/3").assertIsDisplayed()
        daily.assertIsOn()
        savings.assertIsOff()
        history.assertIsOn()
        daily.performClick().assertIsOff()
        history.performClick().assertIsOff()
        inDialog("已选 0/3").assertIsDisplayed()
        apply.assertIsNotEnabled()
        savings.performClick().assertIsOn()
        inDialog("已选 1/3").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("关闭").performClick()
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
        inDialog("常规钱包").assertIsSelected()
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithText("本月收支 · JPY").assertIsDisplayed()
        range.performClick()
        all.assertIsSelected()
        inDialog("恢复默认").performClick()
        apply.performClick()
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
        february.performScrollTo().performClick()
        compose.onNodeWithText("2024 年 2 月").assertIsDisplayed()
        compose.onNodeWithContentDescription("下个月").performClick()
        compose.onNodeWithText("2024 年 3 月").assertIsDisplayed()
        compose.onNodeWithContentDescription("上个月").performClick()
        leapDay.performScrollTo().performClick().assertIsSelected()
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
        val dialTopLabel = compose.activity.getString(androidx.compose.material3.R.string.m3c_time_picker_hour_24h_suffix, 0)
        val dialTop = compose.onNodeWithContentDescription(dialTopLabel)
        compose.waitUntil(10_000) { dialTop.isDisplayed() }
        val dialogCenter = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot.center.x
        val hourCenter = compose.onNodeWithText("23").fetchSemanticsNode().boundsInRoot.center.x
        val minuteCenter = compose.onNodeWithText("59").fetchSemanticsNode().boundsInRoot.center.x
        assertEquals("The dial is centered in the dialog", dialogCenter, dialTop.fetchSemanticsNode().boundsInRoot.center.x, 1f)
        assertEquals("The time display is centered in the dialog", dialogCenter, (hourCenter + minuteCenter) / 2, 1f)
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
    fun verticalDragStartingOnLedgerFabScrollsListWithoutTriggeringClick() {
        val now = "2026-11-17T12:00:00Z"
        val wallet = Wallet("regular", "日常钱包", "日常钱包", "CNY", 2, 0, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, now, now)
        val category = Category("food", Scope.EXPENSE, null, "", "餐饮", "餐饮", 0, false, false, now, now)
        val entries = (50 downTo 1).map { index ->
            val day = ((index - 1) % 28) + 1
            val hour = 10 + (index - 1) / 28
            FinancialEntry("entry-$index", YearMonth.now().atDay(day).toString(), "%02d:00".format(hour),
                EntryType.EXPENSE, wallet.id, 100, category.id, null, null, null, "第 $index 笔", now, now)
        }
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), listOf(category), emptyList(), emptyMap(), listOf(wallet), entries)
        var route: String? = null
        var detail: String? = null
        compose.setContent {
            KitsuneTheme(records.theme) {
                Box(Modifier.width(360.dp).height(640.dp)) {
                    LedgerPage(records, { route = it }, { detail = it })
                }
            }
        }

        val title = compose.onNodeWithText("记账")
        val fab = compose.onNodeWithContentDescription("记一笔")
        val list = compose.onNode(hasScrollToNodeAction())
        fun fabBounds() = fab.fetchSemanticsNode().boundsInRoot
        fun listPosition() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

        val expandedBounds = fabBounds()
        val expandedWidth = expandedBounds.width
        assertEquals(0f, listPosition(), 0f)

        // 1. Real touch click on expanded FAB opens new entry without leaking to underlying rows.
        compose.onRoot().performTouchInput { click(expandedBounds.center) }
        compose.runOnIdle {
            assertEquals("entryForm/-", route)
            assertEquals(null, detail)
        }
        route = null

        // 2. Fast vertical swipe starting on the left half of the expanded FAB continues with inertial fling after release.
        val positionAtRelease: Float
        val positionAfterFling: Float
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performTouchInput {
                val startX = expandedBounds.left + expandedBounds.width * 0.25f
                val step = 45.dp.toPx()
                down(Offset(startX, expandedBounds.center.y))
                repeat(5) {
                    advanceEventTime(12L)
                    moveBy(Offset(0f, -step))
                }
            }
            compose.mainClock.advanceTimeByFrame()
            positionAtRelease = listPosition()
            assertTrue("Dragging upward from the expanded FAB should scroll the list", positionAtRelease > 0f)
            compose.onRoot().performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1_000L)
            compose.mainClock.advanceTimeByFrame()
            positionAfterFling = listPosition()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertTrue(
            "Fast swipe release on the FAB should continue scrolling with inertial fling",
            positionAfterFling > positionAtRelease,
        )
        val compactBounds = fabBounds()
        assertTrue("Scrolling from the FAB should collapse the FAB to its compact width", compactBounds.width < expandedWidth)
        compose.runOnIdle {
            assertEquals(null, route)
            assertEquals(null, detail)
        }

        // 3. Fast drag followed by pausing before release must not produce extra inertial scrolling.
        val positionAfterPause: Float
        val positionAfterPausedRelease: Float
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performTouchInput {
                val step = 45.dp.toPx()
                down(compactBounds.center)
                repeat(5) {
                    advanceEventTime(12L)
                    moveBy(Offset(0f, -step))
                }
            }
            compose.mainClock.advanceTimeBy(200L)
            compose.mainClock.advanceTimeByFrame()
            positionAfterPause = listPosition()
            assertTrue("Dragging from the compact FAB should scroll further", positionAfterPause > positionAfterFling)
            compose.onRoot().performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1_000L)
            compose.mainClock.advanceTimeByFrame()
            positionAfterPausedRelease = listPosition()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertEquals(
            "Pausing before lifting the finger on the FAB should not trigger extra fling scrolling",
            positionAfterPause,
            positionAfterPausedRelease,
            0f,
        )
        title.assertIsNotDisplayed()
        compose.runOnIdle {
            assertEquals(null, route)
            assertEquals(null, detail)
        }

        // 4. Pulling downward from the compact FAB re-expands the collapsed header without clicking the FAB.
        val compactBeforeReverse = fabBounds()
        compose.onRoot().performTouchInput {
            swipe(
                start = Offset(compactBeforeReverse.center.x, compactBeforeReverse.top + 6.dp.toPx()),
                end = Offset(compactBeforeReverse.center.x, height - 2f),
                durationMillis = 300,
            )
        }
        compose.waitForIdle()
        title.assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(null, route)
            assertEquals(null, detail)
        }

        // 5. Real touch click on compact FAB still opens the new entry form without leaking to underlying rows.
        compose.onRoot().performTouchInput { click(fabBounds().center) }
        compose.runOnIdle {
            assertEquals("entryForm/-", route)
            assertEquals(null, detail)
        }
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
        val result = compose.onNodeWithContentDescription("计算结果")
        val backspace = compose.onNodeWithContentDescription("退格")
        val clear = compose.onNodeWithContentDescription("清空")
        val apply = compose.onNodeWithText("确认金额")
        fun press(vararg keys: String) {
            keys.forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        }

        result.assertIsDisplayed().assertTextEquals("12.50")
        assertFalse(result.fetchSemanticsNode().config.contains(SemanticsActions.SetText))
        expression.assertDoesNotExist()
        clear.performClick()
        press("1", "÷")
        result.assertDoesNotExist()
        compose.onNodeWithText("表达式缺少有效数字").assertDoesNotExist()
        press("0", "=")
        compose.onNodeWithText("除数不能为零").assertIsDisplayed()
        backspace.performScrollTo().performClick()
        press("3", "×", "1", "0", "0")
        restoration.emulateSavedInstanceStateRestore()
        expression.performScrollTo().assertTextEquals("1÷3×100")
        result.assertIsDisplayed().assertTextEquals("33.33")

        press("=")
        restoration.emulateSavedInstanceStateRestore()
        expression.assertTextEquals("1÷3×100")
        result.assertTextEquals("33.33")
        backspace.performScrollTo().performClick()
        expression.assertTextEquals("1÷3×10")
        result.assertTextEquals("3.33")
        press("0", "=", "+", "2")
        expression.assertTextEquals("33.33+2")
        result.assertTextEquals("35.33")
        apply.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("35.33", amount) }

        press("=", "9")
        expression.assertDoesNotExist()
        result.assertTextEquals("9")
        press("8", "7", "6", "5", "4", "3")
        backspace.performScrollTo().performTouchInput { down(center) }
        try {
            compose.waitUntil(3_000) {
                result.fetchSemanticsNode().config[SemanticsProperties.Text].single().text.length < 6
            }
        } finally {
            backspace.performTouchInput { up() }
        }
        val afterHold = result.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertTrue("长按应删除多个字符", afterHold.length < 6)
        compose.mainClock.advanceTimeBy(300)
        result.assertTextEquals(afterHold)
        compose.runOnIdle {
            assertFalse(ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true)
        }

        clear.performScrollTo().performClick()
        apply.assertIsNotEnabled()
        press("2", "×", "2", "5")
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertEquals("35.33", amount)
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
        val apply = compose.onNodeWithText("确认金额")
        note.performScrollTo().performTextReplacement("未保存备注")
        val initial = compose.onNode(sourceAmount and hasText("12.50"))
        assertTrue(!initial.fetchSemanticsNode().config.contains(SemanticsActions.SetText))
        initial.performScrollTo().performClick()
        compose.onNode(hasContentDescription("计算结果") and hasText("12.50")).assertIsDisplayed()
        compose.onNodeWithText("+").performScrollTo().performClick()
        compose.onNodeWithText("1").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(expression and hasText("12.50+1")).performScrollTo().assertIsDisplayed()
        apply.performClick()
        compose.onNode(sourceAmount and hasText("13.50")).assertExists()
        note.assertTextEquals("未保存备注")

        compose.onNode(targetAmount and hasText("300")).performScrollTo().performClick()
        compose.onNodeWithText("JPY").assertIsDisplayed()
        compose.onNode(hasContentDescription("计算结果") and hasText("300")).assertIsDisplayed()
        compose.onNodeWithContentDescription("清空").performScrollTo().performClick()
        listOf("2", ".", "5").forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        apply.performClick()
        compose.onNode(targetAmount and hasText("3")).assertExists()
        compose.onNode(sourceAmount and hasText("13.50")).assertExists()
    }

    @Test
    fun aNewDraftWithoutAnAvailableDefaultWalletDoesNotOpenTheCalculator() {
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyList(), emptyList())
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java] }
        compose.setContent {
            KitsuneTheme(records.theme) { EntryForm(records, null, null, model, back = {}, finished = {}) }
        }
        compose.onNodeWithText("记一笔").assertIsDisplayed()
        compose.onNodeWithContentDescription("金额 · 请选择钱包").assertIsNotEnabled()
        compose.onNodeWithText("请手动选择钱包").assertExists()
        compose.onNodeWithText("金额计算器").assertDoesNotExist()
    }

}
