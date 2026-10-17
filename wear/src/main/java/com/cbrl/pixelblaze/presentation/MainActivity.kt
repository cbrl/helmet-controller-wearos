package com.cbrl.pixelblaze.presentation

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.ProgressIndicatorDefaults
import androidx.wear.compose.material.RadioButton
import androidx.wear.compose.material.SplitToggleChip
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.cbrl.pixelblaze.device.PixelblazeEndpoint
import com.cbrl.pixelblaze.helmet.DiscoveryState
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetEffect
import com.cbrl.pixelblaze.helmet.HelmetLayout
import com.cbrl.pixelblaze.helmet.HelmetMessage
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.presentation.theme.PixelblazeTheme
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent { NavigationStack() }
    }
}

@Composable
fun NavigationStack(helmetViewModel: HelmetViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val discovery by helmetViewModel.discovery.collectAsStateWithLifecycle()
    val selectedDevice by helmetViewModel.selectedDevice.collectAsStateWithLifecycle()
    var openedDeviceKey by rememberSaveable { mutableStateOf<String?>(null) }

    // The repository connects to the selected device. Show its segments once for each selection.
    LaunchedEffect(selectedDevice) {
        val device = selectedDevice ?: return@LaunchedEffect
        val key = "${device.id}@${device.address}"
        if (openedDeviceKey == key) return@LaunchedEffect
        openedDeviceKey = key
        navController.navigate("${Screen.SegmentSelect.route}?device=${device.id}")
    }

    NavHost(navController = navController, startDestination = Screen.DeviceList.route) {
        composable(Screen.DeviceList.route) {
            DeviceListScreen(
                state = discovery,
                onOpenWifiSettings = {
                    context.startActivity(
                        Intent("com.google.android.clockwork.settings.connectivity.wifi.ADD_NETWORK_SETTINGS"),
                    )
                },
                onDeviceSelected = { device ->
                    openedDeviceKey = "${device.id}@${device.address}"
                    helmetViewModel.selectDevice(device)
                    navController.navigate("${Screen.SegmentSelect.route}?device=${device.id}")
                },
            )
        }

        composable(
            route = "${Screen.SegmentSelect.route}?device={device}",
            arguments = listOf(navArgument("device") { type = NavType.IntType }),
        ) {
            val connection by helmetViewModel.connection.collectAsStateWithLifecycle()
            SegmentSelectScreen(
                connectionState = connection,
                onRetry = helmetViewModel::retry,
                onSegmentSelected = { segment ->
                    helmetViewModel.selectSegment(segment)
                    navController.navigate("${Screen.HelmetController.route}?segment=${segment.id}")
                },
            )
        }

        composable(
            route = "${Screen.HelmetController.route}?segment={segment}",
            arguments = listOf(navArgument("segment") { type = NavType.IntType }),
        ) { entry ->
            val segment = HelmetSegment.fromId(entry.arguments?.getInt("segment") ?: 0)
                ?: HelmetSegment.Visor
            LaunchedEffect(segment) { helmetViewModel.selectSegment(segment) }

            val connection by helmetViewModel.connection.collectAsStateWithLifecycle()
            ConnectionContent(connection, helmetViewModel::retry) {
                HelmetControllerScreen(helmetViewModel)
            }
        }
    }
}

@Composable
private fun ConnectionContent(
    connectionState: HelmetConnectionState,
    onRetry: () -> Unit,
    readyContent: @Composable () -> Unit,
) {
    when (connectionState) {
        HelmetConnectionState.Ready -> readyContent()
        HelmetConnectionState.Idle,
        HelmetConnectionState.Connecting,
        -> StatusScreen("Connecting…", showProgress = true)
        is HelmetConnectionState.Reconnecting -> StatusScreen(
            "Reconnecting (${connectionState.attempt})…",
            detail = connectionState.reason,
            showProgress = true,
        )
        is HelmetConnectionState.MissingPattern -> StatusScreen(
            connectionState.message,
            actionLabel = "Retry",
            onAction = onRetry,
        )
        is HelmetConnectionState.Error -> StatusScreen(
            connectionState.message,
            actionLabel = "Retry",
            onAction = onRetry,
        )
    }
}

@Composable
private fun StatusScreen(
    message: String,
    detail: String? = null,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    PixelblazeTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showProgress) CircularProgressIndicator()
            Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            detail?.takeIf(String::isNotBlank)?.let {
                Text(it, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
            actionLabel?.let {
                CompactChip(label = { Text(it) }, onClick = onAction)
            }
        }
    }
}

@Composable
fun DeviceListScreen(
    state: DiscoveryState,
    onOpenWifiSettings: () -> Unit,
    onDeviceSelected: (PixelblazeEndpoint) -> Unit,
) {
    PixelblazeTheme {
        if (state.devices.isNotEmpty()) {
            val scrollState = rememberScalingLazyListState()
            ScalingLazyColumn(state = scrollState) {
                item { Text("Pixelblaze devices", textAlign = TextAlign.Center) }
                items(state.devices.values.sortedBy { it.id }) { device ->
                    Chip(
                        label = { Text(device.displayId) },
                        secondaryLabel = { Text(device.address) },
                        onClick = { onDeviceSelected(device) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            StatusScreen(
                message = if (state.wifiConnected) "Searching for Pixelblaze…" else "Wi-Fi is required",
                detail = state.errorMessage,
                showProgress = state.wifiConnected,
                actionLabel = if (state.wifiConnected) null else "Wi-Fi settings",
                onAction = onOpenWifiSettings,
            )
        }
    }
}

@Composable
fun SegmentSelectScreen(
    connectionState: HelmetConnectionState,
    onRetry: () -> Unit,
    onSegmentSelected: (HelmetSegment) -> Unit,
) {
    PixelblazeTheme {
        val scrollState = rememberScalingLazyListState()
        ScalingLazyColumn(state = scrollState) {
            item {
                when (connectionState) {
                    HelmetConnectionState.Ready -> Text("Choose a segment")
                    HelmetConnectionState.Connecting -> Text("Connecting…")
                    is HelmetConnectionState.Reconnecting -> Text("Reconnecting…")
                    is HelmetConnectionState.MissingPattern -> CompactChip(
                        label = { Text("Helmet pattern missing") },
                        onClick = onRetry,
                    )
                    is HelmetConnectionState.Error -> CompactChip(
                        label = { Text("Retry connection") },
                        onClick = onRetry,
                    )
                    HelmetConnectionState.Idle -> Text("Waiting for device…")
                }
            }
            items(HelmetSegment.entries) { segment ->
                Chip(
                    label = { Text(segment.shortLabel) },
                    secondaryLabel = { Text(if (HelmetLayout.Default[segment].is2D) "2D effects" else "LED strip") },
                    onClick = { onSegmentSelected(segment) },
                    enabled = connectionState == HelmetConnectionState.Ready,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun HelmetControllerScreen(controllerView: HelmetViewModel) {
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    var editingColor by rememberSaveable(segment) { mutableStateOf(false) }
    val pageCount = if (segment == HelmetSegment.Visor) 6 else 5
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val pageIndicatorState = remember(pagerState) {
        object : PageIndicatorState {
            override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
            override val selectedPage: Int get() = pagerState.currentPage
            override val pageCount: Int get() = pagerState.pageCount
        }
    }

    PixelblazeTheme {
        if (editingColor) {
            BackHandler { editingColor = false }
            HelmetColorEditor(controllerView, onClose = { editingColor = false })
        } else {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> EffectSelector(controllerView)
                    1 -> HelmetBrightnessControl(controllerView)
                    2 -> HelmetColorSummary(controllerView, onEdit = { editingColor = true })
                    3 -> SegmentOptionsControl(controllerView)
                    4 -> WearPresetScreen(controllerView)
                    5 -> VisorMessageScreen(controllerView)
                }
            }
            HorizontalPageIndicator(pageIndicatorState = pageIndicatorState)
        }
    }
}

@Composable
fun EffectSelector(controllerView: HelmetViewModel) {
    val state by controllerView.state.collectAsStateWithLifecycle()
    val layout by controllerView.layout.collectAsStateWithLifecycle()
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    val effectId = state?.get(segment)?.effect?.id ?: 0
    val scrollState = rememberScalingLazyListState()

    ScalingLazyColumn(state = scrollState) {
        item { Text("${segment.shortLabel} · Effect", textAlign = TextAlign.Center) }
        items(HelmetEffect.availableFor(segment, layout)) { effect ->
            val select = { controllerView.controller.setSegmentEffect(segment, effect) }
            SplitToggleChip(
                label = { Text(effect.label, textAlign = TextAlign.Center) },
                onClick = select,
                modifier = Modifier.fillMaxWidth(),
                checked = effectId == effect.id,
                toggleControl = { RadioButton(selected = effectId == effect.id) },
                onCheckedChange = { select() },
            )
        }
    }
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun HelmetBrightnessControl(controllerView: HelmetViewModel) {
    val state by controllerView.state.collectAsStateWithLifecycle()
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    val deviceBrightness = state?.get(segment)?.brightness ?: 0f
    var brightness by remember(deviceBrightness, segment) { mutableFloatStateOf(deviceBrightness) }
    var brightnessChanged by remember(segment) { mutableStateOf(false) }
    val animatedBrightness by animateFloatAsState(
        targetValue = brightness,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "brightness",
    )
    LaunchedEffect(brightness, brightnessChanged) {
        if (brightnessChanged) {
            delay(180L)
            controllerView.controller.setSegmentBrightness(segment, brightness)
            brightnessChanged = false
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent {
                brightness = when {
                    it.verticalScrollPixels > 0 -> min(brightness + 0.05f, 1f)
                    it.verticalScrollPixels < 0 -> max(brightness - 0.05f, 0f)
                    else -> brightness
                }
                brightnessChanged = true
                true
            }
            .requestFocusOnHierarchyActive()
            .focusable(),
    ) {
        CircularProgressIndicator(
            progress = animatedBrightness,
            modifier = Modifier.fillMaxSize().padding(1.dp),
            strokeWidth = 5.dp,
            startAngle = 290f,
            endAngle = 250f,
        )
        Text("${segment.shortLabel} · Brightness", modifier = Modifier.align(Alignment.TopCenter))
        Text("%.0f%%\nRotate crown".format(round(brightness * 100)), textAlign = TextAlign.Center)
    }
}

@Composable
fun HelmetColorSummary(controllerView: HelmetViewModel, onEdit: () -> Unit) {
    val state by controllerView.state.collectAsStateWithLifecycle()
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    val segmentState = state?.get(segment)
    val hue = (segmentState?.hue ?: 0f) * 360f
    val saturation = segmentState?.saturation ?: 1f
    val color = Color.hsv(hue, saturation, 1f)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Text("${segment.shortLabel} · Color", modifier = Modifier.align(Alignment.TopCenter))
        Box(Modifier.size(66.dp).background(color, CircleShape))
        CompactChip(
            label = { Text("Edit color") },
            onClick = onEdit,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun HelmetColorEditor(controllerView: HelmetViewModel, onClose: () -> Unit) {
    val state by controllerView.state.collectAsStateWithLifecycle()
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    val segmentState = state?.get(segment)
    val hue = (segmentState?.hue ?: 0f) * 360f
    val saturation = segmentState?.saturation ?: 1f
    var selectedHue by remember(hue, segment) { mutableFloatStateOf(hue / 360f) }
    var selectedSaturation by remember(saturation, segment) { mutableFloatStateOf(saturation) }
    val color = Color.hsv(selectedHue * 360f, selectedSaturation, 1f)
    val scrollState = rememberScalingLazyListState()

    ScalingLazyColumn(state = scrollState) {
        item { Text("Choose ${segment.shortLabel} color", textAlign = TextAlign.Center) }
        item { Box(Modifier.size(58.dp).background(color, CircleShape)) }
        item { Text("Hue ${(selectedHue * 360f).roundToInt()}°") }
        item {
            InlineSlider(
                value = selectedHue,
                onValueChange = { selectedHue = it },
                steps = 35,
                valueRange = 0f..1f,
                segmented = false,
                decreaseIcon = { Icon(Icons.Filled.Remove, "Decrease hue") },
                increaseIcon = { Icon(Icons.Filled.Add, "Increase hue") },
            )
        }
        item { Text("Saturation ${(selectedSaturation * 100f).roundToInt()}%") }
        item {
            InlineSlider(
                value = selectedSaturation,
                onValueChange = { selectedSaturation = it },
                steps = 19,
                valueRange = 0f..1f,
                segmented = false,
                decreaseIcon = { Icon(Icons.Filled.Remove, "Decrease saturation") },
                increaseIcon = { Icon(Icons.Filled.Add, "Increase saturation") },
            )
        }
        item {
            CompactChip(
                label = { Text("Full saturation") },
                onClick = { selectedSaturation = 1f },
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactChip(label = { Text("Cancel") }, onClick = onClose)
                CompactChip(
                    label = { Text("Apply") },
                    onClick = {
                        controllerView.controller.setSegmentColor(segment, selectedHue, selectedSaturation)
                        onClose()
                    },
                )
            }
        }
    }
}

@Composable
fun WearPresetScreen(controllerView: HelmetViewModel) {
    val presets by controllerView.presets.collectAsStateWithLifecycle()
    val scrollState = rememberScalingLazyListState()

    ScalingLazyColumn(state = scrollState) {
        item { Text("Full helmet presets", textAlign = TextAlign.Center) }
        item {
            UserInputBox(
                text = "Save current",
                inputLabel = "Preset name",
                onInput = controllerView::savePreset,
            )
        }
        if (presets.isEmpty()) {
            item { Text("No presets saved", textAlign = TextAlign.Center) }
        }
        items(presets) { preset ->
            val effect = preset.look[HelmetSegment.Visor].effect.label
            Chip(
                label = { Text(preset.name) },
                secondaryLabel = { Text("Load · $effect") },
                onClick = { controllerView.loadPreset(preset.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SegmentOptionsControl(controllerView: HelmetViewModel) {
    val state by controllerView.state.collectAsStateWithLifecycle()
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    val segmentState = state?.get(segment)
    var speed by remember(segment, segmentState?.speed) { mutableFloatStateOf(segmentState?.speed ?: 1f) }
    var fade by remember(segment, segmentState?.fadeSeconds) { mutableFloatStateOf(segmentState?.fadeSeconds ?: 0f) }
    val enabled = segmentState?.on ?: true
    val scrollState = rememberScalingLazyListState()
    val haptics = LocalHapticFeedback.current

    ScalingLazyColumn(state = scrollState) {
        item { Text("${segment.shortLabel} · Options", textAlign = TextAlign.Center) }
        if (segment == HelmetSegment.Visor) {
            item {
                Chip(
                    label = { Text("Emergency blackout") },
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        controllerView.controller.setBlackout(true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CompactChip(
                    label = { Text("Lights on") },
                    onClick = { controllerView.controller.setBlackout(false) },
                )
            }
        }
        item {
            Chip(
                label = { Text(if (enabled) "Segment on" else "Segment off") },
                onClick = { controllerView.controller.setSegmentEnabled(segment, !enabled) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            ValueStepper("Speed", speed, 0.05f, 10f, 0.1f) {
                speed = it
                controllerView.controller.setSegmentSpeed(segment, it)
            }
        }
        item {
            ValueStepper("Fade seconds", fade, 0f, 30f, 0.25f) {
                fade = it
                controllerView.controller.setSegmentFadeTime(segment, it)
            }
        }
        item {
            CompactChip(
                label = { Text("Copy look to all") },
                onClick = { controllerView.controller.copyAppearanceToAll(segment) },
            )
        }
    }
}

@Composable
private fun ValueStepper(
    label: String,
    value: Float,
    minimum: Float,
    maximum: Float,
    step: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CompactButton(onClick = { onValueChange(max(minimum, value - step)) }) { Text("−") }
        Text("$label\n%.2f".format(value), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        CompactButton(onClick = { onValueChange(min(maximum, value + step)) }) { Text("+") }
    }
}

@Composable
fun UserInputBox(
    modifier: Modifier = Modifier,
    text: String = "",
    inputLabel: String = "Helmet message",
    onInput: (String) -> Unit,
) {
    val inputTextKey = "input_text"
    val remoteInputs = listOf(
        RemoteInput.Builder(inputTextKey)
            .setLabel(inputLabel)
            .wearableExtender {
                setEmojisAllowed(false)
                setInputActionType(EditorInfo.IME_ACTION_DONE)
            }
            .build(),
    )
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val data = it.data ?: return@rememberLauncherForActivityResult
        val results = RemoteInput.getResultsFromIntent(data) ?: return@rememberLauncherForActivityResult
        onInput(results.getCharSequence(inputTextKey)?.toString().orEmpty())
    }
    val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent().also {
        RemoteInputIntentHelper.putRemoteInputsExtra(it, remoteInputs)
    }

    Row(modifier = modifier.fillMaxWidth(0.8f), verticalAlignment = Alignment.CenterVertically) {
        Text(text.ifEmpty { "No message" }, Modifier.weight(1f), textAlign = TextAlign.Center)
        CompactButton(onClick = { launcher.launch(intent) }) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit helmet message")
        }
    }
}

@Composable
fun VisorMessageScreen(controllerView: HelmetViewModel) {
    val state by controllerView.state.collectAsStateWithLifecycle()
    val message = state?.message.orEmpty()
    var userInput by remember(message) { mutableStateOf(message) }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        UserInputBox(text = userInput, onInput = { userInput = HelmetMessage.sanitize(it) })
        CompactChip(
            label = { Text("Set message") },
            onClick = { controllerView.controller.setMessage(userInput) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
