package com.cbrl.pixelblaze.presentation.segments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.cbrl.pixelblaze.helmet.EffectCategory
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetEffect
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.presentation.components.icon
import com.cbrl.pixelblaze.presentation.components.listItem

/** The effects for a segment, grouped by category. A tap shows the effect on the helmet at once. */
@Composable
fun EffectScreen(viewModel: HelmetViewModel, segment: HelmetSegment) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val selected = state?.get(segment)?.effect
    val ready = connection == HelmetConnectionState.Ready && selected != null
    val groups = remember(segment, layout) {
        val effects = HelmetEffect.availableFor(segment, layout)
        EffectCategory.entries.mapNotNull { category ->
            effects.filter { it.category == category }.takeIf { it.isNotEmpty() }?.let { category to it }
        }
    }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    // Start at the current effect. Item 0 is the header, and each group starts with a sub-header.
    LaunchedEffect(groups) {
        var index = 1
        for ((_, effects) in groups) {
            val position = effects.indexOf(selected)
            if (position >= 0) {
                listState.scrollToItem(index + 1 + position)
                break
            }
            index += 1 + effects.size
        }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(Modifier.listItem(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text("${segment.shortLabel} effect")
                }
            }
            groups.forEach { (category, effects) ->
                item(key = "category-${category.name}") {
                    ListSubHeader(
                        modifier = Modifier.listItem(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(category.icon, contentDescription = null) },
                        label = { Text(category.label) },
                    )
                }
                items(effects, key = { it.id }) { effect ->
                    RadioButton(
                        selected = effect == selected,
                        onSelect = { viewModel.controller.setSegmentEffect(segment, effect) },
                        modifier = Modifier.listItem(this, spec),
                        enabled = ready,
                        transformation = SurfaceTransformation(spec),
                        label = { Text(effect.label) },
                    )
                }
            }
        }
    }
}
