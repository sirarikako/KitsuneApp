package com.kitsuneapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.kitsuneapp.data.presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ThemeContrastTest {
    @Test
    fun apricotRolesMatchRenderedDesignInBothModes() {
        val expected = listOf(
            "f6f3ed fffcf7 eee7dc e5ded4 f4e5d8 8c5a39 302b26 706459 46624a a63832 fff8f7",
            "201e1b 2a2723 383129 443c34 463325 e4bda0 f3eae1 baaea2 b7d0ae ffb4ab 451714",
        )
        for ((index, dark) in listOf(false, true).withIndex()) {
            val palette = designColors(Color(0xFFC47A4A), dark)
            val roles = listOf(palette.background, palette.surface, palette.surfaceVariant, palette.outlineVariant,
                palette.primaryContainer, palette.primary, palette.onSurface, palette.onSurfaceVariant,
                palette.tertiary, palette.error, palette.onError)
            assertEquals(expected[index], roles.joinToString(" ") { "%06x".format(it.toArgb() and 0xFFFFFF) })
        }
    }

    @Test
    fun tonalSpotTextRemainsReadableAcrossPresetsAndExtremeSeeds() {
        val extremes = listOf("#000000", "#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF")
        for (hex in presets.values.map { it.second } + extremes) {
            val seed = Color(0xFF000000L or hex.substring(1).toLong(16))
            for (dark in listOf(false, true)) {
                val roles = designColors(seed, dark)
                val pairs = listOf(
                    roles.onBackground to roles.background,
                    roles.onSurface to roles.surface,
                    roles.onSurfaceVariant to roles.surface,
                    roles.onPrimary to roles.primary,
                    roles.onPrimaryContainer to roles.primaryContainer,
                    roles.onError to roles.error,
                    roles.error to roles.surface,
                    roles.tertiary to roles.surface,
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
