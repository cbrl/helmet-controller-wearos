package com.cbrl.pixelblaze.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.cbrl.pixelblaze.helmet.ColorUse
import com.cbrl.pixelblaze.helmet.EffectCategory
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetLook
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.SegmentState

/** The color of an LED that is off, as on the phone. */
private val LedOff = Color(0xFF1E2430)

private fun rainbow(saturation: Float): List<Color> =
    List(7) { Color.hsv(it * 60f % 360f, saturation, 1f) }

/** The color of a segment: its own color, a rainbow for effects that make their own hues, or dark when off. */
fun segmentBrush(state: SegmentState): Brush = when {
    !state.on -> SolidColor(LedOff)
    state.effect.colorUse == ColorUse.Full -> SolidColor(Color.hsv(state.hue * 360f, state.saturation, 1f))
    state.effect.colorUse == ColorUse.SaturationOnly -> Brush.sweepGradient(rainbow(state.saturation))
    else -> Brush.sweepGradient(rainbow(1f))
}

@Composable
fun SegmentDot(state: SegmentState?, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(state?.let(::segmentBrush) ?: SolidColor(LedOff))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
    )
}

/** Three dots for a preset: the visor, a side and an ear. */
@Composable
fun LookDots(look: HelmetLook, modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp)) {
        SegmentDot(look[HelmetSegment.Visor], Modifier.align(Alignment.TopStart), 14.dp)
        SegmentDot(look[HelmetSegment.VisorSide1], Modifier.align(Alignment.TopEnd), 14.dp)
        SegmentDot(look[HelmetSegment.LeftEar], Modifier.align(Alignment.BottomCenter), 14.dp)
    }
}

val EffectCategory.icon: ImageVector
    get() = when (this) {
        EffectCategory.Solid -> Icons.Rounded.Lightbulb
        EffectCategory.Motion -> Icons.Rounded.Waves
        EffectCategory.Sparkle -> Icons.Rounded.AutoAwesome
        EffectCategory.Rainbow -> Icons.Rounded.Gradient
        EffectCategory.Matrix -> Icons.Rounded.GridOn
    }

data class ConnectionStatus(val label: String, val color: Color, val busy: Boolean)

@Composable
fun connectionStatus(connection: HelmetConnectionState, hasDevice: Boolean): ConnectionStatus {
    val colors = MaterialTheme.colorScheme
    if (!hasDevice) return ConnectionStatus("No helmet", colors.onSurfaceVariant, busy = false)
    return when (connection) {
        HelmetConnectionState.Ready -> ConnectionStatus("Live", colors.primary, busy = false)
        HelmetConnectionState.Idle -> ConnectionStatus("Starting", colors.tertiary, busy = true)
        HelmetConnectionState.Connecting -> ConnectionStatus("Connecting", colors.tertiary, busy = true)
        is HelmetConnectionState.Reconnecting -> ConnectionStatus("Reconnecting", colors.tertiary, busy = true)
        is HelmetConnectionState.MissingPattern -> ConnectionStatus("No pattern", colors.error, busy = false)
        is HelmetConnectionState.Error -> ConnectionStatus("Error", colors.error, busy = false)
    }
}

@Composable
fun StatusDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "status")
        val pulse by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "pulse",
        )
        pulse
    } else {
        1f
    }
    Box(modifier.size(8.dp).alpha(alpha).background(color, CircleShape))
}

/** A full-width list item that shrinks at the top and bottom of the screen. [scope] is the item scope. */
fun Modifier.listItem(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier =
    fillMaxWidth().transformedHeight(scope, spec)
