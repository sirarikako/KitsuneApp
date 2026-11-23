package com.kitsuneapp

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kitsuneapp.data.ColorSource
import com.kitsuneapp.data.ThemeMode
import com.kitsuneapp.data.ThemePreferences
import com.kitsuneapp.ui.KitsuneTheme
import com.kitsuneapp.ui.Paper
import com.kitsuneapp.ui.expenseColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class WallpaperThemeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersDistinctSystemTonesForPageAndCardInBothModes() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            assumeTrue("System wallpaper colors require Android 12", false)
            return
        }
        var mode by mutableStateOf(ThemeMode.LIGHT)
        lateinit var appliedColors: ColorScheme
        var appliedExpense = Color.Unspecified
        compose.setContent {
            KitsuneTheme(ThemePreferences(themeMode = mode, colorSource = ColorSource.SYSTEM)) {
                val palette = MaterialTheme.colorScheme
                val expense = expenseColor
                SideEffect {
                    appliedColors = palette
                    appliedExpense = expense
                }
                Surface(Modifier.fillMaxWidth().testTag("wallpaper-page"), color = palette.background) {
                    Box(Modifier.padding(16.dp)) {
                        Paper(Modifier.testTag("wallpaper-card")) { Text("早餐") }
                    }
                }
            }
        }

        for (themeMode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            compose.runOnIdle { mode = themeMode }
            val page = sampleBackground("wallpaper-page")
            val card = sampleBackground("wallpaper-card")
            val dark = themeMode == ThemeMode.DARK
            val platform = if (dark) dynamicDarkColorScheme(compose.activity) else dynamicLightColorScheme(compose.activity)
            assertEquals((if (dark) platform.background else platform.surfaceContainerLow).toArgb(), page.toArgb())
            assertEquals((if (dark) platform.surfaceContainer else platform.surfaceContainerLowest).toArgb(), card.toArgb())
            assertTrue("$themeMode: the card should be lighter than the page", card.luminance() > page.luminance())
            compose.runOnIdle {
                assertEquals(platform.primary, appliedColors.primary)
                assertReadable(appliedColors.onBackground, page)
                assertReadable(appliedColors.onSurface, card)
                assertReadable(appliedColors.onSurfaceVariant, card)
                assertReadable(appliedExpense, card)
                assertReadable(appliedExpense, page)
                assertNotEquals(appliedColors.tertiary, appliedExpense)
                assertNotEquals(appliedColors.error, appliedExpense)
            }
        }
    }

    private fun sampleBackground(tag: String): Color {
        val image = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val inset = with(compose.density) { 6.dp.roundToPx() }
        return Color(image.getPixel(image.width / 2, inset))
    }

    private fun assertReadable(text: Color, background: Color) {
        val first = text.luminance()
        val second = background.luminance()
        val contrast = (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
        assertTrue("Text $text on $background has contrast $contrast", contrast >= 4.5f)
    }
}
