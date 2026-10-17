package com.cbrl.pixelblaze.presentation.segments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.cbrl.pixelblaze.helmet.ColorUse
import com.cbrl.pixelblaze.helmet.FadeSteps
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.helmet.SegmentState
import com.cbrl.pixelblaze.helmet.SpeedScale
import com.cbrl.pixelblaze.helmet.percentLabel
import com.cbrl.pixelblaze.presentation.Routes
import com.cbrl.pixelblaze.presentation.components.LevelScreen
import com.cbrl.pixelblaze.presentation.components.SegmentDot
import com.cbrl.pixelblaze.presentation.components.StepSlider
import com.cbrl.pixelblaze.presentation.components.icon
import com.cbrl.pixelblaze.presentation.components.listItem
import com.cbrl.pixelblaze.presentation.theme.Led
import kotlin.math.roundToInt

@Composable
fun SegmentsScreen(viewModel: HelmetViewModel, onOpen: (HelmetSegment) -> Unit) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready = connection == HelmetConnectionState.Ready && state != null
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(Modifier.listItem(this, spec), transformation = SurfaceTransformation(spec)) { Text("SEGMENTS") }
            }
            items(HelmetSegment.entries, key = { it.id }) { segment ->
                val segmentState = state?.get(segment)
                FilledTonalButton(
                    onClick = { onOpen(segment) },
                    modifier = Modifier.listItem(this, spec),
                    enabled = ready,
                    transformation = SurfaceTransformation(spec),
                    icon = { SegmentDot(segmentState) },
                    secondaryLabel = segmentState?.let {
                        { Text(if (it.on) it.effect.label else "Off", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    label = { Text(segment.label, maxLines = 1) },
                )
            }
        }
    }
}

@Composable
fun SegmentScreen(viewModel: HelmetViewModel, segment: HelmetSegment, onOpen: (String) -> Unit) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val segmentState = state?.get(segment)
    val ready = connection == HelmetConnectionState.Ready && segmentState != null
    val controller = viewModel.controller
    val haptics = LocalHapticFeedback.current
    var confirmCopy by remember { mutableStateOf(false) }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(Modifier.listItem(this, spec), transformation = SurfaceTransformation(spec)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SegmentDot(segmentState, size = 14.dp)
                        Text(segment.label.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (segmentState == null) {
                item { NotConnectedText() }
            } else {
                item {
                    SwitchButton(
                        checked = segmentState.on,
                        onCheckedChange = { controller.setSegmentEnabled(segment, it) },
                        modifier = Modifier.listItem(this, spec),
                        enabled = ready,
                        transformation = SurfaceTransformation(spec),
                        secondaryLabel = { Text(if (segmentState.on) "On" else "Off") },
                        label = { Text("Lights") },
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = { onOpen(Routes.effect(segment)) },
                        modifier = Modifier.listItem(this, spec),
                        enabled = ready,
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(segmentState.effect.category.icon, contentDescription = null) },
                        secondaryLabel = { Text(segmentState.effect.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        label = { Text("Effect") },
                    )
                }
                if (segmentState.effect.colorUse != ColorUse.None) {
                    item {
                        FilledTonalButton(
                            onClick = { onOpen(Routes.color(segment)) },
                            modifier = Modifier.listItem(this, spec),
                            enabled = ready,
                            transformation = SurfaceTransformation(spec),
                            icon = { SegmentDot(segmentState) },
                            secondaryLabel = { Text(colorSummary(segmentState)) },
                            label = {
                                Text(if (segmentState.effect.colorUse == ColorUse.Full) "Color" else "Saturation")
                            },
                        )
                    }
                }
                item {
                    FilledTonalButton(
                        onClick = { onOpen(Routes.brightness(segment)) },
                        modifier = Modifier.listItem(this, spec),
                        enabled = ready,
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(Icons.Rounded.BrightnessMedium, contentDescription = null) },
                        secondaryLabel = { Text(percentLabel(segmentState.brightness)) },
                        label = { Text("Brightness") },
                    )
                }
                if (segmentState.effect.usesSpeed) {
                    item {
                        StepSlider(
                            label = "Speed",
                            valueLabel = speedLabel(segmentState.speed),
                            index = SpeedScale.stepIndex(segmentState.speed),
                            count = SpeedScale.STEPS.size,
                            enabled = ready,
                            onIndexChange = { controller.setSegmentSpeed(segment, SpeedScale.STEPS[it]) },
                            modifier = Modifier.listItem(this, spec),
                        )
                    }
                }
                item {
                    StepSlider(
                        label = "Fade",
                        valueLabel = FadeSteps.label(segmentState.fadeSeconds),
                        index = FadeSteps.indexOf(segmentState.fadeSeconds),
                        count = FadeSteps.VALUES.size,
                        enabled = ready,
                        onIndexChange = { controller.setSegmentFadeTime(segment, FadeSteps.VALUES[it]) },
                        modifier = Modifier.listItem(this, spec),
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { confirmCopy = true },
                        modifier = Modifier.listItem(this, spec),
                        enabled = ready,
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        label = { Text("Copy to all") },
                    )
                }
            }
        }
    }

    AlertDialog(
        visible = confirmCopy,
        onDismissRequest = { confirmCopy = false },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    controller.copyAppearanceToAll(segment)
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    confirmCopy = false
                },
            )
        },
        title = { Text("Copy to all?") },
        text = { Text("Every segment gets the ${segment.shortLabel} look.", textAlign = TextAlign.Center) },
    )
}

@Composable
fun SegmentBrightnessScreen(viewModel: HelmetViewModel, segment: HelmetSegment) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val segmentState = state?.get(segment)
    LevelScreen(
        title = "${segment.shortLabel} brightness",
        level = segmentState?.brightness ?: 0f,
        enabled = connection == HelmetConnectionState.Ready && segmentState != null,
        onLevelChange = { viewModel.controller.setSegmentBrightness(segment, it) },
        currentLevel = { viewModel.controller.state.value?.get(segment)?.brightness ?: 0f },
        ledColor = segmentState
            ?.takeIf { it.effect.colorUse == ColorUse.Full }
            ?.let { Color.hsv(it.hue * 360f, it.saturation, 1f) }
            ?: Led.White,
    )
}

@Composable
internal fun NotConnectedText(modifier: Modifier = Modifier) {
    Text(
        "Connect to the helmet to change this.",
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** "1×" for a step value, "1.23×" for a value that the phone set between steps. */
private fun speedLabel(speed: Float): String =
    if (SpeedScale.STEPS.any { it == speed }) SpeedScale.shortLabel(speed) else SpeedScale.label(speed)

private fun colorSummary(state: SegmentState): String = when (state.effect.colorUse) {
    ColorUse.Full -> "${(state.hue * 360f).roundToInt()}° · ${percentLabel(state.saturation)}"
    else -> percentLabel(state.saturation)
}
