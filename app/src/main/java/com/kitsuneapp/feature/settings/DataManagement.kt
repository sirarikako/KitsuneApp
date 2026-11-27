package com.kitsuneapp.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kitsuneapp.AppViewModel
import com.kitsuneapp.R
import com.kitsuneapp.RestorePreview
import com.kitsuneapp.backup.CLOUD_LIMIT
import com.kitsuneapp.backup.FolderActivity
import com.kitsuneapp.backup.FolderOption
import com.kitsuneapp.backup.backupReadableTime
import com.kitsuneapp.backup.folderDisplayName
import com.kitsuneapp.backup.formatBackupSize
import com.kitsuneapp.backup.input
import com.kitsuneapp.backup.output
import com.kitsuneapp.backup.suggestedBackupFilename
import com.kitsuneapp.data.*
import com.kitsuneapp.ui.*
import com.kitsuneapp.userMessage
import kotlinx.coroutines.CancellationException

@Composable
fun LocalBackupPage(records: Records, model: AppViewModel, back: () -> Unit) {
    val backup = model.backupServices
    val folder by backup.directory.collectAsState()
    val activity by backup.folderActivity.collectAsState()
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    val preview by model.restorePreview.collectAsState()
    val localRestore = preview as? RestorePreview.Local
    val restored by model.localRestoreSucceeded.collectAsState()
    var showRestorePicker by rememberSaveable { mutableStateOf(false) }
    var loadingOptions by remember { mutableStateOf(false) }
    var folderOptions by remember { mutableStateOf<List<FolderOption>>(emptyList()) }
    var optionsError by remember { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf("") }

    val state = records.state
    val owned = folder?.takeIf {
        it.installation == state.installationId &&
            (it.generation != state.generation || it.revision <= state.revision)
    }
    val ownershipInvalid = folder != null && owned == null
    val needsReauth = ownershipInvalid || owned?.authError == true
    val configured = owned != null && !owned.authError
    val current = configured && owned.generation == state.generation && owned.revision == state.revision
    val failed = owned != null && !needsReauth && (owned.failedRevision == state.revision || owned.error != null)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        backup.verifyFolder()
    }

    LaunchedEffect(showRestorePicker, configured, owned?.uri, owned?.published) {
        if (!showRestorePicker) return@LaunchedEffect
        if (!configured) {
            loadingOptions = false
            folderOptions = emptyList()
            optionsError = null
            return@LaunchedEffect
        }
        loadingOptions = true
        optionsError = null
        try {
            folderOptions = backup.folderOptions()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            folderOptions = emptyList()
            optionsError = failure.userMessage("无法读取已授权目录中的自动备份")
        } finally {
            loadingOptions = false
        }
    }

    val selectFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) model.backup({
            model.clearLocalRestoreResult()
            status = "备份目录已更新"
        }) { configureFolder(uri) }
    }
    val protect = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && localRestore != null) model.restoreBackup(localRestore, uri)
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) model.backup({
            model.clearLocalRestoreResult()
            status = "完整备份已导出并校验"
        }) { exportJson(uri) }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            showRestorePicker = false
            model.previewLocalRestore(uri)
        }
    }

    val statusText = when {
        needsReauth -> owned?.error ?: "目录授权异常，请重新授权"
        owned == null -> "未配置"
        activity == FolderActivity.CHECKING -> "正在检查备份完整性"
        activity == FolderActivity.BACKING_UP -> "正在备份"
        failed -> owned.error ?: "备份失败"
        owned.cleanupPending -> "已发布新备份，正在清理旧文件"
        current -> "自动备份正常"
        else -> "等待自动备份"
    }
    val statusColor = if (needsReauth || failed) MaterialTheme.colorScheme.error else Color.Unspecified

    Column {
        Header("本地备份与恢复", back = back)
        Form {
            Section("自动备份")
            Paper {
                Text(statusText, color = statusColor, fontWeight = FontWeight.Medium)
                if (activity != FolderActivity.IDLE) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                backupStatusTime(owned?.succeededAt)?.let { Secondary("上次成功 · $it") }
                if (owned != null) {
                    Secondary("备份目录 · ${folderDisplayName(owned.uri)}")
                } else {
                    Secondary("授权本地目录后，数据变更会自动备份并在进入页面时校验完整性。")
                }
                when {
                    owned == null -> Primary("设置目录", { selectFolder.launch(null) }, Modifier.fillMaxWidth(), enabled = !busy)
                    needsReauth -> Primary("重新授权目录", { selectFolder.launch(null) }, Modifier.fillMaxWidth(), enabled = !busy)
                    failed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Primary("重试", { model.backup { scheduleFolder(force = true) } },
                            Modifier.weight(1f), enabled = !busy && activity == FolderActivity.IDLE)
                        Choice("更改目录", { selectFolder.launch(null) }, Modifier.weight(1f), enabled = !busy)
                    }
                    else -> Choice("更改目录", { selectFolder.launch(null) }, Modifier.fillMaxWidth(), enabled = !busy)
                }
            }

            Section("备份与恢复")
            Paper(padding = 0) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Setting("手动备份", designIcon(R.drawable.ks_files),
                        hint = "导出完整 JSON 到自选位置，不参与自动清理", enabled = !busy) {
                        exportJson.launch(suggestedBackupFilename(BackupKind.MANUAL))
                    }
                    HorizontalDivider()
                    Setting("恢复备份", designIcon(R.drawable.ks_undo2),
                        hint = "从自动备份候选或本地 JSON 文件恢复", enabled = !busy) {
                        model.clearError()
                        showRestorePicker = true
                    }
                }
            }
            Secondary("完整备份包含所有业务数据和外观。文件为可读 JSON，不含凭据。")

            if (records.state.arrayElements >= MAX_ARRAY_ELEMENTS * 8 / 10) {
                Secondary("完整备份记录及关联已用 ${records.state.arrayElements} / $MAX_ARRAY_ELEMENTS，接近容量。")
            }
            val result = if (restored) "完整恢复成功" else status
            if (result.isNotBlank()) Text(result, color = incomeColor)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showRestorePicker) {
        RestoreSourceDialog(
            configured = configured,
            needsReauth = needsReauth,
            loading = loadingOptions,
            options = folderOptions,
            optionsError = optionsError,
            busy = busy,
            error = error,
            onSelectOption = { option ->
                model.previewFolderRestore(option) { showRestorePicker = false }
            },
            onChooseOtherFile = {
                importJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
            onDismiss = { if (!busy) showRestorePicker = false },
        )
    }

    localRestore?.let { source ->
        RestoreDialog(source.snapshot, records.state.revision != 0L, configured, busy, error,
            { model.discardRestorePreview(source) }, { model.restoreBackup(source, null) },
            { protect.launch(suggestedBackupFilename(BackupKind.PRE_RESTORE)) })
    }
}

@Composable
private fun RestoreSourceDialog(
    configured: Boolean,
    needsReauth: Boolean,
    loading: Boolean,
    options: List<FolderOption>,
    optionsError: String?,
    busy: Boolean,
    error: String?,
    onSelectOption: (FolderOption) -> Unit,
    onChooseOtherFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("恢复备份") },
        shape = DialogShape,
        text = {
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    needsReauth -> Secondary("自动备份目录授权异常，无法读取目录中的备份。仍可选择其他本地备份文件。")
                    !configured -> Secondary("尚未配置自动备份目录，可直接选择本地备份文件。")
                    loading -> {
                        Secondary("正在读取已发布的自动备份…")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    optionsError != null -> Text(optionsError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    options.isEmpty() -> Secondary("已授权目录中暂无已发布的自动备份。")
                    else -> {
                        Secondary("已授权目录中的自动备份（按时间倒序）")
                        options.forEach { option ->
                            val timeLabel = option.time?.let(::backupReadableTime) ?: option.document.name
                            val sizeLabel = formatBackupSize(option.marker.size)
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(ControlShape)
                                    .clickable(enabled = !busy, role = Role.Button) { onSelectOption(option) },
                                shape = ControlShape,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(timeLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Secondary(sizeLabel)
                                    }
                                    Icon(designIcon(R.drawable.ks_chevron_right), null, Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Choice("选择其他备份文件", onChooseOtherFile, Modifier.fillMaxWidth(),
                    enabled = !busy, icon = designIcon(R.drawable.ks_files))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onDismiss, enabled = !busy) { Text("取消") } },
    )
}

@Composable
internal fun RestoreDialog(staged: StagedSnapshot, needsProtection: Boolean, folder: Boolean, busy: Boolean,
                           error: String?, dismiss: () -> Unit, apply: () -> Unit, chooseProtection: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("确认完整恢复") }, shape = DialogShape,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
fun CsvPage(records: Records, model: AppViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val cloud by model.backupServices.github.state.collectAsState()
    val estimateCloud = cloud?.installation == records.state.installationId
    var kind by rememberSaveable { mutableStateOf(CsvKind.MEALS) }
    var exportKind by rememberSaveable { mutableStateOf(CsvKind.MEALS) }
    var template by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("") }
    var document by remember { mutableStateOf<CsvDocument?>(null) }
    var preview by remember { mutableStateOf<CsvPreview?>(null) }
    var mappings by remember { mutableStateOf(CsvMappings()) }
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) model.backup({
            status = if (template) "${exportKind.label} CSV 模板已导出" else "${exportKind.label} CSV 已导出"
        }) {
            context.contentResolver.output(uri).use { csv.export(exportKind, it, template) }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.backup {
            val source = context.contentResolver.input(uri).use { CsvDocument.load(kind, it) }
            mappings = CsvMappings()
            document = source
            preview = csv.preview(source, mappings, estimateCloud)
        }
    }

    Column {
        Header("CSV 导入与导出", back = back)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Tabs(listOf("用餐", "记账"), if (kind == CsvKind.MEALS) 0 else 1) { index ->
                if (!busy) {
                    val next = if (index == 0) CsvKind.MEALS else CsvKind.LEDGER
                    if (next != kind) {
                        kind = next
                        status = ""
                        model.clearError()
                    }
                }
            }
        }
        Form {
            Section("导入")
            Paper {
                Primary("选择 CSV 文件", {
                    import.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                }, Modifier.fillMaxWidth(), enabled = !busy)
                Choice("导出 CSV 模板", {
                    template = true
                    exportKind = kind
                    export.launch("kitsune-" + kind.name.lowercase() + "-template.csv")
                }, Modifier.fillMaxWidth(), enabled = !busy)
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        .clickable(role = Role.Button) { showGuide = !showGuide }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("CSV 格式说明", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Secondary(if (showGuide) "收起" else "展开")
                    Icon(designIcon(R.drawable.ks_chevron_down), null,
                        Modifier.size(14.dp).rotate(if (showGuide) 180f else 0f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(showGuide) {
                    CsvFormatGuide(kind)
                }
            }

            Section("导出")
            Paper {
                Secondary(if (kind == CsvKind.MEALS) {
                    "导出全部用餐记录为 UTF-8 CSV 文件，保留稳定记录 ID。"
                } else {
                    "导出全部记账记录为 UTF-8 CSV 文件，保留稳定记录 ID。"
                })
                Choice("导出全部${kind.label}记录", {
                    template = false
                    exportKind = kind
                    export.launch("kitsune-" + kind.name.lowercase() + ".csv")
                }, Modifier.fillMaxWidth(), enabled = !busy)
            }

            if (status.isNotBlank()) Text(status, color = incomeColor)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    document?.let { source ->
        fun recheck(next: CsvMappings) {
            mappings = next
            preview = null
            model.backup { preview = csv.preview(source, next, estimateCloud) }
        }
        CsvImportPreviewDialog(
            records = records,
            source = source,
            preview = preview,
            mappings = mappings,
            busy = busy,
            error = error,
            onRecheck = ::recheck,
            onCommit = {
                val count = preview?.additions ?: 0
                model.backup({
                    document = null
                    preview = null
                    status = "CSV 导入已提交（新增 $count 条）"
                }) { csv.commit(source, mappings) }
            },
            onDismiss = { if (!busy) { document = null; preview = null } },
        )
    }
}

@Composable
private fun CsvFormatGuide(kind: CsvKind) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (kind == CsvKind.MEALS) {
            Secondary("表头：id, date, meal_slot, planned_at_home, meal_method, preparation_categories, dishes, note")
            Secondary("日期：YYYY-MM-DD；餐次：BREAKFAST / LUNCH / DINNER；计划在家：true / false（必填）。")
            Secondary("计划在家为 true 时须填写 meal_method；preparation_categories 为 JSON 字符串数组（如 [\"炒菜\"]，无分类填 []）。")
            Secondary("使用 UTF-8 编码（支持 BOM）。未知用餐方式可在导入预览中映射到已有方式。")
        } else {
            Secondary("表头：id, date, time, type, wallet, currency, amount, category, to_wallet, to_currency, to_amount, original_expense_id, note")
            Secondary("日期：YYYY-MM-DD；时间：24 小时制 HH:mm（必填）；类型：INCOME / EXPENSE / TRANSFER / REFUND。")
            Secondary("金额为十进制文本（不含千分位和货币符号），精度不得超过钱包币种支持范围。")
            Secondary("TRANSFER 须填写转入钱包、币种和金额；REFUND 须填写 original_expense_id。未知钱包可在预览中映射到同币种钱包。")
        }
    }
}

@Composable
internal fun CsvImportPreviewDialog(
    records: Records,
    source: CsvDocument,
    preview: CsvPreview?,
    mappings: CsvMappings,
    busy: Boolean,
    error: String?,
    onRecheck: (CsvMappings) -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val additions = preview?.additions ?: 0
    val canCommit = !busy && preview != null && preview.additions > 0 && preview.errors.isEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(source.kind.label + " CSV 导入预览") },
        shape = DialogShape,
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                preview?.let {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CsvMetric("新增", it.additions.toString(),
                            if (it.additions > 0) incomeColor else Color.Unspecified, Modifier.weight(1f))
                        CsvMetric("重复", it.duplicates.toString(), Color.Unspecified, Modifier.weight(1f))
                        CsvMetric("错误", it.errors.size.toString(),
                            if (it.errors.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Unspecified, Modifier.weight(1f))
                    }
                    Secondary("预计完整备份记录及关联 ${it.elements} / $MAX_ARRAY_ELEMENTS")
                    if (it.elements >= MAX_ARRAY_ELEMENTS * 8 / 10) Secondary("接近完整备份记录与关联上限")
                    val bytes = it.snapshotBytes
                    if (bytes != null && bytes >= CLOUD_LIMIT * 8 / 10) {
                        Secondary(if (bytes > CLOUD_LIMIT) "预计超过 GitHub 备份的 32 MiB 上限，仍可使用本地备份。"
                            else "预计接近 GitHub 备份的 32 MiB 上限。")
                    }
                    it.errors.take(20).forEach { row -> Text(row, color = MaterialTheme.colorScheme.error) }
                    if (it.errors.size > 20) Secondary("另有 ${it.errors.size - 20} 条错误")
                }
                source.methods().filter { name -> records.methods.none { it.normalizedName == normalizedName(name) } }
                    .forEach { name ->
                        SheetSelect("映射 $name", mappings.methods[name], records.methods.map { it.id }, records::methodName,
                            { onRecheck(mappings.copy(methods = mappings.methods + (name to it))) }, enabled = !busy)
                    }
                source.wallets().filter { (name, code) -> records.wallets.none {
                    it.normalizedName == normalizedName(name) && it.currencyCode == code
                } }.forEach { (name, code) ->
                    val choices = records.wallets.filter { it.currencyCode == code }.associateBy { it.id }
                    if (choices.isEmpty()) Secondary("请先新建 $code 钱包，再重新导入。")
                    else SheetSelect("映射 $name · $code", mappings.wallets[name to code], choices.keys.toList(),
                        { id -> choices[id]?.name.orEmpty() }, { id ->
                            onRecheck(mappings.copy(wallets = mappings.wallets + ((name to code) to id)))
                        }, enabled = !busy, secondary = { id -> choices[id]?.let(::walletSelectionDetails) })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Primary("确认导入（$additions 条）", onCommit, enabled = canCommit)
        },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("取消") } },
    )
}

@Composable
private fun CsvMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Secondary(label)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

private val CsvKind.label get() = if (this == CsvKind.MEALS) "用餐" else "记账"
