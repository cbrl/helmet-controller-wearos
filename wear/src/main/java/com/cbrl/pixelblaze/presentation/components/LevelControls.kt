package com.cbrl.pixelblaze.presentation.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LevelIndicatorDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.StepperLevelIndicator
import androidx.wear.compose.material3.Text
import com.cbrl.pixelblaze.helmet.percentLabel
import kotlin.math.roundToInt
import kotlin.math.sign

private const val LEVEL_STEP = 0.05f

/** The system feature of watches with a bezel or crown that sends one event for each detent. */
private const val LOW_RES_ROTARY_FEATURE = "android.hardware.rotaryencoder.lowres"

/** The scroll distance for one step on a smooth crown. */
private const val PIXELS_PER_STEP = 40f

/** Rounds a level to the 5 % grid of [LevelScreen]. */
fun snapLevel(value: Float): Float =
    ((value.coerceIn(0f, 1f) / LEVEL_STEP).roundToInt() * LEVEL_STEP).coerceIn(0f, 1f)

/**
 * A full-screen level control, for brightness. The buttons, the crown and the bezel change the
 * level in steps of 5 %. Each change goes to the helmet at once.
 *
 * [currentLevel] reads the level without waiting for a recomposition, so that fast crown turns
 * do not lose steps.
 */
@Composable
fun LevelScreen(
    title: String,
    level: Float,
    enabled: Boolean,
    onLevelChange: (Float) -> Unit,
    currentLevel: () -> Float = { level },
) {
    val haptics = LocalHapticFeedback.current
    val rotary = rememberRotarySteps()
    // No time text: it would cover the top button of the stepper.
    ScreenScaffold(
        timeText = {},
        scrollIndicator = {
            StepperLevelIndicator(
                value = { level },
                enabled = enabled,
                colors = LevelIndicatorDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary),
            )
        },
    ) {
        Stepper(
            value = level,
            onValueChange = { onLevelChange(snapLevel(it)) },
            steps = (1f / LEVEL_STEP).roundToInt() - 1,
            decreaseIcon = { Icon(Icons.Rounded.Remove, contentDescription = "Lower") },
            increaseIcon = { Icon(Icons.Rounded.Add, contentDescription = "Raise") },
            modifier = Modifier.rotarySteps(rotary, enabled) { steps ->
                val now = currentLevel()
                val next = snapLevel(now + steps * LEVEL_STEP)
                if (next != now) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onLevelChange(next)
                }
            },
            enabled = enabled,
            valueRange = 0f..1f,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(percentLabel(level), style = MaterialTheme.typography.numeralMedium)
            }
        }
    }
}

/**
 * Turns crown and bezel scroll into steps: +1 for each step clockwise. A low-resolution input (a
 * bezel) gives one step for each event. A smooth crown gives one step for each [pixelsPerStep].
 */
class RotarySteps(private val lowRes: Boolean, private val pixelsPerStep: Float = PIXELS_PER_STEP) {
    private var pixels = 0f

    fun steps(scrollPixels: Float): Int {
        if (lowRes) return scrollPixels.sign.toInt()
        pixels += scrollPixels
        val steps = (pixels / pixelsPerStep).toInt()
        pixels -= steps * pixelsPerStep
        return steps
    }
}

@Composable
fun rememberRotarySteps(): RotarySteps {
    val context = LocalContext.current
    return remember(context) {
        RotarySteps(lowRes = context.packageManager.hasSystemFeature(LOW_RES_ROTARY_FEATURE))
    }
}

/** Sends crown and bezel steps to [onSteps] while the screen is active. */
fun Modifier.rotarySteps(rotary: RotarySteps, enabled: Boolean, onSteps: (Int) -> Unit): Modifier =
    onRotaryScrollEvent { event ->
        if (!enabled) return@onRotaryScrollEvent false
        val steps = rotary.steps(event.verticalScrollPixels)
        if (steps != 0) onSteps(steps)
        true
    }
        .requestFocusOnHierarchyActive()
        .focusable()

/** A label, the current value and a slider over [count] steps. */
@Composable
fun StepSlider(
    label: String,
    valueLabel: String,
    index: Int,
    count: Int,
    enabled: Boolean,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$label · $valueLabel",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            value = index,
            onValueChange = onIndexChange,
            valueProgression = 0 until count,
            enabled = enabled,
        )
    }
}
