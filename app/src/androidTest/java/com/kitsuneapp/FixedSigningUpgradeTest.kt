package com.kitsuneapp

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsuneapp.data.WalletDraft
import com.kitsuneapp.data.saveWallet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Runs only during the fixed-signature upgrade job on a fresh emulator.
 * The same signed instrumentation APK exercises Debug and Release in turn.
 */
@OptIn(ExperimentalTestApi::class)
class FixedSigningUpgradeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun walletSurvivesVariantSwitch() {
        val phase = InstrumentationRegistry.getArguments().getString("kitsuneUpgradePhase")
        assumeTrue("Only run in fixed-signature upgrade verification", phase != null)
        assertTrue("Unknown upgrade phase: $phase", phase == "seed" || phase == "verify")
        val name = "CI upgrade wallet"
        compose.waitUntilAtLeastOneExists(hasText("日常记录"), 20_000)
        val repository = runBlocking { (compose.activity.application as KitsuneApplication).repository() }
        runBlocking {
            if (phase == "seed") {
                assertTrue(repository.read().wallets.none { it.name == name })
                repository.saveWallet(WalletDraft(null, name, "JPY", "12345", 0, false, false))
            }
            val wallet = repository.read().wallets.single { it.name == name }
            assertEquals("JPY", wallet.currencyCode)
            assertEquals(12345L, wallet.openingBalanceMinor)
        }
        compose.onNodeWithText("记账").performClick()
        compose.onNodeWithText("钱包").performClick()
        compose.waitUntilAtLeastOneExists(hasText("$name · JPY"), 20_000)
    }
}
