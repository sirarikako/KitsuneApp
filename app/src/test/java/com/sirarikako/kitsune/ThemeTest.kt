package com.sirarikako.kitsune

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.sirarikako.kitsune.core.theme.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ThemeTest {
    @Test fun hexNormalizationRejectsAlphaAndPartialInput() {
        assertEquals("#ABCDEF", normalizedHex(" abcdef "))
        listOf("#ABC", "#FFABCDEF", "#GG0000", "red", "").forEach { assertNull(normalizedHex(it)) }
    }
    @Test fun tonalSpotPreservesTextContrastForPresetsAndExtremeSeeds() {
        fun ratio(a: Color, b: Color): Double {
            val x = a.luminance().toDouble(); val y = b.luminance().toDouble()
            return (max(x, y) + 0.05) / (min(x, y) + 0.05)
        }
        (presets.map { it.hex } + listOf("#000000", "#FFFFFF", "#FF0000", "#00FF00", "#0000FF")).forEach { seed ->
            listOf(false, true).forEach { dark ->
                val c = tonalScheme(seed, dark)
                listOf(c.primary to c.onPrimary, c.primaryContainer to c.onPrimaryContainer,
                    c.surface to c.onSurface, c.surfaceContainer to c.onSurface,
                    c.background to c.onBackground, c.secondaryContainer to c.onSecondaryContainer,
                    c.error to c.onError, c.tertiary to c.onTertiary).forEach { (background, foreground) ->
                    assertTrue("$seed dark=$dark contrast=${ratio(background, foreground)}", ratio(background, foreground) >= 4.5)
                }
            }
        }
    }
}
