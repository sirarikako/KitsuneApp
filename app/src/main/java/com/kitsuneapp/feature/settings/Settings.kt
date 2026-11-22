package com.kitsuneapp.feature.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kitsuneapp.AppViewModel
import com.kitsuneapp.data.*
import com.kitsuneapp.ui.*
import java.util.Locale

@Composable
fun SettingsPage(navigate: (String) -> Unit) {
    Column {
        Header("设置")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("分类管理")
            Paper(padding = 0) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Setting("用餐方式", Icons.Outlined.DateRange) { navigate("methods") }
                    HorizontalDivider()
                    Setting("制作分类", Icons.AutoMirrored.Outlined.List) { navigate("categories/PREPARATION") }
                    HorizontalDivider()
                    Setting("通用收支分类", Icons.AutoMirrored.Outlined.List) { navigate("categorySettings") }
                    HorizontalDivider()
                    Setting("钱包专属分类", Icons.Outlined.Edit) { navigate("walletCategories") }
                }
            }
            Section("个性化")
            Paper(padding = 0) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Setting("外观与调色板", Icons.Outlined.FavoriteBorder) { navigate("appearance") }
                }
            }
            Section("数据与备份")
            Paper(padding = 0) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Setting("导入与导出", Icons.AutoMirrored.Outlined.List) { navigate("data") }
                    HorizontalDivider()
                    Setting("本地文件夹备份", Icons.Outlined.Settings) { navigate("folder") }
                    HorizontalDivider()
                    Setting("GitHub 备份", Icons.Outlined.Settings) { navigate("github") }
                }
            }
        }
    }
}

@Composable
fun Setting(label: String, icon: ImageVector? = null, action: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().heightIn(min = if (icon == null) 44.dp else 55.dp)
        .clickable(onClick = action).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Text(label, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(14.dp), tint = tint)
    }
}

@Composable
fun VocabularyPage(
    records: Records,
    scope: Scope?,
    owner: String?,
    navigate: (String) -> Unit,
    back: () -> Unit,
) {
    val methods = scope == null
    val title = scope?.label ?: "用餐方式"
    val path = if (scope == null) "method" else "category/${scope.name}/${owner ?: "-"}"
    Column {
        Header(title, back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Primary("新增$title", { navigate("$path/-") }, Modifier.fillMaxWidth())
            if (methods) {
                records.methods.forEach {
                    RecordRow(it.name, if (it.showPreparationCategories) "显示制作分类" else "隐藏制作分类",
                        icon = Icons.AutoMirrored.Outlined.List, badge = if (it.archived) "已停用" else null, onClick = { navigate("$path/" + it.id) })
                }
            } else records.categories.filter { it.scope == scope && it.walletId == owner }.forEach {
                if (it.isDefault) Paper { Text(it.name); Secondary("全局兜底分类") }
                else RecordRow(it.name, "排序 ${it.sortOrder}", icon = Icons.AutoMirrored.Outlined.List,
                    badge = if (it.archived) "已停用" else null, onClick = { navigate("$path/" + it.id) })
            }
        }
    }
}

@Composable
fun VocabularyForm(records: Records, id: String?, scope: Scope?, owner: String?, model: AppViewModel, back: () -> Unit) {
    val category = records.categories.find { it.id == id && it.scope == scope && it.walletId == owner }
    val method = records.methods.find { it.id == id && scope == null }
    val title = scope?.label ?: "用餐方式"
    val next = if (scope == null) records.methods.maxOfOrNull { it.sortOrder }
        else records.categories.filter { it.scope == scope && it.walletId == owner }.maxOfOrNull { it.sortOrder }
    var name by rememberSaveable { mutableStateOf(category?.name ?: method?.name.orEmpty()) }
    var order by rememberSaveable { mutableStateOf((category?.sortOrder ?: method?.sortOrder
        ?: ((next ?: -1).coerceAtMost(Int.MAX_VALUE - 1) + 1)).toString()) }
    var archived by rememberSaveable { mutableStateOf(category?.archived ?: method?.archived ?: false) }
    var show by rememberSaveable { mutableStateOf(method?.showPreparationCategories ?: false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    Column {
        Header((if (id == null) "新增" else "编辑") + title, back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("名称", name, { name = it })
            Field("排序（数字越小越靠前）", order, { order = it }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (scope == null) Toggle("显示制作分类", show) { show = it }
            Toggle("停用", archived) { archived = it }
            SaveActions(busy, error, {
                model.save(back) {
                    val position = requireNotNull(order.toIntOrNull()) { "排序必须为非负整数" }
                    if (scope == null) saveMethod(id, name, position, archived, show)
                    else saveCategory(id, scope, owner, name, position, archived)
                }
            }, id?.let { "删除$title" }, { delete = true })
        }
    }
    if (id != null && delete) ConfirmDelete(category?.name ?: method?.name.orEmpty(),
        { delete = false }, {
            delete = false
            model.save(back) { if (scope == null) deleteMethod(id) else deleteCategory(id) }
        })
}

@Composable
fun CategorySettings(records: Records, model: AppViewModel, specific: Boolean, walletId: String?, open: (String) -> Unit, back: () -> Unit) {
    var wallet by rememberSaveable { mutableStateOf(walletId) }
    var scope by rememberSaveable { mutableStateOf(Scope.EXPENSE) }
    val current = records.wallets.find { it.id == wallet }
    Column {
        Header(if (specific) "钱包专属分类" else "通用收支分类", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (specific) Select("钱包", wallet, records.wallets.map { it.id },
                { id -> records.wallets.find { it.id == id }?.let { it.name + " · " + it.currencyCode } ?: "已移除" }, { wallet = it })
            Tabs(listOf("支出", "收入"), if (scope == Scope.EXPENSE) 0 else 1) { scope = if (it == 0) Scope.EXPENSE else Scope.INCOME }
            if (current != null) {
                val busy by model.busy.collectAsState()
                val error by model.error.collectAsState()
                Select("分类来源", current.categoryMode(scope), CategoryMode.entries,
                    { if (it == CategoryMode.DEFAULT) "使用通用分类" else "使用专属分类" },
                    { mode -> model.save { setCategoryMode(current.id, scope, mode) } }, enabled = !busy)
                if (current.categoryMode(scope) == CategoryMode.CUSTOM) {
                    Secondary("该方向只使用钱包专属分类与全局未分类。")
                } else Secondary("专属词表仍可维护，切换来源后生效。")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (!specific || current != null) {
                Primary("管理${scope.label}分类", { open("categories/${scope.name}/${current?.id ?: "-"}") }, Modifier.fillMaxWidth())
            } else Secondary("请选择钱包")
        }
    }
}

@Composable
fun AppearancePage(applied: ThemePreferences, model: AppViewModel, back: () -> Unit) {
    var mode by rememberSaveable(applied) { mutableStateOf(applied.themeMode) }
    var source by rememberSaveable(applied) { mutableStateOf(applied.colorSource) }
    var preset by rememberSaveable(applied) { mutableStateOf(applied.presetId) }
    var seed by rememberSaveable(applied) { mutableStateOf(applied.customSeedColor) }
    var previewDarkOverride by rememberSaveable(applied) { mutableStateOf<Boolean?>(null) }
    val previewDark = previewDarkOverride ?: when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val normalized = normalizedColor(seed)
    val draft = ThemePreferences(themeMode = mode, colorSource = source, presetId = preset,
        customSeedColor = normalized ?: applied.customSeedColor)
    val changed = draft != applied || normalized == null
    LaunchedEffect(changed) { if (!changed) previewDarkOverride = null }
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    Column {
        Header("外观与调色板", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Paper {
                Select("明暗模式", mode, ThemeMode.entries, { it.label }, { mode = it })
                Select("配色来源", source, ColorSource.entries.filter { it != ColorSource.SYSTEM || Build.VERSION.SDK_INT >= 31 },
                    { it.label }, { source = it })
                if (Build.VERSION.SDK_INT < 31) Secondary("跟随系统壁纸需要 Android 12 或更新版本")
                if (source == ColorSource.PRESET) Select("调色板", preset, presets.keys.toList(),
                    { id -> presets.getValue(id).first }, { preset = it })
                if (source == ColorSource.CUSTOM) {
                    Field("主色 HEX", seed, { seed = it })
                    ColorPicker(normalized ?: applied.customSeedColor) { seed = it }
                    if (normalized == null) Text("请输入六位 HEX 色值", color = MaterialTheme.colorScheme.error)
                }
                TextButton({
                    val defaults = ThemePreferences()
                    mode = defaults.themeMode
                    source = defaults.colorSource
                    preset = defaults.presetId
                    seed = defaults.customSeedColor
                }) { Text("恢复默认") }
            }
            AnimatedVisibility(changed, modifier = if (changed) Modifier else Modifier.inert("外观草稿"),
                enter = fadeIn(tween(200, easing = motion)) + expandVertically(tween(200, easing = motion)),
                exit = fadeOut(tween(200, easing = motion)) + shrinkVertically(tween(200, easing = motion))) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Secondary("预览明暗")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choice("浅色", { previewDarkOverride = false }, Modifier.weight(1f), selected = !previewDark)
                        Choice("深色", { previewDarkOverride = true }, Modifier.weight(1f), selected = previewDark)
                    }
                    KitsuneTheme(draft, forceDark = previewDark) {
                        Surface(color = MaterialTheme.colorScheme.background, shape = CardShape) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecordRow("早餐", "燕麦粥、煎蛋", icon = Icons.Outlined.DateRange, onClick = null, badge = "下厨")
                                RecordRow("工资", "收入", icon = Icons.AutoMirrored.Outlined.List, onClick = null,
                                    amount = "+100.00", currency = "CNY", amountColor = incomeColor)
                                RecordRow("餐饮", "支出", icon = Icons.AutoMirrored.Outlined.List, onClick = null,
                                    amount = "−28.00", currency = "CNY", amountColor = MaterialTheme.colorScheme.error)
                                Surface(Modifier.fillMaxWidth().heightIn(min = 46.dp), color = MaterialTheme.colorScheme.primary,
                                    shape = ControlShape) {
                                    Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) { Text("保存") }
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choice("取消", {
                            mode = applied.themeMode
                            source = applied.colorSource
                            preset = applied.presetId
                            seed = applied.customSeedColor
                            model.clearError()
                        }, Modifier.weight(1f), enabled = changed && !busy)
                        Primary("应用外观", { model.save { applyTheme(draft) } },
                            Modifier.weight(1f), enabled = changed && normalized != null && !busy)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPicker(hex: String, set: (String) -> Unit) {
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), it) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(28.dp).background(Color(android.graphics.Color.parseColor(hex)), ControlShape))
        Secondary("自选主色")
    }
    listOf("色相", "饱和度", "亮度").forEachIndexed { index, name ->
        val maximum = if (index == 0) 359.99f else 1f
        Secondary(name)
        Slider(hsv[index].coerceIn(0f, maximum), {
            hsv[index] = it
            set(String.format(Locale.ROOT, "#%06X", android.graphics.Color.HSVToColor(hsv) and 0xFFFFFF))
        }, valueRange = 0f..maximum)
    }
}
