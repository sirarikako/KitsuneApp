package com.kitsuneapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsuneapp.data.*
import com.kitsuneapp.feature.meals.*
import com.kitsuneapp.feature.settings.*
import com.kitsuneapp.ui.*
import java.time.LocalDateTime
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: AppViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            var refresh by remember { mutableIntStateOf(0) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
            KitsuneTheme(state.records?.theme ?: ThemePreferences(), refresh = refresh) {
                val dark = LocalDarkMode.current
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val records = state.records
                    when {
                        records != null -> KitsuneApp(records, model, refresh)
                        state.openingError != null -> OpenError(state, model)
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenError(state: AppState, model: AppViewModel) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(state.openingError.orEmpty())
        if (state.needsNewDatabase) {
            Secondary("原数据库将保留。建立独立的新库后可从完整备份恢复。")
            Primary("建立独立新库", { confirm = true })
        } else Choice("重试", { model.open(false) })
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("建立独立新库？") },
        text = { Text("原文件将保留，并启用空的新数据库。") },
        confirmButton = { Primary("建立新库", { confirm = false; model.open(true) }) },
        dismissButton = { TextButton({ confirm = false }) { Text("取消") } })
}

private val roots = listOf("home", "meals", "ledger", "settings")
private val titles = listOf("首页", "用餐", "记账", "设置")

@Composable
private fun KitsuneApp(records: Records, model: AppViewModel, refresh: Int) {
    var stack by rememberSaveable { mutableStateOf(arrayListOf("home")) }
    var filter by rememberSaveable { mutableStateOf<MealFilter?>(null) }
    val holder = rememberSaveableStateHolder()
    val busy by model.busy.collectAsStateWithLifecycle()
    val top = stack.last()
    val root = stack.first()
    fun open(path: String) {
        if (busy || stack.last() == path) return
        model.clearError()
        stack = if (path in roots) arrayListOf(path) else ArrayList(stack + path)
    }
    fun back() {
        if (stack.size > 1) {
            model.clearError()
            val old = stack.last()
            stack = ArrayList(stack.dropLast(1))
            holder.removeState(old)
        }
    }
    fun openMeal(date: String, slot: MealSlot, id: String?) = open("meal/$date/${slot.name}/${id ?: "-"}")
    BackHandler(stack.size > 1) { if (!busy) back() }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(refresh) {
        while (true) {
            now = LocalDateTime.now()
            delay((60 - now.second) * 1000L)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        AnimatedContent(
            targetState = top,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                when {
                    initialState in roots && targetState in roots -> {
                        (fadeIn(tween(112, delayMillis = 88, easing = motion)) +
                            scaleIn(tween(112, delayMillis = 88, easing = motion), initialScale = 0.96f)) togetherWith
                            fadeOut(tween(88, easing = motion))
                    }
                    stack.size > 1 && targetState in stack && stack.indexOf(targetState) > stack.indexOf(initialState) ->
                        slideInHorizontally(tween(240, easing = motion)) { it } togetherWith
                            slideOutHorizontally(tween(240, easing = motion)) { -it / 5 }
                    else -> slideInHorizontally(tween(220, easing = motion)) { -it / 5 } togetherWith
                        slideOutHorizontally(tween(220, easing = motion)) { it }
                }
            },
            label = "页面过渡",
        ) { path ->
            holder.SaveableStateProvider(path) {
                val parts = path.split("/")
                val goBack = { if (!busy) back() }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (parts[0]) {
                        "home" -> Home(records, now, ::open, ::openMeal)
                        "meals" -> MealsPage(records, filter, { filter = null }, { open("mealFilter") }, ::openMeal)
                        "meal" -> MealForm(records, parts[3].takeUnless { it == "-" }, parts[1], MealSlot.valueOf(parts[2]), model, goBack)
                        "mealFilter" -> MealFilterPage(records, filter ?: MealFilter(), goBack) { filter = it; back() }
                        "settings" -> SettingsPage(::open)
                        "methods" -> VocabularyPage(records, null, null, ::open, goBack)
                        "method" -> VocabularyForm(records, parts[1].takeUnless { it == "-" }, null, null, model, goBack)
                        "categories" -> VocabularyPage(records, Scope.valueOf(parts[1]), parts.getOrNull(2)?.takeUnless { it == "-" }, ::open, goBack)
                        "category" -> VocabularyForm(records, parts[3].takeUnless { it == "-" },
                            Scope.valueOf(parts[1]), parts[2].takeUnless { it == "-" }, model, goBack)
                        "categorySettings" -> CategorySettings(records, model, false, null, ::open, goBack)
                        "walletCategories" -> CategorySettings(records, model, true, parts.getOrNull(1), ::open, goBack)
                        "appearance" -> AppearancePage(records.theme, model, goBack)
                        "ledger" -> Column { Header("记账"); Secondary("记账功能正在开发", Modifier.padding(16.dp)) }
                    }
                }
            }
        }
        if (stack.size == 1) {
            val icons = listOf(Icons.Outlined.Home, Icons.Outlined.DateRange, Icons.AutoMirrored.Outlined.List, Icons.Outlined.Settings)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                roots.forEachIndexed { index, route ->
                    NavigationBarItem(selected = root == route, onClick = { open(route) },
                        icon = { Icon(icons[index], null, Modifier.size(18.dp)) },
                        label = { Text(titles[index], style = MaterialTheme.typography.labelSmall) })
                }
            }
        } else Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun Home(records: Records, now: LocalDateTime, open: (String) -> Unit, meal: (String, MealSlot, String?) -> Unit) {
    val today = now.toLocalDate().toString()
    val recorded = records.meals.filter { it.date == today }.associateBy { it.mealSlot }
    val suggested = when (now.hour) {
        in 0..10 -> MealSlot.BREAKFAST
        in 11..16 -> MealSlot.LUNCH
        else -> MealSlot.DINNER
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Header("日常记录", today) {
            Image(painterResource(R.drawable.fox_artwork), contentDescription = null, modifier = Modifier.size(48.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Paper {
                Section("今日用餐  ${recorded.size} / 3") { TextButton({ open("meals") }) { Text("全部记录 ›") } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealSlot.entries.forEach {
                        Tag(it.label + if (recorded.containsKey(it)) "已记" else "待记")
                    }
                }
                val current = recorded[suggested]
                Primary((if (current == null) "记录" else "编辑") + suggested.label,
                    { meal(today, suggested, current?.id) }, Modifier.fillMaxWidth())
            }
            Section("本月收支") { TextButton({ open("ledger") }) { Text("查看账本 ↗") } }
            Paper {
                Secondary("尚无收支记录")
                Primary("记一笔", { open("ledger") }, Modifier.fillMaxWidth())
            }
        }
    }
}
