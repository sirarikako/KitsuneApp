package com.kitsuneapp

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsuneapp.data.ColorSource
import com.kitsuneapp.feature.ledger.EntryForm
import com.kitsuneapp.feature.ledger.LedgerPage
import com.kitsuneapp.feature.ledger.WalletForm
import com.kitsuneapp.ui.KitsuneTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class SelectionSheetsScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val inDialog = hasAnyAncestor(isDialog())

    @Test
    fun capturesSelectionSheets() {
        val profile = InstrumentationRegistry.getArguments().getString("selectionProfile")
        assumeTrue("Run via capture-selection-sheets.sh", profile != null)
        checkNotNull(profile)
        val configuration = compose.activity.resources.configuration
        val isDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (profile == "narrow" || profile == "narrow-large") {
            assertEquals(320, configuration.screenWidthDp)
            assertEquals(if (profile == "narrow-large") 1.5f else 1f, configuration.fontScale, .01f)
        }
        assertEquals(profile == "dark", isDark)
        val records = selectionRecords().withMoreFilterCategories()
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java] }
        var screen by mutableStateOf("entry")
        val calculatorTheme = records.theme.copy(colorSource = ColorSource.CUSTOM, customSeedColor = "#386E78")
        var reviewCustomCalculator by mutableStateOf(profile == "narrow-large")
        compose.setContent {
            val theme = if (reviewCustomCalculator) calculatorTheme else records.theme
            KitsuneTheme(theme, forceDark = isDark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (screen) {
                        "entry" -> EntryForm(records, null, null, model, {}, {})
                        "ledger" -> LedgerPage(records, open = {}, openDetails = {})
                        "currency" -> WalletForm(records, null, model, {})
                    }
                }
            }
        }

        listOf("1", "2", "8", ".", "5", "0", "+", "3", "6").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        compose.onNodeWithText("164.50").performScrollTo()
        capture("金额计算器", "calculator", profile)
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.runOnIdle { reviewCustomCalculator = false }
        compose.onNodeWithContentDescription("钱包").performScrollTo().performClick()
        capture("选择钱包", "wallet", profile)
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithContentDescription("分类").performScrollTo().performClick()
        capture("选择分类", "category", profile)
        compose.onNodeWithContentDescription("关闭").performClick()

        compose.runOnIdle { screen = "ledger" }
        compose.onNodeWithText("筛选").performScrollTo().performClick()
        val list = compose.onNode(hasScrollToNodeAction() and inDialog)
        capture("流水筛选", "ledger-filter", profile)

        sheetText("支出").performClick()
        list.performScrollToNode(hasText("餐饮"))
        sheetText("餐饮").performClick()
        list.performScrollToIndex(0)
        capture("流水筛选", "ledger-filter-partial", profile)

        list.performScrollToNode(hasText("图书影音"))
        capture("流水筛选", "ledger-filter-categories", profile)
        compose.onNodeWithContentDescription("关闭").performClick()

        compose.onNodeWithContentDescription("钱包范围").performClick()
        sheetText("手动选择").performClick()
        list.performScrollToNode(hasText("旧钱包"))
        capture("钱包范围", "wallet-range", profile)
        compose.onNodeWithContentDescription("关闭").performClick()

        compose.runOnIdle { screen = "currency" }
        compose.onNodeWithContentDescription("币种").performScrollTo().performClick()
        compose.onNodeWithContentDescription("搜索代码或名称").performTextReplacement("jpy")
        capture("选择币种", "currency", profile)
    }

    private fun sheetText(text: String) = compose.onNode(hasText(text) and inDialog)

    private fun capture(title: String, name: String, profile: String) {
        sheetText(title).assertIsDisplayed()
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // A Compose node can be visible before Android presents the new dialog window.
        automation.waitForIdle(300L, 5_000L)
        if (Build.VERSION.SDK_INT >= 29) {
            val frameCommitted = CountDownLatch(1)
            compose.runOnUiThread {
                val view = WindowInspector.getGlobalWindowViews().lastOrNull {
                    it.hasWindowFocus() && it.isHardwareAccelerated
                }
                if (view != null) {
                    view.viewTreeObserver.registerFrameCommitCallback { frameCommitted.countDown() }
                    view.invalidate()
                } else {
                    frameCommitted.countDown()
                }
            }
            check(frameCommitted.await(5, TimeUnit.SECONDS)) { "The selection sheet did not render a new frame." }
        }
        val bitmap = checkNotNull(automation.takeScreenshot())
        val file = "selection-$profile-$name"
        File(compose.activity.cacheDir, "$file.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        val review = InstrumentationRegistry.getArguments().getString("selectionVisualReview") == "true"
        if (review && (name == "wallet-range" || name.startsWith("ledger-filter"))) {
            val width = minOf(bitmap.width, 420)
            val height = bitmap.height * width / bitmap.width
            val thumbnail = Bitmap.createScaledBitmap(bitmap, width, height, true)
            File(compose.activity.cacheDir, "$file.jpg").outputStream().use {
                check(thumbnail.compress(Bitmap.CompressFormat.JPEG, 78, it))
            }
        }
    }
}
