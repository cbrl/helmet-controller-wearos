package com.cbrl.pixelblaze.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cbrl.pixelblaze.ui.theme.HelmetText
import com.cbrl.pixelblaze.ui.theme.Led
import kotlin.math.max
import kotlin.math.roundToInt

/** The label and the current value above a slider. The value shows in LED dots. */
@Composable
fun SliderHeader(
    label: String,
    valueLabel: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        trailing()
        LedText(valueLabel, HelmetText.ledSmall, MaterialTheme.colorScheme.onSurface, glow = false)
    }
}

/**
 * A slider with a label, drawn as a row of LEDs that light up to the value. [onValueChange] runs
 * for each move during a drag, so the helmet follows the finger. With [steps], each value has one
 * LED. [ledColor] gives the color of a lit LED at a position from 0 to 1.
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
    ledColor: (Float) -> Color = { Led.White },
) {
    Column(modifier) {
        SliderHeader(label, valueLabel)
        LedSlider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            fill = LedFill.UpToValue,
            ledColor = ledColor,
        )
    }
}

/** Which LEDs of a [LedTrack] are lit. */
enum class LedFill {
    /** The LEDs up to the value, as on a level meter. */
    UpToValue,

    /** All LEDs, as a scale such as the hues. The thumb marks the value. */
    All,
}

/** A [Slider] with a [LedTrack] and a light thumb. A [thumbColor] shows a color thumb instead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    fill: LedFill = LedFill.UpToValue,
    thumbColor: Color? = null,
    ledColor: (Float) -> Color = { Led.White },
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        thumb = { if (thumbColor != null) ColorThumb(thumbColor, enabled) else LightThumb(enabled) },
        track = { state ->
            LedTrack(
                fraction = state.fraction,
                fill = fill,
                ledCount = if (steps > 0) steps + 2 else null,
                enabled = enabled,
                ledColor = ledColor,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
private val SliderState.fraction: Float
    get() {
        val span = valueRange.endInclusive - valueRange.start
        return if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    }

/**
 * A row of LEDs. [ledCount] gives one LED for each value of a stepped control; without it, the
 * row fits as many LEDs as the width allows.
 */
@Composable
fun LedTrack(
    fraction: Float,
    fill: LedFill,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    ledCount: Int? = null,
    ledColor: (Float) -> Color = { Led.White },
) {
    Canvas(modifier.fillMaxWidth().height(24.dp).alpha(if (enabled) 1f else 0.4f)) {
        val gap = 3.dp.toPx()
        val count = ledCount ?: max(8, ((size.width + gap) / 11.dp.toPx()).toInt())
        val ledWidth = (size.width - gap * (count - 1)) / count
        val ledHeight = 8.dp.toPx()
        val top = (size.height - ledHeight) / 2f
        val corner = CornerRadius(1.5.dp.toPx())
        val halo = 2.dp.toPx()
        val litIndex = (fraction * (count - 1)).roundToInt()
        for (index in 0 until count) {
            val position = if (count == 1) 0f else index / (count - 1f)
            val lit = when {
                fill == LedFill.All -> true
                ledCount != null -> index <= litIndex
                else -> (index + 0.5f) / count < fraction
            }
            val left = index * (ledWidth + gap)
            if (!lit) {
                drawRoundRect(Led.Off, Offset(left, top), Size(ledWidth, ledHeight), corner)
                continue
            }
            val color = ledColor(position)
            if (fill == LedFill.UpToValue) {
                // A soft glow around a lit LED of a meter.
                drawRoundRect(
                    color.copy(alpha = 0.18f),
                    Offset(left - halo, top - halo),
                    Size(ledWidth + 2 * halo, ledHeight + 2 * halo),
                    CornerRadius(corner.x + halo),
                )
            }
            drawRoundRect(color, Offset(left, top), Size(ledWidth, ledHeight), corner)
        }
    }
}

/** A thin bar of stage white. */
@Composable
fun LightThumb(enabled: Boolean) {
    Box(
        Modifier
            .size(width = 6.dp, height = 28.dp)
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/** The current color inside a white ring. */
@Composable
fun ColorThumb(color: Color, enabled: Boolean) {
    Box(
        Modifier
            .size(26.dp)
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
            .padding(3.dp)
            .background(Color.Black, CircleShape)
            .padding(2.dp)
            .background(if (enabled) color else color.copy(alpha = 0.4f), CircleShape),
    )
}

/** Seven hue stops, for a rainbow track. */
fun rainbowColors(saturation: Float = 1f): List<Color> =
    List(7) { Color.hsv(it * 60f % 360f, saturation, 1f) }
