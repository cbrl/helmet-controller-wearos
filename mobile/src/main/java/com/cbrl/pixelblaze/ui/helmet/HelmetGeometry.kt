package com.cbrl.pixelblaze.ui.helmet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.cbrl.pixelblaze.helmet.HelmetLayout
import com.cbrl.pixelblaze.helmet.HelmetSegment
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Pixel positions for the helmet diagram, in units of one LED pitch. This is a schematic, not the
 * physical shape: the ears are rings, the side strips are vertical bars beside the visor, and the
 * visor is its zigzag matrix.
 *
 * Left to right: left ear, side 2, side 1, visor, side 3, side 4, right ear. Strip pixel 0 is at
 * the bottom, so "up" effects move up. Visor row 0 is at the top and odd rows run right to left,
 * as in the pattern.
 */
class HelmetGeometry private constructor(val layout: HelmetLayout) {
    /** The position of each pixel, in wiring order. */
    val pixels: Array<Offset>

    /** The segment of each pixel, in wiring order. */
    val segmentOf: Array<HelmetSegment>

    /** The index of each pixel in its segment. */
    val localOf: IntArray

    val width: Float
    val height: Float

    private val bounds = HashMap<HelmetSegment, Rect>()

    init {
        val visor = layout[HelmetSegment.Visor]
        val visorWidth = if (visor.is2D) visor.width else visor.size
        val visorHeight = if (visor.is2D) visor.height else 1
        val ringRadius = HelmetSegment.entries
            .filter { it == HelmetSegment.LeftEar || it == HelmetSegment.RightEar }
            .maxOf { max(MIN_RING_RADIUS, layout[it].size * RING_PITCH / (2f * PI.toFloat())) }
        val stripLength = (1..4).maxOf { layout[HelmetSegment.fromId(it)!!].size }
        val contentHeight = maxOf(visorHeight.toFloat(), stripLength.toFloat(), 2f * ringRadius + 1f)
        val centerY = MARGIN + (contentHeight - 1f) / 2f

        var x = MARGIN + ringRadius
        val leftEarX = x
        x += ringRadius + GAP
        val side2X = x
        x += STRIP_GAP
        val side1X = x
        x += GAP
        val visorX = x
        x += visorWidth - 1 + GAP
        val side3X = x
        x += STRIP_GAP
        val side4X = x
        x += GAP + ringRadius
        val rightEarX = x
        width = x + ringRadius + MARGIN
        height = contentHeight + 2f * MARGIN - 1f

        val positions = ArrayList<Offset>(layout.pixelCount)
        val segments = ArrayList<HelmetSegment>(layout.pixelCount)
        val locals = ArrayList<Int>(layout.pixelCount)
        for (segment in HelmetSegment.entries) {
            val size = layout[segment].size
            for (local in 0 until size) {
                positions += when (segment) {
                    HelmetSegment.Visor -> {
                        val row = local / visorWidth
                        val column = (local % visorWidth).let { if (row % 2 == 1) visorWidth - 1 - it else it }
                        Offset(visorX + column, centerY - (visorHeight - 1) / 2f + row)
                    }
                    HelmetSegment.VisorSide1 -> strip(side1X, centerY, size, local)
                    HelmetSegment.VisorSide2 -> strip(side2X, centerY, size, local)
                    HelmetSegment.VisorSide3 -> strip(side3X, centerY, size, local)
                    HelmetSegment.VisorSide4 -> strip(side4X, centerY, size, local)
                    HelmetSegment.LeftEar -> ring(leftEarX, centerY, ringRadius, size, local, mirror = true)
                    HelmetSegment.RightEar -> ring(rightEarX, centerY, ringRadius, size, local, mirror = false)
                }
                segments += segment
                locals += local
            }
        }
        pixels = positions.toTypedArray()
        segmentOf = segments.toTypedArray()
        localOf = locals.toIntArray()

        for (segment in HelmetSegment.entries) {
            val start = layout.start(segment)
            val points = pixels.asList().subList(start, start + layout[segment].size)
            if (points.isEmpty()) continue
            bounds[segment] = Rect(
                left = points.minOf { it.x } - 0.5f,
                top = points.minOf { it.y } - 0.5f,
                right = points.maxOf { it.x } + 0.5f,
                bottom = points.maxOf { it.y } + 0.5f,
            )
        }
    }

    /** The area that the pixels of [segment] cover, or null for an empty segment. */
    fun bounds(segment: HelmetSegment): Rect? = bounds[segment]

    /** The segment with the pixel nearest to [point], if that pixel is within [maxDistance]. */
    fun segmentAt(point: Offset, maxDistance: Float = TOUCH_DISTANCE): HelmetSegment? {
        var best: HelmetSegment? = null
        var bestDistance = maxDistance * maxDistance
        pixels.forEachIndexed { index, pixel ->
            val distance = (pixel - point).getDistanceSquared()
            if (distance < bestDistance) {
                bestDistance = distance
                best = segmentOf[index]
            }
        }
        return best
    }

    private fun strip(x: Float, centerY: Float, size: Int, local: Int) =
        Offset(x, centerY + (size - 1) / 2f - local)

    private fun ring(cx: Float, cy: Float, radius: Float, size: Int, local: Int, mirror: Boolean): Offset {
        val angle = 2.0 * PI * local / size
        val dx = (radius * sin(angle)).toFloat()
        return Offset(if (mirror) cx - dx else cx + dx, cy - (radius * cos(angle)).toFloat())
    }

    companion object {
        private const val MARGIN = 0.6f
        private const val GAP = 1.9f
        private const val STRIP_GAP = 1.7f
        private const val RING_PITCH = 1.35f
        private const val MIN_RING_RADIUS = 1.5f
        private const val TOUCH_DISTANCE = 3.4f

        private val Default = HelmetGeometry(HelmetLayout.Default)

        fun of(layout: HelmetLayout): HelmetGeometry = if (layout == HelmetLayout.Default) Default else HelmetGeometry(layout)
    }
}
