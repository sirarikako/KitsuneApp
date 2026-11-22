package com.kitsuneapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kitsuneapp.data.presets
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ThemeContrastTest {
    @Test
    fun tonalSpotTextRemainsReadableAcrossPresetsAndExtremeSeeds() {
        val extremes = listOf("#000000", "#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF")
        for (hex in presets.values.map { it.second } + extremes) {
            val seed = Color(0xFF000000L or hex.substring(1).toLong(16))
            for (dark in listOf(false, true)) {
                val roles = tonalColors(seed, dark)
                val pairs = listOf(
                    roles.onBackground to roles.background,
                    roles.onSurface to roles.surface,
                    roles.onSurfaceVariant to roles.surface,
                    roles.onPrimary to roles.primary,
                    roles.onPrimaryContainer to roles.primaryContainer,
                    roles.onError to roles.error,
                    roles.error to roles.surface,
                )
                pairs.forEachIndexed { index, (text, background) ->
                    val first = text.luminance()
                    val second = background.luminance()
                    val ratio = (max(first, second) + 0.05f) / (min(first, second) + 0.05f)
                    assertTrue("$hex dark=$dark role=$index contrast=$ratio", ratio >= 4.5f)
                }
            }
        }
    }
}
