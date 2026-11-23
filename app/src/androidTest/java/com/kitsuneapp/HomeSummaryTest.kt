package com.kitsuneapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsuneapp.data.CategoryMode
import com.kitsuneapp.data.ColorSource
import com.kitsuneapp.data.DataState
import com.kitsuneapp.data.EntryType
import com.kitsuneapp.data.FinancialEntry
import com.kitsuneapp.data.MealRecord
import com.kitsuneapp.data.MealSlot
import com.kitsuneapp.data.Records
import com.kitsuneapp.data.ThemePreferences
import com.kitsuneapp.data.Wallet
import com.kitsuneapp.ui.KitsuneTheme
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeSummaryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = LocalDateTime.of(2026, 11, 23, 8, 30)
    private val date = now.toLocalDate().toString()
    private val stamp = "2026-11-23T08:30:00Z"
    private val empty = Records(DataState(installationId = "test", datasetId = "test", arrayElements = 0),
        ThemePreferences(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyList(), emptyList())

    @Test
    fun emptySummaryCardsShareGeometryAndHeadings() {
        compose.setContent { HomeViewport { Home(empty, now, {}, { _, _, _ -> }) } }
        compose.onNodeWithText("暂无常规钱包的收支").assertIsDisplayed()
        assertCardGeometry()
        capture("home-android-light-empty")
    }

    @Test
    fun recommendedActionsPassTheDateSlotAndExistingId() {
        val clock = mutableStateOf(now)
        val records = mutableStateOf(empty)
        var selection: Triple<String, MealSlot, String?>? = null
        compose.setContent {
            HomeViewport {
                Home(records.value, clock.value, {}, { day, slot, id -> selection = Triple(day, slot, id) })
            }
        }
        for ((hour, slot) in listOf(10 to MealSlot.BREAKFAST, 11 to MealSlot.LUNCH, 17 to MealSlot.DINNER)) {
            compose.runOnIdle {
                clock.value = now.withHour(hour)
                records.value = empty
            }
            compose.onNodeWithText("记录" + slot.label).assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(Triple(date, slot, null), selection)
                records.value = empty.copy(meals = listOf(MealRecord("meal-$hour", date, slot,
                    false, null, "面条", "", stamp, stamp)))
            }
            compose.onNodeWithText("编辑" + slot.label).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(Triple(date, slot, "meal-$hour"), selection) }
        }
    }

    @Test
    fun narrowDarkSummaryKeepsAllDigitsAndPlacesCnyFirst() {
        val cny = wallet("CNY")
        val original = FinancialEntry("old-expense", "2026-10-30", "08:30", EntryType.EXPENSE,
            cny.id, Long.MAX_VALUE, "expense", null, null, null, "", stamp, stamp)
        val refund = original.copy(id = "refund", date = date, type = EntryType.REFUND,
            categoryId = null, originalExpenseId = original.id)
        val income = original.copy(id = "income", date = date, type = EntryType.INCOME, categoryId = "income")
        val records = empty.copy(wallets = listOf(wallet("CAD"), cny, wallet("JPY"), wallet("USD", separate = true)),
            entries = listOf(refund, income, income.copy(id = "income-2"), original))
        compose.setContent {
            HomeViewport(width = 320, fontScale = 1.7f, dark = true) {
                Home(records, now, {}, { _, _, _ -> })
            }
        }

        val codes = compose.onAllNodes(hasText("CNY") or hasText("CAD") or hasText("JPY"))
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].single().text }
        assertEquals(listOf("CNY", "CAD", "JPY"), codes)
        compose.onNodeWithText("USD").assertDoesNotExist()
        capture("home-android-dark-320")

        val expense = "-92,233,720,368,547,758.07"
        val revenue = "184,467,440,737,095,516.14"
        val expenseNode = compose.onNodeWithText(expense).performScrollTo().assertIsDisplayed()
        val revenueNode = compose.onNodeWithText(revenue).performScrollTo().assertIsDisplayed()
        for (node in listOf(expenseNode, revenueNode)) {
            val layout = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertFalse(layout.single().hasVisualOverflow)
        }
        assertTrue(expenseNode.fetchSemanticsNode().boundsInRoot.bottom <= revenueNode.fetchSemanticsNode().boundsInRoot.top)
        capture("home-android-dark-320-amounts")
        compose.onNodeWithText("记一笔").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun customColorShowsOneCurrencyWithZeroIncomeAndExpense() {
        val records = empty.copy(wallets = listOf(wallet("JPY")),
            theme = ThemePreferences(colorSource = ColorSource.CUSTOM, customSeedColor = "#4B638F"))
        compose.setContent { HomeViewport(theme = records.theme) { Home(records, now, {}, { _, _, _ -> }) } }

        compose.onNodeWithText("JPY").assertIsDisplayed()
        compose.onNodeWithText("CNY").assertDoesNotExist()
        assertEquals(2, compose.onAllNodes(hasText("0") and hasAnyAncestor(hasTestTag("home-ledger-card")))
            .fetchSemanticsNodes().size)
        assertCardGeometry()
        capture("home-android-custom-jpy-zero")
    }

    @Test
    fun dynamicColorKeepsTheTwoCardsAligned() {
        val records = empty.copy(wallets = listOf(wallet("CNY")),
            theme = ThemePreferences(colorSource = ColorSource.SYSTEM))
        compose.setContent {
            HomeViewport(theme = records.theme) { Home(records, now, {}, { _, _, _ -> }) }
        }

        compose.onNodeWithText("CNY").assertIsDisplayed()
        assertCardGeometry()
        capture("home-android-dynamic")
    }

    @Composable
    private fun HomeViewport(
        width: Int = 430,
        fontScale: Float = 1f,
        dark: Boolean = false,
        theme: ThemePreferences = empty.theme,
        content: @Composable () -> Unit,
    ) {
        KitsuneTheme(theme, forceDark = dark) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.widthIn(max = width.dp).fillMaxWidth().fillMaxHeight().testTag("home-viewport"),
                        color = MaterialTheme.colorScheme.background, content = content)
                }
            }
        }
    }

    private fun assertCardGeometry() {
        val meals = compose.onNodeWithTag("home-meals-card").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val ledger = compose.onNodeWithTag("home-ledger-card").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val recordMeal = compose.onNodeWithText("记录早餐").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val addEntry = compose.onNodeWithText("记一笔").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(meals.left, ledger.left, 1f)
        assertEquals(meals.width, ledger.width, 1f)
        val gap = with(compose.density) { 16.dp.toPx() }
        assertEquals(gap, ledger.top - meals.bottom, 1f)
        assertEquals(gap, recordMeal.left - meals.left, 1f)
        assertEquals(recordMeal.left, addEntry.left, 1f)
        assertEquals(recordMeal.width, addEntry.width, 1f)
        assertEquals(recordMeal.height, addEntry.height, 1f)
        for ((title, tag) in listOf("今日用餐" to "home-meals-card", "本月收支" to "home-ledger-card")) {
            val node = compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag(tag))).assertIsDisplayed()
            assertTrue(node.fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
        }
        compose.onNode(hasText("全部记录") and hasAnyAncestor(hasTestTag("home-meals-card"))).assertIsDisplayed()
        compose.onNode(hasText("查看账本") and hasAnyAncestor(hasTestTag("home-ledger-card"))).assertIsDisplayed()
    }

    private fun wallet(code: String, separate: Boolean = false) =
        Wallet(code, code, code, code, if (code == "JPY") 0 else 2, 0, 0, false, separate,
            CategoryMode.DEFAULT, CategoryMode.DEFAULT, stamp, stamp)

    private fun capture(name: String) {
        val configured = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
        val destination = configured?.takeIf { it.isDirectory || it.mkdirs() } ?: compose.activity.cacheDir
        val bitmap = compose.onNodeWithTag("home-viewport").captureToImage().asAndroidBitmap()
        File(destination, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
