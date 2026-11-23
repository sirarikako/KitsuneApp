package com.kitsuneapp.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsuneapp.AppViewModel
import com.kitsuneapp.RestorePreview
import com.kitsuneapp.backup.CLOUD_LIMIT
import com.kitsuneapp.backup.GitHubPause
import com.kitsuneapp.backup.GitHubPauseReason
import com.kitsuneapp.backup.suggestedBackupFilename
import com.kitsuneapp.data.BackupKind
import com.kitsuneapp.data.Records
import com.kitsuneapp.data.backupStatusTime
import com.kitsuneapp.ui.*
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException

internal const val GITHUB_PAUSED = "自动备份已暂停"
private const val LOCAL_DATA_SAVED = "本机记录仍会正常保存，暂时不会自动上传。"

private enum class CloudDialogStep { SUMMARY, RESTORE, UPLOAD }

@Composable
fun GitHubPage(records: Records, model: AppViewModel, back: () -> Unit) {
    val backup = model.backupServices
    val service = backup.github
    val config by service.state.collectAsState()
    val running by service.running.collectAsState()
    val directory by backup.directory.collectAsState()
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    val state = records.state
    val owned = config?.takeIf { it.belongsTo(state) }
    val pause = config?.pause(state)
    val wifiAvailable by service.wifiChanges.collectAsStateWithLifecycle(service.wifiAvailable())
    val wifiChange by model.githubWifiChange.collectAsState()
    val wifiUpdate = wifiChange?.takeIf { it.binding == owned?.binding }
    val hasFolder = directory?.let { it.installation == state.installationId &&
        (it.generation != state.generation || it.revision <= state.revision) } == true
    var editingBinding by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = owned == null || editingBinding == owned.binding
    var target by rememberSaveable { mutableStateOf(config?.target?.let { "${it.owner}/${it.repository}" }.orEmpty()) }
    var branch by rememberSaveable { mutableStateOf(config?.target?.branch ?: "main") }
    var token by remember { mutableStateOf("") }
    var wifi by rememberSaveable { mutableStateOf(owned?.wifiOnly ?: false) }
    var threshold by rememberSaveable(owned?.capacity?.thresholdMb) { mutableStateOf((owned?.capacity?.thresholdMb ?: 800).toString()) }
    val preview by model.restorePreview.collectAsState()
    val cloudRestore = preview as? RestorePreview.Cloud
    var step by rememberSaveable { mutableStateOf(CloudDialogStep.SUMMARY) }
    var estimated by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.revision, owned?.repositoryId) {
        estimated = if (owned == null) null else try {
            service.estimateBytes()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            null
        }
    }

    val protect = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && cloudRestore != null) model.restoreBackup(cloudRestore, uri)
    }

    fun reconfigure() {
        target = config?.target?.let { "${it.owner}/${it.repository}" }.orEmpty()
        branch = config?.target?.branch ?: "main"
        token = ""
        wifi = owned?.wifiOnly ?: false
        editingBinding = owned?.binding
    }

    fun preview(resolvePause: Boolean) {
        step = CloudDialogStep.SUMMARY
        model.previewCloudRestore(resolvePause)
    }

    Column {
        Header("GitHub 备份", back = back)
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (busy || running) LinearProgressIndicator(Modifier.fillMaxSize())
        }
        Form {
            Paper {
                val status = owned?.status(state.revision, running, wifiAvailable)
                Text(if (pause != null) GITHUB_PAUSED else status?.label ?: "未配置",
                    style = MaterialTheme.typography.titleLarge)
                if (pause != null) {
                    Text(pause.description())
                    Secondary(LOCAL_DATA_SAVED)
                }
                config?.let {
                    Secondary("上次成功 · ${backupStatusTime(owned?.succeededAt) ?: "尚无成功记录"}")
                    Secondary("${it.target.owner}/${it.target.repository} · ${it.target.branch}")
                }
                if (owned != null && (pause == null || owned.consent != null)) {
                    if (pause != null && status != null) Secondary(status.label)
                    owned.status?.takeIf { owned.failedRevision == state.revision && it != "等待连接到 Wi-Fi" }
                        ?.let { Secondary(it) }
                }
                estimated?.takeIf { it >= CLOUD_LIMIT * 8 / 10 }?.let {
                    Text(if (it > CLOUD_LIMIT) "当前快照超过 GitHub 的 32 MiB 上限，请使用本地备份。"
                        else "当前快照接近 GitHub 的 32 MiB 上限。", color = MaterialTheme.colorScheme.error)
                }
                if (pause != null) {
                    val canConfigure = pause.reason == GitHubPauseReason.CONNECTION
                    Primary(pause.reason.action, {
                        if (canConfigure) reconfigure() else preview(resolvePause = true)
                    }, Modifier.fillMaxWidth(), enabled = !busy && !running && !(canConfigure && editing))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (editing) {
                Section("连接私有仓库")
                Paper {
                    Field("仓库（owner/repository）", target, { target = it })
                    Field("分支", branch, { branch = it })
                    Field("细粒度访问令牌", token, { token = it }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Password),
                        transformation = PasswordVisualTransformation())
                    Toggle("仅使用 Wi-Fi", wifi, enabled = !busy) { wifi = it }
                    Secondary("请将令牌限定为数据仓库的 Contents 读写权限。完整 JSON 会保存在 backups/latest.json。")
                    Primary("测试并保存连接", {
                        val supplied = token
                        val savedTarget = target
                        val savedBranch = branch
                        val savedWifi = wifi
                        model.backup({ token = ""; editingBinding = null }) {
                            service.connect(savedTarget, savedBranch, supplied, savedWifi)
                        }
                    }, Modifier.fillMaxWidth(), enabled = !busy && token.isNotBlank())
                    if (owned != null) Choice("取消", { token = ""; editingBinding = null }, Modifier.fillMaxWidth(), enabled = !busy)
                }
            }
            if (owned != null) {
                Paper {
                    Primary("立即备份", { model.backup { service.schedule(manual = true) } }, Modifier.fillMaxWidth(),
                        enabled = pause == null && !busy && !running)
                    Choice("从 GitHub 恢复", { preview(resolvePause = false) }, Modifier.fillMaxWidth(), enabled = !busy)
                }
                Paper {
                    Toggle("仅使用 Wi-Fi", wifiUpdate?.requested ?: owned.wifiOnly,
                        enabled = !busy && !editing && wifiUpdate?.requested == null) { selected ->
                        model.setGitHubWifi(owned.binding, selected)
                    }
                    if (pause?.reason != GitHubPauseReason.CONNECTION) {
                        Choice("重新配置连接", ::reconfigure, Modifier.fillMaxWidth(), enabled = !busy && !editing)
                    }
                    wifiUpdate?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Section("仓库容量提醒")
                Paper {
                    val capacity = owned.capacity
                    Secondary(capacity.sizeKb?.let { "最近一次 · ${formatCapacity(it)} MB" } ?: "尚无有效容量读数")
                    backupStatusTime(capacity.checkedAt)?.let { Secondary("检查时间 · $it") }
                    if (capacity.failed) Secondary("当前无法更新仓库容量")
                    Field("提醒阈值（MB）", threshold, { threshold = it }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Choice("保存阈值", { model.backup { service.setThreshold(threshold) } }, Modifier.fillMaxWidth(), enabled = !busy)
                }
            }
        }
    }

    cloudRestore?.let { preview ->
        val staged = preview.source.stage
        val needsProtection = state.revision != 0L
        val dismiss = { model.discardRestorePreview(preview) }
        when (step) {
            CloudDialogStep.RESTORE -> if (staged != null) {
                RestoreDialog(staged, needsProtection, hasFolder, busy, error, dismiss,
                    { model.restoreBackup(preview, null) },
                    { protect.launch(suggestedBackupFilename(BackupKind.PRE_RESTORE)) })
            }
            CloudDialogStep.UPLOAD -> if (preview.source.pause != null) {
                val action = uploadAction(preview, owned?.accepted)
                AlertDialog(onDismissRequest = { if (!busy) dismiss() }, shape = DialogShape,
                    title = { Text(action) }, text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (staged != null) "这会用本机完整数据覆盖当前 GitHub 备份。"
                                else "这会将本机完整数据写入 GitHub，建立新的备份文件。")
                            Secondary("确认仅适用于本次核对的仓库、分支和远端版本。")
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }, confirmButton = {
                        Primary(if (staged == null) "确认备份" else "确认覆盖 GitHub",
                            { model.confirmCloudBackup(preview) }, enabled = !busy, danger = staged != null)
                    }, dismissButton = { TextButton(dismiss, enabled = !busy) { Text("取消") } })
            }
            CloudDialogStep.SUMMARY -> GitHubSummary(records, preview, needsProtection, busy, error, owned?.accepted,
                dismiss, { step = CloudDialogStep.RESTORE }, { step = CloudDialogStep.UPLOAD })
        }
    }
}

private fun uploadAction(preview: RestorePreview.Cloud, accepted: String?): String = when {
    preview.source.stage != null -> "保留本机数据并覆盖 GitHub"
    preview.source.pause?.reason == GitHubPauseReason.CONFLICT || !accepted.isNullOrEmpty() -> "重新创建 GitHub 备份"
    else -> "创建 GitHub 备份"
}

@Composable
private fun GitHubSummary(
    records: Records,
    preview: RestorePreview.Cloud,
    needsProtection: Boolean,
    busy: Boolean,
    error: String?,
    accepted: String?,
    dismiss: () -> Unit,
    restore: () -> Unit,
    upload: () -> Unit,
) {
    val staged = preview.source.stage
    val resolution = preview.source.pause
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, shape = DialogShape,
        title = { Text(resolution?.reason?.action ?: "从 GitHub 恢复") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("本机 · 用餐 ${records.meals.size} 条 · 钱包 ${records.wallets.size} 个 · 账目 ${records.entries.size} 笔")
                if (staged == null) {
                    Text("GitHub 尚无备份")
                } else {
                    Text("GitHub 备份时间 · ${backupStatusTime(staged.info.exportedAt) ?: staged.info.exportedAt}")
                    Text("用餐 ${staged.counts.meals} 条 · 钱包 ${staged.counts.wallets} 个 · 账目 ${staged.counts.entries} 笔")
                    if (resolution?.reason != GitHubPauseReason.RESTORED) {
                        Secondary("恢复会替换本机全部业务数据与外观。")
                        if (needsProtection) Secondary("继续恢复前需写入并校验本机保护文件。")
                    }
                }
                if (resolution != null) {
                    if (staged != null && resolution.reason != GitHubPauseReason.RESTORED) {
                        Choice("使用 GitHub 数据恢复本机", restore, Modifier.fillMaxWidth(), enabled = !busy)
                    }
                    Choice(uploadAction(preview, accepted), upload, Modifier.fillMaxWidth(), enabled = !busy)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            if (resolution == null && staged != null) Primary("继续恢复", restore, enabled = !busy)
            if (resolution == null && staged == null) TextButton(dismiss, enabled = !busy) { Text("返回") }
        }, dismissButton = {
            if (resolution != null || staged != null) TextButton(dismiss, enabled = !busy) { Text("取消") }
        })
}

private fun GitHubPause.description() = detail?.takeIf(String::isNotBlank) ?: reason.description

@Composable
internal fun GitHubPauseAlert(pause: GitHubPause, model: AppViewModel, onHandle: () -> Unit) {
    fun acknowledge(handle: Boolean) {
        model.backup({ if (handle) onHandle() }) { github.acknowledgePause(pause.id) }
    }
    AlertDialog(onDismissRequest = { acknowledge(false) }, shape = DialogShape,
        title = { Text("GitHub 自动备份已暂停") },
        text = { Text("${pause.description()}\n\n$LOCAL_DATA_SAVED", Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { Primary("前往处理", { acknowledge(true) }) },
        dismissButton = { TextButton({ acknowledge(false) }) { Text("稍后处理") } })
}

@Composable
fun CapacityAlert(records: Records, model: AppViewModel) {
    val source = model.backupServices.github
    val config by source.state.collectAsState()
    val busy by model.busy.collectAsState()
    val context = LocalContext.current
    val state = config?.takeIf { it.belongsTo(records.state) && it.capacity.pending }
    val size = state?.capacity?.sizeKb
    if (state == null || size == null || busy) return

    fun acknowledge(visit: Boolean) {
        model.backup({
            if (visit) {
                val url = "https://github.com/${state.target.owner}/${state.target.repository}"
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                    // The capacity reading has already been acknowledged.
                }
            }
        }) { source.dismissCapacity(state.repositoryId, size) }
    }
    AlertDialog(onDismissRequest = { acknowledge(false) }, shape = DialogShape, title = { Text("GitHub 仓库容量提醒") },
        text = { Text("${state.target.owner}/${state.target.repository} 已达 ${formatCapacity(size)} MB，超过 ${state.capacity.thresholdMb} MB 阈值。") },
        confirmButton = { Primary("查看 GitHub 仓库", { acknowledge(true) }) },
        dismissButton = { TextButton({ acknowledge(false) }) { Text("稍后处理") } })
}

private fun formatCapacity(kb: Long) = BigDecimal.valueOf(kb, 3).stripTrailingZeros().toPlainString()
