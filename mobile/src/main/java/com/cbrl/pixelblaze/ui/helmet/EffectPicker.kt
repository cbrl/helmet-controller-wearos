package com.cbrl.pixelblaze.ui.helmet

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cbrl.pixelblaze.helmet.ColorUse
import com.cbrl.pixelblaze.helmet.EffectCategory
import com.cbrl.pixelblaze.helmet.HelmetEffect
import com.cbrl.pixelblaze.ui.components.SectionLabel
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
            "Choose an effect",
            style = MaterialTheme.typography.titleLarge,
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
                        Icon(category.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        SectionLabel(category.label)
                    }
                }
                items(members, key = { it.id }) { effect ->
                    EffectTile(
                        effect = effect,
                        selected = effect == selected,
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
private fun EffectTile(effect: HelmetEffect, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.fillMaxWidth().height(88.dp).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    effect.category.icon,
                    contentDescription = null,
                    tint = if (selected) colors.primary else colors.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.weight(1f))
                if (effect.colorUse == ColorUse.Full) {
                    Icon(Icons.Filled.Palette, "Uses the segment color", Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                }
                if (effect.usesSpeed) {
                    Icon(Icons.Filled.Speed, "Uses the speed", Modifier.padding(start = 4.dp).size(16.dp), tint = colors.onSurfaceVariant)
                }
                if (selected) {
                    Icon(Icons.Filled.Check, "Selected", Modifier.padding(start = 4.dp).size(18.dp), tint = colors.primary)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                effect.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
