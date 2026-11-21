package com.kitsuneapp.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsuneapp.AppViewModel
import com.kitsuneapp.backup.FolderOption
import com.kitsuneapp.backup.input
import com.kitsuneapp.backup.output
import com.kitsuneapp.data.*
import com.kitsuneapp.ui.*

@Composable
fun DataManagementPage(records: Records, model: AppViewModel, back: () -> Unit) {
    val backup = model.backupServices
    val directory by backup.directory.collectAsState()
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    var staged by remember { mutableStateOf<StagedSnapshot?>(null) }
    var folderOptions by remember { mutableStateOf<List<FolderOption>?>(null) }
    var status by rememberSaveable { mutableStateOf("") }
    val configured = directory?.let { it.installation == records.state.installationId &&
        (it.generation != records.state.generation || it.revision <= records.state.revision) } == true
    DisposableEffect(staged) {
        val stage = staged
        onDispose { stage?.close() }
    }
    fun restore(uri: Uri? = null) {
        val stage = staged ?: return
        model.backup({
            staged = null
            status = "完整恢复成功"
        }) { restore(stage, uri) }
    }
    val protect = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) restore(uri)
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) model.backup({ status = "完整备份已导出并校验" }) { exportJson(uri) }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.backup { staged = openFile(uri) }
    }

    Column {
        Header("导入与导出", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("完整 JSON")
            Paper {
                Setting("导出完整备份") { if (!busy) exportJson.launch("kitsune-manual.json") }
                HorizontalDivider()
                Setting("从 JSON 恢复") { if (!busy) importJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                if (configured) {
                    HorizontalDivider()
                    Setting("从文件夹备份恢复") {
                        if (!busy) model.backup {
                            val options = folderOptions()
                            require(options.isNotEmpty()) { "目录中没有已发布的自动备份" }
                            folderOptions = options
                        }
                    }
                }
                Secondary("包含所有业务数据和外观。文件为可读 JSON，不含凭据。")
            }
            Section("CSV 批量交换")
            CsvPanel(records, model) { status = it }
            if (records.state.arrayElements >= MAX_ARRAY_ELEMENTS * 8 / 10) {
                Secondary("完整备份记录及关联已用 ${records.state.arrayElements} / $MAX_ARRAY_ELEMENTS，接近容量。")
            }
            if (status.isNotBlank()) Text(status, color = incomeColor)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    folderOptions?.let { options ->
        AlertDialog(onDismissRequest = { if (!busy) folderOptions = null },
            title = { Text("选择已发布的文件夹备份") }, shape = DialogShape,
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        TextButton({
                            model.backup({ folderOptions = null }) { staged = openFolder(option) }
                        }, enabled = !busy) { Text(option.document.name) }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = { TextButton({ folderOptions = null }, enabled = !busy) { Text("取消") } })
    }
    staged?.let { stage ->
        RestoreDialog(stage, records.state.revision != 0L, configured, busy, error,
            { staged = null }, { restore() }, { protect.launch("kitsune-pre-restore.json") })
    }
}

@Composable
internal fun RestoreDialog(staged: StagedSnapshot, needsProtection: Boolean, folder: Boolean, busy: Boolean,
                           error: String?, dismiss: () -> Unit, apply: () -> Unit, chooseProtection: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("确认完整恢复") }, shape = DialogShape,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("备份时间 · ${staged.info.exportedAt}")
                Text("用餐 ${staged.counts.meals} 条 · 钱包 ${staged.counts.wallets} 个 · 账目 ${staged.counts.entries} 笔")
                Text("恢复将替换本机全部业务数据与外观。")
                if (needsProtection) Secondary("恢复前需写入并校验本机保护文件。")
                if (needsProtection && folder) TextButton(chooseProtection, enabled = !busy) { Text("另选位置导出保护文件") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = {
            val choose = needsProtection && !folder
            Primary(if (choose) "导出保护文件并恢复" else "恢复全部数据",
                if (choose) chooseProtection else apply, enabled = !busy)
        }, dismissButton = { TextButton(dismiss, enabled = !busy) { Text("取消") } })
}

@Composable
private fun CsvPanel(records: Records, model: AppViewModel, message: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var kind by rememberSaveable { mutableStateOf(CsvKind.MEALS) }
    var exportKind by rememberSaveable { mutableStateOf(CsvKind.MEALS) }
    var template by rememberSaveable { mutableStateOf(false) }
    var document by remember { mutableStateOf<CsvDocument?>(null) }
    var preview by remember { mutableStateOf<CsvPreview?>(null) }
    var mappings by remember { mutableStateOf(CsvMappings()) }
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) model.backup({ message("CSV 已导出") }) {
            context.contentResolver.output(uri).use { csv.export(exportKind, it, template) }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.backup {
            val source = context.contentResolver.input(uri).use { CsvDocument.load(kind, it) }
            mappings = CsvMappings()
            document = source
            preview = csv.preview(source, mappings)
        }
    }
    Paper {
        Select("模板", kind, CsvKind.entries, { it.label }, { kind = it }, enabled = !busy)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("下载模板", {
                template = true; exportKind = kind; export.launch("kitsune-" + kind.name.lowercase() + "-template.csv")
            }, Modifier.weight(1f), enabled = !busy)
            Choice("导出记录", {
                template = false; exportKind = kind; export.launch("kitsune-" + kind.name.lowercase() + ".csv")
            }, Modifier.weight(1f), enabled = !busy)
        }
        Primary("选择 CSV 文件", { import.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
            Modifier.fillMaxWidth(), enabled = !busy)
        Secondary(if (kind == CsvKind.MEALS) "日期 YYYY-MM-DD，餐次 BREAKFAST／LUNCH／DINNER，计划在家 true／false。"
            else "日期 YYYY-MM-DD，时间 HH:mm，类型 INCOME／EXPENSE／TRANSFER／REFUND；按钱包币种精度填写金额。")
    }
    document?.let { source ->
        fun recheck(next: CsvMappings) {
            mappings = next
            preview = null
            model.backup { preview = csv.preview(source, next) }
        }
        AlertDialog(onDismissRequest = { if (!busy) { document = null; preview = null } },
            title = { Text(source.kind.label + " CSV 导入预览") }, shape = DialogShape,
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    preview?.let {
                        Text("新增 ${it.additions} · 重复 ${it.duplicates} · 错误 ${it.errors.size}")
                        Secondary("预计完整备份记录及关联 ${it.elements} / $MAX_ARRAY_ELEMENTS")
                        if (it.elements >= MAX_ARRAY_ELEMENTS * 8 / 10) Secondary("接近完整备份容量")
                        it.errors.take(20).forEach { row -> Text(row, color = MaterialTheme.colorScheme.error) }
                        if (it.errors.size > 20) Secondary("另有 ${it.errors.size - 20} 条错误")
                    }
                    source.methods().filter { name -> records.methods.none { it.normalizedName == normalizedName(name) } }
                        .forEach { name ->
                            Select("映射 $name", mappings.methods[name], records.methods.map { it.id }, records::methodName,
                                { recheck(mappings.copy(methods = mappings.methods + (name to it))) }, enabled = !busy)
                        }
                    source.wallets().filter { (name, code) -> records.wallets.none {
                        it.normalizedName == normalizedName(name) && it.currencyCode == code
                    } }.forEach { (name, code) ->
                        val choices = records.wallets.filter { it.currencyCode == code }
                        if (choices.isEmpty()) Secondary("请先新建 $code 钱包，再重新导入。")
                        else Select("映射 $name · $code", mappings.wallets[name to code], choices.map { it.id },
                            { id -> choices.find { it.id == id }?.name.orEmpty() }, { id ->
                                recheck(mappings.copy(wallets = mappings.wallets + ((name to code) to id)))
                            }, enabled = !busy)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }, confirmButton = {
                Primary("确认整批导入", {
                    model.backup({
                        document = null
                        preview = null
                        message("CSV 导入已提交")
                    }) { csv.commit(source, mappings) }
                }, enabled = !busy && preview?.let { it.additions > 0 && it.errors.isEmpty() } == true)
            }, dismissButton = { TextButton({ document = null; preview = null }, enabled = !busy) { Text("取消") } })
    }
}

private val CsvKind.label get() = if (this == CsvKind.MEALS) "用餐" else "记账"

@Composable
fun FolderPage(records: Records, model: AppViewModel, open: (String) -> Unit, back: () -> Unit) {
    val backup = model.backupServices
    val folder by backup.directory.collectAsState()
    val running by backup.folderRunning.collectAsState()
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    val directory = folder?.takeIf { it.installation == records.state.installationId }
    val state = records.state
    val current = directory?.generation == state.generation && directory.revision == state.revision
    val select = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) model.backup { configureFolder(uri) }
    }
    Column {
        Header("本地文件夹备份", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Paper {
                Text(when {
                    folder != null && (directory == null || directory.generation == state.generation && directory.revision > state.revision) ->
                        "需重新授权文件夹"
                    directory == null -> "未配置"
                    running -> "正在备份"
                    directory.failedRevision == state.revision -> directory.error ?: "备份失败"
                    current -> "最新修订已备份"
                    else -> "最新修订等待备份"
                })
                directory?.succeededAt?.let { Secondary("上次成功 · $it") }
                if (directory?.cleanupPending == true) Secondary("已发布新备份，正在等待清理旧文件。")
                Secondary("使用 Android 文件夹授权。新备份校验发布后才清理之前的自动备份。")
            }
            Primary(if (directory == null) "授权文件夹" else "重新授权文件夹", { select.launch(null) }, Modifier.fillMaxWidth(), !busy)
            if (directory != null) {
                Choice("立即备份", { model.backup { scheduleFolder(force = true) } }, Modifier.fillMaxWidth(), enabled = !busy && !running)
                Choice("从文件夹备份恢复", { open("data") }, Modifier.fillMaxWidth(), enabled = !busy)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
