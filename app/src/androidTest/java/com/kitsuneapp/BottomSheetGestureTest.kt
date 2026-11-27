package com.kitsuneapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.kitsuneapp.data.LedgerEntryFilters
import com.kitsuneapp.data.RangeMode
import com.kitsuneapp.data.ThemePreferences
import com.kitsuneapp.data.WalletRange
import com.kitsuneapp.feature.ledger.Calculator
import com.kitsuneapp.feature.ledger.EntryForm
import com.kitsuneapp.feature.ledger.LedgerEntryFilterDialog
import com.kitsuneapp.feature.ledger.WalletRangeDialog
import com.kitsuneapp.ui.DesignSheet
import com.kitsuneapp.ui.KitsuneTheme
import com.kitsuneapp.ui.Primary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BottomSheetGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val inDialog = hasAnyAncestor(isDialog())

    @Test
    fun ledgerFilterContentAndFooterSwipesNeverDismissWhileHeaderDragsFollowThresholds() {
        val records = selectionRecords().withMoreFilterCategories()
        var applied = LedgerEntryFilters()
        var open by mutableStateOf(false)
        compose.setContent {
            KitsuneTheme(records.theme) {
                Box(Modifier.fillMaxSize()) { Primary("打开筛选", { open = true }) }
                if (open) {
                    LedgerEntryFilterDialog(applied, records.categories, dismiss = { open = false }) {
                        applied = it
                        open = false
                    }
                }
            }
        }

        fun openSheet() {
            compose.onNodeWithText("打开筛选").performClick()
            sheetText("流水筛选").assertIsDisplayed()
        }

        openSheet()
        sheetText("支出").assertIsSelected().performClick()

        val listNode = compose.onNode(hasScrollToNodeAction() and inDialog)
        val listBounds = listNode.fetchSemanticsNode().boundsInRoot
        val titleBounds = sheetText("流水筛选").fetchSemanticsNode().boundsInRoot
        val applyBounds = sheetText("应用").fetchSemanticsNode().boundsInRoot

        // 1. Repeated slow and fast downward flings when list is already at top must never dismiss.
        compose.onNode(isDialog()).performTouchInput {
            swipe(
                start = Offset(listBounds.center.x, listBounds.top + 20.dp.toPx()),
                end = Offset(listBounds.center.x, listBounds.top + 200.dp.toPx()),
                durationMillis = 350,
            )
            repeat(5) {
                swipe(
                    start = Offset(listBounds.center.x, listBounds.top + 20.dp.toPx()),
                    end = Offset(listBounds.center.x, listBounds.bottom - 12.dp.toPx()),
                    durationMillis = 55,
                )
            }
        }
        compose.waitForIdle()
        sheetText("流水筛选").assertIsDisplayed()

        // 2. Continuous rapid alternating up/down scrolling past the top edge keeps scrolling normal and stays open.
        compose.onNode(isDialog()).performTouchInput {
            repeat(3) {
                swipe(
                    start = Offset(listBounds.center.x, listBounds.bottom - 20.dp.toPx()),
                    end = Offset(listBounds.center.x, listBounds.top + 20.dp.toPx()),
                    durationMillis = 70,
                )
                swipe(
                    start = Offset(listBounds.center.x, listBounds.top + 20.dp.toPx()),
                    end = Offset(listBounds.center.x, listBounds.bottom - 12.dp.toPx()),
                    durationMillis = 70,
                )
            }
        }
        compose.waitForIdle()
        sheetText("流水筛选").assertIsDisplayed()

        // 3. Scroll deep into the list, then pull down continuously past the top; sheet returns to top and stays open.
        listNode.performScrollToNode(hasText("图书影音"))
        compose.onNode(isDialog()).performTouchInput {
            repeat(4) {
                swipe(
                    start = Offset(listBounds.center.x, listBounds.top + 20.dp.toPx()),
                    end = Offset(listBounds.center.x, listBounds.bottom - 12.dp.toPx()),
                    durationMillis = 100,
                )
            }
        }
        compose.waitForIdle()
        sheetText("流水筛选").assertIsDisplayed()
        assertEquals(0f, listNode.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)

        // 4. Gesture starting in content, crossing into header, then dragging down must keep content behavior.
        compose.onNode(isDialog()).performTouchInput {
            down(Offset(listBounds.center.x, listBounds.top + 24.dp.toPx()))
            moveTo(titleBounds.center, delayMillis = 80)
            moveTo(Offset(titleBounds.center.x, titleBounds.center.y + 180.dp.toPx()), delayMillis = 120)
            up()
        }
        compose.waitForIdle()
        sheetText("流水筛选").assertIsDisplayed()

        // 5. Repeated downward swipes starting on the bottom action button must neither dismiss nor apply.
        compose.onNode(isDialog()).performTouchInput {
            repeat(3) {
                swipe(
                    start = applyBounds.center,
                    end = Offset(applyBounds.center.x, applyBounds.center.y + 160.dp.toPx()),
                    durationMillis = 80,
                )
            }
        }
        compose.waitForIdle()
        sheetText("流水筛选").assertIsDisplayed()
        compose.runOnIdle { assertEquals(LedgerEntryFilters(), applied) }

        // 6. Fast downward pull below 100dp followed by a pause before releasing must snap back, not dismiss.
        val restingTitleTop = sheetText("流水筛选").fetchSemanticsNode().boundsInRoot.top
        compose.onNode(isDialog()).performTouchInput {
            down(titleBounds.center)
            moveTo(Offset(titleBounds.center.x, titleBounds.center.y + 28.dp.toPx()), delayMillis = 20)
            moveTo(Offset(titleBounds.center.x, titleBounds.center.y + 56.dp.toPx()), delayMillis = 20)
            advanceEventTime(200)
            up()
        }
        compose.waitForIdle()
        sheetText("流水筛选").assertIsDisplayed()
        assertEquals(restingTitleTop, sheetText("流水筛选").fetchSemanticsNode().boundsInRoot.top, 2f)

        // 7. Slight slow downward drag from the header must snap back without dismissing.
        compose.onNode(isDialog()).performTouchInput {
            swipe(
                start = titleBounds.center,
                end = Offset(titleBounds.center.x, titleBounds.center.y + 48.dp.toPx()),
                durationMillis = 400,
            )
        }
        compose.waitForIdle()
        sheetText("流水筛选").assertIsDisplayed()
        assertEquals(restingTitleTop, sheetText("流水筛选").fetchSemanticsNode().boundsInRoot.top, 2f)

        // 8. Long slow downward drag from the header (>= 100dp) dismisses even if paused before release.
        compose.onNode(isDialog()).performTouchInput {
            down(titleBounds.center)
            moveTo(Offset(titleBounds.center.x, titleBounds.center.y + 70.dp.toPx()), delayMillis = 300)
            moveTo(Offset(titleBounds.center.x, titleBounds.center.y + 140.dp.toPx()), delayMillis = 300)
            advanceEventTime(200)
            up()
        }
        compose.waitUntilDoesNotExist(isDialog(), 5_000)
        compose.runOnIdle { assertEquals(LedgerEntryFilters(), applied) }

        // 9. Fast downward fling starting from the top handle bar area without pause dismisses below 100dp.
        openSheet()
        val reopenedTitle = sheetText("流水筛选").fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).performTouchInput {
            val handleY = (reopenedTitle.top - 22.dp.toPx()).coerceAtLeast(2f)
            swipe(
                start = Offset(reopenedTitle.center.x, handleY),
                end = Offset(reopenedTitle.center.x, handleY + 64.dp.toPx()),
                durationMillis = 50,
            )
        }
        compose.waitUntilDoesNotExist(isDialog(), 5_000)
        compose.runOnIdle { assertEquals(LedgerEntryFilters(), applied) }

        // 10. Drag starting in header and crossing down into content dismisses cleanly.
        openSheet()
        val thirdTitle = sheetText("流水筛选").fetchSemanticsNode().boundsInRoot
        val thirdList = compose.onNode(hasScrollToNodeAction() and inDialog).fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).performTouchInput {
            down(thirdTitle.center)
            moveTo(thirdList.center, delayMillis = 150)
            moveTo(Offset(thirdList.center.x, thirdList.center.y + 140.dp.toPx()), delayMillis = 150)
            up()
        }
        compose.waitUntilDoesNotExist(isDialog(), 5_000)
    }

    @Test
    fun shortNonScrollableWalletRangeAndSingleSelectSheetsIgnoreContentAndFooterSwipes() {
        val records = selectionRecords()
        var range by mutableStateOf(WalletRange())
        var rangeOpen by mutableStateOf(true)
        compose.setContent {
            KitsuneTheme(records.theme) {
                Box(Modifier.fillMaxSize()) {
                    if (rangeOpen) {
                        WalletRangeDialog(range, records.wallets, dismiss = { rangeOpen = false }) {
                            range = it
                            rangeOpen = false
                        }
                    }
                }
            }
        }

        // WalletRange in REGULAR mode has short non-scrollable content.
        val hintBounds = sheetText("排除独立核算钱包，保留归档历史。").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val resetBounds = sheetText("恢复默认").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val applyBounds = sheetText("应用").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).performTouchInput {
            repeat(3) {
                swipe(
                    start = hintBounds.center,
                    end = Offset(hintBounds.center.x, hintBounds.center.y + 180.dp.toPx()),
                    durationMillis = 60,
                )
            }
            swipe(
                start = resetBounds.center,
                end = Offset(resetBounds.center.x, resetBounds.center.y + 160.dp.toPx()),
                durationMillis = 70,
            )
            swipe(
                start = applyBounds.center,
                end = Offset(applyBounds.center.x, applyBounds.center.y + 160.dp.toPx()),
                durationMillis = 70,
            )
        }
        compose.waitForIdle()
        sheetText("钱包范围").assertIsDisplayed()

        // Switching to manual mode and swiping down at the top of the wallet list also stays open.
        sheetText("手动选择").performClick()
        val walletItem = sheetText("日常钱包").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).performTouchInput {
            repeat(3) {
                swipe(
                    start = walletItem.center,
                    end = Offset(walletItem.center.x, walletItem.center.y + 180.dp.toPx()),
                    durationMillis = 60,
                )
            }
        }
        compose.waitForIdle()
        sheetText("钱包范围").assertIsDisplayed()

        // Confirm button still works normally.
        sheetText("全部钱包").performClick()
        sheetText("应用").performClick()
        compose.waitUntilDoesNotExist(isDialog(), 5_000)
        compose.runOnIdle { assertEquals(WalletRange(RangeMode.ALL), range) }
    }

    @Test
    fun categoryAndWalletPickersIgnoreBodyAndFooterDragsAndDismissFromHeader() {
        val records = selectionRecords()
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java] }
        compose.setContent {
            KitsuneTheme(records.theme) { EntryForm(records, null, null, model, {}, {}) }
        }

        // Close auto-opened calculator via close button.
        sheetText("金额计算器").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.waitUntilDoesNotExist(isDialog(), 5_000)

        // Category picker (short list + footer "新增分类")
        compose.onNodeWithContentDescription("分类").performScrollTo().performClick()
        val foodBounds = sheetText("餐饮").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val addBounds = sheetText("新增分类").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).performTouchInput {
            repeat(3) {
                swipe(
                    start = foodBounds.center,
                    end = Offset(foodBounds.center.x, foodBounds.center.y + 180.dp.toPx()),
                    durationMillis = 60,
                )
            }
            swipe(
                start = addBounds.center,
                end = Offset(addBounds.center.x, addBounds.center.y + 160.dp.toPx()),
                durationMillis = 70,
            )
        }
        compose.waitForIdle()
        sheetText("选择分类").assertIsDisplayed()
        val categoryTitle = sheetText("选择分类").fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).performTouchInput {
            swipe(
                start = categoryTitle.center,
                end = Offset(categoryTitle.center.x, categoryTitle.center.y + 140.dp.toPx()),
                durationMillis = 300,
            )
        }
        compose.waitUntilDoesNotExist(isDialog(), 5_000)

        // Wallet picker (short list)
        compose.onNodeWithContentDescription("钱包").performScrollTo().performClick()
        val walletBounds = sheetText("日常钱包").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).performTouchInput {
            repeat(3) {
                swipe(
                    start = walletBounds.center,
                    end = Offset(walletBounds.center.x, walletBounds.center.y + 180.dp.toPx()),
                    durationMillis = 60,
                )
            }
        }
        compose.waitForIdle()
        sheetText("选择钱包").assertIsDisplayed()
        sheetText("旅行钱包").performClick()
        compose.waitUntilDoesNotExist(isDialog(), 5_000)
        compose.onNode(hasContentDescription("钱包") and hasText("旅行钱包 · JPY")).assertExists()
    }

    @Test
    fun calculatorIgnoresKeypadAndConfirmSwipesAndDismissesOnlyFromHeader() {
        var applied: String? = null
        var open by mutableStateOf(true)
        compose.setContent {
            KitsuneTheme(ThemePreferences()) {
                Box(Modifier.width(320.dp).height(450.dp)) {
                    if (open) {
                        Calculator(2, "CNY", "12.50", dismiss = { open = false }, apply = {
                            applied = it
                            open = false
                        })
                    }
                }
            }
        }

        val keyFive = sheetText("5").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val confirmBounds = sheetText("确认金额").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).performTouchInput {
            repeat(3) {
                swipe(
                    start = keyFive.center,
                    end = Offset(keyFive.center.x, keyFive.center.y + 180.dp.toPx()),
                    durationMillis = 60,
                )
            }
            swipe(
                start = confirmBounds.center,
                end = Offset(confirmBounds.center.x, confirmBounds.center.y + 160.dp.toPx()),
                durationMillis = 70,
            )
        }
        compose.waitForIdle()
        sheetText("金额计算器").assertIsDisplayed()
        compose.runOnIdle { assertNull(applied) }

        // Fast downward pull followed by pause on calculator header must snap back.
        val titleBounds = sheetText("金额计算器").fetchSemanticsNode().boundsInRoot
        val restingTop = titleBounds.top
        compose.onNode(isDialog()).performTouchInput {
            down(titleBounds.center)
            moveTo(Offset(titleBounds.center.x, titleBounds.center.y + 26.dp.toPx()), delayMillis = 20)
            moveTo(Offset(titleBounds.center.x, titleBounds.center.y + 52.dp.toPx()), delayMillis = 20)
            advanceEventTime(200)
            up()
        }
        compose.waitForIdle()
        sheetText("金额计算器").assertIsDisplayed()
        assertEquals(restingTop, sheetText("金额计算器").fetchSemanticsNode().boundsInRoot.top, 2f)

        compose.onNode(isDialog()).performTouchInput {
            swipe(
                start = titleBounds.center,
                end = Offset(titleBounds.center.x, titleBounds.center.y + 140.dp.toPx()),
                durationMillis = 250,
            )
        }
        compose.waitUntilDoesNotExist(isDialog(), 5_000)
        compose.runOnIdle { assertNull(applied) }
    }

    @Test
    fun designSheetBlocksContentAndFooterSwipeDismiss() {
        var dismissed by mutableStateOf(false)
        compose.setContent {
            KitsuneTheme(ThemePreferences()) {
                var open by remember { mutableStateOf(true) }
                if (open) {
                    DesignSheet(
                        dismiss = {
                            dismissed = true
                            open = false
                        },
                        footer = { Primary("底部按钮", {}) },
                    ) {
                        Text("第一项")
                        Text("第二项")
                    }
                }
            }
        }

        val itemBounds = sheetText("第一项").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val footerBounds = sheetText("底部按钮").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).performTouchInput {
            repeat(3) {
                swipe(
                    start = itemBounds.center,
                    end = Offset(itemBounds.center.x, itemBounds.center.y + 180.dp.toPx()),
                    durationMillis = 60,
                )
            }
            swipe(
                start = footerBounds.center,
                end = Offset(footerBounds.center.x, footerBounds.center.y + 160.dp.toPx()),
                durationMillis = 70,
            )
        }
        compose.waitForIdle()
        sheetText("第一项").assertIsDisplayed()
        compose.runOnIdle { assertFalse(dismissed) }
        assertTrue(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())
    }

    private fun sheetText(text: String) = compose.onNode(hasText(text) and inDialog)
}
