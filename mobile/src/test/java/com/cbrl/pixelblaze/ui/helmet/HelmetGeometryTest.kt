package com.cbrl.pixelblaze.ui.helmet

import androidx.compose.ui.geometry.Offset
import com.cbrl.pixelblaze.helmet.HelmetLayout
import com.cbrl.pixelblaze.helmet.HelmetSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelmetGeometryTest {
    private val layout = HelmetLayout.Default
    private val geometry = HelmetGeometry.of(layout)

    @Test
    fun hasOnePositionForEachPixelInWiringOrder() {
        assertEquals(layout.pixelCount, geometry.pixels.size)
        HelmetSegment.entries.forEach { segment ->
            val start = layout.start(segment)
            repeat(layout[segment].size) { local ->
                assertEquals(segment, geometry.segmentOf[start + local])
                assertEquals(local, geometry.localOf[start + local])
            }
        }
    }

    @Test
    fun visorRowsZigzag() {
        val pixels = geometry.pixels
        val width = layout[HelmetSegment.Visor].width
        // Row 0 runs left to right, row 1 runs right to left.
        assertTrue(pixels[0].x < pixels[width - 1].x)
        assertEquals(pixels[width - 1].x, pixels[width].x, 1e-4f)
        assertEquals(pixels[width - 1].y + 1f, pixels[width].y, 1e-4f)
        assertEquals(pixels[0].x, pixels[2 * width - 1].x, 1e-4f)
    }

    @Test
    fun stripsStartAtTheBottom() {
        val start = layout.start(HelmetSegment.VisorSide1)
        val end = start + layout[HelmetSegment.VisorSide1].size - 1
        assertTrue(geometry.pixels[start].y > geometry.pixels[end].y)
    }

    @Test
    fun segmentsAreLeftToRight() {
        val order = listOf(
            HelmetSegment.LeftEar,
            HelmetSegment.VisorSide2,
            HelmetSegment.VisorSide1,
            HelmetSegment.Visor,
            HelmetSegment.VisorSide3,
            HelmetSegment.VisorSide4,
            HelmetSegment.RightEar,
        )
        val centers = order.map { geometry.bounds(it)!!.center.x }
        assertEquals(centers.sorted(), centers)
        assertTrue(geometry.bounds(HelmetSegment.RightEar)!!.right <= geometry.width)
    }

    @Test
    fun everyPixelHitsItsOwnSegment() {
        geometry.pixels.forEachIndexed { index, pixel ->
            assertEquals("pixel $index", geometry.segmentOf[index], geometry.segmentAt(pixel))
        }
    }

    @Test
    fun ringCenterSelectsTheEarAndFarPointsSelectNothing() {
        assertEquals(HelmetSegment.LeftEar, geometry.segmentAt(geometry.bounds(HelmetSegment.LeftEar)!!.center))
        assertEquals(HelmetSegment.RightEar, geometry.segmentAt(geometry.bounds(HelmetSegment.RightEar)!!.center))
        assertNull(geometry.segmentAt(Offset(geometry.width / 2f, geometry.height + 20f)))
    }
}
