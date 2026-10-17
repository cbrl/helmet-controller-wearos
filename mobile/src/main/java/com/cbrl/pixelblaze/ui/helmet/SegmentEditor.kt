package com.cbrl.pixelblaze.ui.helmet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.cbrl.pixelblaze.ui.components.LabeledSlider
import com.cbrl.pixelblaze.ui.components.LedFill
import com.cbrl.pixelblaze.ui.components.LedSlider
import com.cbrl.pixelblaze.ui.components.LedText
import com.cbrl.pixelblaze.ui.components.SectionCard
import com.cbrl.pixelblaze.ui.components.SectionLabel
import com.cbrl.pixelblaze.ui.components.SliderHeader
import com.cbrl.pixelblaze.ui.components.VisorPanel
import com.cbrl.pixelblaze.ui.components.hairline
import com.cbrl.pixelblaze.ui.components.lightChipBorder
import com.cbrl.pixelblaze.ui.components.lightChipColors
import com.cbrl.pixelblaze.ui.theme.HelmetText
import com.cbrl.pixelblaze.ui.theme.Led
import kotlin.math.abs
import kotlin.math.roundToInt

private data class Swatch(val name: String, val hue: Float, val saturation: Float) {
    val color: Color get() = Color.hsv(hue * 360f, saturation, 1f)
}

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
    // Lit LEDs of the brightness meter show the segment color.
    val ledColor = if (state.effect.colorUse == ColorUse.Full) Color.hsv(state.hue * 360f, state.saturation, 1f) else Led.White

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
            ledColor = { ledColor },
        )
        if (state.effect.usesSpeed) {
            SpeedControl(state.speed, enabled) { controller.setSegmentSpeed(segment, it) }
        }
        FadeControl(state.fadeSeconds, enabled) { controller.setSegmentFadeTime(segment, it) }

        if (segment == HelmetSegment.Visor) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MessageField(
                message = message,
                ledColor = if (state.effect.colorUse == ColorUse.Full) ledColor else Led.Red,
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
            color = Color.hsv(state.hue * 360f, state.saturation, 1f),
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
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        SegmentDot(state, size = 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(segment.label.uppercase(), style = MaterialTheme.typography.headlineSmall)
            Text(
                "${layout[segment].size} LEDs · ${if (state.on) "On" else "Off"}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.on,
            onCheckedChange = onEnabledChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                checkedBorderColor = colors.primary,
                uncheckedThumbColor = colors.onSurfaceVariant,
                uncheckedTrackColor = colors.surfaceContainerHighest,
                uncheckedBorderColor = colors.outline,
            ),
            modifier = Modifier.semantics { contentDescription = "${segment.label} on" },
        )
    }
}

@Composable
private fun EffectField(effect: HelmetEffect, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = colors.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f).hairline(shape),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(effect.category.icon, null, tint = colors.onSurface)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                SectionLabel("Effect · ${effect.category.label}")
                Text(effect.label, style = MaterialTheme.typography.titleMedium)
            }
            Text("Change", style = MaterialTheme.typography.labelLarge, color = colors.primary)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.primary)
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
    val hueColor = Color.hsv(state.hue * 360f, 1f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showHue) {
            SliderHeader("Hue", "${(state.hue * 360f).roundToInt()}°")
            LedSlider(
                value = state.hue,
                onValueChange = { hue -> update { it.copy(hue = hue.coerceAtMost(MAX_SLIDER_HUE)) } },
                enabled = enabled,
                fill = LedFill.All,
                thumbColor = hueColor,
                ledColor = { Color.hsv(it * 360f, 1f, 1f) },
            )
        } else {
            Text(
                "This effect makes its own hues. Saturation still applies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SliderHeader("Saturation", percentLabel(state.saturation))
        LedSlider(
            value = state.saturation,
            onValueChange = { saturation -> update { it.copy(saturation = saturation) } },
            enabled = enabled,
            fill = LedFill.All,
            thumbColor = Color.hsv(if (showHue) state.hue * 360f else 0f, state.saturation, 1f),
            ledColor = { position ->
                // Without a hue, the LEDs show how a rainbow fades to white.
                lerp(Color.White, if (showHue) hueColor else Color.hsv(position * 360f, 1f, 1f), position)
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaturationPresets.forEach { value ->
                val selected = abs(state.saturation - value) < 0.005f
                FilterChip(
                    selected = selected,
                    onClick = { update { it.copy(saturation = value) } },
                    enabled = enabled,
                    label = { Text(percentLabel(value)) },
                    shape = CircleShape,
                    colors = lightChipColors(),
                    border = lightChipBorder(selected, enabled),
                )
            }
        }

        if (showHue) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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

/** A swatch drawn as an LED. The selected LED glows inside a white ring. */
@Composable
private fun SwatchButton(
    swatch: Swatch,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ring = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = swatch.name
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .sizeIn(maxWidth = 38.dp, maxHeight = 38.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .alpha(if (enabled) 1f else 0.38f),
        ) {
            val radius = size.minDimension / 2f
            val color = swatch.color
            if (selected) {
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.6f), Color.Transparent), center, radius), radius)
                drawCircle(ring, radius * 0.86f, style = Stroke(1.5.dp.toPx()))
            }
            drawCircle(color, radius * 0.6f)
        }
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
                val selected = speed == point
                FilterChip(
                    selected = selected,
                    onClick = { change(point) },
                    enabled = enabled,
                    label = { Text(SpeedScale.shortLabel(point)) },
                    shape = CircleShape,
                    colors = lightChipColors(),
                    border = lightChipBorder(selected, enabled),
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
    ledColor: Color,
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
        // The message in LED dots, as the visor scrolls it.
        VisorPanel(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            LedText(
                text.ifBlank { "No message" },
                style = HelmetText.ledSmall.copy(fontSize = 24.sp, lineHeight = 26.sp),
                color = if (text.isBlank()) MaterialTheme.colorScheme.outline else ledColor,
                glow = text.isNotBlank(),
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0),
            )
        }
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
