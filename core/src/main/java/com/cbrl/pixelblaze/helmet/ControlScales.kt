package com.cbrl.pixelblaze.helmet

import java.math.BigDecimal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Maps the animation speed to a slider position on a log scale, so that 0.5x and 2x are the same
 * distance from 1x. Positions near [SNAP_POINTS] snap to them.
 */
object SpeedScale {
    val SNAP_POINTS = floatArrayOf(0.25f, 0.5f, 1f, 2f, 4f)

    /** The speeds that a step control offers, for example on the watch. */
    val STEPS = floatArrayOf(0.05f, 0.1f, 0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f, 6f, 10f)

    /** The snap distance in natural-log units: 0.08 is about 8 % of the speed. */
    private const val SNAP_DISTANCE = 0.08f

    private val minLog = ln(SegmentState.MIN_SPEED)
    private val maxLog = ln(SegmentState.MAX_SPEED)

    fun toPosition(speed: Float): Float =
        (ln(speed.coerceIn(SegmentState.MIN_SPEED, SegmentState.MAX_SPEED)) - minLog) / (maxLog - minLog)

    fun fromPosition(position: Float): Float {
        val logSpeed = minLog + position.coerceIn(0f, 1f) * (maxLog - minLog)
        val snap = SNAP_POINTS.minBy { abs(ln(it) - logSpeed) }
        if (abs(ln(snap) - logSpeed) < SNAP_DISTANCE) return snap
        val speed = exp(logSpeed)
        return ((speed * 100f).roundToInt() / 100f).coerceIn(SegmentState.MIN_SPEED, SegmentState.MAX_SPEED)
    }

    fun isSnapPoint(speed: Float): Boolean = SNAP_POINTS.any { it == speed }

    /** The index of the step in [STEPS] nearest to [speed] on the log scale. */
    fun stepIndex(speed: Float): Int {
        val logSpeed = ln(speed.coerceIn(SegmentState.MIN_SPEED, SegmentState.MAX_SPEED))
        return STEPS.indices.minBy { abs(ln(STEPS[it]) - logSpeed) }
    }

    /** A label without trailing zeros, for the snap points: "0.25×", "0.5×", "1×". */
    fun shortLabel(speed: Float): String =
        BigDecimal(speed.toString()).stripTrailingZeros().toPlainString() + "×"

    fun label(speed: Float): String = when {
        speed >= 10f -> String.format(Locale.US, "%.0f×", speed)
        speed >= 1f && speed * 10f == (speed * 10f).roundToInt().toFloat() -> String.format(Locale.US, "%.1f×", speed)
        else -> String.format(Locale.US, "%.2f×", speed)
    }
}

/** The fade times that the fade controls offer. */
object FadeSteps {
    val VALUES = floatArrayOf(0f, 0.1f, 0.25f, 0.5f, 1f, 1.5f, 2f, 3f, 5f, 8f, 10f, 15f, 20f, 30f)

    /** The index of the step nearest to [seconds]. */
    fun indexOf(seconds: Float): Int = VALUES.indices.minBy { abs(VALUES[it] - seconds) }

    fun label(seconds: Float): String = when {
        seconds <= 0f -> "Instant"
        seconds < 1f -> String.format(Locale.US, "%.2f s", seconds)
        seconds == seconds.roundToInt().toFloat() -> "${seconds.roundToInt()} s"
        else -> String.format(Locale.US, "%.1f s", seconds)
    }
}

fun percentLabel(value: Float): String = "${(value * 100f).roundToInt()}%"
