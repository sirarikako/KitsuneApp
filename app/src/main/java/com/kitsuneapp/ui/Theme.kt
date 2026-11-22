package com.kitsuneapp.ui

import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsuneapp.data.ColorSource
import com.kitsuneapp.data.ThemeMode
import com.kitsuneapp.data.ThemePreferences
import com.kitsuneapp.data.presets
import com.materialkolor.dynamicColorScheme
import com.materialkolor.ktx.animateColorScheme

val LocalDarkMode = staticCompositionLocalOf { false }
val incomeColor: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary
val motion = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
internal val CardRadius = 17.dp
val CardShape = RoundedCornerShape(CardRadius)
val ControlShape = RoundedCornerShape(12.dp)
val DialogShape = RoundedCornerShape(24.dp)

internal fun tonalColors(seed: Color, dark: Boolean): ColorScheme = dynamicColorScheme(
    seedColor = seed, isDark = dark, isAmoled = false, contrastLevel = 0.25,
)

private val type = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 33.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 27.sp),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
)

@Composable
fun KitsuneTheme(preferences: ThemePreferences, forceDark: Boolean? = null, refresh: Int = 0, content: @Composable () -> Unit) {
    val dark = forceDark ?: when (preferences.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors: ColorScheme = remember(preferences, dark, context, refresh) {
        if (preferences.colorSource == ColorSource.SYSTEM && Build.VERSION.SDK_INT >= 31) {
            val system = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // Material's dynamic background and surface use the same tone by default.
            system.copy(
                background = if (dark) system.background else system.surfaceContainerLow,
                surface = if (dark) system.surfaceContainer else system.surfaceContainerLowest,
                tertiary = designIncomeColor(dark),
            )
        } else {
            val seed = when (preferences.colorSource) {
                ColorSource.CUSTOM -> preferences.customSeedColor
                else -> presets[preferences.presetId]?.second ?: "#C47A4A"
            }
            designColors(Color(android.graphics.Color.parseColor(seed)), dark)
        }
    }
    val applied = if (forceDark != null) colors else key(dark) { animateColorScheme(colors, tween(250, easing = motion)) }
    CompositionLocalProvider(LocalDarkMode provides dark) {
        MaterialTheme(applied, shapes = Shapes(small = ControlShape, medium = CardShape, large = DialogShape),
            typography = type, content = content)
    }
}
