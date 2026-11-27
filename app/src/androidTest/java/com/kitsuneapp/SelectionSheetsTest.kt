package com.kitsuneapp

import android.os.Build
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsuneapp.data.*
import com.kitsuneapp.feature.ledger.EntryForm
import com.kitsuneapp.feature.ledger.LedgerEntryFilterDialog
import com.kitsuneapp.feature.ledger.WalletForm
import com.kitsuneapp.feature.ledger.WalletRangeDialog
import com.kitsuneapp.feature.meals.MealFilter
import com.kitsuneapp.feature.meals.MealFilterPage
import com.kitsuneapp.ui.KitsuneTheme
import com.kitsuneapp.ui.Primary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SelectionSheetsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val inDialog = hasAnyAncestor(isDialog())
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Test
    fun walletChangeClearsAnIncompatibleCategoryAndRestoresThePickerDraft() {
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java] }
        val restoration = StateRestorationTester(compose)
        val records = selectionRecords()
        restoration.setContent {
            KitsuneTheme(records.theme) { EntryForm(records, null, null, model, {}, {}) }
        }

        compose.onNodeWithText("金额计算器").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭").performClick()
        val wallet = compose.onNodeWithContentDescription("钱包")
        val category = compose.onNodeWithContentDescription("分类")
        compose.onNode(hasContentDescription("钱包") and hasText("日常钱包 · CNY")).assertExists()
        category.performScrollTo().performClick()
        sheetText("通用分类 · 支出").assertIsDisplayed()
        sheetText("新增分类").assertIsDisplayed()
        sheetText("餐饮").performClick()
        compose.onNode(hasContentDescription("分类") and hasText("餐饮")).assertExists()

        wallet.performScrollTo().performClick()
        sheetText("旅行钱包").performClick()
        compose.onNode(hasContentDescription("钱包") and hasText("旅行钱包 · JPY")).assertExists()
        compose.onNodeWithText("请重新选择该钱包的分类").assertExists()
        val note = compose.onNodeWithContentDescription("备注")
        note.performScrollTo().performTextReplacement("保留草稿")
        category.performScrollTo().performClick()
        sheetText("本钱包专属分类 · 支出").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        sheetText("本钱包专属分类 · 支出").assertIsDisplayed()
        sheetText("餐饮").assertDoesNotExist()
        sheetText("旅途交通").performClick()
        compose.onNode(hasContentDescription("分类") and hasText("旅途交通")).assertExists()
        compose.onNode(hasContentDescription("备注") and hasText("保留草稿")).assertExists()
    }

    @Test
    fun historicalWalletRemainsSelectableOnEditAndRefundOnlyShowsSameCurrency() {
        val records = selectionRecords()
        val original = records.entries.single()
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java] }
        var editing by mutableStateOf(true)
        compose.setContent {
            KitsuneTheme(records.theme) {
                key(editing) {
                    EntryForm(records, original.id.takeIf { editing }, original.id.takeUnless { editing },
                        model, {}, {})
                }
            }
        }

        val wallet = compose.onNodeWithContentDescription("钱包")
        compose.onNodeWithText("金额计算器").assertDoesNotExist()
        wallet.performScrollTo().performClick()
        compose.onNode(hasText("旧钱包") and hasText("CNY · 已归档") and inDialog).assertIsSelected()
        sheetText("旅行钱包").assertIsDisplayed()
        close()
        compose.runOnIdle { editing = false }

        compose.onNodeWithText("记录退款").assertIsDisplayed()
        compose.onNodeWithText("金额计算器").assertDoesNotExist()
        wallet.performScrollTo().performClick()
        sheetText("旧钱包").assertIsSelected()
        sheetText("旅行钱包").assertDoesNotExist()
        sheetText("日常钱包").performClick()
        compose.onNode(hasContentDescription("钱包") and hasText("日常钱包 · CNY")).assertExists()
    }

    @Test
    fun currencySearchRestoresTextUsesCodeAndNameAndLocksAReferencedWallet() {
        val records = selectionRecords()
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java] }
        val restoration = StateRestorationTester(compose)
        var editing by mutableStateOf(false)
        restoration.setContent {
            KitsuneTheme(records.theme) {
                key(editing) {
                    WalletForm(records, "archived".takeIf { editing }, model, {})
                }
            }
        }
        val currency = compose.onNodeWithContentDescription("币种")
        currency.performScrollTo().performClick()
        list().performScrollToIndex(30)
        val search = compose.onNodeWithContentDescription("搜索代码或名称")
        search.performTextReplacement("无匹配的搜索")
        sheetText("没有匹配币种").assertIsDisplayed()
        search.performTextReplacement("日元")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasContentDescription("搜索代码或名称") and hasText("日元")).assertIsDisplayed()
        sheetText("JPY").performClick()
        compose.onNode(hasContentDescription("币种") and hasText("JPY")).assertExists()
        currency.performClick()
        search.performTextReplacement("jpy")
        sheetText("JPY").assertIsDisplayed().assertIsSelected()
        close()
        compose.runOnIdle { editing = true }
        currency.assertIsNotEnabled()
        compose.onNodeWithText("已有流水，币种和精度固定").assertExists()
    }

    @Test
    fun mealMethodSelectionChangesOnlyTheFilterDraftUntilApply() {
        val records = selectionRecords()
        var applied: MealFilter? = null
        compose.setContent {
            KitsuneTheme(records.theme) {
                val filter = MealFilter()
                MealFilterPage(records, filter, filter, filter, {}, { applied = it })
            }
        }
        compose.onNodeWithText("早餐").performScrollTo().performClick()
        compose.onNodeWithText("晚餐").performScrollTo().performClick()
        compose.onNodeWithText("是").performScrollTo().performClick()

        val chooser = compose.onNodeWithContentDescription("用餐方式")
        chooser.performScrollTo().performClick()
        sheetText("选择用餐方式").assertIsDisplayed()
        sheetText("未填写方式").performClick()
        sheetText("应用").performClick()
        compose.onNode(hasContentDescription("用餐方式") and hasText("未填写方式")).assertExists()
        compose.runOnIdle { assertNull(applied) }

        chooser.performClick()
        sheetText("全选").performClick()
        close()
        compose.onNode(hasContentDescription("用餐方式") and hasText("未填写方式")).assertExists()
        compose.onNodeWithText("应用筛选").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(MealSlot.BREAKFAST, MealSlot.DINNER), applied?.slots)
            assertEquals(listOf(true), applied?.atHome)
            assertEquals(listOf(MealFilter.NO_METHOD), applied?.methods)
        }
    }

    @Test
    fun mealFilterRetainsFullMultiSelectionsAndUsesBottomSheetForExtraPreparationCategories() {
        val time = "2026-11-17T12:00:00Z"
        val methods = listOf(
            MealMethod("m-cook", "下厨", "下厨", 0, false, true, time, time),
            MealMethod("m-takeout", "外卖", "外卖", 1, false, false, time, time),
        )
        val preps = (1..10).map { i ->
            Category("prep-$i", Scope.PREPARATION, null, GLOBAL_OWNER, "制作$i", "制作$i", i, false, false, time, time)
        }
        val base = selectionRecords()
        val records = base.copy(methods = methods, categories = base.categories + preps)
        var applied: MealFilter? = null
        compose.setContent {
            KitsuneTheme(records.theme) {
                val filter = MealFilter()
                MealFilterPage(records, filter, filter, filter, {}, { applied = it })
            }
        }

        // Selecting all methods retains full selection instead of collapsing to empty.
        val chooser = compose.onNodeWithContentDescription("用餐方式")
        chooser.performScrollTo().performClick()
        sheetText("全选").performClick()
        sheetText("应用").performClick()
        compose.onNode(hasContentDescription("用餐方式") and hasText("已选 3 项 · 未填写方式、下厨等")).assertExists()

        // Reopening the method sheet retains all checked states; clearing resets to "全部方式".
        chooser.performScrollTo().performClick()
        sheetText("未填写方式").assertIsOn()
        sheetText("下厨").assertIsOn()
        sheetText("外卖").assertIsOn()
        sheetText("全不选").performClick()
        sheetText("应用").performClick()
        compose.onNode(hasContentDescription("用餐方式") and hasText("全部方式")).assertExists()

        // Selecting an extra preparation category via "更多分类" bottom sheet.
        compose.onNodeWithText("更多分类").performScrollTo().performClick()
        sheetText("选择制作分类").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜索分类").performTextReplacement("10")
        sheetText("制作10").performClick()
        close()
        compose.onNodeWithText("已选更多分类 · 制作10").assertDoesNotExist()

        compose.onNodeWithText("更多分类").performScrollTo().performClick()
        compose.onNodeWithContentDescription("搜索分类").performTextReplacement("10")
        sheetText("制作10").performClick()
        sheetText("应用").performClick()
        compose.onNodeWithText("已选更多分类 · 制作10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("应用筛选").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(emptyList<String>(), applied?.methods)
            assertEquals(listOf("prep-10"), applied?.categories)
        }

        // Selecting all methods and all preparation categories retains every selected ID on apply.
        chooser.performScrollTo().performClick()
        sheetText("全选").performClick()
        sheetText("应用").performClick()
        compose.onNodeWithText("更多分类").performScrollTo().performClick()
        sheetText("全选").performClick()
        sheetText("应用").performClick()
        compose.onNodeWithText("应用筛选").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(MealFilter.NO_METHOD, "m-cook", "m-takeout"), applied?.methods)
            assertEquals((1..10).map { "prep-$it" }, applied?.categories)
        }
    }

    @Test
    fun dismissGesturesDoNotApplyAndARecreatedFilterRetainsItsDraft() {
        val records = selectionRecords()
        val applied = mutableStateOf(LedgerEntryFilters())
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            KitsuneTheme(records.theme) {
                var open by rememberSaveable { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) { Primary("打开筛选", { open = true }) }
                if (open) LedgerEntryFilterDialog(applied.value, records.categories, { open = false }) {
                    applied.value = it
                    open = false
                }
            }
        }

        fun open() {
            compose.onNodeWithText("打开筛选").performClick()
            sheetText("支出").assertIsSelected().performClick()
        }
        fun assertCanceled() {
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("流水筛选") and inDialog).fetchSemanticsNodes().isEmpty() }
            compose.runOnIdle { assertEquals(LedgerEntryFilters(), applied.value) }
        }

        open()
        close()
        assertCanceled()
        open()
        automation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        assertCanceled()

        open()
        // Tap the scrim above the sheet.
        val title = sheetText("流水筛选").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        automation.executeShellCommand("input tap ${title.center.x.toInt()} ${(title.top / 3).toInt()}").close()
        assertCanceled()

        open()
        val heading = sheetText("流水筛选").fetchSemanticsNode().boundsInWindow
        val displayHeight = compose.activity.resources.displayMetrics.heightPixels
        val x = heading.center.x.toInt()
        val y = heading.center.y.toInt()
        automation.executeShellCommand("input swipe $x $y $x ${displayHeight - 2} 250").close()
        assertCanceled()

        open()
        list().performScrollToNode(hasContentDescription("备注关键词"))
        compose.onNodeWithContentDescription("备注关键词").performTextReplacement("草稿")
        restoration.emulateSavedInstanceStateRestore()
        list().performScrollToNode(hasContentDescription("备注关键词"))
        compose.onNode(hasContentDescription("备注关键词") and hasText("草稿")).assertIsDisplayed()
        list().performScrollToNode(hasText("支出"))
        sheetText("支出").assertIsNotSelected()
        sheetText("应用").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(EntryType.entries.toSet() - EntryType.EXPENSE, applied.value.types)
            assertEquals("草稿", applied.value.note)
        }
    }

    @Test
    fun filterStartsAtTopAndKeepsApplyAccessibleWithSoftwareKeyboard() {
        val records = selectionRecords().withMoreFilterCategories()
        var applied = LedgerEntryFilters()
        val title = sheetText("流水筛选")
        val apply = sheetText("应用")
        val noteMatcher = hasContentDescription("备注关键词") and inDialog
        val note = compose.onNode(noteMatcher)
        val focusedNote = compose.onNode(noteMatcher and isFocused())
        compose.setContent {
            KitsuneTheme(records.theme) {
                var open by rememberSaveable { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) { Primary("打开筛选", { open = true }) }
                if (open) LedgerEntryFilterDialog(applied, records.categories, { open = false }) {
                    applied = it
                    open = false
                }
            }
        }
        fun openAndAssertStartsAtTop() {
            compose.onNodeWithText("打开筛选").performClick()
            compose.waitUntil("new filter sheet visible", 10_000) {
                title.isDisplayed() && sheetText(EntryType.entries.last().label).isDisplayed()
            }
            EntryType.entries.forEach { sheetText(it.label).assertIsDisplayed().assertIsSelected() }
        }
        openAndAssertStartsAtTop()
        sheetText("支出").performClick().assertIsNotSelected()
        list().performScrollToNode(hasText("图书影音"))
        sheetText("图书影音").assertIsOn().performClick()
        title.assertIsDisplayed()
        apply.assertIsDisplayed()
        list().performScrollToIndex(0)
        sheetText("支出").assertIsDisplayed().assertIsNotSelected()
        close()
        compose.waitUntilDoesNotExist(isDialog(), 10_000)

        openAndAssertStartsAtTop()
        list().performScrollToNode(noteMatcher)
        note.assertIsDisplayed().performClick()
        val activityDecor = compose.activity.window.decorView
        fun sheetImeVisible(): Boolean = compose.runOnUiThread {
            val decor = if (Build.VERSION.SDK_INT >= 29) {
                WindowInspector.getGlobalWindowViews().singleOrNull { it !== activityDecor && it.hasWindowFocus() }
            } else {
                activityDecor
            }
            decor?.let { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) } == true
        }
        compose.waitUntil("filter field focused and title, field and footer visible with the sheet IME", 10_000) {
            sheetImeVisible() && focusedNote.isDisplayed() && title.isDisplayed() && apply.isDisplayed()
        }
        note.assertIsDisplayed().performTextReplacement("键盘筛选")
        title.assertIsDisplayed()
        compose.waitUntil("filter apply visible while IME remains open", 10_000) {
            sheetImeVisible() && apply.isDisplayed()
        }
        apply.assertIsDisplayed().performClick()
        compose.waitUntilDoesNotExist(isDialog(), 10_000)
        compose.runOnIdle { assertEquals(LedgerEntryFilters(note = "键盘筛选"), applied) }
    }

    @Test
    fun manualWalletListPreservesPositionAndDraftWhileFooterStaysVisible() {
        val records = selectionRecords()
        val lastWallet = "追加的钱包 20"
        val wallets = records.wallets + (1..20).map { number ->
            val name = "追加的钱包 $number"
            records.wallets.first().copy(id = "wallet-$number", name = name, normalizedName = name,
                separateAccounting = true)
        }
        val restoration = StateRestorationTester(compose)
        var applied = WalletRange()
        restoration.setContent {
            KitsuneTheme(records.theme) {
                var open by rememberSaveable { mutableStateOf(true) }
                if (open) WalletRangeDialog(applied, wallets, { open = false }) {
                    applied = it
                    open = false
                }
            }
        }
        sheetText("手动选择").performClick()
        sheetText("已选 2/23").assertIsDisplayed()
        list().performScrollToNode(hasText(lastWallet))
        sheetText(lastWallet).assertIsDisplayed().performClick().assertIsOn()
        sheetText("应用").assertIsDisplayed()
        compose.runOnIdle { assertEquals(WalletRange(), applied) }

        restoration.emulateSavedInstanceStateRestore()
        sheetText(lastWallet).assertIsDisplayed().assertIsOn()
        sheetText("应用").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(WalletRange(RangeMode.MANUAL, listOf("regular", "archived", "wallet-20")), applied)
        }
    }

    private fun list() = compose.onNode(hasScrollToNodeAction() and inDialog)
    private fun sheetText(text: String) = compose.onNode(hasText(text) and inDialog)
    private fun close() = compose.onNodeWithContentDescription("关闭").performClick()
}

internal fun selectionRecords(): Records {
    val time = "2026-11-17T12:00:00Z"
    val daily = Wallet("regular", "日常钱包", "日常钱包", "CNY", 2, 0, 0, false, false,
        CategoryMode.DEFAULT, CategoryMode.DEFAULT, time, time)
    val travel = daily.copy(id = "travel", name = "旅行钱包", normalizedName = "旅行钱包",
        separateAccounting = true, expenseCategoryMode = CategoryMode.CUSTOM, currencyCode = "JPY", currencyScale = 0)
    val archived = daily.copy(id = "archived", name = "旧钱包", normalizedName = "旧钱包", archived = true)
    val fallback = Category("fallback", Scope.EXPENSE, null, "", "未分类", "未分类", 0, false, true, time, time)
    val food = fallback.copy(id = "food", name = "餐饮", normalizedName = "餐饮", isDefault = false, icon = CategoryIcon.FOOD)
    val transport = food.copy(id = "transport", name = "旅途交通", normalizedName = "旅途交通",
        icon = CategoryIcon.TRANSPORT, walletId = travel.id, ownerKey = travel.id)
    val historical = FinancialEntry("expense", LocalDate.now().toString(), "12:00", EntryType.EXPENSE,
        archived.id, 2000, food.id, null, null, null, "", time, time)
    return Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
        ThemePreferences(), emptyList(), listOf(fallback, food, transport), emptyList(), emptyMap(),
        listOf(daily, travel, archived), listOf(historical))
}


internal fun Records.withMoreFilterCategories(): Records {
    val template = categories.first { it.id == "food" }
    val names = listOf("日常用品", "水电燃气", "公共交通", "医疗保健", "运动健身", "朋友聚餐",
        "通讯网络", "学习培训", "宠物用品", "个人护理", "礼物人情", "本地出行", "居家维修", "图书影音")
    return copy(categories = categories + names.mapIndexed { index, name ->
        template.copy(id = "sample-$index", name = name, normalizedName = name, sortOrder = index + 10,
            icon = CategoryIcon.entries[index % CategoryIcon.entries.size])
    })
}
