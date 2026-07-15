package com.cbrl.pixelblaze

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cbrl.pixelblaze.presentation.DeviceDiscoveryState
import com.cbrl.pixelblaze.presentation.HelmetConnectionState
import com.cbrl.pixelblaze.presentation.HelmetController
import com.cbrl.pixelblaze.presentation.HelmetControllerViewModel
import com.cbrl.pixelblaze.presentation.HelmetEffects
import com.cbrl.pixelblaze.presentation.HelmetPreset
import com.cbrl.pixelblaze.presentation.HelmetSegments
import com.cbrl.pixelblaze.presentation.HelmetState
import com.cbrl.pixelblaze.presentation.MessageState
import com.cbrl.pixelblaze.presentation.PixelblazeDevice
import com.cbrl.pixelblaze.presentation.PixelblazeDeviceViewModel
import com.cbrl.pixelblaze.presentation.SegmentState
import com.cbrl.pixelblaze.presentation.SegmentVars
import com.cbrl.pixelblaze.ui.theme.PixelblazePhoneTheme
import io.mhssn.colorpicker.ColorPicker
import io.mhssn.colorpicker.ColorPickerType
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PixelblazePhoneTheme { PixelblazePhoneApp() } }
    }
}

private enum class PhoneDestination(val label: String, val icon: ImageVector) {
    Control("Control", Icons.Filled.Tune),
    Effects("Effects", Icons.Filled.AutoAwesome),
    Presets("Presets", Icons.Filled.Bookmarks),
    Devices("Devices", Icons.Filled.Devices),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PixelblazePhoneApp(
    deviceViewModel: PixelblazeDeviceViewModel = viewModel(),
    helmetViewModel: HelmetControllerViewModel = viewModel(),
) {
    val discovery by deviceViewModel.state.collectAsStateWithLifecycle()
    val connection by helmetViewModel.connectionState.collectAsStateWithLifecycle()
    val helmetState by helmetViewModel.controller.state.collectAsStateWithLifecycle()
    val segment by helmetViewModel.activeSegment.collectAsStateWithLifecycle()
    val presets by helmetViewModel.presets.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(PhoneDestination.Control) }
    var openedDevice by remember { mutableStateOf<Pair<Int, String>?>(null) }

    LaunchedEffect(discovery.wifiAvailable, discovery.rememberedDevice) {
        val remembered = discovery.rememberedDevice ?: return@LaunchedEffect
        val key = remembered.id to remembered.address
        if (discovery.wifiAvailable && openedDevice != key) {
            openedDevice = key
            helmetViewModel.open(PixelblazeDevice(remembered.address, remembered.id))
        }
    }

    val background = Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainerLowest),
    )
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HELMET CONTROL", fontWeight = FontWeight.Black)
                        Text(
                            discovery.rememberedDevice?.address ?: "No Pixelblaze selected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = { ConnectionIcon(connection) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                PhoneDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(background).padding(padding)) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "phone destination",
            ) { page ->
                when (page) {
                    PhoneDestination.Control -> ControlScreen(
                        connection = connection,
                        state = helmetState,
                        segment = segment,
                        controller = helmetViewModel.controller,
                        onSegmentSelected = helmetViewModel::selectSegment,
                        onRetry = helmetViewModel::retry,
                        onChooseDevice = { destination = PhoneDestination.Devices },
                    )
                    PhoneDestination.Effects -> EffectsScreen(
                        connection = connection,
                        state = helmetState,
                        segment = segment,
                        controller = helmetViewModel.controller,
                        onSegmentSelected = helmetViewModel::selectSegment,
                    )
                    PhoneDestination.Presets -> PresetsScreen(
                        presets = presets,
                        hasCurrentState = helmetState != null,
                        onSave = helmetViewModel::savePreset,
                        onLoad = helmetViewModel::loadPreset,
                        onRename = helmetViewModel::renamePreset,
                        onUpdate = helmetViewModel::updatePreset,
                        onDelete = helmetViewModel::deletePreset,
                    )
                    PhoneDestination.Devices -> DeviceScreen(
                        discovery = discovery,
                        selected = discovery.rememberedDevice?.let { it.id to it.address },
                        onDeviceSelected = { device ->
                            openedDevice = device.id to device.address
                            helmetViewModel.open(device)
                            destination = PhoneDestination.Control
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionIcon(connection: HelmetConnectionState) {
    val (icon, color, description) = when (connection) {
        HelmetConnectionState.Ready -> Triple(Icons.Filled.BluetoothConnected, MaterialTheme.colorScheme.primary, "Connected")
        HelmetConnectionState.Connecting, is HelmetConnectionState.Reconnecting ->
            Triple(Icons.Filled.Refresh, MaterialTheme.colorScheme.tertiary, "Connecting")
        is HelmetConnectionState.Error, is HelmetConnectionState.MissingPattern ->
            Triple(Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error, "Connection error")
        HelmetConnectionState.Idle -> Triple(Icons.Filled.SettingsRemote, MaterialTheme.colorScheme.outline, "Idle")
    }
    IconButton(onClick = {}) { Icon(icon, contentDescription = description, tint = color) }
}

@Composable
private fun ControlScreen(
    connection: HelmetConnectionState,
    state: HelmetState?,
    segment: HelmetSegments,
    controller: HelmetController,
    onSegmentSelected: (HelmetSegments) -> Unit,
    onRetry: () -> Unit,
    onChooseDevice: () -> Unit,
) {
    val enabled = connection == HelmetConnectionState.Ready
    val segmentState = state?.getSegment(segment)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ConnectionBanner(connection, onRetry, onChooseDevice) }
        item { MasterControls(controller, enabled) }
        item { SegmentPicker(segment, onSegmentSelected) }
        item {
            AnimatedContent(targetState = segment, label = "segment controls") {
                SegmentControlCard(
                    segment = it,
                    state = segmentState,
                    controller = controller,
                    controlsEnabled = enabled,
                )
            }
        }
        item {
            CompanionCard()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ConnectionBanner(
    state: HelmetConnectionState,
    onRetry: () -> Unit,
    onChooseDevice: () -> Unit,
) {
    val title: String
    val detail: String
    val action: String?
    when (state) {
        HelmetConnectionState.Ready -> {
            title = "Live control"
            detail = "Changes are sent directly to the helmet."
            action = null
        }
        HelmetConnectionState.Connecting -> {
            title = "Connecting"
            detail = "Loading the Helmet pattern and its current state…"
            action = null
        }
        is HelmetConnectionState.Reconnecting -> {
            title = "Reconnecting · attempt ${state.attempt}"
            detail = state.reason ?: "The controller will keep trying automatically."
            action = "Retry now"
        }
        is HelmetConnectionState.MissingPattern -> {
            title = "Helmet pattern not found"
            detail = state.message
            action = "Retry"
        }
        is HelmetConnectionState.Error -> {
            title = "Connection needs attention"
            detail = state.message
            action = "Retry"
        }
        HelmetConnectionState.Idle -> {
            title = "Choose a Pixelblaze"
            detail = "Connect this phone to the helmet's Wi-Fi network first."
            action = "Choose device"
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    if (state == HelmetConnectionState.Ready) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
                ),
            )
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            action?.let {
                AssistChip(onClick = if (state == HelmetConnectionState.Idle) onChooseDevice else onRetry, label = { Text(it) })
            }
        }
    }
}

@Composable
private fun MasterControls(controller: HelmetController, enabled: Boolean) {
    val haptics = LocalHapticFeedback.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("MASTER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        controller.setAllSegmentsEnabled(false)
                    },
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Blackout")
                }
                FilledTonalButton(
                    onClick = { controller.setAllSegmentsEnabled(true) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.LightMode, null)
                    Spacer(Modifier.width(8.dp))
                    Text("All on")
                }
            }
            MasterBrightness(enabled, controller::setAllSegmentsBrightness)
        }
    }
}

@Composable
private fun MasterBrightness(enabled: Boolean, onApply: (Float) -> Unit) {
    var brightness by rememberSaveable { mutableFloatStateOf(1f) }
    LabeledSlider(
        label = "Master brightness",
        valueLabel = "${(brightness * 100).roundToInt()}%",
        value = brightness,
        range = 0f..1f,
        enabled = enabled,
        onValueChange = { brightness = it },
        onFinished = { onApply(brightness) },
    )
}

@Composable
private fun SegmentPicker(selected: HelmetSegments, onSelected: (HelmetSegments) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SEGMENT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HelmetSegments.entries) { segment ->
                FilterChip(
                    selected = segment == selected,
                    onClick = { onSelected(segment) },
                    label = { Text(segment.shortName) },
                    leadingIcon = if (segment == selected) {
                        { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun SegmentControlCard(
    segment: HelmetSegments,
    state: SegmentState?,
    controller: HelmetController,
    controlsEnabled: Boolean,
) {
    val segmentEnabled = (state?.getState(SegmentVars.Switch) ?: 0.0) >= 0.5
    val stateHue = state?.getState(SegmentVars.Hue)?.toFloat() ?: 0f
    val stateSaturation = state?.getState(SegmentVars.Saturation)?.toFloat() ?: 1f
    val stateBrightness = state?.getState(SegmentVars.Brightness)?.toFloat() ?: 1f
    val stateSpeed = state?.getState(SegmentVars.Speed)?.toFloat() ?: 1f
    val stateFade = state?.getState(SegmentVars.FTime)?.toFloat() ?: 0f
    var selectedColor by remember(segment, stateHue, stateSaturation) {
        mutableStateOf(Color.hsv(stateHue * 360f, stateSaturation, 1f))
    }
    var brightness by remember(segment, stateBrightness) { mutableFloatStateOf(stateBrightness) }
    var speed by remember(segment, stateSpeed) { mutableFloatStateOf(stateSpeed) }
    var fade by remember(segment, stateFade) { mutableFloatStateOf(stateFade) }
    val preview = selectedColor.copy(alpha = brightness.coerceAtLeast(0.15f))

    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(preview)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(segment.prettyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (segmentEnabled) "Output enabled" else "Output disabled", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = segmentEnabled,
                    onCheckedChange = { controller.setSegmentEnabled(segment, it) },
                    enabled = controlsEnabled,
                )
            }
            HorizontalDivider()
            LabeledSlider("Brightness", "${(brightness * 100).roundToInt()}%", brightness, 0f..1f, controlsEnabled,
                { brightness = it }, { controller.setSegmentBrightness(segment, brightness) })
            PhoneColorSelector(
                color = selectedColor,
                enabled = controlsEnabled,
                onColorChanged = { selectedColor = it },
                onApply = { controller.setSegmentColor(segment, selectedColor) },
            )
            LabeledSlider("Animation speed", String.format(Locale.US, "%.2fx", speed), speed, 0.05f..10f, controlsEnabled,
                { speed = it }, { controller.setSegmentSpeed(segment, speed) })
            LabeledSlider("Transition fade", String.format(Locale.US, "%.2fs", fade), fade, 0f..30f, controlsEnabled,
                { fade = it }, { controller.setSegmentFadeTime(segment, fade) })
            OutlinedButton(
                onClick = { controller.copyAppearanceToAll(segment) },
                enabled = controlsEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copy this look to all segments") }
            AnimatedVisibility(segment == HelmetSegments.Visor) {
                MessageControl(
                    initialMessage = stateForMessage(controller),
                    enabled = controlsEnabled,
                    onApply = controller::setMessageText,
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PhoneColorSelector(
    color: Color,
    enabled: Boolean,
    onColorChanged: (Color) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Color", style = MaterialTheme.typography.titleSmall)
                Text("Drag within the wheel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ColorPicker(
                type = ColorPickerType.Circle(showBrightnessBar = false, showAlphaBar = false),
                modifier = Modifier.size(238.dp),
                onPickedColor = onColorChanged,
            )
        }
        FilledTonalButton(
            onClick = onApply,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply color") }
    }
}

@Composable
private fun stateForMessage(controller: HelmetController): String =
    controller.state.collectAsStateWithLifecycle().value?.getMessage()?.value.orEmpty()

@Composable
private fun MessageControl(initialMessage: String, enabled: Boolean, onApply: (String) -> Unit) {
    var message by remember(initialMessage) { mutableStateOf(initialMessage) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Scrolling message", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = message,
            onValueChange = { message = MessageState.sanitize(it) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("${message.length}/${MessageState.MAX_LENGTH} · printable ASCII") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onApply(message) }),
        )
        FilledTonalButton(onClick = { onApply(message) }, enabled = enabled, modifier = Modifier.align(Alignment.End)) {
            Text("Send message")
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value, onValueChange, Modifier.fillMaxWidth(), enabled, valueRange = range, onValueChangeFinished = onFinished)
    }
}

@Composable
private fun EffectsScreen(
    connection: HelmetConnectionState,
    state: HelmetState?,
    segment: HelmetSegments,
    controller: HelmetController,
    onSegmentSelected: (HelmetSegments) -> Unit,
) {
    val selectedEffect = state?.getSegment(segment)?.getState(SegmentVars.Effect)?.toInt()
    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Box(Modifier.padding(horizontal = 16.dp)) { SegmentPicker(segment, onSegmentSelected) }
        Text(
            "Choose an effect",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(156.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(HelmetEffects.availableFor(segment), key = { it.id }) { effect ->
                EffectCard(
                    effect = effect,
                    selected = selectedEffect == effect.id,
                    enabled = connection == HelmetConnectionState.Ready,
                    onClick = { controller.setSegmentEffect(segment, effect) },
                )
            }
        }
    }
}

@Composable
private fun EffectCard(effect: HelmetEffects, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.height(92.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(Icons.Filled.AutoAwesome, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                if (selected) Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
            }
            Text(effect.prettyName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PresetsScreen(
    presets: List<HelmetPreset>,
    hasCurrentState: Boolean,
    onSave: (String) -> Unit,
    onLoad: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onUpdate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<HelmetPreset?>(null) }
    var updating by remember { mutableStateOf<HelmetPreset?>(null) }
    var deleting by remember { mutableStateOf<HelmetPreset?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Helmet presets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "A preset captures every segment, effect, color and animation setting.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Button(
                onClick = { creating = true },
                enabled = hasCurrentState && presets.size < HelmetPreset.MAX_PRESETS,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Save current helmet as preset")
            }
        }
        if (presets.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(
                        Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Bookmarks, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("No presets yet", fontWeight = FontWeight.Bold)
                        Text("Dial in a look, then save it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(presets, key = { it.id }) { preset ->
            PresetCard(
                preset = preset,
                hasCurrentState = hasCurrentState,
                onLoad = { onLoad(preset.id) },
                onRename = { renaming = preset },
                onUpdate = { updating = preset },
                onDelete = { deleting = preset },
            )
        }
    }

    if (creating) {
        PresetNameDialog(
            title = "Save preset",
            initialName = "",
            confirmLabel = "Save",
            onDismiss = { creating = false },
            onConfirm = {
                onSave(it)
                creating = false
            },
        )
    }
    renaming?.let { preset ->
        PresetNameDialog(
            title = "Rename preset",
            initialName = preset.name,
            confirmLabel = "Rename",
            onDismiss = { renaming = null },
            onConfirm = {
                onRename(preset.id, it)
                renaming = null
            },
        )
    }
    updating?.let { preset ->
        ConfirmationDialog(
            title = "Update ${preset.name}?",
            body = "This replaces every setting in the preset with the helmet's current state.",
            confirmLabel = "Replace",
            onDismiss = { updating = null },
            onConfirm = {
                onUpdate(preset.id)
                updating = null
            },
        )
    }
    deleting?.let { preset ->
        ConfirmationDialog(
            title = "Delete ${preset.name}?",
            body = "This removes the preset from both the phone and paired watch.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = {
                onDelete(preset.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun PresetCard(
    preset: HelmetPreset,
    hasCurrentState: Boolean,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    val visor = preset.state.getSegment(HelmetSegments.Visor)
    val effect = HelmetEffects.fromId(visor.getState(SegmentVars.Effect).toInt())?.prettyName ?: "Unknown effect"
    val activeSegments = HelmetSegments.entries.count {
        preset.state.getSegment(it).getState(SegmentVars.Switch) >= 0.5
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape).background(
                        Color.hsv(
                            visor.getState(SegmentVars.Hue).toFloat() * 360f,
                            visor.getState(SegmentVars.Saturation).toFloat(),
                            visor.getState(SegmentVars.Brightness).toFloat(),
                        ),
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(preset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "$effect · $activeSegments/7 segments on",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, "Rename preset") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete preset", tint = MaterialTheme.colorScheme.error) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLoad, modifier = Modifier.weight(1f)) { Text("Load") }
                OutlinedButton(onClick = onUpdate, enabled = hasCurrentState, modifier = Modifier.weight(1f)) {
                    Text("Update")
                }
            }
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
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(HelmetPreset.MAX_NAME_LENGTH) },
                label = { Text("Preset name") },
                supportingText = { Text("${name.length}/${HelmetPreset.MAX_NAME_LENGTH}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(HelmetPreset.sanitizeName(name)) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(HelmetPreset.sanitizeName(name)) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeviceScreen(
    discovery: DeviceDiscoveryState,
    selected: Pair<Int, String>?,
    onDeviceSelected: (PixelblazeDevice) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Pixelblaze devices", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (discovery.wifiAvailable) "Listening for discovery beacons on Wi-Fi"
                else "Connect to the same Wi-Fi network as the helmet",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!discovery.wifiAvailable) {
            item {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) {
                    Icon(Icons.Filled.Wifi, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Wi-Fi settings")
                }
            }
        }
        discovery.errorMessage?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        items(discovery.devices.values.sortedBy { it.id }, key = { it.id }) { device ->
            val isSelected = selected == (device.id to device.address)
            Card(
                onClick = { onDeviceSelected(device) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SettingsRemote, null, Modifier.size(30.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Pixelblaze ${device.id.toUInt().toString(16).uppercase()}", fontWeight = FontWeight.Bold)
                        Text(device.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isSelected) AssistChip(onClick = {}, label = { Text("Selected") })
                }
            }
        }
        if (discovery.wifiAvailable && discovery.devices.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("Searching…", fontWeight = FontWeight.Bold)
                        Text("Make sure Pixelblaze discovery is enabled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BluetoothConnected, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Wear companion", fontWeight = FontWeight.SemiBold)
                Text(
                    "Device selection and the latest helmet state sync automatically with the paired watch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
