package com.cbrl.pixelblaze.ui.helmet

import androidx.compose.ui.graphics.Color
import com.cbrl.pixelblaze.helmet.HelmetEffect
import com.cbrl.pixelblaze.helmet.SegmentState
import com.cbrl.pixelblaze.ui.theme.LedOff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedColorsTest {
    @Test
    fun ledColorsLiftDimPixels() {
        assertEquals(LedOff, LedColors.fromRgb(0, 0, 0))
        assertEquals(LedOff, LedColors.fromRgb(2, 1, 0))
        assertEquals(Color.Red, LedColors.fromRgb(255, 0, 0))
        val dim = LedColors.fromRgb(10, 0, 0)
        assertNotEquals(LedOff, dim)
        assertTrue("a dim pixel keeps its hue", dim.red > dim.green && dim.red > dim.blue)
        assertTrue(LedColors.displayLevel(0.04f) >= 0.3f)
    }

    @Test
    fun segmentColorFollowsOnAndMaster() {
        val red = SegmentState(hue = 0f, saturation = 1f, brightness = 1f, effect = HelmetEffect.SolidColor)
        assertEquals(Color.Red, LedColors.segment(red, 0f, master = 1f))
        assertEquals(LedOff, LedColors.segment(red.copy(on = false), 0f, master = 1f))
        assertEquals(LedOff, LedColors.segment(red, 0f, master = 0f))
    }
}
