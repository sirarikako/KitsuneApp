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
    fun presetSurfacesMatchRenderedDesignInBothModes() {
        val expectedByPreset = mapOf(
            "apricot" to listOf("f4ebe5 fcf9f7 ece1da dfd2c9", "271f1a 362c25 43372f 4f4138"),
            "sage" to listOf("e7ebe8 f8f9f8 dde2de cdd3ce", "1c201d 282c29 323833 3c423d"),
            "mist_blue" to listOf("e7ebef f7f9fa dce1e6 ccd2d8", "1c1f22 272c30 31373c 3b4248"),
            "lavender" to listOf("edeaef faf9fb e3e0e6 d5d0d8", "211e23 2e2b31 39353d 444048"),
            "rose" to listOf("f0e9ec fbf9f9 e8dfe2 dad0d3", "241e20 322a2d 3e3538 4a3f43"),
        )
        for ((id, preset) in presets) {
            for ((index, dark) in listOf(false, true).withIndex()) {
                val colors = designColors(seedColor(preset.second), dark, usePresetTones = true)
                val roles = listOf(colors.background, colors.surface, colors.surfaceVariant, colors.outlineVariant)
                assertEquals("$id dark=$dark", expectedByPreset.getValue(id)[index], roles.joinToString(" ", transform = ::rgb))
                assertTrue("$id dark=$dark", colors.surface.luminance() > colors.background.luminance())
            }
        }
    }

    @Test
    fun customApricotRolesMatchRenderedDesignInBothModes() {
        val expected = listOf(
            "f6f3ed fffcf7 eee7dc e5ded4 f4e5d8 8c5a39 302b26 706459 46624a a63832 fff8f7",
            "201e1b 2a2723 383129 443c34 463325 e4bda0 f3eae1 baaea2 b7d0ae ffb4ab 451714",
        )
        for ((index, dark) in listOf(false, true).withIndex()) {
            val palette = designColors(Color(0xFFC47A4A), dark, usePresetTones = false)
            val roles = listOf(palette.background, palette.surface, palette.surfaceVariant, palette.outlineVariant,
                palette.primaryContainer, palette.primary, palette.onSurface, palette.onSurfaceVariant,
                palette.tertiary, palette.error, palette.onError)
            assertEquals(expected[index], roles.joinToString(" ", transform = ::rgb))
        }
    }

    @Test
    fun tonalSpotTextRemainsReadableAcrossPresetsAndExtremeSeeds() {
        val extremes = listOf("#000000", "#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF")
        val seeds = presets.values.map { it.second to true } + extremes.map { it to false }
        for ((hex, usePresetTones) in seeds) {
            for (dark in listOf(false, true)) {
                val roles = designColors(seedColor(hex), dark, usePresetTones)
                val pairs = listOf(
                    roles.onBackground to roles.background,
                    roles.onSurface to roles.surface,
                    roles.onSurfaceVariant to roles.surface,
                    roles.onSurfaceVariant to roles.surfaceVariant,
                    roles.onSurfaceVariant to roles.background,
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

    private fun seedColor(hex: String) = Color(0xFF000000L or hex.substring(1).toLong(16))

    private fun rgb(color: Color) = "%06x".format(color.toArgb() and 0xFFFFFF)
}
