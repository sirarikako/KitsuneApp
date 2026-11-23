package com.kitsuneapp

import android.graphics.Bitmap
import android.view.View
import android.widget.DatePicker
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsuneapp.data.*
import com.kitsuneapp.feature.ledger.LedgerPage
import com.kitsuneapp.feature.ledger.Calculator
import com.kitsuneapp.ui.KitsuneTheme
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LedgerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
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
        val categories = compose.onAllNodesWithText("管理本钱包分类")[0].assertIsDisplayed()
        val editBounds = edit.fetchSemanticsNode().boundsInRoot
        val categoryBounds = categories.fetchSemanticsNode().boundsInRoot
        assertTrue(editBounds.left > name.right && editBounds.bottom <= balance.top)
        assertTrue(categoryBounds.top >= balance.bottom && categoryBounds.right < editBounds.right)
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
        val range = compose.onNodeWithText("钱包范围")
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

        fun monthLabel(month: YearMonth) = LedgerPeriod(month.toString()).label
        fun inDialog(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isDialog()))
        val picker = compose.onNodeWithContentDescription("选择日期范围")
        val monthly = inDialog("按月")
        val custom = inDialog("自定义日期")
        val apply = inDialog("应用")

        compose.onNodeWithText(monthLabel(current)).assertIsDisplayed()
        compose.onNodeWithContentDescription("上个月").performClick()
        compose.onNodeWithText(monthLabel(previous)).assertIsDisplayed()
        compose.onNodeWithContentDescription("下个月").performClick()
        compose.onNodeWithText(monthLabel(current)).assertIsDisplayed()

        picker.performClick()
        monthly.assertIsSelected()
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

        val range = compose.onNodeWithText("$from 至 $to")
        range.assertIsDisplayed()
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
        onView(isAssignableFrom(DatePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
            override fun getDescription() = "Select a date in the platform calendar"
            override fun perform(uiController: UiController, view: View) {
                (view as DatePicker).updateDate(date.year, date.monthValue - 1, date.dayOfMonth)
            }
        })
        onView(withId(android.R.id.button1)).perform(click())
    }

    @Test
    fun calculatorAcceptsExpressionsAndAppliesResultAfterEditing() {
        var amount: String? = null
        compose.setContent {
            KitsuneTheme(ThemePreferences(), forceDark = false) {
                Calculator(2, "CNY", "12.50", dismiss = {}, apply = { amount = it })
            }
        }
        val expression = compose.onNodeWithContentDescription("计算表达式")
        val equals = compose.onNodeWithText("=")
        val apply = compose.onNodeWithText("使用此金额")
        expression.performTextReplacement("1÷0")
        equals.performScrollTo().performClick()
        compose.onNodeWithText("除数不能为零").assertIsDisplayed()
        expression.performScrollTo().performTextReplacement("1÷3×100")
        equals.performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText("33.33").isDisplayed() }
        apply.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("33.33", amount) }

        saveScreenshot("kitsune-calculator")
        expression.performClick()
        expression.performTextReplacement("2×25")
        apply.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("50.00", amount) }
    }

    private fun saveScreenshot(name: String) {
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
