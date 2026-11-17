package com.sirarikako.kitsune.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sirarikako.kitsune.core.data.*
import com.sirarikako.kitsune.ui.*

@Composable
fun SettingsScreen(data: AppData, vm: KitsuneViewModel, busy: Boolean) {
    var screen by rememberSaveable { mutableStateOf("main") }
    BackHandler(screen != "main") { screen = "main" }
    when (screen) {
        "appearance" -> AppearanceScreen(data.theme, busy, onCancel = { screen = "main" },
            onApply = { theme -> vm.perform(onSuccess = { screen = "main" }) { applyTheme(theme) } })
        "methods", "categories" -> VocabularyScreen(data, vm, busy, screen == "methods", onBack = { screen = "main" })
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("设置", style = MaterialTheme.typography.headlineLarge) }
            item { SectionCard("让记录顺手一点") {
                OutlinedButton(onClick = { screen = "methods" }) { Text("管理用餐方式") }
                OutlinedButton(onClick = { screen = "categories" }) { Text("管理制作分类") }
                OutlinedButton(onClick = { screen = "appearance" }) { Text("外观与调色板") }
            } }
            item { SectionCard("数据与备份") {
                Text("当前记录保存在此设备，可离线使用。")
                Text("文件夹备份：未配置\nGitHub 备份：未配置")
                Text("JSON／CSV 与本地备份将在阶段 3 提供，GitHub 备份将在阶段 4 提供。")
            } }
            item { SectionCard("KitsuneApp 0.1.0") {
                Text("阶段 1 · 主题与用餐\nAndroid 8.0 及以上")
            } }
        }
    }
}
@Composable
private fun VocabularyScreen(data: AppData, vm: KitsuneViewModel, busy: Boolean, methods: Boolean, onBack: () -> Unit) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val categories = data.categories.filter { it.scope == CategoryScope.PREPARATION }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = onBack) { Text("返回设置") } }
        item { Text(if (methods) "用餐方式" else "制作分类", style = MaterialTheme.typography.headlineLarge) }
        item { Text("停用后保留历史引用，名称修改会同步显示在历史记录中。") }
        item { Button(enabled = !busy, onClick = { editingId = null; name = ""; show = false; dialog = true }) { Text("＋ 新增") } }
        if (methods) items(data.methods, key = { it.id }) { method ->
            SectionCard(method.name + if (method.archived) "（已停用）" else "") {
                Text(if (method.showPreparationCategories) "显示制作分类" else "不显示制作分类")
                ChipRow {
                    TextButton(enabled = !busy, onClick = { editingId = method.id; name = method.name; show = method.showPreparationCategories; dialog = true }) { Text("编辑") }
                    TextButton(enabled = !busy, onClick = { vm.perform { archiveMethod(method.id) } }) { Text(if (method.archived) "启用" else "停用") }
                    TextButton(enabled = !busy && data.methods.first().id != method.id, onClick = { vm.perform { moveMethod(method.id, -1) } }) { Text("上移") }
                    TextButton(enabled = !busy && data.methods.last().id != method.id, onClick = { vm.perform { moveMethod(method.id, 1) } }) { Text("下移") }
                }
            }
        } else items(categories, key = { it.id }) { category ->
            SectionCard(category.name + if (category.archived) "（已停用）" else "") {
                ChipRow {
                    TextButton(enabled = !busy, onClick = { editingId = category.id; name = category.name; dialog = true }) { Text("改名") }
                    TextButton(enabled = !busy, onClick = { vm.perform { archiveCategory(category.id) } }) { Text(if (category.archived) "启用" else "停用") }
                    TextButton(enabled = !busy && categories.first().id != category.id, onClick = { vm.perform { moveCategory(category.id, -1) } }) { Text("上移") }
                    TextButton(enabled = !busy && categories.last().id != category.id, onClick = { vm.perform { moveCategory(category.id, 1) } }) { Text("下移") }
                }
            }
        }
    }
    if (dialog) AlertDialog(onDismissRequest = { if (!busy) dialog = false }, title = { Text(if (editingId == null) "新增" else "编辑") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
            if (methods) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Checkbox(show, onCheckedChange = { show = it }); Text("显示制作分类", modifier = Modifier.padding(top = 12.dp)) }
                Text("关闭开关不会删除历史记录中的分类。")
            }
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && !busy, onClick = {
        vm.perform(onSuccess = { dialog = false }) { if (methods) saveMethod(editingId, name, show) else saveCategory(editingId, name) }
    }) { Text("保存") } }, dismissButton = { TextButton(enabled = !busy, onClick = { dialog = false }) { Text("取消") } })
}
