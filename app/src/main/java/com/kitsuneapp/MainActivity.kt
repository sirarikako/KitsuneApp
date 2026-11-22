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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsuneapp.data.*
import com.kitsuneapp.feature.meals.*
import com.kitsuneapp.feature.ledger.*
import com.kitsuneapp.feature.settings.*
import com.kitsuneapp.ui.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
                        records != null -> {
                            KitsuneApp(records, model, refresh)
                            CapacityAlert(records, model)
                        }
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

private data class RecordNavigation(val id: String, val source: String, val detail: String)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun KitsuneApp(records: Records, model: AppViewModel, refresh: Int) {
    var stack by rememberSaveable { mutableStateOf<List<String>>(listOf("home")) }
    var filter by rememberSaveable { mutableStateOf<MealFilter?>(null) }
    val holder = rememberSaveableStateHolder()
    var recordNavigation by remember { mutableStateOf<RecordNavigation?>(null) }
    val top = stack.last()
    val root = stack.first()
    fun open(path: String) {
        if (model.isBusy || stack.last() == path) return
        model.clearError()
        stack = when {
            path in roots -> arrayListOf(path)
            path in stack -> ArrayList(stack.take(stack.indexOf(path) + 1))
            else -> ArrayList(stack + path)
        }
    }
    fun openRecord(path: String, id: String) {
        if (model.isBusy) return
        recordNavigation = RecordNavigation(id, stack.last(), path)
        open(path)
    }
    fun back() {
        if (stack.size > 1) {
            model.clearError()
            val old = stack.last()
            if (old == "data" || old == "github") model.discardRestorePreview()
            if (old == "data") model.clearLocalRestoreResult()
            recordNavigation?.takeIf { it.detail == old }?.let { navigation ->
                val stillVisible = if (old.startsWith("meal/")) {
                    val record = records.meals.find { it.id == navigation.id }
                    record != null && (filter?.matches(record, records) ?: (old.split("/")[1] == record.date))
                } else records.entries.any { it.id == navigation.id }
                if (!stillVisible) recordNavigation = null
            }
            stack = ArrayList(stack.dropLast(1))
            holder.removeState(old)
        }
    }
    fun openMeal(date: String, slot: MealSlot, id: String?) = open("meal/$date/${slot.name}/${id ?: "-"}")
    BackHandler(stack.size > 1) { if (!model.isBusy) back() }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(refresh) {
        while (true) {
            now = LocalDateTime.now()
            delay((60 - now.second) * 1000L)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        SharedTransitionLayout(Modifier.weight(1f).fillMaxWidth()) {
            val shared = this
            AnimatedContent(
                targetState = top,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val record = recordNavigation
                    val transform = record != null && ((record.source == initialState && record.detail == targetState) ||
                        (record.detail == initialState && record.source == targetState))
                    when {
                        transform -> fadeIn(tween(280, easing = motion)) togetherWith fadeOut(tween(280, easing = motion))
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
                    val goBack = { if (!model.isBusy && path == stack.last()) back() }
                    val active = path == top
                    val navigation = recordNavigation
                    val key = navigation?.id?.takeIf { path == navigation.detail }
                    val outgoing = if (active) Modifier else Modifier.inert(path)
                    CompositionLocalProvider(LocalRecordMotion provides RecordMotion(shared, this@AnimatedContent)) {
                        Box(recordBounds(outgoing, key).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            when (parts[0]) {
                                "home" -> Home(records, now, ::open, ::openMeal)
                                "meals" -> MealsPage(records, filter, { filter = null }, { filter = MealFilter() },
                                    { open("mealFilter") }, ::openMeal) { meal ->
                                    openRecord("meal/${meal.date}/${meal.mealSlot.name}/${meal.id}", meal.id)
                                }
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
                                "data" -> DataManagementPage(records, model, goBack)
                                "folder" -> FolderPage(records, model, ::open, goBack)
                                "github" -> GitHubPage(records, model, goBack)
                                "ledger" -> LedgerPage(records, ::open) { id -> openRecord("entry/$id", id) }
                                "wallet" -> WalletForm(records, parts[1].takeUnless { it == "-" }, model, goBack)
                                "entry" -> EntryDetails(records, parts[1], model, ::open, goBack)
                                "entryForm" -> EntryForm(records, parts[1].takeUnless { it == "-" }, null, model, goBack, ::back)
                                "refund" -> EntryForm(records, null, parts[1], model, goBack, ::back)
                            }
                    }
                    }
                }
            }
        }
        if (stack.size == 1) {
            val icons = listOf(R.drawable.ks_house, R.drawable.ks_utensils, R.drawable.ks_wallet, R.drawable.ks_sliders_horizontal)
            val colors = MaterialTheme.colorScheme
            Surface(color = colors.surface) {
                Column {
                    HorizontalDivider(color = colors.outlineVariant)
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 8.dp)
                        .selectableGroup(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        roots.forEachIndexed { index, route ->
                            val selected = root == route
                            val tint = if (selected) colors.primary else colors.onSurfaceVariant
                            Column(Modifier.weight(1f).heightIn(min = 49.dp).clip(ControlShape)
                                .selectable(selected, role = Role.Tab, onClick = { open(route) }).padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(49.dp, 25.dp).clip(CircleShape)
                                    .then(if (selected) Modifier.background(colors.primaryContainer) else Modifier),
                                    contentAlignment = Alignment.Center) {
                                    Icon(designIcon(icons[index]), null, Modifier.size(18.dp),
                                        tint = if (selected) colors.onPrimaryContainer else tint)
                                }
                                Text(titles[index], style = MaterialTheme.typography.labelSmall, color = tint,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
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
        val dateLabel = now.format(DateTimeFormatter.ofPattern("M 月 d 日 · EEEE", Locale.SIMPLIFIED_CHINESE))
        Header("日常记录", dateLabel, leading = {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                .background(if (LocalDarkMode.current) Color(0xFF59402E) else Color(0xFFF7DFC6)),
                contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.fox_artwork), contentDescription = null, modifier = Modifier.size(36.dp))
            }
        })
        Column(Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Paper {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("今日用餐", style = MaterialTheme.typography.titleMedium)
                        Text(recorded.size.toString(), fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.Medium)
                        Secondary("/ 3")
                    }
                    TextAction("全部记录", { open("meals") }, designIcon(R.drawable.ks_chevron_right))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pendingColor = MaterialTheme.colorScheme.onSurfaceVariant
                    MealSlot.entries.forEach { slot ->
                        val done = recorded.containsKey(slot)
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (done) Icon(designIcon(R.drawable.ks_circle_check), null, Modifier.size(14.dp), tint = incomeColor)
                            else Spacer(Modifier.size(14.dp).drawBehind {
                                val line = 1.dp.toPx()
                                drawCircle(pendingColor, radius = size.minDimension / 2 - line,
                                    style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))))
                            })
                            Text(slot.label + if (done) "已记" else "待记", style = MaterialTheme.typography.bodySmall,
                                color = if (done) MaterialTheme.colorScheme.onSurface else pendingColor)
                        }
                    }
                }
                val current = recorded[suggested]
                Primary((if (current == null) "记录" else "编辑") + suggested.label,
                    { meal(today, suggested, current?.id) }, Modifier.fillMaxWidth(),
                    icon = designIcon(if (current == null) R.drawable.ks_plus else R.drawable.ks_pencil))
            }
            Section("本月收支") { TextAction("查看账本", { open("ledger") }, designIcon(R.drawable.ks_arrow_up_right)) }
            Paper {
                val ledger = remember(records) { Ledger(records) }
                val month = java.time.YearMonth.from(now).toString()
                val totals = remember(ledger, month) { ledger.statistics(LedgerPeriod(month), WalletRange()) }
                if (totals.isEmpty()) Secondary("暂无常规钱包的收支")
                totals.forEachIndexed { index, total ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text(total.currency, Modifier.width(36.dp), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Column(Modifier.weight(1f)) {
                            Text("净支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(displayMoney(total.expenses, total.scale), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("收入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(displayMoney(total.income, total.scale), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Primary("记一笔", {
                    open(if (ledger.availableWallets().isEmpty()) "wallet/-" else "entryForm/-")
                }, Modifier.fillMaxWidth(), icon = designIcon(R.drawable.ks_plus))
            }
        }
    }
}
