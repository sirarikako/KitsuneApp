package com.kitsuneapp

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsuneapp.data.CategoryIcon
import com.kitsuneapp.data.MealDraft
import com.kitsuneapp.data.MealSlot
import com.kitsuneapp.data.Scope
import com.kitsuneapp.data.WalletDraft
import com.kitsuneapp.data.money
import com.kitsuneapp.data.removeEntry
import com.kitsuneapp.data.removeWallet
import com.kitsuneapp.data.saveWallet
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    @Test
    fun homeCardActionsOpenMealsLedgerAndEntryForm() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("全部记录").performClick()
        compose.onNodeWithText("三餐记录").assertIsDisplayed()
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithText("查看账本").performScrollTo().performClick()
        compose.onNodeWithText("流水").assertIsDisplayed()
        compose.onNodeWithText("首页").performClick()

        val repository = runBlocking { (compose.activity.application as KitsuneApplication).repository() }
        val name = "home-" + UUID.randomUUID()
        val walletId = runBlocking {
            repository.saveWallet(WalletDraft(null, name, "JPY", "0", 0, false, false))
            repository.read().wallets.single { it.name == name }.id
        }
        try {
            compose.waitUntilAtLeastOneExists(hasText("JPY"), 20_000)
            compose.onNodeWithText("记一笔").performScrollTo().performClick()
            compose.waitUntilExactlyOneExists(hasText("金额计算器"), 10_000)
            compose.onNodeWithContentDescription("关闭").performClick()
            compose.onNodeWithText("收支类型").assertIsDisplayed()
            compose.onNodeWithText("金额计算器").assertDoesNotExist()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithTag("home-ledger-card").assertExists()
        } finally {
            runBlocking { repository.removeWallet(walletId) }
        }
    }

    @Test
    fun newEntriesUseTheWalletTypeUntilTheUserChoosesAnotherType() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        val repository = runBlocking { (compose.activity.application as KitsuneApplication).repository() }
        val suffix = UUID.randomUUID().toString().take(6)
        val expenseName = "日常$suffix"
        val incomeName = "收款$suffix"
        val walletIds = runBlocking {
            repository.saveWallet(WalletDraft(null, expenseName, "CNY", "0", 0, false, false))
            repository.saveWallet(WalletDraft(null, incomeName, "JPY", "0", 1, false, true,
                setAsDefault = true, defaultToIncome = true))
            repository.read().wallets.filter { it.name == expenseName || it.name == incomeName }.map { it.id }
        }
        val incomeWallet = "$incomeName · JPY"
        val expenseWallet = "$expenseName · CNY"
        try {
            compose.onNodeWithText("记账").performClick()
            compose.onNodeWithText("钱包").performClick()
            compose.waitUntilAtLeastOneExists(hasText(incomeWallet), 20_000)
            compose.onNodeWithText("首页").performClick()
            compose.onNodeWithText("记一笔").performScrollTo().performClick()
            compose.waitUntilExactlyOneExists(hasText("金额计算器"), 10_000)
            compose.onNodeWithText("JPY").assertIsDisplayed()
            compose.onNodeWithContentDescription("关闭").performClick()
            compose.onNodeWithText("收入").assertIsSelected()

            compose.onNodeWithText(incomeWallet).performClick()
            compose.onNodeWithText(expenseName).performClick()
            compose.onNodeWithText("支出").assertIsSelected()
            compose.onNodeWithText(expenseWallet).performClick()
            compose.onNode(hasText(incomeName) and hasText("JPY · 独立核算")).performClick()
            compose.onNodeWithText("收入").assertIsSelected()

            compose.onNodeWithText("支出").performClick()
            compose.onNodeWithText(incomeWallet).performClick()
            compose.onNodeWithText(expenseName).performClick()
            compose.onNodeWithText(expenseWallet).performClick()
            compose.onNode(hasText(incomeName) and hasText("JPY · 独立核算")).performClick()
            compose.onNodeWithText("支出").assertIsSelected()
            compose.onNodeWithText("金额计算器").assertDoesNotExist()

            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("记账").performClick()
            compose.onNodeWithText("流水").performClick()
            compose.onNodeWithContentDescription("记一笔").performClick()
            compose.waitUntilExactlyOneExists(hasText("金额计算器"), 10_000)
            compose.onNodeWithContentDescription("关闭").performClick()
            compose.onNodeWithText("收入").assertIsSelected()
        } finally {
            runBlocking { walletIds.forEach { repository.removeWallet(it) } }
        }
    }

    @Test
    fun savingThenQuicklyRecordingAgainOpensAFreshDraft() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        val repository = runBlocking { (compose.activity.application as KitsuneApplication).repository() }
        val name = "收款" + UUID.randomUUID().toString().take(6)
        val walletId = runBlocking {
            repository.saveWallet(WalletDraft(null, name, "JPY", "0", 0, false, false,
                setAsDefault = true, defaultToIncome = true))
            repository.read().wallets.single { it.name == name }.id
        }
        val fab = compose.onNodeWithContentDescription("记一笔")
        val amount = hasContentDescription("金额 · JPY")
        val note = compose.onNodeWithContentDescription("备注")
        try {
            compose.onNodeWithText("记账").performClick()
            compose.onNodeWithText("钱包").performClick()
            compose.waitUntilAtLeastOneExists(hasText("$name · JPY"), 20_000)
            compose.onNodeWithText("流水").performClick()
            fab.performClick()
            compose.waitUntilExactlyOneExists(hasText("金额计算器"), 10_000)
            compose.onNodeWithText("3").performScrollTo().performClick()
            compose.activityRule.scenario.recreate()
            compose.waitUntilExactlyOneExists(hasText("金额计算器"), 15_000)
            compose.onNode(hasContentDescription("计算结果") and hasText("3")).assertIsDisplayed()
            compose.onNodeWithText("7").performScrollTo().performClick()
            compose.onNodeWithText("确认金额").performClick()
            compose.onNode(amount and hasText("37")).assertExists()
            compose.activityRule.scenario.recreate()
            compose.waitUntilExactlyOneExists(amount and hasText("37"), 15_000)
            compose.onNodeWithText("金额计算器").assertDoesNotExist()
            note.performScrollTo().performTextReplacement("上一笔的备注")

            val save = compose.onNodeWithText("保存收入").performScrollTo().assertIsEnabled()
            compose.mainClock.autoAdvance = false
            save.performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(20_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithContentDescription("记一笔").fetchSemanticsNodes().isNotEmpty()
            }
            // Reopen while the saved form is still leaving the screen.
            fab.performSemanticsAction(SemanticsActions.OnClick)
            compose.mainClock.autoAdvance = true

            compose.waitUntilExactlyOneExists(hasText("金额计算器"), 10_000)
            compose.onNodeWithContentDescription("关闭").performClick()
            compose.onNodeWithText("收入").assertIsSelected()
            compose.onNodeWithText("$name · JPY").assertExists()
            compose.onNode(amount and hasText("0")).assertExists()
            assertEquals("", note.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            val stored = runBlocking { repository.read().entries.single { it.walletId == walletId } }
            assertEquals(37L, stored.amountMinor)
            assertEquals("上一笔的备注", stored.note)
        } finally {
            compose.mainClock.autoAdvance = true
            runBlocking {
                repository.read().entries.filter { it.walletId == walletId }.forEach { repository.removeEntry(it.id) }
                repository.removeWallet(walletId)
            }
        }
    }

    @Test
    fun openWalletPickerAndEntryDraftSurviveRotationAndRecreation() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        val repository = runBlocking { (compose.activity.application as KitsuneApplication).repository() }
        val name = "旋转测试-" + UUID.randomUUID().toString().take(8)
        val wallet = runBlocking {
            repository.saveWallet(WalletDraft(null, name, "CNY", "0", 0, false, false))
            repository.read().wallets.single { it.name == name }
        }
        val activity = compose.activityRule.scenario
        val initialOrientation = compose.activity.requestedOrientation
        val initialConfiguration = compose.activity.resources.configuration.orientation
        val rotateTo = if (initialConfiguration == Configuration.ORIENTATION_PORTRAIT)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val pickerTitle = compose.onNodeWithText("选择钱包")
        fun assertPickerVisible() {
            // The sheet can be composed while the IME or rotation transition still covers it.
            compose.waitUntil(15_000) { pickerTitle.isDisplayed() }
            pickerTitle.assertIsDisplayed()
        }
        try {
            compose.onNodeWithText("记一笔").performScrollTo().performClick()
            compose.waitUntilExactlyOneExists(hasText("金额计算器"), 10_000)
            compose.onNodeWithContentDescription("关闭").performClick()
            compose.onNodeWithContentDescription("备注").performScrollTo().performTextReplacement("旋转保留")
            compose.onNodeWithContentDescription("钱包").performScrollTo().performClick()
            assertPickerVisible()
            activity.onActivity { it.requestedOrientation = rotateTo }
            compose.waitUntil(15_000) {
                compose.activity.resources.configuration.orientation != initialConfiguration
            }
            assertPickerVisible()
            activity.recreate()
            assertPickerVisible()
            compose.onNodeWithText(name).performClick()
            compose.onNodeWithText("金额计算器").assertDoesNotExist()
            compose.onNode(hasContentDescription("钱包") and hasText("$name · CNY")).assertExists()
            assertEquals("旋转保留", compose.onNodeWithContentDescription("备注")
                .fetchSemanticsNode().config[SemanticsProperties.InputText].text)
        } finally {
            activity.onActivity { it.requestedOrientation = initialOrientation }
            runBlocking { repository.removeWallet(wallet.id) }
        }
    }

    @Test
    fun opensAppearanceFromSettings() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        screenshot("kitsune-home")
        compose.onNodeWithText("设置").performClick()
        screenshot("kitsune-settings")
        compose.onNodeWithText("外观与调色板").performClick()
        compose.onNodeWithText("恢复默认").assertIsDisplayed()
    }

    @Test
    fun creatingAnEntryCategorySelectsItAndKeepsTheEntryDraft() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        val repository = runBlocking { (compose.activity.application as KitsuneApplication).repository() }
        val token = UUID.randomUUID().toString().take(8)
        val walletName = "分类测试-$token"
        val categoryName = "礼金-$token"
        val previousDefault = runBlocking { repository.read().state.defaultWalletId }
        val wallet = runBlocking {
            repository.saveWallet(WalletDraft(null, walletName, "CNY", "0", 0, false, false, setAsDefault = true))
            repository.read().wallets.single { it.name == walletName }
        }
        try {
            // Wait for the activity's records flow to display the seeded wallet before opening a new entry.
            compose.onNodeWithText("记账").performClick()
            compose.onNodeWithText("钱包").performClick()
            compose.waitUntilAtLeastOneExists(hasText("$walletName · CNY"), 20_000)
            compose.onNodeWithText("首页").performClick()
            compose.onNodeWithText("记一笔").performScrollTo().performClick()
            compose.waitUntilExactlyOneExists(hasText("金额计算器"), 10_000)
            compose.onNodeWithContentDescription("关闭").performClick()
            compose.waitUntilAtLeastOneExists(hasContentDescription("分类"), 10_000)
            compose.onNodeWithContentDescription("备注").performScrollTo().performTextReplacement("保留备注")
            val category = compose.onNodeWithContentDescription("分类")
            val dialogContent = hasAnyAncestor(isDialog())
            val sheetTitle = hasText("选择分类") and dialogContent
            val categoryNameField = hasContentDescription("分类名称") and dialogContent
            fun openPicker() {
                category.performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
                compose.waitUntilExactlyOneExists(sheetTitle, 10_000)
            }
            fun openEditor() {
                openPicker()
                compose.onNode(hasText("新增分类") and dialogContent).assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.OnClick)
                compose.waitUntilExactlyOneExists(categoryNameField, 10_000)
            }
            openEditor()
            compose.onNode(categoryNameField).performTextReplacement(categoryName)
            compose.onNode(hasText("取消") and dialogContent).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntilDoesNotExist(categoryNameField, 10_000)
            compose.onNode(sheetTitle).assertDoesNotExist()
            category.assertExists()
            assertTrue(runBlocking { repository.read().categories.none { it.name == categoryName } })

            openEditor()
            compose.onNode(categoryNameField).performTextReplacement(categoryName)
            compose.onNodeWithText("礼物").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            compose.onNodeWithText("保存").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)

            val selected = hasContentDescription("分类") and hasText(categoryName)
            compose.waitUntilExactlyOneExists(selected, 10_000)
            assertEquals("保留备注", compose.onNodeWithContentDescription("备注")
                .fetchSemanticsNode().config[SemanticsProperties.InputText].text)
            assertEquals(CategoryIcon.GIFT, runBlocking { repository.read().categories.single { it.name == categoryName }.icon })
            // The saved value can appear before Android returns input focus from the dialog to the form.
            compose.waitUntil(10_000) {
                compose.runOnUiThread { compose.activity.window.decorView.hasWindowFocus() }
            }
            compose.onNode(selected).performScrollTo().assertIsDisplayed()
            openPicker()
            val categoryList = hasScrollToNodeAction() and dialogContent
            compose.waitUntilExactlyOneExists(categoryList, 10_000)
            compose.onNode(categoryList).performScrollToNode(hasText(categoryName))
            compose.onNode(hasText(categoryName) and dialogContent).assertIsSelected()
            compose.onNode(hasContentDescription("关闭") and dialogContent).performSemanticsAction(SemanticsActions.OnClick)
            compose.onNode(selected).assertExists()
        } finally {
            runBlocking {
                repository.read().categories.find { it.name == categoryName }?.let { repository.deleteCategory(it.id) }
                repository.removeWallet(wallet.id)
                previousDefault?.let { id ->
                    repository.read().wallets.find { it.id == id }?.let {
                        repository.saveWallet(WalletDraft(it.id, it.name, it.currencyCode,
                            money(it.openingBalanceMinor, it.currencyScale), it.sortOrder,
                            it.archived, it.separateAccounting, setAsDefault = true))
                    }
                }
            }
        }
    }

    @Test
    fun mealStatisticsCanSelectAMonthOrAllTimeWhileKeepingTheKeywordFilter() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        val repository = runBlocking { (compose.activity.application as KitsuneApplication).repository() }
        val keyword = "统计测试-" + UUID.randomUUID()
        val previousMonth = java.time.YearMonth.now().minusMonths(1)
        val date = previousMonth.atDay(1).toString()
        val mealId = runBlocking {
            repository.saveMeal(MealDraft(
                date = date,
                slot = MealSlot.BREAKFAST,
                atHome = false,
                methodId = null,
                preparationIds = emptyList(),
                newPreparations = emptyList(),
                dishes = keyword,
                note = "",
            ))
            repository.read().meals.single { it.dishes == keyword }.id
        }
        val resultsList = hasScrollToNodeAction()
        val results = compose.onNode(resultsList)
        fun applyStatisticsFilter() {
            compose.onNodeWithText("应用筛选").performScrollTo().assertIsEnabled()
                .performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntilExactlyOneExists(resultsList, 10_000)
        }
        try {
            compose.onNodeWithText("用餐").performClick()
            compose.onNodeWithText("统计").performClick()
            compose.onNodeWithText("本月").assertIsDisplayed()
            compose.onNodeWithText("筛选").performClick()
            compose.onNodeWithText("本月").assertIsSelected()
            compose.onNodeWithContentDescription("菜品／备注关键词").performScrollTo().performTextReplacement(keyword)
            applyStatisticsFilter()
            results.performScrollToNode(hasText("没有符合条件的用餐记录"))
            compose.onNodeWithText("没有符合条件的用餐记录").assertIsDisplayed()

            val monthPicker = compose.onNodeWithContentDescription("选择统计月份")
            monthPicker.performClick()
            compose.onNodeWithContentDescription("上个月").performClick()
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithText("本月").assertIsDisplayed()

            monthPicker.performClick()
            compose.onNodeWithContentDescription("上个月").performClick()
            compose.onNodeWithText("应用").performClick()
            val previousMonthLabel = "${previousMonth.year}年${previousMonth.monthValue}月"
            compose.onNodeWithText(previousMonthLabel).assertIsDisplayed()
            results.performScrollToNode(hasText("关键词：$keyword"))
            compose.onNodeWithText("关键词：$keyword").assertIsDisplayed()
            results.performScrollToNode(hasText("否 1 餐"))
            compose.onNodeWithText("否 1 餐").assertIsDisplayed()

            compose.onNodeWithText("筛选").performClick()
            compose.onNodeWithText("所有时间").performClick().assertIsSelected()
            applyStatisticsFilter()
            compose.onNodeWithText("所有时间").assertIsDisplayed()
            results.performScrollToNode(hasText("关键词：$keyword"))
            compose.onNodeWithText("关键词：$keyword").assertIsDisplayed()
            results.performScrollToNode(hasText("否 1 餐"))
            compose.onNodeWithText("否 1 餐").assertIsDisplayed()
            results.performScrollToNode(hasText("未填写"))
            compose.onNodeWithText("未填写").assertIsDisplayed()

            compose.onNodeWithText("记录").performClick()
            compose.onNodeWithText("三餐记录").assertIsDisplayed()
            compose.onNodeWithText("统计").performClick()
            compose.onNodeWithText("所有时间").assertIsDisplayed()
        } finally {
            runBlocking { repository.deleteMeal(mealId) }
        }
    }

    @Test
    fun systemBackGestureHidesKeyboardBeforeLeavingMealForm() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("用餐").performClick()
        compose.onNodeWithText("早餐").performClick()
        val dish = compose.onNodeWithContentDescription("菜品内容").performScrollTo()
        dish.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(10_000) { keyboardIsVisible() }
        dish.performTextReplacement("键盘测试草稿")
        compose.onNodeWithText("保存早餐记录").performScrollTo().assertIsDisplayed()

        swipeFromLeftEdge()
        compose.waitUntil(10_000) { !keyboardIsVisible() }
        assertSingleLineMealText("菜品内容", "键盘测试草稿")

        swipeFromLeftEdge()
        compose.waitUntilAtLeastOneExists(hasText("三餐记录"), 10_000)
        dish.assertDoesNotExist()
    }

    private fun keyboardIsVisible(): Boolean = compose.runOnUiThread {
        ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    private fun swipeFromLeftEdge() {
        val decor = compose.activity.window.decorView
        val (endX, y) = compose.runOnUiThread { decor.width / 2 to decor.height / 2 }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("input swipe 1 $y $endX $y 300"),
        ).use { it.readBytes() }
    }

    @Test
    fun keepsPageViewportAndMainNavigationFixedDuringSubpageSlide() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("设置").performClick()
        val pageViewport = compose.onNodeWithTag("navigation-pages")
        val mainNav = compose.onNodeWithTag("main-navigation", useUnmergedTree = true)
        val appearance = compose.onNodeWithText("外观与调色板")
        val viewportBounds = pageViewport.fetchSemanticsNode().boundsInRoot
        val navPosition = mainNav.fetchSemanticsNode().positionInRoot

        compose.mainClock.autoAdvance = false
        try {
            appearance.performClick()
            compose.mainClock.advanceTimeBy(96)
            assertEquals(viewportBounds, pageViewport.fetchSemanticsNode().boundsInRoot)
            assertEquals(navPosition, mainNav.fetchSemanticsNode().positionInRoot)
            val slidingTitle = appearance.fetchSemanticsNode().positionInRoot

            compose.mainClock.advanceTimeBy(280)
            val settledTitle = appearance.fetchSemanticsNode().positionInRoot
            assertTrue("The child page should enter from the right", slidingTitle.x > settledTitle.x)
            assertEquals(settledTitle.y, slidingTitle.y, 1f)
            assertEquals(viewportBounds, pageViewport.fetchSemanticsNode().boundsInRoot)
            mainNav.assertIsNotDisplayed()

            compose.onNodeWithContentDescription("返回").performClick()
            compose.mainClock.advanceTimeBy(96)
            assertEquals(viewportBounds, pageViewport.fetchSemanticsNode().boundsInRoot)
            assertEquals(navPosition, mainNav.fetchSemanticsNode().positionInRoot)
            compose.mainClock.advanceTimeBy(260)
            appearance.assertIsDisplayed()
            assertEquals(navPosition, mainNav.fetchSemanticsNode().positionInRoot)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun returningFromGitHubBackupKeepsTheSettingsRowAboveMainNavigation() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("设置").performClick()
        val backup = compose.onNode(hasText("GitHub 备份") and hasClickAction())
        val mainNav = compose.onNodeWithTag("main-navigation", useUnmergedTree = true)
        backup.performScrollTo().assertIsDisplayed()
        val initialRow = backup.fetchSemanticsNode()
        val rowTop = initialRow.positionInRoot.y
        val rowHeight = initialRow.size.height
        val barTop = mainNav.fetchSemanticsNode().positionInRoot.y

        compose.mainClock.autoAdvance = false
        try {
            fun checkReturn(back: () -> Unit) {
                backup.performClick()
                compose.mainClock.advanceTimeBy(300)
                mainNav.assertIsNotDisplayed()
                back()
                compose.mainClock.advanceTimeBy(96)
                assertEquals(rowTop, backup.fetchSemanticsNode().positionInRoot.y, 1f)
                assertEquals(barTop, mainNav.fetchSemanticsNode().positionInRoot.y, 1f)
                compose.mainClock.advanceTimeBy(260)
                backup.assertIsDisplayed()
                val returnedTop = backup.fetchSemanticsNode().positionInRoot.y
                assertEquals(rowTop, returnedTop, 1f)
                assertTrue("The backup row should fit above the main navigation", returnedTop + rowHeight <= barTop + 1f)
            }

            checkReturn { compose.onNodeWithContentDescription("返回").performClick() }
            checkReturn { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() } }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun returnsRightFromNestedSettingsWithEitherBackAction() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("设置").performClick()
        val methods = compose.onNodeWithText("用餐方式")
        val edit = compose.onNodeWithText("编辑用餐方式")
        val cooking = compose.onNodeWithText("下厨")
        methods.performClick()
        val methodsPosition = methods.fetchSemanticsNode().positionInRoot

        compose.mainClock.autoAdvance = false
        try {
            cooking.performClick()
            compose.mainClock.advanceTimeBy(96)
            val enteringPosition = edit.fetchSemanticsNode().positionInRoot
            compose.mainClock.advanceTimeBy(280)
            val editPosition = edit.fetchSemanticsNode().positionInRoot
            assertTrue("The editor should enter from the right", enteringPosition.x > editPosition.x)
            assertEquals(editPosition.y, enteringPosition.y, 1f)

            fun checkReturn(back: () -> Unit) {
                back()
                compose.mainClock.advanceTimeBy(96)
                val returningPosition = methods.fetchSemanticsNode().positionInRoot
                assertTrue("The parent should return from the left", returningPosition.x < methodsPosition.x)
                assertEquals(methodsPosition.y, returningPosition.y, 1f)
                compose.mainClock.advanceTimeBy(260)
                cooking.assertIsDisplayed()
            }

            checkReturn { compose.onNodeWithContentDescription("返回").performClick() }
            cooking.performClick()
            compose.mainClock.advanceTimeBy(320)
            checkReturn { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() } }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun checksRequiredMealPlanAndReturnsAfterSave() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("用餐").performClick()
        screenshot("kitsune-meals")
        compose.onNodeWithText("早餐").performClick()
        screenshot("kitsune-meal-form")
        compose.onNodeWithText("是").assertIsSelected()
        val cooking = compose.onNodeWithText("下厨")
        cooking.assertIsSelected().performClick()
        compose.onNodeWithText("保存早餐记录").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("请选择用餐方式"), 15_000)
        compose.onNodeWithText("否").performScrollTo().performClick()
        val values = mapOf("菜品内容" to "番茄炒蛋、\"紫菜汤\"、".repeat(8), "备注" to "配菜另装\n少放盐")
        for ((label, value) in values) {
            compose.onNodeWithContentDescription(label).performScrollTo().performTextReplacement(value)
            assertSingleLineMealText(label, value)
        }
        compose.onNodeWithText("保存早餐记录").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("三餐记录"), 15_000)
        compose.onNodeWithText("早餐").performClick()
        compose.onNodeWithText("否").assertIsSelected()
        cooking.assertIsNotSelected()
        for ((label, value) in values) assertSingleLineMealText(label, value)
        compose.onNodeWithText("删除这条用餐记录").performScrollTo().performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntilAtLeastOneExists(hasText("添加记录"), 15_000)
    }

    @Test
    fun addsMealOptionsFromDialogsWithoutSavingMeal() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        val repository = runBlocking { (compose.activity.application as KitsuneApplication).repository() }
        val suffix = UUID.randomUUID().toString().take(8)
        val method = "试做-$suffix"
        val preparation = "清蒸-$suffix"
        val mealsBefore = runBlocking { repository.read().meals }

        try {
            compose.onNodeWithText("用餐").performClick()
            compose.onNodeWithText("早餐").performClick()
            compose.onNodeWithContentDescription("菜品内容").performScrollTo().performTextReplacement("保留草稿")

            fun open(label: String, name: String) {
                compose.onNodeWithContentDescription(label).performScrollTo().assertIsEnabled()
                    .performSemanticsAction(SemanticsActions.OnClick)
                compose.waitUntilAtLeastOneExists(hasContentDescription("名称"), 10_000)
                compose.onNodeWithContentDescription("名称").performTextReplacement(name)
            }

            open("新增用餐方式", "  $method  ")
            compose.onNodeWithText("确定").performClick()
            compose.waitUntilAtLeastOneExists(hasText(method), 15_000)
            compose.onNodeWithText(method).assertIsNotSelected()
            compose.onNodeWithText("下厨").assertIsSelected()

            open("新增用餐方式", method)
            compose.onNodeWithText("确定").performClick()
            compose.waitUntilAtLeastOneExists(hasText("已有同名用餐方式"), 15_000)
            compose.onNodeWithText("取消").performClick()

            open("新增制作分类", preparation)
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithText(preparation).assertDoesNotExist()
            open("新增制作分类", preparation)
            compose.onNodeWithText("确定").performClick()
            compose.waitUntilAtLeastOneExists(hasText(preparation), 15_000)
            compose.onNodeWithText(preparation).assertIsNotSelected()
            assertSingleLineMealText("菜品内容", "保留草稿")

            val saved = runBlocking { repository.read() }
            assertEquals(mealsBefore, saved.meals)
            assertEquals(method, saved.methods.last().name)
            assertEquals(false, saved.methods.last().showPreparationCategories)
            assertEquals(preparation, saved.categories.filter { it.scope == Scope.PREPARATION }.last().name)
        } finally {
            runBlocking {
                val saved = repository.read()
                saved.methods.find { it.name == method }?.let { repository.deleteMethod(it.id) }
                saved.categories.find { it.scope == Scope.PREPARATION && it.name == preparation }
                    ?.let { repository.deleteCategory(it.id) }
            }
        }
    }

    @Test
    fun selectsDaysAndBrowsesWeeksBeforeOpeningMeal() {
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        compose.onNodeWithText("用餐").performClick()
        val today = LocalDate.now()
        val other = if (today.dayOfWeek.value == 7) today.minusDays(1) else today.plusDays(1)
        fun dayLabel(date: LocalDate): String {
            val weekday = "一二三四五六日"[date.dayOfWeek.value - 1]
            return "${date.monthValue} 月 ${date.dayOfMonth} 日，星期$weekday"
        }
        compose.onNodeWithContentDescription(dayLabel(today)).assertIsSelected()
        compose.onNodeWithContentDescription(dayLabel(other)).assertIsNotSelected().performClick().assertIsSelected()
        compose.onNodeWithContentDescription(dayLabel(today)).assertIsNotSelected()
        val previous = compose.onNodeWithText("上一周")
        previous.performClick().performClick()
        compose.onNodeWithContentDescription(dayLabel(other.minusWeeks(2))).assertIsSelected()
        compose.onNodeWithText("下一周").performClick()
        val selected = other.minusWeeks(1)
        compose.onNodeWithContentDescription(dayLabel(selected)).assertIsSelected()
        compose.onNodeWithText(selected.toString(), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("三餐记录").assertIsDisplayed()
        compose.onNodeWithText("早餐").performClick()
        compose.onNodeWithText(selected.toString(), useUnmergedTree = true).assertIsDisplayed()
    }

    private fun assertSingleLineMealText(label: String, value: String) {
        val field = compose.onNodeWithContentDescription(label).performScrollTo()
        assertEquals(value, field.fetchSemanticsNode().config[SemanticsProperties.InputText].text)
        val layouts = mutableListOf<TextLayoutResult>()
        field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(1, layout.lineCount)
        assertEquals(value.replace('\n', ' '), layout.layoutInput.text.text)
    }

    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
