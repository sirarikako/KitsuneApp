package com.kitsuneapp.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import kotlin.math.roundToInt

// Match the visual roles in makePalette in docs/ui/src/app/01-state.js.inc.
// The platform and Tonal Spot still provide the rest of the Material roles.
internal fun designColors(seed: Color, dark: Boolean, usePresetTones: Boolean): ColorScheme {
    // Mixing the seed with neutral grays keeps the preset's hue while changing lightness.
    val background = if (usePresetTones) {
        mix(seed, Color(if (dark) 0xFF151515 else 0xFFFAFAFA), if (dark) 0.10 else 0.12)
    } else Color(if (dark) 0xFF201E1B else 0xFFF6F3ED)
    val paper = if (usePresetTones) {
        mix(seed, Color(if (dark) 0xFF1F1F1F else 0xFFFFFFFF), if (dark) 0.14 else 0.045)
    } else Color(if (dark) 0xFF2A2723 else 0xFFFFFCF7)
    val soft = if (usePresetTones) {
        mix(seed, Color(if (dark) 0xFF292929 else 0xFFF3F3F3), if (dark) 0.17 else 0.15)
    } else Color(if (dark) 0xFF383129 else 0xFFEEE7DC)
    val line = if (usePresetTones) {
        mix(seed, Color(if (dark) 0xFF343434 else 0xFFE5E5E5), if (dark) 0.19 else 0.18)
    } else Color(if (dark) 0xFF443C34 else 0xFFE5DED4)
    val selected = mix(seed, if (dark) Color(0xFF211E1A) else paper, if (dark) 0.23 else 0.18)
    val surfaces = listOf(background, paper, soft, selected)
    val accent = readable(mix(seed, Color(if (dark) 0xFFFFF4E6 else 0xFF342C23), if (dark) 0.45 else 0.72),
        surfaces, dark)
    val ink = readable(Color(if (dark) 0xFFF3EAE1 else 0xFF302B26), surfaces, dark)
    val dim = readable(Color(if (dark) 0xFFBAAEA2 else 0xFF76695E), surfaces, dark)
    val danger = readable(Color(if (dark) 0xFFFFB4AB else 0xFFA63832), surfaces, dark)
    val onDanger = readable(Color(if (dark) 0xFF451714 else 0xFFFFF8F7), listOf(danger), !dark)
    return tonalColors(seed, dark).copy(
        background = background, onBackground = ink,
        surface = paper, onSurface = ink, surfaceVariant = soft, onSurfaceVariant = dim,
        surfaceContainerLowest = paper, surfaceContainerLow = paper, surfaceContainer = paper,
        surfaceContainerHigh = paper, surfaceContainerHighest = soft,
        surfaceBright = paper, surfaceDim = background, surfaceTint = Color.Transparent,
        outline = dim, outlineVariant = line,
        primary = accent, onPrimary = paper, primaryContainer = selected, onPrimaryContainer = accent,
        tertiary = readable(designIncomeColor(dark), surfaces, dark), onTertiary = paper,
        error = danger, onError = onDanger,
    )
}

internal fun designIncomeColor(dark: Boolean) =
    Color(if (dark) 0xFFB7D0AE else 0xFF46624A)

internal fun designExpenseColor(colors: ColorScheme, dark: Boolean): Color =
    readable(Color(if (dark) 0xFFE8B276 else 0xFF82551F), listOf(colors.background, colors.surface), dark)

private fun mix(first: Color, second: Color, weight: Double): Color {
    val a = first.toArgb()
    val b = second.toArgb()
    fun channel(shift: Int) = (((a shr shift) and 255) * weight + ((b shr shift) and 255) * (1 - weight)).roundToInt()
    return Color(channel(16), channel(8), channel(0))
}

private fun readable(color: Color, surfaces: List<Color>, dark: Boolean): Color {
    val backgrounds = surfaces.map(::luminance)
    val endpoint = if (dark) Color.White else Color.Black
    for (step in 0..100) {
        val candidate = mix(color, endpoint, 1 - step / 100.0)
        val text = luminance(candidate)
        if (backgrounds.all { (maxOf(text, it) + 0.05) / (minOf(text, it) + 0.05) >= 4.6 }) return candidate
    }
    error("Design palette cannot reach the required contrast")
}

private fun luminance(color: Color): Double {
    val rgb = color.toArgb()
    fun linear(shift: Int): Double {
        val channel = ((rgb shr shift) and 255) / 255.0
        return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    }
    return linear(16) * 0.2126 + linear(8) * 0.7152 + linear(0) * 0.0722
}
