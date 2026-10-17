package com.cbrl.pixelblaze.ui.helmet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cbrl.pixelblaze.device.PixelblazeEndpoint
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetLayout
import com.cbrl.pixelblaze.helmet.HelmetPreset
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetState
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.helmet.percentLabel
import com.cbrl.pixelblaze.ui.components.LedSlider
import com.cbrl.pixelblaze.ui.components.LedText
import com.cbrl.pixelblaze.ui.components.PyramidLoader
import com.cbrl.pixelblaze.ui.components.SectionCard
import com.cbrl.pixelblaze.ui.components.SectionLabel
import com.cbrl.pixelblaze.ui.components.StatusDot
import com.cbrl.pixelblaze.ui.components.VisorPanel
import com.cbrl.pixelblaze.ui.components.hairline
import com.cbrl.pixelblaze.ui.components.lightChipBorder
import com.cbrl.pixelblaze.ui.components.lightChipColors
import com.cbrl.pixelblaze.ui.components.panel
import com.cbrl.pixelblaze.ui.components.showMessage
import com.cbrl.pixelblaze.ui.theme.HelmetText
import com.cbrl.pixelblaze.ui.theme.Led
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VisorPanel(Modifier.fillMaxWidth(), grid = true) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveViewToggle(previewOn) { previewOn = !previewOn }
                            Spacer(Modifier.weight(1f))
                            val fps = status?.fps
                            if (ready && fps != null) {
                                LedText(
                                    "${fps.roundToInt()} FPS",
                                    style = HelmetText.ledSmall.copy(fontSize = 15.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    glow = false,
                                )
                            }
                        }
                        HelmetDiagram(
                            layout = layout,
                            look = state?.look,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            master = state?.master ?: 1f,
                            blackout = state?.blackout ?: false,
                            frame = { frame.value },
                            selected = segment,
                            onSegmentClick = viewModel::selectSegment,
                            showLabels = true,
                        )
                    }
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
                    layout = layout,
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
    val shape = RoundedCornerShape(20.dp)
    val panel = if (problem) Modifier.clip(shape).background(colors.errorContainer).hairline(shape) else Modifier.panel(shape)
    Column(Modifier.fillMaxWidth().then(panel)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                PyramidLoader(Led.Amber, Modifier.padding(4.dp))
            } else {
                StatusDot(if (problem) Led.Red else colors.outline, pulsing = false)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem) colors.onErrorContainer else colors.onSurfaceVariant,
                )
            }
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
        }
    }
}

/** Turns the live pixels of the helmet on or off. */
@Composable
private fun LiveViewToggle(on: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        checked = on,
        onCheckedChange = { onToggle() },
        shape = CircleShape,
        color = Color.White.copy(alpha = if (on) 0.08f else 0.03f),
        contentColor = if (on) colors.onSurface else colors.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = "Live view" },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // A red light while the live view runs, as on a camera.
            if (on) {
                StatusDot(Led.Red, pulsing = false, size = 7.dp)
            } else {
                Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(if (on) "LIVE VIEW" else "LIVE VIEW OFF", style = HelmetText.section.copy(fontSize = 9.sp))
        }
    }
}

@Composable
private fun SegmentChips(state: HelmetState?, selected: HelmetSegment, onSelect: (HelmetSegment) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem(max(0, selected.id - 1)) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(HelmetSegment.entries, key = { it.id }) { segment ->
            val isSelected = segment == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(segment) },
                label = { Text(segment.shortLabel) },
                leadingIcon = { SegmentDot(state?.get(segment), size = 16.dp) },
                shape = CircleShape,
                colors = lightChipColors(),
                border = lightChipBorder(isSelected),
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
    SectionCard(title = "Master") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlackoutToggle(blackout, enabled, onBlackout)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Brightness", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    LedText(
                        percentLabel(master),
                        style = HelmetText.ledLarge,
                        color = if (blackout) MaterialTheme.colorScheme.outline else Led.White,
                        glow = !blackout && enabled,
                    )
                }
                val meterColor = if (blackout) MaterialTheme.colorScheme.outline else Led.White
                LedSlider(value = master, onValueChange = onMaster, enabled = enabled, ledColor = { meterColor })
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

/**
 * A large round toggle: a red ring and power symbol while the helmet is lit, and a white disc with
 * "Lights on" while it is dark.
 */
@Composable
private fun BlackoutToggle(blackout: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val content = if (blackout) MaterialTheme.colorScheme.onPrimary else Led.Red
    Surface(
        checked = blackout,
        onCheckedChange = {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onChange(it)
        },
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
            .size(100.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .semantics {
                contentDescription = "Blackout"
                stateDescription = if (blackout) "Helmet dark" else "Helmet lit"
            },
    ) {
        Box(
            Modifier
                .background(if (blackout) MaterialTheme.colorScheme.primary else Color.Black, CircleShape)
                .then(if (blackout) Modifier else Modifier.border(2.dp, Led.Red, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (blackout) Icons.Filled.LightMode else Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    if (blackout) "LIGHTS ON" else "BLACKOUT",
                    style = HelmetText.section.copy(fontSize = 8.sp, letterSpacing = 1.sp),
                    color = content,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickPresets(
    presets: List<HelmetPreset>,
    layout: HelmetLayout,
    enabled: Boolean,
    onLoad: (HelmetPreset) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Presets", Modifier.weight(1f).padding(start = 4.dp, end = 8.dp), line = true)
            TextButton(onClick = onSeeAll) { Text("All presets") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(presets, key = { it.id }) { preset ->
                Column(
                    Modifier
                        .width(150.dp)
                        .alpha(if (enabled) 1f else 0.5f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = enabled, onClickLabel = "Show ${preset.name}") { onLoad(preset) },
                ) {
                    VisorPanel(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                    ) {
                        HelmetDiagram(layout = layout, look = preset.look, modifier = Modifier.fillMaxWidth(), glow = false, dotRadius = 0.42f)
                    }
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp, top = 6.dp, end = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorPlaceholder() {
    SectionCard(title = "Segments") {
        Text(
            "Connect to the helmet to edit its segments. The app reads the current look from the helmet first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
