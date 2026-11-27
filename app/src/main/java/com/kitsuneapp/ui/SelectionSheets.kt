package com.kitsuneapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsuneapp.R
import com.kitsuneapp.data.Wallet
import kotlin.math.abs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val SheetDismissDistance = 100.dp
internal val SheetDismissFlingVelocity = 800.dp
internal val SheetDismissMinFlingDistance = 24.dp
internal val SheetMinEffectiveDismissDistance = 56.dp
internal const val SheetDismissMaxFlingPauseMs = 100L

internal fun effectiveSheetDismissDistance(
    defaultDistancePx: Float,
    sheetHeightPx: Int,
    minDistancePx: Float,
): Float = if (sheetHeightPx > 0) {
    minOf(defaultDistancePx, maxOf(minDistancePx, sheetHeightPx * 0.5f))
} else {
    defaultDistancePx
}

internal fun shouldDismissSheet(
    offsetPx: Float,
    velocityPxPerSec: Float,
    distanceThresholdPx: Float,
    flingVelocityThresholdPx: Float,
    minFlingDistancePx: Float,
    pauseBeforeReleaseMs: Long = 0L,
    maxFlingPauseMs: Long = SheetDismissMaxFlingPauseMs,
): Boolean {
    val effectiveVelocity = if (pauseBeforeReleaseMs >= maxFlingPauseMs) 0f else velocityPxPerSec
    return when {
        effectiveVelocity <= -flingVelocityThresholdPx -> false
        offsetPx >= distanceThresholdPx -> true
        offsetPx >= minFlingDistancePx && effectiveVelocity >= flingVelocityThresholdPx -> true
        else -> false
    }
}

private fun Modifier.sheetHeaderDismissGesture(
    dragEnabled: () -> Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (
        velocityY: Float,
        pauseBeforeReleaseMs: Long,
        defaultDistancePx: Float,
        flingVelocityPx: Float,
        minFlingDistancePx: Float,
        minEffectiveDistancePx: Float,
    ) -> Unit,
    onDragCancel: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!dragEnabled()) return@awaitEachGesture
        var overSlop = 0f
        val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            overSlop = over
        } ?: return@awaitEachGesture

        onDragStart()
        val velocityTracker = VelocityTracker()
        // Accumulate screen-space deltas so translating the parent Surface never cancels measured velocity.
        var trackedY = 0f
        velocityTracker.addPosition(drag.previousUptimeMillis, Offset.Zero)
        trackedY += drag.positionChange().y
        velocityTracker.addPosition(drag.uptimeMillis, Offset(0f, trackedY))
        var lastMoveUptimeMillis = drag.uptimeMillis
        if (overSlop != 0f) onDrag(overSlop)

        var activePointerId = drag.id
        var completed = false
        var releaseUptimeMillis = drag.uptimeMillis
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == activePointerId }
            if (change == null) {
                val replacement = event.changes.firstOrNull { it.pressed }
                if (replacement == null) {
                    completed = true
                    break
                }
                activePointerId = replacement.id
                velocityTracker.resetTracking()
                velocityTracker.addPosition(replacement.uptimeMillis, Offset(0f, trackedY))
                lastMoveUptimeMillis = replacement.uptimeMillis
                continue
            }
            if (change.isConsumed) {
                completed = false
                break
            }
            if (!change.pressed) {
                val otherPressed = event.changes.firstOrNull { it.pressed }
                if (otherPressed != null) {
                    activePointerId = otherPressed.id
                    velocityTracker.resetTracking()
                    velocityTracker.addPosition(otherPressed.uptimeMillis, Offset(0f, trackedY))
                    lastMoveUptimeMillis = otherPressed.uptimeMillis
                    continue
                }
                releaseUptimeMillis = change.uptimeMillis
                completed = true
                break
            }
            val deltaY = change.positionChange().y
            trackedY += deltaY
            velocityTracker.addPosition(change.uptimeMillis, Offset(0f, trackedY))
            if (deltaY != 0f) {
                if (abs(deltaY) >= 1f) {
                    lastMoveUptimeMillis = change.uptimeMillis
                }
                onDrag(deltaY)
                change.consume()
            }
        }

        if (completed) {
            val pauseMs = (releaseUptimeMillis - lastMoveUptimeMillis).coerceAtLeast(0L)
            onDragEnd(
                velocityTracker.calculateVelocity().y,
                pauseMs,
                SheetDismissDistance.toPx(),
                SheetDismissFlingVelocity.toPx(),
                SheetDismissMinFlingDistance.toPx(),
                SheetMinEffectiveDismissDistance.toPx(),
            )
        } else {
            onDragCancel()
        }
    }
}

@Composable
internal fun SelectionSheet(
    title: String,
    dismiss: () -> Unit,
    subtitle: String? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    singleChoice: Boolean = false,
    listStateKey: String = "",
    content: LazyListScope.() -> Unit,
) {
    val listState = key(listStateKey) { rememberLazyListState() }
    FloatingSheet(title, dismiss, bottomPadding = 12.dp) {
        header?.invoke()
        LazyColumn(
            Modifier.weight(1f, fill = false).heightIn(max = 420.dp)
                .then(if (singleChoice) Modifier.selectableGroup() else Modifier),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (subtitle != null) item { Secondary(subtitle) }
            content()
        }
        if (footer != null) {
            Column(Modifier.padding(top = 8.dp), content = footer)
        }
    }
}

private class SheetDragHolder {
    var sheetHeightPx: Int = 0
    var offsetPx: Float = 0f
    var isDismissing: Boolean = false
    var settleJob: Job? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FloatingSheet(
    title: String,
    dismiss: () -> Unit,
    bottomPadding: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val latestDismiss by rememberUpdatedState(dismiss)
    val dragOffset = remember { Animatable(0f) }
    val holder = remember { SheetDragHolder() }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        containerColor = Color.Transparent,
        scrimColor = colors.scrim.copy(alpha = .33f),
        dragHandle = null,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
    ) {
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(9.dp)
                .onSizeChanged { holder.sheetHeightPx = it.height }
                .graphicsLayer { translationY = dragOffset.value },
            shape = DialogShape,
            color = colors.surface,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .sheetHeaderDismissGesture(
                            dragEnabled = { !holder.isDismissing },
                            onDragStart = {
                                holder.settleJob?.cancel()
                                holder.settleJob = null
                                holder.offsetPx = dragOffset.value
                            },
                            onDrag = { deltaY ->
                                val next = (holder.offsetPx + deltaY).coerceAtLeast(0f)
                                holder.offsetPx = next
                                holder.settleJob?.cancel()
                                holder.settleJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    dragOffset.snapTo(next)
                                }
                            },
                            onDragEnd = { velocityY, pauseMs, defaultDistancePx, flingVelocityPx, minFlingDistancePx, minEffectiveDistancePx ->
                                val thresholdPx = effectiveSheetDismissDistance(
                                    defaultDistancePx = defaultDistancePx,
                                    sheetHeightPx = holder.sheetHeightPx,
                                    minDistancePx = minEffectiveDistancePx,
                                )
                                if (shouldDismissSheet(
                                        offsetPx = holder.offsetPx,
                                        velocityPxPerSec = velocityY,
                                        distanceThresholdPx = thresholdPx,
                                        flingVelocityThresholdPx = flingVelocityPx,
                                        minFlingDistancePx = minFlingDistancePx,
                                        pauseBeforeReleaseMs = pauseMs,
                                    )
                                ) {
                                    holder.isDismissing = true
                                    holder.settleJob?.cancel()
                                    holder.settleJob = scope.launch {
                                        try {
                                            sheetState.hide()
                                        } finally {
                                            latestDismiss()
                                        }
                                    }
                                } else {
                                    holder.settleJob?.cancel()
                                    holder.settleJob = scope.launch {
                                        dragOffset.animateTo(0f, tween(durationMillis = 200, easing = motion))
                                    }
                                }
                            },
                            onDragCancel = {
                                if (!holder.isDismissing) {
                                    holder.settleJob?.cancel()
                                    holder.settleJob = scope.launch {
                                        dragOffset.animateTo(0f, tween(durationMillis = 200, easing = motion))
                                    }
                                }
                            },
                        )
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 4.dp)
                            .size(32.dp, 4.dp)
                            .clip(ControlShape)
                            .background(colors.outlineVariant),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                        )
                        IconButton(onClick = dismiss, modifier = Modifier.size(48.dp)) {
                            Icon(designIcon(R.drawable.ks_x), contentDescription = "关闭", modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun SelectionRow(
    label: String, selected: Boolean, onSelect: () -> Unit,
    secondary: String? = null, icon: ImageVector? = null,
) = SelectionOption(label, selected, Modifier.selectable(selected, role = Role.RadioButton, onClick = onSelect),
    secondary, icon) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (selected) Icon(designIcon(R.drawable.ks_check), contentDescription = null)
    }
}

@Composable
internal fun MultiSelectionRow(
    label: String, checked: Boolean, change: (Boolean) -> Unit,
    secondary: String? = null, icon: ImageVector? = null,
) = SelectionOption(label, false, Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = change),
    secondary, icon) {
    Checkbox(checked, onCheckedChange = null)
}

@Composable
private fun SelectionOption(
    label: String, highlighted: Boolean, modifier: Modifier,
    secondary: String?, icon: ImageVector?, indicator: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val foreground = if (highlighted) colors.onPrimaryContainer else colors.onSurface
    val supporting = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (secondary == null) 48.dp else 64.dp)
            .clip(ControlShape).background(if (highlighted) colors.primaryContainer else Color.Transparent)
            .then(modifier).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = supporting)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = foreground)
            secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = supporting) }
        }
        CompositionLocalProvider(LocalContentColor provides foreground, content = indicator)
    }
}

@Composable
internal fun SelectionGroupTitle(label: String, modifier: Modifier = Modifier) {
    Text(label, modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge)
}

@Composable
internal fun <T> SingleSelectSheet(
    title: String, value: T?, options: List<T>, optionKey: (T) -> String,
    label: (T) -> String, onSelect: (T) -> Unit, dismiss: () -> Unit,
    subtitle: String? = null, secondary: ((T) -> String?)? = null,
    optionIcon: (@Composable (T) -> ImageVector)? = null, empty: String = "没有可选项目",
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    listStateKey: String = "",
) {
    SelectionSheet(title, dismiss, subtitle, header, footer, singleChoice = true, listStateKey = listStateKey) {
        if (options.isEmpty()) item { EmptyState(empty) }
        items(options, key = optionKey) { option ->
            SelectionRow(label(option), option == value, {
                dismiss()
                onSelect(option)
            }, secondary?.invoke(option), optionIcon?.invoke(option))
        }
    }
}

@Composable
internal fun SelectionControl(
    title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: ImageVector? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(title)
        OutlinedAction(onClick, Modifier.fillMaxWidth().semantics { contentDescription = title },
            enabled = enabled, spread = true) {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp))
            Text(value, Modifier.weight(1f), color =
                if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(designIcon(R.drawable.ks_chevron_down), null, Modifier.size(14.dp))
        }
    }
}

@Composable
internal fun SheetSelect(
    title: String, value: String?, options: List<String>, label: (String) -> String,
    onSelect: (String) -> Unit, enabled: Boolean = true, secondary: ((String) -> String?)? = null,
    currentLabel: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SelectionControl(title, currentLabel ?: value?.let(label) ?: "请选择", { open = true }, enabled = enabled)
    if (open && enabled) {
        SingleSelectSheet("选择$title", value, options, { it }, label, onSelect, { open = false },
            secondary = secondary)
    }
}

internal fun walletSelectionDetails(wallet: Wallet): String = buildString {
    append(wallet.currencyCode)
    if (wallet.separateAccounting) append(" · 独立核算")
    if (wallet.archived) append(" · 已归档")
}
