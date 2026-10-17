package com.cbrl.pixelblaze.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetLayout
import com.cbrl.pixelblaze.helmet.HelmetPreset
import com.cbrl.pixelblaze.helmet.HelmetPresets
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.ui.components.SectionShape
import com.cbrl.pixelblaze.ui.components.showMessage
import com.cbrl.pixelblaze.ui.helmet.HelmetDiagram
import com.cbrl.pixelblaze.ui.theme.DiagramPanel

@Composable
fun PresetsScreen(viewModel: HelmetViewModel, snackbar: SnackbarHostState) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val ready = connection == HelmetConnectionState.Ready
    val scope = rememberCoroutineScope()

    var creating by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var updatingId by rememberSaveable { mutableStateOf<String?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Presets", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${presets.size} of ${HelmetPresets.MAX_PRESETS} · synced with the watch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = { creating = true },
                    enabled = state != null && presets.size < HelmetPresets.MAX_PRESETS,
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Text("Save look", Modifier.padding(start = 8.dp))
                }
            }
        }
        if (presets.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyPresets() }
        } else if (!ready) {
            item(key = "offline", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Connect to the helmet to show a preset.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        items(presets, key = { it.id }) { preset ->
            PresetCard(
                preset = preset,
                canUpdate = state != null && ready,
                onLoad = {
                    if (!ready) {
                        scope.showMessage(snackbar, "Connect to the helmet to show a preset.")
                    } else {
                        val before = state?.look
                        viewModel.loadPreset(preset.id)
                        scope.showMessage(snackbar, "Showing ${preset.name}", before?.let { { viewModel.controller.applyLook(it) } })
                    }
                },
                onRename = { renamingId = preset.id },
                onUpdate = { updatingId = preset.id },
                onDelete = {
                    viewModel.deletePreset(preset.id)
                    scope.showMessage(snackbar, "Deleted ${preset.name}") { viewModel.restorePreset(preset.id) }
                },
            )
        }
    }

    if (creating) {
        PresetNameDialog(
            title = "Save preset",
            initialName = state?.get(HelmetSegment.Visor)?.effect?.label.orEmpty(),
            confirmLabel = "Save",
            onDismiss = { creating = false },
            onConfirm = { name ->
                viewModel.savePreset(name)
                creating = false
                scope.showMessage(snackbar, "Saved ${HelmetPresets.sanitizeName(name)}")
            },
        )
    }
    presets.firstOrNull { it.id == renamingId }?.let { preset ->
        PresetNameDialog(
            title = "Rename preset",
            initialName = preset.name,
            confirmLabel = "Rename",
            onDismiss = { renamingId = null },
            onConfirm = { name ->
                viewModel.renamePreset(preset.id, name)
                renamingId = null
            },
        )
    }
    presets.firstOrNull { it.id == updatingId }?.let { preset ->
        AlertDialog(
            onDismissRequest = { updatingId = null },
            icon = { Icon(Icons.Filled.Update, null) },
            title = { Text("Update ${preset.name}?") },
            text = { Text("The preset gets the current look of the helmet. Its old look is replaced.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updatePreset(preset.id)
                    updatingId = null
                    scope.showMessage(snackbar, "Updated ${preset.name}")
                }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { updatingId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PresetCard(
    preset: HelmetPreset,
    canUpdate: Boolean,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val look = preset.look
    val onCount = HelmetSegment.entries.count { look[it].on }
    val summary = look[HelmetSegment.Visor].effect.label +
        if (onCount < HelmetSegment.entries.size) " · $onCount/${HelmetSegment.entries.size} on" else ""

    Card(
        onClick = onLoad,
        shape = SectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                .background(DiagramPanel, RoundedCornerShape(16.dp))
                .padding(horizontal = 6.dp, vertical = 10.dp),
        ) {
            HelmetDiagram(
                layout = HelmetLayout.Default,
                look = look,
                modifier = Modifier.fillMaxWidth(),
                glow = false,
                dotRadius = 0.42f,
            )
        }
        Row(Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Actions for ${preset.name}") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Update from helmet") },
                        leadingIcon = { Icon(Icons.Filled.Update, null) },
                        enabled = canUpdate,
                        onClick = {
                            menuOpen = false
                            onUpdate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPresets() {
    Card(
        shape = SectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Bookmarks, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No presets yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Make a look on the Helmet tab, then save it here. A preset stores every segment and the message.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PresetNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) {
        mutableStateOf(TextFieldValue(initialName, selection = TextRange(0, initialName.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val confirm = { onConfirm(HelmetPresets.sanitizeName(name.text)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.copy(text = it.text.take(HelmetPresets.MAX_NAME_LENGTH)) },
                label = { Text("Name") },
                supportingText = { Text("${name.text.length}/${HelmetPresets.MAX_NAME_LENGTH}") },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        },
        confirmButton = { TextButton(onClick = confirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
