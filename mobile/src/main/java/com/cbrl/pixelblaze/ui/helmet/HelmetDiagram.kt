package com.cbrl.pixelblaze.ui.helmet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.cbrl.pixelblaze.helmet.ColorUse
import com.cbrl.pixelblaze.helmet.HelmetLayout
import com.cbrl.pixelblaze.helmet.HelmetLook
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.SegmentState
import com.cbrl.pixelblaze.ui.theme.LedOff
import kotlin.math.min
import kotlin.math.sqrt

/** Screen colors for LED values. Dim LEDs are lifted, so that a visor at 10 % still shows its color. */
object LedColors {
    private const val FLOOR = 0.3f
    private const val MIN_VISIBLE = 3

    fun displayLevel(level: Float): Float = if (level <= 0f) 0f else FLOOR + (1f - FLOOR) * sqrt(level.coerceAtMost(1f))

    fun fromHsv(hue: Float, saturation: Float, level: Float): Color =
        if (level <= 0f) LedOff else lerp(LedOff, Color.hsv(hue * 360f, saturation, 1f), displayLevel(level))

    fun fromRgb(red: Int, green: Int, blue: Int): Color {
        val max = maxOf(red, green, blue)
        if (max < MIN_VISIBLE) return LedOff
        val full = Color(red / max.toFloat(), green / max.toFloat(), blue / max.toFloat())
        return lerp(LedOff, full, displayLevel(max / 255f))
    }

    /** The color that a segment shows at [position] (0..1 along the segment), without animation. */
    fun segment(state: SegmentState, position: Float, master: Float): Color {
        val level = if (state.on) state.brightness * master else 0f
        return when (state.effect.colorUse) {
            ColorUse.Full -> fromHsv(state.hue, state.saturation, level)
            ColorUse.SaturationOnly -> fromHsv(position, state.saturation, level)
            ColorUse.None -> fromHsv(position, 1f, level)
        }
    }
}

private val SegmentLabels = mapOf(
    HelmetSegment.VisorSide1 to "1",
    HelmetSegment.VisorSide2 to "2",
    HelmetSegment.VisorSide3 to "3",
    HelmetSegment.VisorSide4 to "4",
    HelmetSegment.LeftEar to "L",
    HelmetSegment.RightEar to "R",
)

private const val LABEL_SPACE = 2.4f

/**
 * Draws the helmet. When [frame] returns RGB bytes, the diagram shows those live pixels. Otherwise
 * it shows the color of each segment in [look]. [frame] is read only while drawing, so a new frame
 * redraws the diagram without a recomposition. [dotRadius] is in units of the LED pitch.
 */
@Composable
fun HelmetDiagram(
    layout: HelmetLayout,
    look: HelmetLook?,
    modifier: Modifier = Modifier,
    master: Float = 1f,
    blackout: Boolean = false,
    frame: () -> ByteArray? = { null },
    selected: HelmetSegment? = null,
    onSegmentClick: ((HelmetSegment) -> Unit)? = null,
    showLabels: Boolean = false,
    glow: Boolean = true,
    dotRadius: Float = 0.38f,
) {
    val geometry = remember(layout) { HelmetGeometry.of(layout) }
    val totalHeight = geometry.height + if (showLabels) LABEL_SPACE else 0f
    val highlight = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labels: Map<HelmetSegment, TextLayoutResult> = remember(measurer, labelStyle, showLabels) {
        if (showLabels) SegmentLabels.mapValues { measurer.measure(it.value, labelStyle) } else emptyMap()
    }

    val interactive = if (onSegmentClick == null) Modifier else Modifier
        .pointerInput(geometry, totalHeight, onSegmentClick) {
            detectTapGestures { position ->
                val transform = DiagramTransform(size.width.toFloat(), size.height.toFloat(), geometry.width, totalHeight)
                geometry.segmentAt(transform.toUnits(position))?.let(onSegmentClick)
            }
        }
        .semantics {
            contentDescription = "Helmet diagram" + (selected?.let { ". ${it.label} selected" } ?: "")
            customActions = HelmetSegment.entries.map { segment ->
                CustomAccessibilityAction("Select ${segment.label}") { onSegmentClick(segment); true }
            }
        }

    Canvas(modifier.aspectRatio(geometry.width / totalHeight).then(interactive)) {
        val transform = DiagramTransform(size.width, size.height, geometry.width, totalHeight)
        val unit = transform.scale
        val pixels = frame()
        val livePixels = pixels?.let { min(it.size / 3, geometry.pixels.size) } ?: 0
        val level = if (blackout) 0f else master

        geometry.pixels.forEachIndexed { index, position ->
            val color = when {
                index < livePixels -> LedColors.fromRgb(
                    pixels!![index * 3].toInt() and 0xFF,
                    pixels[index * 3 + 1].toInt() and 0xFF,
                    pixels[index * 3 + 2].toInt() and 0xFF,
                )
                look != null -> {
                    val segment = geometry.segmentOf[index]
                    val size = layout[segment].size
                    val position = if (size > 1) geometry.localOf[index] / (size - 1f) else 0f
                    LedColors.segment(look[segment], position, level)
                }
                else -> LedOff
            }
            val center = transform.toPixels(position)
            if (glow && color != LedOff) drawCircle(color.copy(alpha = 0.22f), radius = unit * 0.85f, center = center)
            drawCircle(color, radius = unit * dotRadius, center = center)
        }

        if (selected != null) {
            geometry.bounds(selected)?.let { area ->
                val inset = 0.45f * unit
                val topLeft = transform.toPixels(area.topLeft)
                drawRoundRect(
                    color = highlight,
                    topLeft = topLeft - Offset(inset, inset),
                    size = Size(area.width * unit + 2 * inset, area.height * unit + 2 * inset),
                    cornerRadius = CornerRadius(unit * 1.1f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        labels.forEach { (segment, text) ->
            val area = geometry.bounds(segment) ?: return@forEach
            val anchor = transform.toPixels(Offset(area.center.x, geometry.height + 0.2f))
            drawText(
                textLayoutResult = text,
                color = if (segment == selected) highlight else labelColor,
                topLeft = Offset(anchor.x - text.size.width / 2f, anchor.y),
            )
        }
    }
}

/** Scales diagram units to fit a canvas, and centers the diagram. */
private class DiagramTransform(canvasWidth: Float, canvasHeight: Float, unitsWide: Float, unitsHigh: Float) {
    val scale = min(canvasWidth / unitsWide, canvasHeight / unitsHigh)
    private val left = (canvasWidth - unitsWide * scale) / 2f
    private val top = (canvasHeight - unitsHigh * scale) / 2f

    fun toPixels(units: Offset) = Offset(left + units.x * scale, top + units.y * scale)

    fun toUnits(pixels: Offset) = Offset((pixels.x - left) / scale, (pixels.y - top) / scale)
}
