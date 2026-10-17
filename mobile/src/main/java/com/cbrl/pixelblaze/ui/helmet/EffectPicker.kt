package com.cbrl.pixelblaze.ui.helmet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cbrl.pixelblaze.helmet.ColorUse
import com.cbrl.pixelblaze.helmet.EffectCategory
import com.cbrl.pixelblaze.helmet.HelmetEffect
import com.cbrl.pixelblaze.ui.components.SectionLabel
import com.cbrl.pixelblaze.ui.components.panel
import com.cbrl.pixelblaze.ui.theme.LedOff
import kotlinx.coroutines.launch

val EffectCategory.icon: ImageVector
    get() = when (this) {
        EffectCategory.Solid -> Icons.Filled.Lightbulb
        EffectCategory.Motion -> Icons.Filled.Waves
        EffectCategory.Sparkle -> Icons.Filled.AutoAwesome
        EffectCategory.Rainbow -> Icons.Filled.Gradient
        EffectCategory.Matrix -> Icons.Filled.GridOn
    }

/** A bottom sheet with the [effects] in groups by category. The sheet closes after a choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectPickerSheet(
    effects: List<HelmetEffect>,
    selected: HelmetEffect,
    color: Color,
    onSelect: (HelmetEffect) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val groups = effects.groupBy { it.category }.toSortedMap(compareBy { it.ordinal })

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            "EFFECTS",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            groups.forEach { (category, members) ->
                item(key = "header-${category.name}", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.padding(top = 12.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(category.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        SectionLabel(category.label, Modifier.weight(1f), line = true)
                    }
                }
                items(members, key = { it.id }) { effect ->
                    EffectTile(
                        effect = effect,
                        selected = effect == selected,
                        color = color,
                        onClick = {
                            onSelect(effect)
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) onDismiss()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectTile(effect: HelmetEffect, selected: Boolean, color: Color, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val content = colors.onSurface
    val detail = colors.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.Transparent,
        contentColor = content,
        modifier = Modifier.semantics { this.selected = selected },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .panel(shape)
                .then(if (selected) Modifier.border(1.5.dp, colors.primary, shape) else Modifier)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(effect.category.icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                if (effect.colorUse == ColorUse.Full) {
                    Icon(Icons.Filled.Palette, "Uses the segment color", Modifier.size(16.dp), tint = detail)
                }
                if (effect.usesSpeed) {
                    Icon(Icons.Filled.Speed, "Uses the speed", Modifier.padding(start = 4.dp).size(16.dp), tint = detail)
                }
                if (selected) {
                    Icon(Icons.Filled.Check, "Selected", Modifier.padding(start = 4.dp).size(18.dp), tint = content)
                }
            }
            Spacer(Modifier.weight(1f))
            EffectStrip(effect, color, Modifier.padding(bottom = 8.dp))
            Text(
                effect.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val STRIP_LEDS = 12

/**
 * A short row of LEDs that hints at the effect: a solid bar, a moving light with a trail, sparkles,
 * a rainbow, or a matrix. Effects that use the segment color show [color]; the others show hues.
 */
@Composable
private fun EffectStrip(effect: HelmetEffect, color: Color, modifier: Modifier = Modifier) {
    val levels = remember(effect) { stripLevels(effect) }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .background(Color.Black, CircleShape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        val gap = 2.dp.toPx()
        val width = (size.width - gap * (STRIP_LEDS - 1)) / STRIP_LEDS
        val corner = CornerRadius(size.height / 2f)
        levels.forEachIndexed { index, level ->
            val position = index / (STRIP_LEDS - 1f)
            val lit = when (effect.colorUse) {
                ColorUse.Full -> color
                ColorUse.SaturationOnly, ColorUse.None -> Color.hsv(position * 330f, 1f, 1f)
            }
            drawRoundRect(
                lerp(LedOff, lit, level),
                topLeft = Offset(index * (width + gap), 0f),
                size = Size(width, size.height),
                cornerRadius = corner,
            )
        }
    }
}

private fun stripLevels(effect: HelmetEffect): List<Float> {
    val shift = effect.ordinal % 5
    return List(STRIP_LEDS) { index ->
        when {
            effect == HelmetEffect.Breathe -> 0.45f
            effect == HelmetEffect.Strobe -> if (index % 2 == 0) 1f else 0.85f
            effect == HelmetEffect.SpringyTheater -> if (index % 3 == 0) 1f else 0f
            effect == HelmetEffect.ChaserDown -> {
                // The trail of Chaser Up, mirrored.
                val distance = (index - 5 + STRIP_LEDS) % STRIP_LEDS
                if (distance < 5) 1f - distance * 0.22f else 0f
            }
            else -> categoryLevel(effect.category, index, shift)
        }
    }
}

private fun categoryLevel(category: EffectCategory, index: Int, shift: Int): Float =
    when (category) {
        EffectCategory.Solid -> 1f
        EffectCategory.Rainbow -> 1f
        EffectCategory.Motion -> {
            // A bright head with a fading trail.
            val distance = (shift + 6 - index + STRIP_LEDS) % STRIP_LEDS
            if (distance < 5) 1f - distance * 0.22f else 0f
        }
        EffectCategory.Sparkle -> if ((index * 7 + shift * 3) % 5 == 0) 1f else if ((index + shift) % 4 == 0) 0.3f else 0f
        EffectCategory.Matrix -> if ((index + shift) % 3 == 0) 0.15f else 1f
    }
