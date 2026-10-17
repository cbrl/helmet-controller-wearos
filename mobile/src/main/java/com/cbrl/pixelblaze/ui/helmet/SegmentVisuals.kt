package com.cbrl.pixelblaze.ui.helmet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cbrl.pixelblaze.helmet.ColorUse
import com.cbrl.pixelblaze.helmet.HelmetLook
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.SegmentState
import com.cbrl.pixelblaze.ui.components.rainbowColors
import com.cbrl.pixelblaze.ui.theme.LedOff

/** The screen color of a segment at full brightness. */
fun segmentColor(state: SegmentState): Color =
    if (state.on) Color.hsv(state.hue * 360f, state.saturation, 1f) else LedOff

/** A fill for a segment: its color, or a rainbow when the effect makes its own hues. */
fun segmentBrush(state: SegmentState): Brush = when {
    !state.on || state.effect.colorUse == ColorUse.Full -> SolidColor(segmentColor(state))
    state.effect.colorUse == ColorUse.SaturationOnly -> Brush.sweepGradient(rainbowColors(state.saturation))
    else -> Brush.sweepGradient(rainbowColors())
}

@Composable
fun SegmentDot(state: SegmentState?, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(
        modifier
            .size(size)
            .background(state?.let(::segmentBrush) ?: SolidColor(LedOff), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/** Overlapping dots for the visor, the first side strip, and the left ear of a look. */
@Composable
fun LookDots(look: HelmetLook, modifier: Modifier = Modifier) {
    val segments = listOf(HelmetSegment.Visor, HelmetSegment.VisorSide1, HelmetSegment.LeftEar)
    Box(modifier.width(14.dp * segments.size - 4.dp * (segments.size - 1))) {
        segments.forEachIndexed { index, segment ->
            SegmentDot(look[segment], Modifier.offset(x = 10.dp * index), size = 14.dp)
        }
    }
}
