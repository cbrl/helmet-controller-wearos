package com.cbrl.pixelblaze.presentation.presets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetPresets
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.presentation.components.LookDots
import com.cbrl.pixelblaze.presentation.components.listItem
import com.cbrl.pixelblaze.presentation.components.rememberTextInput

/** A tap loads a preset on the helmet. The edge button saves the current look. */
@Composable
fun PresetsScreen(viewModel: HelmetViewModel) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val ready = connection == HelmetConnectionState.Ready && state != null
    val haptics = LocalHapticFeedback.current
    val suggestedName = state?.get(HelmetSegment.Visor)?.effect?.label
    val saveCurrent = rememberTextInput(label = "Preset name", choices = listOfNotNull(suggestedName)) { name ->
        viewModel.savePreset(name)
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    // The list keeps the center item in place. Show the top when a new preset arrives there.
    val newestId = presets.firstOrNull()?.id
    var shownNewestId by remember { mutableStateOf(newestId) }
    LaunchedEffect(newestId) {
        if (newestId != shownNewestId) {
            shownNewestId = newestId
            listState.scrollToItem(0)
        }
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = saveCurrent,
                buttonSize = EdgeButtonSize.Medium,
                enabled = state != null && presets.size < HelmetPresets.MAX_PRESETS,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("Save")
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(Modifier.listItem(this, spec), transformation = SurfaceTransformation(spec)) { Text("PRESETS") }
            }
            if (presets.isEmpty()) {
                item { Hint("No presets yet. Save the current look, or save one on the phone.") }
            } else if (!ready) {
                item { Hint("Connect to the helmet to load a preset.") }
            }
            items(presets, key = { it.id }) { preset ->
                FilledTonalButton(
                    onClick = {
                        viewModel.loadPreset(preset.id)
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    },
                    modifier = Modifier.listItem(this, spec),
                    enabled = ready,
                    transformation = SurfaceTransformation(spec),
                    icon = { LookDots(preset.look) },
                    secondaryLabel = {
                        Text(preset.look[HelmetSegment.Visor].effect.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    label = { Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
