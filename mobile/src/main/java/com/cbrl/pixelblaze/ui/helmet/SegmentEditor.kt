package com.cbrl.pixelblaze.ui.helmet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cbrl.pixelblaze.helmet.ColorUse
import com.cbrl.pixelblaze.helmet.FadeSteps
import com.cbrl.pixelblaze.helmet.HelmetController
import com.cbrl.pixelblaze.helmet.HelmetEffect
import com.cbrl.pixelblaze.helmet.HelmetLayout
import com.cbrl.pixelblaze.helmet.HelmetMessage
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.SegmentState
import com.cbrl.pixelblaze.helmet.SpeedScale
import com.cbrl.pixelblaze.helmet.percentLabel
import com.cbrl.pixelblaze.ui.components.GradientSlider
import com.cbrl.pixelblaze.ui.components.LabeledSlider
import com.cbrl.pixelblaze.ui.components.SectionCard
import com.cbrl.pixelblaze.ui.components.SliderHeader
import com.cbrl.pixelblaze.ui.components.rainbowColors
import kotlin.math.abs
import kotlin.math.roundToInt

private data class Swatch(val name: String, val hue: Float, val saturation: Float)

/** Colors that look clean on the LEDs. */
private val Swatches = listOf(
    Swatch("Red", 0f, 1f),
    Swatch("Orange", 0.05f, 1f),
    Swatch("Yellow", 0.14f, 1f),
    Swatch("Green", 0.33f, 1f),
    Swatch("Cyan", 0.5f, 1f),
    Swatch("Blue", 0.66f, 1f),
    Swatch("Purple", 0.76f, 1f),
    Swatch("Magenta", 0.88f, 1f),
    Swatch("White", 0f, 0f),
)

private val SaturationPresets = listOf(1f, 0.95f, 0.9f)

/** The highest hue that the slider sends. A hue of 1 is the same as 0 and would move the thumb to the start. */
private const val MAX_SLIDER_HUE = 0.999f

/**
 * All controls of one segment. Each control writes to the helmet while it moves.
 * The color controls show only what the effect uses.
 */
@Composable
fun SegmentEditor(
    segment: HelmetSegment,
    state: SegmentState,
    layout: HelmetLayout,
    message: String,
    enabled: Boolean,
    controller: HelmetController,
    onCopyToAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingEffect by rememberSaveable { mutableStateOf(false) }

    SectionCard(modifier) {
        EditorHeader(segment, state, layout, enabled) { controller.setSegmentEnabled(segment, it) }
        EffectField(state.effect, enabled) { pickingEffect = true }

        when (state.effect.colorUse) {
            ColorUse.Full -> ColorControls(state, enabled, showHue = true) { transform ->
                controller.updateSegment(segment, transform)
            }
            ColorUse.SaturationOnly -> ColorControls(state, enabled, showHue = false) { transform ->
                controller.updateSegment(segment, transform)
            }
            ColorUse.None -> OwnColorsNote()
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LabeledSlider(
            label = "Brightness",
            valueLabel = percentLabel(state.brightness),
            value = state.brightness,
            onValueChange = { controller.setSegmentBrightness(segment, it) },
            enabled = enabled,
        )
        if (state.effect.usesSpeed) {
            SpeedControl(state.speed, enabled) { controller.setSegmentSpeed(segment, it) }
        }
        FadeControl(state.fadeSeconds, enabled) { controller.setSegmentFadeTime(segment, it) }

        if (segment == HelmetSegment.Visor) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MessageField(
                message = message,
                showsMessage = state.effect == HelmetEffect.Text,
                enabled = enabled,
                onSend = controller::setMessage,
                onShowMessage = { controller.setSegmentEffect(segment, HelmetEffect.Text) },
            )
        }

        OutlinedButton(onClick = onCopyToAll, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
            Text("Copy to all segments", Modifier.padding(start = 8.dp))
        }
    }

    if (pickingEffect) {
        EffectPickerSheet(
            effects = HelmetEffect.availableFor(segment, layout),
            selected = state.effect,
            onSelect = { controller.setSegmentEffect(segment, it) },
            onDismiss = { pickingEffect = false },
        )
    }
}

@Composable
private fun EditorHeader(
    segment: HelmetSegment,
    state: SegmentState,
    layout: HelmetLayout,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorOrb(state)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(segment.label, style = MaterialTheme.typography.titleLarge)
            Text(
                "${layout[segment].size} LEDs · ${if (state.on) "On" else "Off"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.on,
            onCheckedChange = onEnabledChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "${segment.label} on" },
        )
    }
}

/** A glowing circle in the segment color. Effects that make their own hues show a rainbow. */
@Composable
private fun ColorOrb(state: SegmentState) {
    val glow = if (state.effect.colorUse == ColorUse.Full) segmentColor(state) else MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(44.dp)
            .shadow(if (state.on) 14.dp else 0.dp, CircleShape, ambientColor = glow, spotColor = glow)
            .background(segmentBrush(state), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

@Composable
private fun EffectField(effect: HelmetEffect, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(effect.category.icon, null, tint = MaterialTheme.colorScheme.tertiary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    "Effect · ${effect.category.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(effect.label, style = MaterialTheme.typography.titleMedium)
            }
            Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ColorControls(
    state: SegmentState,
    enabled: Boolean,
    showHue: Boolean,
    update: ((SegmentState) -> SegmentState) -> Unit,
) {
    val color = Color.hsv(state.hue * 360f, state.saturation, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showHue) {
            SliderHeader("Hue", "${(state.hue * 360f).roundToInt()}°")
            GradientSlider(
                value = state.hue,
                onValueChange = { hue -> update { it.copy(hue = hue.coerceAtMost(MAX_SLIDER_HUE)) } },
                layers = listOf(Brush.horizontalGradient(rainbowColors())),
                thumbColor = Color.hsv(state.hue * 360f, 1f, 1f),
                enabled = enabled,
            )
        } else {
            Text(
                "This effect makes its own hues. Saturation still applies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SliderHeader("Saturation", percentLabel(state.saturation))
        GradientSlider(
            value = state.saturation,
            onValueChange = { saturation -> update { it.copy(saturation = saturation) } },
            layers = if (showHue) {
                listOf(Brush.horizontalGradient(listOf(Color.White, Color.hsv(state.hue * 360f, 1f, 1f))))
            } else {
                listOf(
                    Brush.horizontalGradient(rainbowColors()),
                    Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))),
                )
            },
            thumbColor = if (showHue) color else Color.hsv(0f, state.saturation, 1f),
            enabled = enabled,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaturationPresets.forEach { value ->
                FilterChip(
                    selected = abs(state.saturation - value) < 0.005f,
                    onClick = { update { it.copy(saturation = value) } },
                    enabled = enabled,
                    label = { Text(percentLabel(value)) },
                )
            }
        }

        if (showHue) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Swatches.forEach { swatch ->
                    val selected = abs(state.saturation - swatch.saturation) < 0.01f &&
                        (swatch.saturation == 0f || abs(state.hue - swatch.hue) < 0.005f)
                    SwatchButton(
                        swatch = swatch,
                        selected = selected,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        update {
                            if (swatch.saturation == 0f) it.copy(saturation = 0f) else it.copy(hue = swatch.hue, saturation = swatch.saturation)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwatchButton(
    swatch: Swatch,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ring = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = swatch.name
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .sizeIn(maxWidth = 36.dp, maxHeight = 36.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .alpha(if (enabled) 1f else 0.38f)
                .border(if (selected) 2.dp else 1.dp, ring, CircleShape)
                .padding(if (selected) 4.dp else 0.dp)
                .background(Color.hsv(swatch.hue * 360f, swatch.saturation, 1f), CircleShape),
        )
    }
}

@Composable
private fun OwnColorsNote() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "This effect makes its own colors.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun SpeedControl(speed: Float, enabled: Boolean, onSpeedChange: (Float) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val change = { value: Float ->
        if (value != speed && SpeedScale.isSnapPoint(value)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onSpeedChange(value)
    }
    Column {
        LabeledSlider(
            label = "Speed",
            valueLabel = SpeedScale.label(speed),
            value = SpeedScale.toPosition(speed),
            onValueChange = { change(SpeedScale.fromPosition(it)) },
            enabled = enabled,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpeedScale.SNAP_POINTS.forEach { point ->
                FilterChip(
                    selected = speed == point,
                    onClick = { change(point) },
                    enabled = enabled,
                    label = { Text(SpeedScale.shortLabel(point)) },
                )
            }
        }
    }
}

@Composable
private fun FadeControl(seconds: Float, enabled: Boolean, onFadeChange: (Float) -> Unit) {
    val steps = FadeSteps.VALUES
    LabeledSlider(
        label = "Fade time",
        valueLabel = FadeSteps.label(seconds),
        value = FadeSteps.indexOf(seconds).toFloat(),
        onValueChange = { onFadeChange(steps[it.roundToInt().coerceIn(steps.indices)]) },
        enabled = enabled,
        valueRange = 0f..(steps.size - 1).toFloat(),
        steps = steps.size - 2,
    )
}

@Composable
private fun MessageField(
    message: String,
    showsMessage: Boolean,
    enabled: Boolean,
    onSend: (String) -> Unit,
    onShowMessage: () -> Unit,
) {
    var text by rememberSaveable(message) { mutableStateOf(message) }
    val focusManager = LocalFocusManager.current
    val send = {
        onSend(text)
        focusManager.clearFocus()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = HelmetMessage.sanitize(it) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Scrolling message") },
            singleLine = true,
            supportingText = {
                Text(
                    "${text.length}/${HelmetMessage.MAX_LENGTH}" +
                        if (showsMessage) "" else " · Shows with the Scrolling Text effect",
                )
            },
            trailingIcon = {
                IconButton(onClick = send, enabled = enabled && text != message) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send message")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        if (!showsMessage) {
            TextButton(onClick = onShowMessage, enabled = enabled) { Text("Show the message on the visor") }
        }
    }
}
