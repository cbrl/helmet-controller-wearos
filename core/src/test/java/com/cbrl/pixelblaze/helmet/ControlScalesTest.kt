package com.cbrl.pixelblaze.helmet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlScalesTest {
    @Test
    fun speedScaleCoversTheWholeRange() {
        assertEquals(0f, SpeedScale.toPosition(SegmentState.MIN_SPEED), 1e-6f)
        assertEquals(1f, SpeedScale.toPosition(SegmentState.MAX_SPEED), 1e-6f)
        assertEquals(SegmentState.MIN_SPEED, SpeedScale.fromPosition(0f), 1e-6f)
        assertEquals(SegmentState.MAX_SPEED, SpeedScale.fromPosition(1f), 1e-6f)
    }

    @Test
    fun speedScaleIsLogarithmic() {
        val one = SpeedScale.toPosition(1f)
        assertEquals(one - SpeedScale.toPosition(0.5f), SpeedScale.toPosition(2f) - one, 1e-5f)
    }

    @Test
    fun snapPointsRoundTripAndAttract() {
        SpeedScale.SNAP_POINTS.forEach { assertEquals(it, SpeedScale.fromPosition(SpeedScale.toPosition(it))) }
        assertEquals(1f, SpeedScale.fromPosition(SpeedScale.toPosition(1.05f)))
        assertEquals(2f, SpeedScale.fromPosition(SpeedScale.toPosition(1.9f)))
        assertEquals(1.5f, SpeedScale.fromPosition(SpeedScale.toPosition(1.5f)), 0.011f)
    }

    @Test
    fun speedStepsCoverTheRangeInOrder() {
        assertEquals(SegmentState.MIN_SPEED, SpeedScale.STEPS.first())
        assertEquals(SegmentState.MAX_SPEED, SpeedScale.STEPS.last())
        assertTrue(SpeedScale.STEPS.asList().zipWithNext().all { (a, b) -> a < b })
        SpeedScale.SNAP_POINTS.forEach { assertTrue("$it is a step", it in SpeedScale.STEPS.asList()) }
    }

    @Test
    fun stepIndexFindsTheNearestStepOnTheLogScale() {
        SpeedScale.STEPS.forEachIndexed { index, step -> assertEquals(index, SpeedScale.stepIndex(step)) }
        assertEquals(SpeedScale.STEPS.asList().indexOf(1f), SpeedScale.stepIndex(1.2f))
        // 1.25 is nearer to 1.5 than to 1 on the log scale.
        assertEquals(SpeedScale.STEPS.asList().indexOf(1.5f), SpeedScale.stepIndex(1.25f))
        assertEquals(0, SpeedScale.stepIndex(0f))
        assertEquals(SpeedScale.STEPS.lastIndex, SpeedScale.stepIndex(50f))
    }

    @Test
    fun speedLabels() {
        assertEquals("0.25×", SpeedScale.shortLabel(0.25f))
        assertEquals("0.5×", SpeedScale.shortLabel(0.5f))
        assertEquals("1×", SpeedScale.shortLabel(1f))
        assertEquals("4×", SpeedScale.shortLabel(4f))
        assertEquals("1.5×", SpeedScale.label(1.5f))
        assertEquals("0.35×", SpeedScale.label(0.35f))
        assertEquals("1.23×", SpeedScale.label(1.23f))
        assertEquals("10×", SpeedScale.label(10f))
    }

    @Test
    fun fadeStepsPickTheNearestStep() {
        assertEquals(0, FadeSteps.indexOf(0f))
        assertEquals(FadeSteps.VALUES.asList().indexOf(0.25f), FadeSteps.indexOf(0.3f))
        assertEquals(FadeSteps.VALUES.lastIndex, FadeSteps.indexOf(SegmentState.MAX_FADE_SECONDS))
        assertEquals("Instant", FadeSteps.label(0f))
        assertEquals("0.25 s", FadeSteps.label(0.25f))
        assertEquals("1.5 s", FadeSteps.label(1.5f))
        assertEquals("2 s", FadeSteps.label(2f))
    }

    @Test
    fun percentLabelRounds() {
        assertEquals("0%", percentLabel(0f))
        assertEquals("15%", percentLabel(0.149f))
        assertEquals("100%", percentLabel(1f))
    }
}
