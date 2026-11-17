package com.sirarikako.kitsune.feature.settings

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sirarikako.kitsune.core.data.*
import com.sirarikako.kitsune.core.theme.*
import com.sirarikako.kitsune.ui.*

@Composable
fun AppearanceScreen(initial: ThemePreferences, busy: Boolean, onCancel: () -> Unit, onApply: (ThemePreferences) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(initial.themeMode.name) }
    var source by rememberSaveable { mutableStateOf(initial.colorSource.name) }
    var preset by rememberSaveable { mutableStateOf(initial.presetId) }
    var hex by rememberSaveable { mutableStateOf(initial.customSeedColor) }
    var validHex by rememberSaveable { mutableStateOf(initial.customSeedColor) }
    var darkPreview by rememberSaveable { mutableStateOf(false) }
    val draft = ThemePreferences(themeMode = ThemeMode.valueOf(mode), colorSource = ColorSource.valueOf(source), presetId = preset, customSeedColor = validHex)
    val valid = source != ColorSource.CUSTOM.name || normalizedHex(hex) != null
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = onCancel, enabled = !busy) { Text("取消并返回") } }
        item { Text("给生活换种颜色", style = MaterialTheme.typography.headlineLarge) }
        item { Text("选择仅改变预览，点击应用后保存。") }
        item { SectionCard("明暗模式") { ChipRow { ThemeMode.entries.forEach { item -> FilterChip(mode == item.name, onClick = { mode = item.name }, label = { Text(item.label) }) } } } }
        item { SectionCard("配色来源") {
            ColorSource.entries.forEach { item ->
                val supported = item != ColorSource.SYSTEM || Build.VERSION.SDK_INT >= 31
                FilterChip(source == item.name, onClick = { source = item.name }, enabled = supported, label = { Text(item.label) })
            }
            if (Build.VERSION.SDK_INT < 31) Text("系统动态取色需 Android 12 及以上；已有偏好会保留并回退到预设。", style = MaterialTheme.typography.bodySmall)
        } }
        if (source == ColorSource.PRESET.name) item { SectionCard("预设调色板") {
            presets.forEach { item -> FilterChip(preset == item.id, onClick = { preset = item.id }, label = { Text(item.label) },
                leadingIcon = { Box(Modifier.size(18.dp).background(hexColor(item.hex), CircleShape)) }) }
        } }
        if (source == ColorSource.CUSTOM.name) item { SectionCard("自选主色") {
            OutlinedTextField(hex, onValueChange = { value -> hex = value; normalizedHex(value)?.let { validHex = it } },
                label = { Text("六位 HEX，如 #C47A4A") }, isError = normalizedHex(hex) == null, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (normalizedHex(hex) == null) Text("请输入六位十六进制颜色，不含透明度。", color = MaterialTheme.colorScheme.error)
            HsvPicker(validHex) { value -> hex = value; validHex = value }
        } }
        item { ChipRow {
            FilterChip(!darkPreview, onClick = { darkPreview = false }, label = { Text("浅色预览") })
            FilterChip(darkPreview, onClick = { darkPreview = true }, label = { Text("深色预览") })
        } }
        item { KitsuneTheme(draft, darkOverride = darkPreview) {
            Surface(color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("今天也好好吃饭", style = MaterialTheme.typography.titleLarge)
                    SectionCard("晚餐 · 下厨") {
                        Text("番茄炒蛋、米饭")
                        SuggestionChip(onClick = {}, label = { Text("炒菜") })
                        Text("＋ 收入 CNY 100.00", color = MaterialTheme.colorScheme.tertiary)
                        Text("− 支出 CNY 12.80", color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = {}) { Text("保存记录 · 预览") }
                }
            }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = !busy, onClick = {
                mode = ThemeMode.SYSTEM.name; source = ColorSource.PRESET.name; preset = "apricot"; hex = "#C47A4A"; validHex = hex
            }) { Text("恢复默认") }
            Button(enabled = valid && !busy, onClick = { onApply(draft.copy(customSeedColor = normalizedHex(hex) ?: validHex)) }) { Text(if (busy) "正在应用…" else "应用") }
        } }
    }
}
@Composable
private fun HsvPicker(hex: String, onColor: (String) -> Unit) {
    val hsv = remember(hex) { FloatArray(3).also { AndroidColor.colorToHSV((0xFF000000L or hex.removePrefix("#").toLong(16)).toInt(), it) } }
    // Keep hue/saturation in UI state even when black or white round-trips through HEX.
    var hue by rememberSaveable { mutableFloatStateOf(hsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(hsv[1]) }
    var value by rememberSaveable { mutableFloatStateOf(hsv[2]) }
    var emitted by remember { mutableStateOf(hex) }
    LaunchedEffect(hex) { if (hex != emitted) { hue = hsv[0]; saturation = hsv[1]; value = hsv[2]; emitted = hex } }
    fun emit() {
        val color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)) and 0xFFFFFF
        emitted = String.format(java.util.Locale.ROOT, "#%06X", color); onColor(emitted)
    }
    Box(Modifier.fillMaxWidth().height(44.dp).background(hexColor(hex), MaterialTheme.shapes.small))
    Text("色相")
    Slider(hue, onValueChange = { hue = it; emit() }, valueRange = 0f..360f)
    Text("饱和度")
    Slider(saturation, onValueChange = { saturation = it; emit() })
    Text("明度")
    Slider(value, onValueChange = { value = it; emit() })
}
