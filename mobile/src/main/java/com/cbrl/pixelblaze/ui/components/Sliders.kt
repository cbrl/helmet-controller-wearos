package com.cbrl.pixelblaze.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The label and the current value above a slider. */
@Composable
fun SliderHeader(
    label: String,
    valueLabel: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        trailing()
        Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * A slider with a label. [onValueChange] runs for each move during a drag, so the helmet follows the
 * finger.
 */
@Composable
fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        SliderHeader(label, valueLabel)
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            // The default inactive track is the secondary container (magenta), which competes with the value.
            colors = SliderDefaults.colors(
                inactiveTrackColor = colors.surfaceContainerHighest,
                inactiveTickColor = colors.onSurfaceVariant,
            ),
        )
    }
}

/**
 * A slider with a painted track, for hue and saturation. The [layers] paint the track from bottom
 * to top, and the thumb shows [thumbColor].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    layers: List<Brush>,
    thumbColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f),
        enabled = enabled,
        thumb = {
            Box(
                Modifier
                    .size(28.dp)
                    .shadow(4.dp, CircleShape)
                    .background(thumbColor, CircleShape)
                    .border(3.dp, Color.White, CircleShape),
            )
        },
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(CircleShape)
                    .drawBehind { layers.forEach { drawRect(it) } }
                    .border(1.dp, outline, CircleShape),
            )
        },
    )
}

/** Seven hue stops, for a rainbow track. */
fun rainbowColors(saturation: Float = 1f): List<Color> =
    List(7) { Color.hsv(it * 60f % 360f, saturation, 1f) }
