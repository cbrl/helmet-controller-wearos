package com.cbrl.pixelblaze.ui.helmet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cbrl.pixelblaze.helmet.ColorUse
import com.cbrl.pixelblaze.helmet.HelmetLook
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetState
import com.cbrl.pixelblaze.helmet.SegmentState
import com.cbrl.pixelblaze.ui.components.rainbowColors
import com.cbrl.pixelblaze.ui.theme.Led
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

/** How LED text shows the visor: its color, or a rainbow [brush]. [lit] is false when the visor is dark. */
data class VisorInk(val color: Color, val brush: Brush?, val lit: Boolean)

/** The ink of the wordmark: the visor color, so the app shows what the helmet shows. */
fun visorInk(state: HelmetState?): VisorInk {
    val visor = state?.get(HelmetSegment.Visor) ?: return VisorInk(Led.White, null, lit = true)
    if (state.blackout || !visor.on || state.master <= 0f) return VisorInk(Color(0xFF3A3D44), null, lit = false)
    return when (visor.effect.colorUse) {
        ColorUse.Full -> VisorInk(Color.hsv(visor.hue * 360f, visor.saturation, 1f), null, lit = true)
        ColorUse.SaturationOnly -> VisorInk(Led.White, Brush.horizontalGradient(rainbowColors(visor.saturation)), lit = true)
        ColorUse.None -> VisorInk(Led.White, Brush.horizontalGradient(rainbowColors()), lit = true)
    }
}

/** A segment as one LED with a soft glow. Off segments show a dark LED. */
@Composable
fun SegmentDot(state: SegmentState?, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val lit = state?.on == true
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        if (lit) {
            val glow = if (state.effect.colorUse == ColorUse.Full) segmentColor(state) else Color.White
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent), center, radius), radius)
            drawCircle(segmentBrush(state), radius * 0.62f)
        } else {
            drawCircle(LedOff, radius * 0.62f)
            drawCircle(Color.White.copy(alpha = 0.12f), radius * 0.62f, style = Stroke(1.dp.toPx()))
        }
    }
}

/** Overlapping LEDs for the visor, the first side strip, and the left ear of a look. */
@Composable
fun LookDots(look: HelmetLook, modifier: Modifier = Modifier) {
    val segments = listOf(HelmetSegment.Visor, HelmetSegment.VisorSide1, HelmetSegment.LeftEar)
    Box(modifier.width(18.dp + 12.dp * (segments.size - 1))) {
        segments.forEachIndexed { index, segment ->
            SegmentDot(look[segment], Modifier.offset(x = 12.dp * index), size = 18.dp)
        }
    }
}
