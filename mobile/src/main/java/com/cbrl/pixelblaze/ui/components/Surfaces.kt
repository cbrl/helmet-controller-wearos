package com.cbrl.pixelblaze.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cbrl.pixelblaze.design.HelmetPalette
import kotlin.math.sqrt

/** The thin line around panels and controls. */
val Hairline = Color.White.copy(alpha = 0.1f)

fun Modifier.hairline(shape: Shape, color: Color = Hairline): Modifier = border(BorderStroke(1.dp, color), shape)

/** A flat dark panel with a hairline edge. */
fun Modifier.panel(shape: Shape): Modifier = this
    .clip(shape)
    .background(Color(HelmetPalette.SURFACE_LOW))
    .hairline(shape)

/**
 * A faint grid of dots, as on the LED walls of the Alive 2007 stage. The grid fades out toward the
 * bottom when [fadeTo] is less than 1.
 */
fun Modifier.ledGrid(pitch: Dp = 10.dp, alpha: Float = 0.07f, fadeTo: Float = 1f): Modifier = drawWithCache {
    val step = pitch.toPx()
    val radius = 0.9.dp.toPx()
    val columns = (size.width / step).toInt() + 1
    val rows = (size.height / step).toInt() + 1
    val offsetX = (size.width - (columns - 1) * step) / 2f
    onDrawBehind {
        for (row in 0 until rows) {
            val y = step / 2f + row * step
            val fade = if (fadeTo >= 1f) 1f else (1f - y / (size.height * fadeTo)).coerceIn(0f, 1f)
            if (fade <= 0f) break
            val color = Color.White.copy(alpha = alpha * fade)
            for (column in 0 until columns) {
                drawCircle(color, radius, Offset(offsetX + column * step, y))
            }
        }
    }
}

/** The visor: flat black with a hairline edge, over a faint LED grid when [grid] is on. */
@Composable
fun VisorPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
    grid: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(Color.Black)
            .then(if (grid) Modifier.ledGrid() else Modifier)
            .hairline(shape)
            .padding(contentPadding),
        content = content,
    )
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
    val blur = with(LocalDensity.current) { (style.fontSize.toPx() * 0.45f) }
    val shadow = if (glow) Shadow(color.copy(alpha = 0.85f), Offset.Zero, blur) else null
    Text(
        text,
        modifier = modifier,
        style = if (brush != null) style.copy(brush = brush, shadow = shadow) else style.copy(color = color, shadow = shadow),
        maxLines = 1,
    )
}

/**
 * The ear panel of the Discovery helmet: five LED bars in the colors of a rainbow. The bars are
 * dark when [lit] is false.
 */
@Composable
fun EarPanelMark(lit: Boolean, modifier: Modifier = Modifier) {
    val off = Color(HelmetPalette.LED_OFF)
    Canvas(modifier.size(width = 14.dp, height = 24.dp)) {
        val count = HelmetPalette.EAR_PANEL.size
        val gap = 1.5.dp.toPx()
        val height = (size.height - gap * (count - 1)) / count
        HelmetPalette.EAR_PANEL.forEachIndexed { index, argb ->
            drawRoundRect(
                if (lit) Color(argb) else off,
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
fun PyramidLoader(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
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

/** Chip colors: clear, or stage white when selected. */
@Composable
fun lightChipColors(): SelectableChipColors {
    val colors = MaterialTheme.colorScheme
    return FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = colors.onSurface,
        iconColor = colors.onSurfaceVariant,
        selectedContainerColor = colors.primary,
        selectedLabelColor = colors.onPrimary,
        selectedLeadingIconColor = colors.onPrimary,
    )
}

@Composable
fun lightChipBorder(selected: Boolean, enabled: Boolean = true): BorderStroke =
    FilterChipDefaults.filterChipBorder(
        enabled = enabled,
        selected = selected,
        borderColor = Color.White.copy(alpha = 0.16f),
        selectedBorderColor = Color.Transparent,
    )
