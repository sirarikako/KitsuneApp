package com.kitsuneapp

import android.content.pm.ActivityInfo
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsuneapp.data.EntryType
import com.kitsuneapp.data.LedgerEntryFilters
import com.kitsuneapp.feature.ledger.LedgerEntryFilterDialog
import com.kitsuneapp.ui.KitsuneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

@OptIn(ExperimentalTestApi::class)
class SelectionSheetLayoutTest {
    // The bottom sheet has its own Android window and needs the actual display width.
    @get:Rule(order = 0)
    val narrowDisplay = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val size = displayOverride("size", "Override size: ")
                val density = displayOverride("density", "Override density: ")
                val fontScale = shell("settings get system font_scale").trim()
                try {
                    shell("wm density 320")
                    shell("wm size 640x1280")
                    shell("settings put system font_scale 1.0")
                    base.evaluate()
                } finally {
                    shell("wm size $size")
                    shell("wm density $density")
                    if (fontScale == "null") shell("settings delete system font_scale")
                    else shell("settings put system font_scale $fontScale")
                }
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ledgerFilterFitsAllTypesOnOneRowAndKeepsControlsFixedAt320Dp() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil("320dp portrait viewport with default font scale", 10_000) {
            val configuration = compose.activity.resources.configuration
            configuration.screenWidthDp == 320 && configuration.fontScale == 1f
        }
        val records = selectionRecords().withMoreFilterCategories()
        compose.setContent {
            KitsuneTheme(records.theme) {
                LedgerEntryFilterDialog(LedgerEntryFilters(), records.categories, dismiss = {}, apply = {})
            }
        }
        val inDialog = hasAnyAncestor(isDialog())
        val title = compose.onNode(hasText("流水筛选") and inDialog)
        val apply = compose.onNode(hasText("应用") and inDialog)
        val list = compose.onNode(hasScrollToNodeAction() and inDialog)
        val types = EntryType.entries.map { compose.onNode(hasText(it.label) and inDialog) }
        compose.waitUntil("ledger filter sheet visible", 10_000) { title.isDisplayed() && apply.isDisplayed() }
        val rows = types.map { it.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top }
        assertEquals("All ledger entry types should fit on one row at 320dp", 1, rows.distinct().size)

        val titleBefore = title.fetchSemanticsNode().boundsInRoot
        val applyBefore = apply.fetchSemanticsNode().boundsInRoot
        list.performScrollToNode(hasText("图书影音"))
        compose.onNode(hasText("图书影音") and inDialog).assertIsDisplayed()
        assertEquals(titleBefore, title.assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        assertEquals(applyBefore, apply.assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
    }

    private fun displayOverride(property: String, prefix: String) = shell("wm $property").lineSequence()
        .firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim() ?: "reset"

    private fun shell(command: String): String {
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(result).bufferedReader().use { it.readText() }
    }
}
