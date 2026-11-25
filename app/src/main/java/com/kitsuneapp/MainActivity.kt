package com.kitsuneapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsuneapp.backup.GitHubPauseReason
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
            KitsuneTheme(state.records?.theme, refresh = refresh) {
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

private data class RecordNavigation(val id: String, val source: String, val detail: String)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun KitsuneApp(records: Records, model: AppViewModel, refresh: Int) {
    var stack by rememberSaveable { mutableStateOf<List<String>>(listOf("home")) }
    var filter by rememberSaveable { mutableStateOf<MealFilter?>(null) }
    var mealStatsFilter by rememberSaveable { mutableStateOf<MealFilter?>(null) }
    val holder = rememberSaveableStateHolder()
    var recordNavigation by remember { mutableStateOf<RecordNavigation?>(null) }
    val top = stack.last()
    val root = stack.first()
    fun open(path: String) {
        val current = stack.last()
        val newEntry = path == NEW_ENTRY_ROUTE
        if (model.isBusy || current == path || (newEntry && current.startsWith("$NEW_ENTRY_ROUTE/"))) return
        model.clearError()
        stack = when {
            // A previous form can still be composed during its exit animation.
            newEntry -> ArrayList(stack + "$path/${newId()}")
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
    val mealMonth = java.time.YearMonth.from(now)
    val currentMealMonth = remember(mealMonth) { MealFilter.forMonth(mealMonth) }

    val transition = updateTransition(stack, label = "页面过渡")
    val pageSettled = transition.currentState == transition.targetState && !transition.isRunning
    val onPrimaryPage = top in roots
    val showBottomBar = onPrimaryPage || transition.currentState.last() in roots
    NavigationFrame(showBottomBar, bottomBar = {
        MainNavigation(root, onPrimaryPage, ::open)
    }) { bottomBarHeight ->
        SharedTransitionLayout(Modifier.fillMaxSize().clipToBounds().testTag("navigation-pages")) {
            val shared = this
            transition.AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                contentKey = { it.last() },
                transitionSpec = {
                    val from = initialState.last()
                    val to = targetState.last()
                    val record = recordNavigation
                    val transform = record != null && ((record.source == from && record.detail == to) ||
                        (record.detail == from && record.source == to))
                    val pageTransition = when {
                        transform -> fadeIn(tween(280, easing = motion)) togetherWith fadeOut(tween(280, easing = motion))
                        from in roots && to in roots -> {
                            (fadeIn(tween(112, delayMillis = 88, easing = motion)) +
                                scaleIn(tween(112, delayMillis = 88, easing = motion), initialScale = 0.96f)) togetherWith
                                fadeOut(tween(88, easing = motion))
                        }
                        targetState.size > initialState.size ->
                            slideInHorizontally(tween(240, easing = motion)) { it } togetherWith
                                slideOutHorizontally(tween(240, easing = motion)) { -it / 5 }
                        else -> slideInHorizontally(tween(220, easing = motion)) { -it / 5 } togetherWith
                            slideOutHorizontally(tween(220, easing = motion)) { it }
                    }
                    pageTransition.targetContentZIndex = targetState.size.toFloat()
                    pageTransition.using(null)
                },
            ) { pageStack ->
                val path = pageStack.last()
                holder.SaveableStateProvider(path) {
                    val parts = path.split("/")
                    val goBack = { if (!model.isBusy && path == stack.last()) back() }
                    val active = path == top
                    val navigation = recordNavigation
                    val key = navigation?.id?.takeIf { path == navigation.detail }
                    val outgoing = if (active) Modifier else Modifier.inert(path)
                    val pageBackground = Modifier.background(MaterialTheme.colorScheme.background)
                    val pageInsets = if (path in roots) {
                        Modifier.padding(bottom = bottomBarHeight).clipToBounds().then(pageBackground)
                    } else {
                        pageBackground.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    }
                    CompositionLocalProvider(LocalRecordMotion provides RecordMotion(shared, this@AnimatedContent)) {
                        Box(recordBounds(outgoing, key).fillMaxSize().then(pageInsets)) {
                            when (parts[0]) {
                                "home" -> Home(records, now, ::open, ::openMeal)
                                "meals" -> MealsPage(
                                    records = records,
                                    filter = filter,
                                    statsFilter = mealStatsFilter ?: currentMealMonth,
                                    currentMonth = currentMealMonth,
                                    daily = { filter = null },
                                    clearConditions = { filter = MealFilter() },
                                    openFilter = { open("mealFilter") },
                                    openStatsFilter = { open("mealFilter/statistics") },
                                    selectStatsMonth = { month ->
                                        mealStatsFilter = (mealStatsFilter ?: currentMealMonth).withMonth(month)
                                            .takeUnless { it == currentMealMonth }
                                    },
                                    openMeal = ::openMeal,
                                ) { meal ->
                                    openRecord("meal/${meal.date}/${meal.mealSlot.name}/${meal.id}", meal.id)
                                }
                                "meal" -> MealForm(records, parts[3].takeUnless { it == "-" }, parts[1], MealSlot.valueOf(parts[2]), model, goBack)
                                "mealFilter" -> {
                                    val statistics = parts.getOrNull(1) == "statistics"
                                    val defaults = if (statistics) currentMealMonth else MealFilter()
                                    val applied = if (statistics) mealStatsFilter else filter
                                    MealFilterPage(records, applied ?: defaults, defaults, currentMealMonth, goBack) {
                                        if (statistics) mealStatsFilter = it.takeUnless { it == currentMealMonth }
                                        else filter = it
                                        back()
                                    }
                                }
                                "settings" -> {
                                    val config by model.backupServices.github.state.collectAsState()
                                    SettingsPage(config?.pause(records.state) != null, ::open)
                                }
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
                                "ledger" -> LedgerPage(records, ::open,
                                    openDetails = { id -> openRecord("entry/$id", id) }, active = active)
                                "wallet" -> WalletForm(records, parts[1].takeUnless { it == "-" }, model, goBack)
                                "entry" -> EntryDetails(records, parts[1], model, ::open, goBack)
                                "entryForm" -> EntryForm(records, parts[1].takeUnless { it == "-" }, null, model, goBack, ::back,
                                    readyForSheet = active && pageSettled)
                                "refund" -> EntryForm(records, null, parts[1], model, goBack, ::back)
                            }
                        }
                    }
                }
            }
        }
    }
    BackupAlerts(records, model) {
        if (stack.last() != "github") {
            model.clearError()
            stack = arrayListOf("settings", "github")
        }
    }
}

@Composable
private fun NavigationFrame(showBottomBar: Boolean, bottomBar: @Composable () -> Unit, pages: @Composable (Dp) -> Unit) {
    // Keep the primary viewport's height stable while its scroll position is restored.
    // Place the bar behind the full-height subpage during the slide.
    val frameInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    SubcomposeLayout(Modifier.fillMaxSize().windowInsetsPadding(frameInsets).imePadding()) { constraints ->
        val bar = subcompose("bottomBar", bottomBar).single().measure(constraints.copy(minHeight = 0))
        val viewport = subcompose("pages") { pages(bar.height.toDp()) }.single().measure(constraints)
        layout(viewport.width, viewport.height) {
            if (showBottomBar) bar.placeRelative(0, viewport.height - bar.height)
            viewport.placeRelative(0, 0)
        }
    }
}

@Composable
internal fun BackupAlerts(records: Records, model: AppViewModel, handlePause: () -> Unit) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val config by model.backupServices.github.state.collectAsState()
    val busy by model.busy.collectAsState()
    val preview by model.restorePreview.collectAsState()
    if (lifecycle != Lifecycle.State.RESUMED || busy || preview != null) return
    val pause = config?.pause(records.state)
    if (pause != null && pause.reason != GitHubPauseReason.REVIEW && pause.id != config?.acknowledgedPause) {
        GitHubPauseAlert(pause, model, handlePause)
    } else {
        CapacityAlert(records, model)
    }
}

@Composable
private fun MainNavigation(root: String, active: Boolean, open: (String) -> Unit) {
    val icons = listOf(R.drawable.ks_house, R.drawable.ks_utensils, R.drawable.ks_wallet, R.drawable.ks_sliders_horizontal)
    val colors = MaterialTheme.colorScheme
    val modifier = Modifier.testTag("main-navigation").let { if (active) it else it.inert(root) }
    Surface(modifier, color = colors.surface) {
        Column {
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 8.dp)
                .selectableGroup(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                roots.forEachIndexed { index, route ->
                    val selected = root == route
                    val tint = if (selected) colors.primary else colors.onSurfaceVariant
                    Column(Modifier.weight(1f).heightIn(min = 49.dp).clip(ControlShape)
                        .selectable(selected, enabled = active, role = Role.Tab, onClick = { open(route) }).padding(vertical = 4.dp),
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
}

@Composable
internal fun Home(records: Records, now: LocalDateTime, open: (String) -> Unit, meal: (String, MealSlot, String?) -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Paper(Modifier.testTag("home-meals-card")) {
                HomeCardHeading("今日用餐", "全部记录") { open("meals") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(recorded.size.toString(), fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.Medium)
                    Secondary("/ 3 餐已记录")
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
            val ledger = remember(records) { Ledger(records) }
            Paper(Modifier.testTag("home-ledger-card")) {
                HomeCardHeading("本月收支", "查看账本") { open("ledger") }
                val month = java.time.YearMonth.from(now).toString()
                val totals = remember(ledger, month) {
                    ledger.statistics(LedgerPeriod(month), WalletRange()).sortedBy { it.currency != "CNY" }
                }
                if (totals.isEmpty()) Secondary("暂无常规钱包的收支")
                totals.forEachIndexed { index, total ->
                    if (index > 0) HorizontalDivider()
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(total.currency, color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        HomeCurrencyAmounts(displayMoney(total.expenses, total.scale),
                            displayMoney(total.income, total.scale))
                    }
                }
                Primary("记一笔", { open(newEntryRoute(ledger)) }, Modifier.fillMaxWidth(),
                    icon = designIcon(R.drawable.ks_plus))
            }
        }
    }
}

@Composable
private fun HomeCurrencyAmounts(expense: String, income: String) {
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val amounts = listOf("净支出" to expense, "收入" to income)
    val gap = 12.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnWidth = with(LocalDensity.current) { ((maxWidth - gap) / 2).roundToPx() }
        if (amounts.any { (_, value) -> measurer.measure(value, style, softWrap = false).size.width > columnWidth }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                amounts.forEach { (label, value) -> HomeAmount(label, value, style, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                amounts.forEach { (label, value) -> HomeAmount(label, value, style, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HomeAmount(label: String, value: String, style: TextStyle, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = style)
    }
}

@Composable
private fun HomeCardHeading(title: String, actionText: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        TextAction(actionText, onAction, designIcon(R.drawable.ks_chevron_right))
    }
}
