package com.kitsuneapp

import android.content.res.Configuration
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.kitsuneapp.data.ThemeMode
import com.kitsuneapp.data.ThemePreferences
import com.kitsuneapp.ui.KitsuneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class StartupThemeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    @Test
    fun launchWindowUsesNeutralColorsAndMatchingSystemBars() {
        val activity = compose.activity
        for (dark in listOf(false, true)) {
            val config = Configuration(activity.resources.configuration)
            val nightMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            val context = ContextThemeWrapper(activity.createConfigurationContext(config), R.style.AppTheme)
            val attributes = context.obtainStyledAttributes(intArrayOf(
                android.R.attr.windowBackground, android.R.attr.windowLightStatusBar, android.R.attr.windowLightNavigationBar,
            ))
            try {
                assertEquals(if (dark) 0xFF202020.toInt() else 0xFFF4F4F4.toInt(), attributes.getColor(0, 0))
                assertEquals(!dark, attributes.getBoolean(1, dark))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    assertEquals(!dark, attributes.getBoolean(2, dark))
                }
            } finally {
                attributes.recycle()
            }
        }
    }

    @Test
    fun firstSavedThemeIsRenderedImmediatelyAndLaterChangesAnimate() {
        var preferences by mutableStateOf<ThemePreferences?>(null)
        val activity = compose.activity
        val dark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        // Match the system brightness so a dark-mode switch cannot mask an initial color animation.
        val saved = ThemePreferences(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, presetId = "sage")
        val savedBackground = if (dark) 0xFF1C201D.toInt() else 0xFFE7EBE8.toInt()
        val laterBackground = if (dark) 0xFF1C1F22.toInt() else 0xFFE7EBEF.toInt()
        compose.setContent {
            KitsuneTheme(preferences) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("startup-surface"))
            }
        }
        assertEquals(activity.getColor(R.color.startup_background), backgroundPixel())

        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { preferences = saved }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(savedBackground, backgroundPixel())

            compose.runOnIdle { preferences = saved.copy(presetId = "mist_blue") }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(savedBackground, backgroundPixel())
            compose.mainClock.advanceTimeBy(400)
            assertEquals(laterBackground, backgroundPixel())
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun backgroundPixel() = compose.onNodeWithTag("startup-surface").captureToImage().asAndroidBitmap().getPixel(8, 8)
}
