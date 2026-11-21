package com.kitsuneapp.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kitsuneapp.AppViewModel
import com.kitsuneapp.backup.CLOUD_LIMIT
import com.kitsuneapp.backup.GitHubPreview
import com.kitsuneapp.backup.GitHubState
import com.kitsuneapp.data.Records
import com.kitsuneapp.ui.*
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException

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
    val owned = config?.takeIf { it.installation == state.installationId &&
        (it.generation != state.generation || it.lastRevision <= state.revision) }
    val hasFolder = directory?.let { it.installation == state.installationId &&
        (it.generation != state.generation || it.revision <= state.revision) } == true
    var editing by rememberSaveable { mutableStateOf(owned == null) }
    var target by rememberSaveable { mutableStateOf(owned?.let { "${it.target.owner}/${it.target.repository}" }.orEmpty()) }
    var branch by rememberSaveable { mutableStateOf(owned?.target?.branch ?: "main") }
    var token by remember { mutableStateOf("") }
    var wifi by rememberSaveable { mutableStateOf(owned?.wifiOnly ?: false) }
    var threshold by rememberSaveable(owned?.capacity?.thresholdMb) { mutableStateOf((owned?.capacity?.thresholdMb ?: 800).toString()) }
    var preview by remember { mutableStateOf<GitHubPreview?>(null) }
    var restoring by rememberSaveable { mutableStateOf(false) }
    var estimated by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(preview) {
        val file = preview
        onDispose { file?.close() }
    }
    LaunchedEffect(state.revision, owned?.repositoryId) {
        estimated = if (owned == null) null else try {
            service.estimateBytes()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            null
        }
    }

    fun restore(uri: Uri? = null) {
        val source = preview ?: return
        model.backup({ restoring = false; preview = null }) { restoreCloud(source, uri) }
    }
    val protect = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) restore(uri)
    }

    Column {
        Header("GitHub 备份", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Paper {
                Text(when {
                    config != null && owned == null -> "需重新配置并确认"
                    owned == null -> "未配置"
                    running -> "备份执行中"
                    owned.generation != state.generation -> "完整恢复后等待确认更新远端"
                    owned.paused != null && owned.consent == null -> owned.paused
                    owned.status != null && owned.failedRevision == state.revision -> owned.status
                    owned.lastRevision == state.revision -> "最新修订已备份"
                    else -> "最新修订等待备份"
                })
                owned?.succeededAt?.let { Secondary("上次成功 · $it") }
                if (owned != null) {
                    Secondary("${owned.target.owner}/${owned.target.repository} · ${owned.target.branch}")
                }
                estimated?.takeIf { it >= CLOUD_LIMIT * 8 / 10 }?.let {
                    Text(if (it > CLOUD_LIMIT) "当前快照超过 GitHub 的 32 MiB 上限，请使用本地备份。"
                        else "当前快照接近 GitHub 的 32 MiB 上限。", color = MaterialTheme.colorScheme.error)
                }
            }
            if (owned != null) {
                Primary("核对远端备份", { model.backup { preview = service.preview() } }, Modifier.fillMaxWidth(), !busy)
                if (owned.paused == null && owned.generation == state.generation) {
                    Choice("立即备份", { model.backup { service.schedule(manual = true) } }, Modifier.fillMaxWidth(), enabled = !busy && !running)
                }
                Paper {
                    Toggle("仅使用 Wi-Fi", owned.wifiOnly) { selected ->
                        if (!busy) model.backup { service.setWifi(selected) }
                    }
                    Choice("重新配置连接", {
                        target = "${owned.target.owner}/${owned.target.repository}"
                        branch = owned.target.branch
                        wifi = owned.wifiOnly
                        editing = true
                    }, Modifier.fillMaxWidth(), enabled = !busy)
                }
                Section("仓库容量提醒")
                Paper {
                    val capacity = owned.capacity
                    Secondary(capacity.sizeKb?.let { "最近一次 · ${formatCapacity(it)} MB" } ?: "尚无有效容量读数")
                    capacity.checkedAt?.let { Secondary("检查时间 · $it") }
                    if (capacity.failed) Secondary("当前无法更新仓库容量")
                    Field("提醒阈值（MB）", threshold, { threshold = it }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Choice("保存阈值", { model.backup { service.setThreshold(threshold) } }, Modifier.fillMaxWidth(), enabled = !busy)
                }
            }
            if (editing) {
                Section("连接私有仓库")
                Paper {
                    Field("仓库（owner/repository）", target, { target = it })
                    Field("分支", branch, { branch = it })
                    Field("细粒度访问令牌", token, { token = it }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Password),
                        transformation = PasswordVisualTransformation())
                    Toggle("仅使用 Wi-Fi", wifi) { wifi = it }
                    Secondary("请将令牌限定为数据仓库的 Contents 读写权限。完整 JSON 会保存在 backups/latest.json。")
                    Primary("测试并保存连接", {
                        val supplied = token
                        model.backup({ token = ""; editing = false }) { service.connect(target, branch, supplied, wifi) }
                    }, Modifier.fillMaxWidth(), enabled = !busy && token.isNotBlank())
                    if (owned != null) Choice("取消", { token = ""; editing = false }, Modifier.fillMaxWidth(), enabled = !busy)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy || running) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }

    preview?.let { source ->
        val staged = source.stage
        if (restoring && staged != null) {
            RestoreDialog(staged, state.revision != 0L, hasFolder, busy, error,
                { restoring = false }, { restore() }, { protect.launch("kitsune-pre-restore.json") })
        } else {
            AlertDialog(onDismissRequest = { if (!busy) preview = null }, shape = DialogShape,
                title = { Text("核对远端备份") }, text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("本机 · 用餐 ${records.meals.size} 条 · 钱包 ${records.wallets.size} 个 · 账目 ${records.entries.size} 笔")
                        if (staged == null) Text("远端尚无备份文件。确认后创建本机完整备份。")
                        else {
                            Text("远端 · ${staged.info.exportedAt}")
                            Text("用餐 ${staged.counts.meals} 条 · 钱包 ${staged.counts.wallets} 个 · 账目 ${staged.counts.entries} 笔")
                            Secondary("覆盖授权只适用于当前远端版本。")
                            Choice("恢复云端", { restoring = true }, Modifier.fillMaxWidth(), enabled = !busy)
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }, confirmButton = {
                    val action = if (staged != null) "保留本机并覆盖" else if (owned?.accepted?.isNotEmpty() == true) "确认重建" else "确认创建"
                    Primary(action, { model.backup({ preview = null }) { service.confirm(source) } }, enabled = !busy)
                }, dismissButton = { TextButton({ preview = null }, enabled = !busy) { Text("取消") } })
        }
    }
}

@Composable
fun CapacityAlert(records: Records, model: AppViewModel) {
    val source = model.backupServices.github
    val config by source.state.collectAsState()
    val busy by model.busy.collectAsState()
    val context = LocalContext.current
    val state = config?.takeIf { it.installation == records.state.installationId &&
        (it.generation != records.state.generation || it.lastRevision <= records.state.revision) && it.capacity.pending }
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
