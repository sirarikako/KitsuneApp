package com.kitsuneapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.kitsuneapp.data.*
import com.kitsuneapp.feature.ledger.LedgerPage
import com.kitsuneapp.ui.KitsuneTheme
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LedgerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsTrailingSignedAmountAndIndependentWalletOnNarrowDarkLayout() {
        val now = "2026-11-17T12:00:00Z"
        val regular = Wallet("regular", "日常钱包", "日常钱包", "CNY", 2, 10_000, 0, false, false,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, now, now)
        val separate = regular.copy(id = "separate", name = "旅行钱包", currencyCode = "JPY", currencyScale = 0,
            separateAccounting = true)
        val entry = FinancialEntry("expense", LocalDate.now().toString(), "12:00", EntryType.EXPENSE,
            regular.id, 1_250, "expense-category", null, null, null, "", now, now)
        val category = Category("expense-category", Scope.EXPENSE, null, "", "餐饮", "餐饮", 0, false, false, now, now)
        val records = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
            ThemePreferences(), emptyList(), listOf(category), emptyList(), emptyMap(), listOf(regular, separate), listOf(entry))
        var detail: String? = null
        compose.setContent {
            KitsuneTheme(records.theme, forceDark = true) {
                Surface(Modifier.width(320.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
                    Box { LedgerPage(records, open = {}, openDetails = { detail = it }) }
                }
            }
        }
        val title = compose.onNodeWithText("餐饮", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText("−12.50", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val currency = compose.onNodeWithText("CNY", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(value.left > title.right)
        assertTrue(currency.top >= value.bottom)
        compose.onNodeWithText("旅行钱包", substring = true).assertDoesNotExist()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "kitsune-ledger-narrow-dark.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        compose.onNodeWithText("餐饮").performClick()
        compose.runOnIdle { assertEquals(entry.id, detail) }
        compose.onNodeWithText("钱包").performClick()
        compose.onNodeWithText("我的钱包").assertIsDisplayed()
        compose.onNodeWithText("日常钱包 · CNY").assertIsDisplayed()
        compose.onNodeWithText("87.50").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("旅行钱包 · JPY"))
        compose.onNodeWithText("旅行钱包 · JPY").assertIsDisplayed()
    }
}
