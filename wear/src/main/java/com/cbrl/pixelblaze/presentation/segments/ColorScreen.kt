package com.cbrl.pixelblaze.presentation.segments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SliderDefaults
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.cbrl.pixelblaze.helmet.ColorUse
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.helmet.SegmentState
import com.cbrl.pixelblaze.helmet.percentLabel
import com.cbrl.pixelblaze.presentation.components.listItem
import kotlin.math.abs
import kotlin.math.roundToInt

private data class Swatch(val name: String, val hue: Float, val saturation: Float) {
    val color: Color get() = Color.hsv(hue * 360f, saturation, 1f)
}

/** The LED colors of the phone app, in rows of three for the round screen. */
private val SwatchRows = listOf(
    listOf(Swatch("Red", 0f, 1f), Swatch("Orange", 0.05f, 1f), Swatch("Yellow", 0.14f, 1f)),
    listOf(Swatch("Green", 0.33f, 1f), Swatch("Cyan", 0.5f, 1f), Swatch("Blue", 0.66f, 1f)),
    listOf(Swatch("Purple", 0.76f, 1f), Swatch("Magenta", 0.88f, 1f), Swatch("White", 0f, 0f)),
)

private const val HUE_STEP_DEGREES = 10f
private const val MAX_HUE_DEGREES = 350f

@Composable
fun ColorScreen(viewModel: HelmetViewModel, segment: HelmetSegment) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val segmentState = state?.get(segment)
    val ready = connection == HelmetConnectionState.Ready && segmentState != null
    val controller = viewModel.controller
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val fullColor = segmentState?.effect?.colorUse == ColorUse.Full

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(Modifier.listItem(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text("${segment.shortLabel} ${if (fullColor) "color" else "saturation"}".uppercase())
                }
            }
            if (segmentState == null) {
                item { NotConnectedText() }
            } else {
                if (fullColor) {
                    item {
                        Swatches(segmentState, enabled = ready) { swatch ->
                            controller.setSegmentColor(segment, swatch.hue, swatch.saturation)
                        }
                    }
                    item {
                        val degrees = segmentState.hue * 360f
                        LabeledSlider(
                            label = "Hue · ${degrees.roundToInt()}°",
                            value = degrees.coerceAtMost(MAX_HUE_DEGREES),
                            steps = (MAX_HUE_DEGREES / HUE_STEP_DEGREES).roundToInt() - 1,
                            valueRange = 0f..MAX_HUE_DEGREES,
                            barColor = Color.hsv(degrees, 1f, 1f),
                            enabled = ready,
                            onValueChange = { controller.setSegmentColor(segment, it / 360f, segmentState.saturation) },
                            modifier = Modifier.listItem(this, spec),
                        )
                    }
                } else {
                    item {
                        Text(
                            "This effect makes its own hues. Saturation sets how deep they are.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                item {
                    LabeledSlider(
                        label = "Saturation · ${percentLabel(segmentState.saturation)}",
                        value = segmentState.saturation,
                        steps = 19,
                        valueRange = 0f..1f,
                        barColor = Color.hsv(if (fullColor) segmentState.hue * 360f else 0f, segmentState.saturation, 1f),
                        enabled = ready,
                        onValueChange = { controller.setSegmentColor(segment, segmentState.hue, it) },
                        modifier = Modifier.listItem(this, spec),
                    )
                }
            }
        }
    }
}

@Composable
private fun Swatches(state: SegmentState, enabled: Boolean, onPick: (Swatch) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SwatchRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { swatch ->
                    val selected = abs(swatch.saturation - state.saturation) < 0.01f &&
                        (swatch.saturation == 0f || abs(swatch.hue - state.hue) < 0.005f)
                    val ring = MaterialTheme.colorScheme.primary
                    Canvas(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .semantics {
                                this.selected = selected
                                contentDescription = swatch.name
                            }
                            .clickable(enabled = enabled, onClickLabel = swatch.name, role = Role.Button) { onPick(swatch) },
                    ) {
                        // Each swatch is an LED. The selected one glows inside a white ring.
                        val radius = size.minDimension / 2f
                        if (selected) {
                            drawCircle(Brush.radialGradient(listOf(swatch.color.copy(alpha = 0.6f), Color.Transparent), center, radius), radius)
                            drawCircle(ring, radius - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                        }
                        drawCircle(swatch.color, radius * 0.58f)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    steps: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    barColor: Color,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            steps = steps,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.sliderColors(selectedBarColor = barColor),
        )
    }
}
