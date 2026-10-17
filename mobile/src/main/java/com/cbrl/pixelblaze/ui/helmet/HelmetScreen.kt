package com.cbrl.pixelblaze.ui.helmet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cbrl.pixelblaze.device.PixelblazeEndpoint
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetPreset
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetState
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.helmet.percentLabel
import com.cbrl.pixelblaze.ui.components.LabeledSlider
import com.cbrl.pixelblaze.ui.components.SectionCard
import com.cbrl.pixelblaze.ui.components.SectionLabel
import com.cbrl.pixelblaze.ui.components.StatusDot
import com.cbrl.pixelblaze.ui.components.showMessage
import com.cbrl.pixelblaze.ui.theme.DiagramPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max
import kotlin.math.roundToInt

/** A preview frame older than this is dropped, and the diagram shows the segment colors again. */
private const val PREVIEW_STALE_MILLIS = 2_000L

private const val QUICK_PRESETS = 8

@Composable
fun HelmetScreen(
    viewModel: HelmetViewModel,
    snackbar: SnackbarHostState,
    onOpenDevices: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val device by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val segment by viewModel.activeSegment.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val ready = connection == HelmetConnectionState.Ready
    val controller = viewModel.controller
    val scope = rememberCoroutineScope()

    var previewOn by rememberSaveable { mutableStateOf(true) }
    val frame = remember { mutableStateOf<ByteArray?>(null) }
    if (previewOn) {
        // The Pixelblaze sends preview frames only while this screen is visible.
        LifecycleStartEffect(viewModel) {
            viewModel.acquirePreview()
            onStopOrDispose { viewModel.releasePreview() }
        }
    }
    LaunchedEffect(previewOn) {
        frame.value = null
        if (previewOn) {
            viewModel.previewFrames.collectLatest {
                frame.value = it
                delay(PREVIEW_STALE_MILLIS)
                frame.value = null
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "status") {
            AnimatedVisibility(visible = !ready) {
                StatusBanner(connection, device, onRetry = viewModel::retry, onChooseDevice = onOpenDevices)
            }
        }
        item(key = "diagram") {
            SectionCard(
                title = "Helmet",
                actions = {
                    val fps = status?.fps
                    if (ready && fps != null) {
                        Text(
                            "${fps.roundToInt()} FPS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilterChip(
                        selected = previewOn,
                        onClick = { previewOn = !previewOn },
                        label = { Text("Live view") },
                        leadingIcon = {
                            Icon(
                                if (previewOn) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DiagramPanel)
                        .padding(horizontal = 8.dp, vertical = 14.dp),
                ) {
                    HelmetDiagram(
                        layout = layout,
                        look = state?.look,
                        modifier = Modifier.fillMaxWidth(),
                        master = state?.master ?: 1f,
                        blackout = state?.blackout ?: false,
                        frame = { frame.value },
                        selected = segment,
                        onSegmentClick = viewModel::selectSegment,
                        showLabels = true,
                    )
                }
                SegmentChips(state, segment, viewModel::selectSegment)
            }
        }
        item(key = "master") {
            MasterCard(state, ready, onBlackout = controller::setBlackout, onMaster = controller::setMaster)
        }
        if (presets.isNotEmpty()) {
            item(key = "presets") {
                QuickPresets(
                    presets = presets.take(QUICK_PRESETS),
                    enabled = ready,
                    onLoad = { preset ->
                        val before = state?.look
                        viewModel.loadPreset(preset.id)
                        scope.showMessage(snackbar, "Showing ${preset.name}", before?.let { { controller.applyLook(it) } })
                    },
                    onSeeAll = onOpenPresets,
                )
            }
        }
        item(key = "editor") {
            val current = state
            if (current == null) {
                EditorPlaceholder()
            } else {
                SegmentEditor(
                    segment = segment,
                    state = current[segment],
                    layout = layout,
                    message = current.message,
                    enabled = ready,
                    controller = controller,
                    onCopyToAll = {
                        val before = current.look
                        controller.copyAppearanceToAll(segment)
                        scope.showMessage(snackbar, "Copied ${segment.label} to all segments") {
                            controller.applyLook(before)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    connection: HelmetConnectionState,
    device: PixelblazeEndpoint?,
    onRetry: () -> Unit,
    onChooseDevice: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var problem = false
    var busy = false
    var action: Pair<String, () -> Unit>? = null
    val title: String
    val detail: String
    when {
        device == null -> {
            title = "Choose your helmet"
            detail = "Find the Pixelblaze on this network, or enter its address."
            action = "Choose" to onChooseDevice
        }
        connection == HelmetConnectionState.Connecting -> {
            title = "Connecting to ${device.address}"
            detail = "Loading the Helmet pattern and its state."
            busy = true
        }
        connection is HelmetConnectionState.Reconnecting -> {
            title = "Reconnecting · attempt ${connection.attempt}"
            detail = connection.reason ?: "The app keeps trying."
            busy = true
            action = "Retry now" to onRetry
        }
        connection is HelmetConnectionState.MissingPattern -> {
            title = "Helmet pattern not found"
            detail = "${connection.message} Save contrib/helmet_pattern.txt on the Pixelblaze as \"Helmet\"."
            problem = true
            action = "Retry" to onRetry
        }
        connection is HelmetConnectionState.Error -> {
            title = "Cannot control the helmet"
            detail = connection.message
            problem = true
            action = "Retry" to onRetry
        }
        else -> {
            title = "Starting"
            detail = "The app connects while it is open."
            busy = true
        }
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (problem) colors.errorContainer else colors.surfaceContainerHigh),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(if (problem) colors.error else colors.tertiary, pulsing = busy)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem) colors.onErrorContainer else colors.onSurfaceVariant,
                )
            }
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SegmentChips(state: HelmetState?, selected: HelmetSegment, onSelect: (HelmetSegment) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem(max(0, selected.id - 1)) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(HelmetSegment.entries, key = { it.id }) { segment ->
            FilterChip(
                selected = segment == selected,
                onClick = { onSelect(segment) },
                label = { Text(segment.shortLabel) },
                leadingIcon = { SegmentDot(state?.get(segment), size = 10.dp) },
            )
        }
    }
}

@Composable
private fun MasterCard(
    state: HelmetState?,
    enabled: Boolean,
    onBlackout: (Boolean) -> Unit,
    onMaster: (Float) -> Unit,
) {
    val blackout = state?.blackout ?: false
    val master = state?.master ?: 1f
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlackoutToggle(blackout, enabled, onBlackout)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                LabeledSlider(
                    label = "Master brightness",
                    valueLabel = percentLabel(master),
                    value = master,
                    onValueChange = onMaster,
                    enabled = enabled,
                )
                Text(
                    if (blackout) "Blackout: all segments are dark. Their settings stay the same."
                    else "Scales every segment. Segment settings stay the same.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A large toggle. It fades the helmet to dark, or back to the current look. */
@Composable
private fun BlackoutToggle(blackout: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (blackout) colors.errorContainer else colors.surfaceContainerHigh, label = "blackout")
    val content = if (blackout) colors.onErrorContainer else colors.error
    Surface(
        checked = blackout,
        onCheckedChange = {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onChange(it)
        },
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = container,
        border = BorderStroke(1.dp, if (blackout) colors.error else colors.outline),
        modifier = Modifier
            .size(width = 92.dp, height = 96.dp)
            .semantics {
                contentDescription = "Blackout"
                stateDescription = if (blackout) "Helmet dark" else "Helmet lit"
            },
    ) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (blackout) Icons.Filled.LightMode else Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = if (enabled) content else colors.outline,
                modifier = Modifier.size(32.dp),
            )
            Text(
                if (blackout) "Lights on" else "Blackout",
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) content else colors.outline,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun QuickPresets(
    presets: List<HelmetPreset>,
    enabled: Boolean,
    onLoad: (HelmetPreset) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Presets", Modifier.weight(1f).padding(start = 4.dp))
            TextButton(onClick = onSeeAll) { Text("All presets") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets, key = { it.id }) { preset ->
                AssistChip(
                    onClick = { onLoad(preset) },
                    enabled = enabled,
                    label = { Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { LookDots(preset.look) },
                )
            }
        }
    }
}

@Composable
private fun EditorPlaceholder() {
    SectionCard {
        Text("Segments", style = MaterialTheme.typography.titleLarge)
        Text(
            "Connect to the helmet to edit its segments. The app reads the current look from the helmet first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
