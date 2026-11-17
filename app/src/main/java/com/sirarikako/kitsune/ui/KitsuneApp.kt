package com.sirarikako.kitsune.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirarikako.kitsune.R
import com.sirarikako.kitsune.core.data.*
import com.sirarikako.kitsune.feature.meals.*
import com.sirarikako.kitsune.feature.settings.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun KitsuneApp(vm: KitsuneViewModel, data: AppData?) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by rememberSaveable(stateSaver = MealDraftSaver) { mutableStateOf<MealDraft?>(null) }
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (editor == null) NavigationBar {
            listOf("首页" to "⌂", "用餐" to "食", "记账" to "¥", "设置" to "⚙").forEachIndexed { index, (label, icon) ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text(icon) }, label = { Text(label) })
            }
        } },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            if (data == null) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("正在打开本地记录…")
                    CircularProgressIndicator()
                    TextButton(onClick = { vm.load() }) { Text("重新读取") }
                }
            } else if (editor != null) {
                MealEditor(data, editor!!, busy, onCancel = { editor = null },
                    onSave = { draft -> vm.perform(onSuccess = { editor = null }) { saveMeal(draft) } },
                    onDelete = { id -> vm.perform(onSuccess = { editor = null }) { deleteMeal(id) } })
            } else when (tab) {
                0 -> HomeScreen(data, onEdit = { editor = it }, onMeals = { tab = 1 })
                1 -> MealsScreen(data, onEdit = { editor = it })
                2 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("记账", style = MaterialTheme.typography.headlineLarge) }
                    item { SectionCard("多钱包，独立币种") {
                        Text("记账将在阶段 2 提供：钱包、收支、转账、退款与按币种统计。当前版本可离线记录用餐。")
                    } }
                }
                3 -> SettingsScreen(data, vm, busy)
            }
        }
    }
}

@Composable
private fun HomeScreen(data: AppData, onEdit: (MealDraft) -> Unit, onMeals: () -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(dateText)
    val stats = remember(data, date) { mealStatistics(data, YearMonth.from(date)) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.fox_artwork), contentDescription = "Kitsune 狐狸", modifier = Modifier.size(64.dp))
                Column { Text("好好吃饭，慢慢记录", style = MaterialTheme.typography.headlineSmall); Text("Kitsune · 生活里的小事", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item { DateSelector(date) { dateText = it.toString() } }
        items(MealSlot.entries.size) { index ->
            val slot = MealSlot.entries[index]
            val meal = data.meals.find { it.date == dateText && it.mealSlot == slot }
            MealCard(data, meal, slot) { onEdit(meal?.let(data::draft) ?: MealDraft(date = dateText, mealSlot = slot)) }
        }
        item { SectionCard("${YearMonth.from(date)} · 用餐小结") {
            Text("已记录 ${stats.total} 餐 · 计划在家 ${stats.atHome} 餐")
            TextButton(onClick = onMeals) { Text("查看历史与统计") }
        } }
        item { SectionCard("数据保存在此设备") {
            Text("文件夹备份：未配置\nGitHub 备份：未配置")
            Text("备份功能将在后续阶段提供。", style = MaterialTheme.typography.bodySmall)
        } }
    }
}
