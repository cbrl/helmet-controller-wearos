package com.cbrl.pixelblaze.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelControlsTest {
    @Test
    fun aBezelGivesOneStepForEachEvent() {
        val rotary = RotarySteps(lowRes = true)
        assertEquals(1, rotary.steps(96f))
        assertEquals(1, rotary.steps(3f))
        assertEquals(-1, rotary.steps(-200f))
        assertEquals(0, rotary.steps(0f))
    }

    @Test
    fun aSmoothCrownAddsUpSmallScrolls() {
        val rotary = RotarySteps(lowRes = false, pixelsPerStep = 40f)
        assertEquals(0, rotary.steps(15f))
        assertEquals(0, rotary.steps(15f))
        assertEquals(1, rotary.steps(15f))
        // 5 px remain. A fast turn gives several steps at once.
        assertEquals(3, rotary.steps(120f))
        // A turn back first uses up the remainder.
        assertEquals(0, rotary.steps(-20f))
        assertEquals(-1, rotary.steps(-30f))
    }

    @Test
    fun levelsSnapToTheFivePercentGrid() {
        assertEquals(0f, snapLevel(-0.2f))
        assertEquals(1f, snapLevel(1.3f))
        assertEquals(0.85f, snapLevel(0.83f), 1e-6f)
        assertEquals(0.15f, snapLevel(0.15f), 1e-6f)
        assertEquals(0.9f, snapLevel(0.85f + 0.05f), 1e-6f)
    }
}
