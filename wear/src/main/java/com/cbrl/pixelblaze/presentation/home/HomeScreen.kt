package com.cbrl.pixelblaze.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureAction
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureClickIndicator
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureClickIndicatorState
import androidx.wear.compose.material3.onehandedgesture.oneHandedGesture
import androidx.wear.compose.material3.onehandedgesture.rememberOneHandedGestureConfiguration
import com.cbrl.pixelblaze.device.PixelblazeEndpoint
import com.cbrl.pixelblaze.helmet.HelmetAction
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetEffect
import com.cbrl.pixelblaze.helmet.HelmetPreset
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetState
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.helmet.percentLabel
import com.cbrl.pixelblaze.presentation.Routes
import com.cbrl.pixelblaze.presentation.components.EarPanelMark
import com.cbrl.pixelblaze.presentation.components.LedText
import com.cbrl.pixelblaze.presentation.components.LevelScreen
import com.cbrl.pixelblaze.presentation.components.PyramidLoader
import com.cbrl.pixelblaze.presentation.components.StatusDot
import com.cbrl.pixelblaze.presentation.components.connectionStatus
import com.cbrl.pixelblaze.presentation.components.listItem
import com.cbrl.pixelblaze.presentation.components.rememberTextInput
import com.cbrl.pixelblaze.presentation.components.visorInk
import com.cbrl.pixelblaze.presentation.theme.HelmetText
import com.cbrl.pixelblaze.presentation.theme.Led
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(viewModel: HelmetViewModel, onOpen: (String) -> Unit) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val device by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val waiting by viewModel.waitingAction.collectAsStateWithLifecycle()
    val ready = connection == HelmetConnectionState.Ready && state != null
    val haptics = LocalHapticFeedback.current
    val editMessage = rememberTextInput(label = "Visor message") { viewModel.controller.setMessage(it) }

    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    // The list keeps the center item in place. Show the top when the connection notice comes or goes,
    // but not when the screen comes back from another screen.
    var shownReady by rememberSaveable { mutableStateOf(ready) }
    LaunchedEffect(ready) {
        if (ready != shownReady) {
            shownReady = ready
            listState.scrollToItem(0)
        }
    }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item(key = "header") {
                val connectionStatus = connectionStatus(connection, device != null)
                HomeHeader(
                    state = state,
                    label = listOfNotNull(connectionStatus.label, status?.info?.name ?: device?.displayId)
                        .joinToString(" · "),
                    dot = { StatusDot(connectionStatus.color, pulsing = connectionStatus.busy) },
                    modifier = Modifier.listItem(this, spec),
                )
            }
            waiting?.let { action -> item(key = "waiting") { WaitingNotice(action, presets, Modifier.listItem(this, spec)) } }
            if (!ready) {
                item(key = "notice") { ConnectionNotice(connection, device, Modifier.listItem(this, spec)) }
                if (device != null && connection.canRetry) {
                    item(key = "retry") {
                        FilledTonalButton(
                            onClick = viewModel::retry,
                            modifier = Modifier.listItem(this, spec),
                            transformation = SurfaceTransformation(spec),
                            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                            label = { Text("Retry now") },
                        )
                    }
                }
                item(key = "devices") {
                    NavigationButton(
                        label = if (device == null) "Find helmet" else "Devices",
                        secondary = device?.address,
                        icon = Icons.Rounded.Router,
                        spec = spec,
                        onClick = { onOpen(Routes.DEVICES) },
                        filled = device == null,
                    )
                }
            }
            val current = state
            if (current != null) {
                item(key = "blackout") {
                    BlackoutButton(
                        blackout = current.blackout,
                        enabled = ready,
                        onClick = {
                            haptics.performHapticFeedback(
                                if (current.blackout) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                            viewModel.controller.setBlackout(!current.blackout)
                        },
                        modifier = Modifier.listItem(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
            item(key = "presets") {
                NavigationButton(
                    label = "Presets",
                    secondary = if (presets.isEmpty()) "None saved" else "${presets.size} saved",
                    icon = Icons.Rounded.Bookmarks,
                    spec = spec,
                    onClick = { onOpen(Routes.PRESETS) },
                )
            }
            if (current != null) {
                item(key = "brightness") {
                    NavigationButton(
                        label = "Brightness",
                        secondary = percentLabel(current.master),
                        icon = Icons.Rounded.BrightnessMedium,
                        spec = spec,
                        onClick = { onOpen(Routes.MASTER) },
                        enabled = ready,
                    )
                }
                item(key = "segments") {
                    NavigationButton(
                        label = "Segments",
                        secondary = "Visor: ${current[HelmetSegment.Visor].effect.label}",
                        icon = Icons.Rounded.Tune,
                        spec = spec,
                        onClick = { onOpen(Routes.SEGMENTS) },
                        enabled = ready,
                    )
                }
                item(key = "message") {
                    NavigationButton(
                        label = "Message",
                        secondary = current.message.trim().ifEmpty { "None" },
                        icon = Icons.Rounded.TextFields,
                        spec = spec,
                        onClick = editMessage,
                        enabled = ready,
                    )
                }
                if (ready && current.message.isNotBlank() && current[HelmetSegment.Visor].effect != HelmetEffect.Text) {
                    item(key = "show-message") {
                        CompactButton(
                            onClick = { viewModel.controller.setSegmentEffect(HelmetSegment.Visor, HelmetEffect.Text) },
                            transformation = SurfaceTransformation(spec),
                            label = { Text("Show on visor") },
                        )
                    }
                }
            }
        }
    }
}

private val HelmetConnectionState.canRetry: Boolean
    get() = this is HelmetConnectionState.Reconnecting ||
        this is HelmetConnectionState.MissingPattern ||
        this is HelmetConnectionState.Error

/** The wordmark in LED dots, in the color of the visor, and the connection status. */
@Composable
private fun HomeHeader(state: HelmetState?, label: String, dot: @Composable () -> Unit, modifier: Modifier) {
    Column(modifier.padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val ink = visorInk(state)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EarPanelMark(lit = ink.lit)
            LedText(
                "HELMET",
                style = HelmetText.wordmark,
                color = ink.color,
                brush = ink.brush,
                glow = ink.lit,
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dot()
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConnectionNotice(connection: HelmetConnectionState, device: PixelblazeEndpoint?, modifier: Modifier) {
    val (title, detail) = when {
        device == null -> "No helmet chosen" to "Pick your Pixelblaze to start."
        connection == HelmetConnectionState.Idle -> "Starting…" to null
        connection == HelmetConnectionState.Connecting -> "Connecting…" to device.address
        connection is HelmetConnectionState.Reconnecting -> "Reconnecting…" to connection.reason
        connection is HelmetConnectionState.MissingPattern -> "Pattern missing" to connection.message
        connection is HelmetConnectionState.Error -> "Can't control the helmet" to connection.message
        else -> "Reading the helmet…" to null
    }
    val busy = device != null && (
        connection == HelmetConnectionState.Idle ||
            connection == HelmetConnectionState.Connecting ||
            connection is HelmetConnectionState.Reconnecting
        )
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (busy) PyramidLoader(Led.Amber, Modifier.padding(bottom = 6.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        detail?.takeIf(String::isNotBlank)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Shows the tile action that waits for the helmet. */
@Composable
private fun WaitingNotice(action: HelmetAction, presets: List<HelmetPreset>, modifier: Modifier) {
    val name = when (action) {
        is HelmetAction.SetBlackout -> if (action.enabled) "Blackout" else "Lights on"
        is HelmetAction.LoadPreset -> presets.firstOrNull { it.id == action.id }?.name ?: "Preset"
    }
    Row(
        modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        PyramidLoader(Led.Amber, size = 20.dp)
        Text(
            "$name when connected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 2,
        )
    }
}

/**
 * The main control: fades every LED out, or back in. The segment state stays the same. While the
 * helmet is lit, the button is black with a red ring; while it is dark, it is white. The one-handed
 * gesture of the watch (for example a double pinch) also presses it.
 */
@Composable
private fun BlackoutButton(
    blackout: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val gesture = rememberOneHandedGestureConfiguration(action = OneHandedGestureAction.Primary)
    val indicator = remember { OneHandedGestureClickIndicatorState() }
    val scope = rememberCoroutineScope()
    val title = if (blackout) "Lights on" else "Blackout"
    val buttonModifier = modifier.heightIn(min = 64.dp).then(
        if (enabled) {
            Modifier.oneHandedGesture(
                gestureConfiguration = gesture,
                onGestureLabel = title,
                interactionSource = interactionSource,
                onGestureAvailable = { scope.launch { indicator.showIndicator() } },
                onGesture = onClick,
            )
        } else {
            Modifier
        },
    )
    val icon: @Composable () -> Unit = {
        Icon(
            if (blackout) Icons.Rounded.LightMode else Icons.Rounded.PowerSettingsNew,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.LargeIconSize),
        )
    }
    val label: @Composable RowScope.() -> Unit = {
        OneHandedGestureClickIndicator(gesture, indicator) { Text(title) }
    }
    if (blackout) {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            transformation = transformation,
            icon = { icon() },
            secondaryLabel = { Text("The helmet is dark") },
            label = label,
        )
    } else {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = colors.onSurface,
                secondaryContentColor = colors.onSurfaceVariant,
                iconColor = Led.Red,
            ),
            border = BorderStroke(1.5.dp, Led.Red),
            interactionSource = interactionSource,
            transformation = transformation,
            icon = { icon() },
            secondaryLabel = { Text("Fade all lights out") },
            label = label,
        )
    }
}

@Composable
private fun TransformingLazyColumnItemScope.NavigationButton(
    label: String,
    secondary: String?,
    icon: ImageVector,
    spec: TransformationSpec,
    onClick: () -> Unit,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val secondaryLabel: (@Composable RowScope.() -> Unit)? = secondary?.let {
        { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    if (filled) {
        Button(
            onClick = onClick,
            modifier = Modifier.listItem(this, spec),
            enabled = enabled,
            transformation = SurfaceTransformation(spec),
            icon = { Icon(icon, contentDescription = null) },
            secondaryLabel = secondaryLabel,
            label = content,
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier.listItem(this, spec),
            enabled = enabled,
            transformation = SurfaceTransformation(spec),
            icon = { Icon(icon, contentDescription = null) },
            secondaryLabel = secondaryLabel,
            label = content,
        )
    }
}

@Composable
fun MasterBrightnessScreen(viewModel: HelmetViewModel) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LevelScreen(
        title = "Master brightness",
        level = state?.master ?: 0f,
        enabled = connection == HelmetConnectionState.Ready && state != null,
        onLevelChange = viewModel.controller::setMaster,
        currentLevel = { viewModel.controller.state.value?.master ?: 0f },
    )
}
