package com.cbrl.pixelblaze.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.cbrl.pixelblaze.design.HelmetPalette
import com.cbrl.pixelblaze.helmet.ColorUse
import com.cbrl.pixelblaze.helmet.EffectCategory
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetLook
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetState
import com.cbrl.pixelblaze.helmet.SegmentState
import com.cbrl.pixelblaze.presentation.theme.Led
import kotlin.math.sqrt

private fun rainbow(saturation: Float): List<Color> =
    List(7) { Color.hsv(it * 60f % 360f, saturation, 1f) }

/** The color of a segment: its own color, a rainbow for effects that make their own hues, or dark when off. */
fun segmentBrush(state: SegmentState): Brush = when {
    !state.on -> SolidColor(Led.Off)
    state.effect.colorUse == ColorUse.Full -> SolidColor(Color.hsv(state.hue * 360f, state.saturation, 1f))
    state.effect.colorUse == ColorUse.SaturationOnly -> Brush.sweepGradient(rainbow(state.saturation))
    else -> Brush.sweepGradient(rainbow(1f))
}

/** A segment as one LED with a soft glow. Off segments show a dark LED. */
@Composable
fun SegmentDot(state: SegmentState?, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        if (state?.on == true) {
            val glow = if (state.effect.colorUse == ColorUse.Full) Color.hsv(state.hue * 360f, state.saturation, 1f) else Color.White
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.5f), Color.Transparent), center, radius), radius)
            drawCircle(segmentBrush(state), radius * 0.62f)
        } else {
            drawCircle(Led.Off, radius * 0.62f)
            drawCircle(Color.White.copy(alpha = 0.15f), radius * 0.62f, style = Stroke(1.dp.toPx()))
        }
    }
}

/** Three LEDs for a preset: the visor, a side and an ear. */
@Composable
fun LookDots(look: HelmetLook, modifier: Modifier = Modifier) {
    Box(modifier.size(26.dp)) {
        SegmentDot(look[HelmetSegment.Visor], Modifier.align(Alignment.TopStart), 16.dp)
        SegmentDot(look[HelmetSegment.VisorSide1], Modifier.align(Alignment.TopEnd), 16.dp)
        SegmentDot(look[HelmetSegment.LeftEar], Modifier.align(Alignment.BottomCenter), 16.dp)
    }
}

/** How LED text shows the visor: its color, or a rainbow [brush]. [lit] is false when the visor is dark. */
data class VisorInk(val color: Color, val brush: Brush?, val lit: Boolean)

/** The ink of the wordmark: the visor color, so the app shows what the helmet shows. */
fun visorInk(state: HelmetState?): VisorInk {
    val visor = state?.get(HelmetSegment.Visor) ?: return VisorInk(Led.White, null, lit = true)
    if (state.blackout || !visor.on || state.master <= 0f) return VisorInk(Color(0xFF3A3D44), null, lit = false)
    return when (visor.effect.colorUse) {
        ColorUse.Full -> VisorInk(Color.hsv(visor.hue * 360f, visor.saturation, 1f), null, lit = true)
        ColorUse.SaturationOnly -> VisorInk(Led.White, Brush.horizontalGradient(rainbow(visor.saturation)), lit = true)
        ColorUse.None -> VisorInk(Led.White, Brush.horizontalGradient(rainbow(1f)), lit = true)
    }
}

/** Text in LED dots. A glow in the text color shows when [glow] is on. */
@Composable
fun LedText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    glow: Boolean = true,
) {
    val blur = with(LocalDensity.current) { style.fontSize.toPx() * 0.45f }
    val shadow = if (glow) Shadow(color.copy(alpha = 0.85f), Offset.Zero, blur) else null
    Text(
        text,
        modifier = modifier,
        style = if (brush != null) style.copy(brush = brush, shadow = shadow) else style.copy(color = color, shadow = shadow),
        maxLines = 1,
    )
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
    if (!hasDevice) return ConnectionStatus("No helmet", MaterialTheme.colorScheme.onSurfaceVariant, busy = false)
    return when (connection) {
        HelmetConnectionState.Ready -> ConnectionStatus("Live", Led.Green, busy = false)
        HelmetConnectionState.Idle -> ConnectionStatus("Starting", Led.Amber, busy = true)
        HelmetConnectionState.Connecting -> ConnectionStatus("Connecting", Led.Amber, busy = true)
        is HelmetConnectionState.Reconnecting -> ConnectionStatus("Reconnecting", Led.Amber, busy = true)
        is HelmetConnectionState.MissingPattern -> ConnectionStatus("No pattern", Led.Red, busy = false)
        is HelmetConnectionState.Error -> ConnectionStatus("Error", Led.Red, busy = false)
    }
}

/** A status LED with a soft glow. A busy LED pulses. */
@Composable
fun StatusDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val level = if (pulsing) {
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
    Canvas(modifier.size(14.dp)) {
        val radius = 3.5.dp.toPx()
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f * level), Color.Transparent), center, radius * 2f))
        drawCircle(color.copy(alpha = 0.35f + 0.65f * level), radius)
    }
}

/**
 * The ear panel of the Discovery helmet: five LED bars in the colors of a rainbow. The bars are
 * dark when [lit] is false.
 */
@Composable
fun EarPanelMark(lit: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 10.dp, height = 18.dp)) {
        val count = HelmetPalette.EAR_PANEL.size
        val gap = 1.dp.toPx()
        val height = (size.height - gap * (count - 1)) / count
        HelmetPalette.EAR_PANEL.forEachIndexed { index, argb ->
            drawRoundRect(
                if (lit) Color(argb) else Led.Off,
                topLeft = Offset(0f, index * (height + gap)),
                size = Size(size.width, height),
                cornerRadius = CornerRadius(height / 2f),
            )
        }
    }
}

/**
 * A busy indicator: a pyramid of LEDs, after the Alive 2007 stage, with a light that runs along
 * its edges.
 */
@Composable
fun PyramidLoader(color: Color, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val transition = rememberInfiniteTransition(label = "pyramid")
    val head by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "pyramid head",
    )
    val off = Color.White.copy(alpha = 0.14f)
    Canvas(modifier.size(size)) {
        val side = this.size.width
        val height = side * sqrt(3f) / 2f
        val top = (this.size.height - height) / 2f
        val corners = listOf(Offset(side / 2f, top), Offset(side, top + height), Offset(0f, top + height))
        val perSide = 5
        val total = perSide * 3
        val radius = side / 18f
        for (index in 0 until total) {
            val from = corners[index / perSide]
            val to = corners[(index / perSide + 1) % 3]
            val point = from + (to - from) * ((index % perSide) / perSide.toFloat())
            // The distance behind the moving light, from 0 (at the light) to 1.
            val behind = ((head * total - index) / total + 1f) % 1f
            val level = (1f - behind * 3.5f).coerceIn(0f, 1f)
            drawCircle(off, radius, point)
            if (level > 0f) drawCircle(color.copy(alpha = level), radius, point)
        }
    }
}

/** A full-width list item that shrinks at the top and bottom of the screen. [scope] is the item scope. */
fun Modifier.listItem(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier =
    fillMaxWidth().transformedHeight(scope, spec)
