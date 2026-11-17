package com.sirarikako.kitsune.core.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.sirarikako.kitsune.core.data.*

data class Preset(val id: String, val label: String, val hex: String)
val presets = listOf(Preset("apricot", "杏橙", "#C47A4A"), Preset("sage", "茶绿", "#5F7F64"),
    Preset("mist_blue", "雾蓝", "#587C9B"), Preset("lavender", "薰衣草", "#8A729F"), Preset("rose", "豆沙粉", "#A76F82"))
fun normalizedHex(value: String): String? = value.trim().removePrefix("#").uppercase(java.util.Locale.ROOT)
    .takeIf { Regex("[0-9A-F]{6}").matches(it) }?.let { "#$it" }
private fun argb(hex: String): Int = (0xFF000000L or hex.removePrefix("#").toLong(16)).toInt()
fun hexColor(hex: String) = Color(argb(hex))

// Material embeds the official utilities with library-group annotations. Keep the
// pinned implementation behind this adapter; contrast tests guard future upgrades.
@SuppressLint("RestrictedApi")
internal fun tonalScheme(seed: String, dark: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(argb(seed)), dark, 0.0)
    val c = MaterialDynamicColors()
    fun color(dynamic: com.google.android.material.color.utilities.DynamicColor) = Color(dynamic.getArgb(scheme))
    // Every role comes from Material's contrast-aware Tonal Spot scheme, including surfaces.
    return ColorScheme(
        primary = color(c.primary()), onPrimary = color(c.onPrimary()), primaryContainer = color(c.primaryContainer()), onPrimaryContainer = color(c.onPrimaryContainer()),
        inversePrimary = color(c.inversePrimary()), secondary = color(c.secondary()), onSecondary = color(c.onSecondary()), secondaryContainer = color(c.secondaryContainer()), onSecondaryContainer = color(c.onSecondaryContainer()),
        tertiary = color(c.tertiary()), onTertiary = color(c.onTertiary()), tertiaryContainer = color(c.tertiaryContainer()), onTertiaryContainer = color(c.onTertiaryContainer()),
        background = color(c.background()), onBackground = color(c.onBackground()), surface = color(c.surface()), onSurface = color(c.onSurface()),
        surfaceVariant = color(c.surfaceVariant()), onSurfaceVariant = color(c.onSurfaceVariant()), surfaceTint = color(c.primary()),
        inverseSurface = color(c.inverseSurface()), inverseOnSurface = color(c.inverseOnSurface()),
        error = color(c.error()), onError = color(c.onError()), errorContainer = color(c.errorContainer()), onErrorContainer = color(c.onErrorContainer()),
        outline = color(c.outline()), outlineVariant = color(c.outlineVariant()), scrim = color(c.scrim()),
        surfaceBright = color(c.surfaceBright()), surfaceDim = color(c.surfaceDim()),
        surfaceContainer = color(c.surfaceContainer()), surfaceContainerHigh = color(c.surfaceContainerHigh()),
        surfaceContainerHighest = color(c.surfaceContainerHighest()), surfaceContainerLow = color(c.surfaceContainerLow()), surfaceContainerLowest = color(c.surfaceContainerLowest()),
    )
}
@Composable
fun KitsuneTheme(preferences: ThemePreferences, darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkOverride ?: when (preferences.themeMode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumeGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeGeneration++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val seed = if (preferences.colorSource == ColorSource.CUSTOM) preferences.customSeedColor
        else presets.find { it.id == preferences.presetId }?.hex ?: presets.first().hex
    val scheme = if (preferences.colorSource == ColorSource.SYSTEM && Build.VERSION.SDK_INT >= 31) {
        remember(context, dark, resumeGeneration) { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
    } else remember(seed, dark) { tonalScheme(seed, dark) }
    MaterialTheme(colorScheme = scheme, shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)), content = content)
}
