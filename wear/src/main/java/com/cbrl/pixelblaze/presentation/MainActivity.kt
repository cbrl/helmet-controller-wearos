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
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.ProgressIndicatorDefaults
import androidx.wear.compose.material.RadioButton
import androidx.wear.compose.material.SplitToggleChip
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.cbrl.pixelblaze.presentation.theme.PixelblazeTheme
import io.mhssn.colorpicker.ColorPicker
import io.mhssn.colorpicker.ColorPickerType
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent { NavigationStack() }
    }
}

@Composable
fun NavigationStack(
    deviceViewModel: PixelblazeDeviceViewModel = viewModel(),
    helmetViewModel: HelmetControllerViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val discovery by deviceViewModel.state.collectAsStateWithLifecycle()
    var openedDeviceKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(discovery.rememberedDevice, discovery.devices, discovery.wifiAvailable) {
        val remembered = discovery.rememberedDevice ?: return@LaunchedEffect
        if (!discovery.wifiAvailable) return@LaunchedEffect
        val key = "${remembered.id}@${remembered.address}"
        if (openedDeviceKey == key) return@LaunchedEffect
        val device = discovery.devices[remembered.id]
            ?.takeIf { it.address == remembered.address }
            ?: PixelblazeDevice(remembered.address, remembered.id)
        openedDeviceKey = key
        helmetViewModel.open(device)
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
                    helmetViewModel.open(device)
                    navController.navigate("${Screen.SegmentSelect.route}?device=${device.id}")
                },
            )
        }

        composable(
            route = "${Screen.SegmentSelect.route}?device={device}",
            arguments = listOf(navArgument("device") { type = NavType.IntType }),
        ) { entry ->
            val deviceId = entry.arguments?.getInt("device")
            val currentDevice = deviceId?.let(discovery.devices::get)
                ?: discovery.rememberedDevice
                    ?.takeIf { it.id == deviceId }
                    ?.let { PixelblazeDevice(it.address, it.id) }

            LaunchedEffect(currentDevice?.id, currentDevice?.address) {
                currentDevice?.let(helmetViewModel::open)
            }

            val connection by helmetViewModel.connectionState.collectAsStateWithLifecycle()
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
            val segment = HelmetSegments.fromId(entry.arguments?.getInt("segment") ?: 0)
                ?: HelmetSegments.Visor
            LaunchedEffect(segment) { helmetViewModel.selectSegment(segment) }

            val connection by helmetViewModel.connectionState.collectAsStateWithLifecycle()
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
    state: DeviceDiscoveryState,
    onOpenWifiSettings: () -> Unit,
    onDeviceSelected: (PixelblazeDevice) -> Unit,
) {
    PixelblazeTheme {
        if (state.devices.isNotEmpty()) {
            val scrollState = rememberScalingLazyListState()
            ScalingLazyColumn(state = scrollState) {
                item { Text("Pixelblaze devices", textAlign = TextAlign.Center) }
                items(state.devices.values.sortedBy { it.id }) { device ->
                    Chip(
                        label = { Text(device.id.toUInt().toString(16).uppercase()) },
                        secondaryLabel = { Text(device.address) },
                        onClick = { onDeviceSelected(device) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            StatusScreen(
                message = if (state.wifiAvailable) "Searching for Pixelblaze…" else "Wi-Fi is required",
                detail = state.errorMessage,
                showProgress = state.wifiAvailable,
                actionLabel = if (state.wifiAvailable) null else "Wi-Fi settings",
                onAction = onOpenWifiSettings,
            )
        }
    }
}

@Composable
fun SegmentSelectScreen(
    connectionState: HelmetConnectionState,
    onRetry: () -> Unit,
    onSegmentSelected: (HelmetSegments) -> Unit,
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
            items(HelmetSegments.entries) { segment ->
                Chip(
                    label = { Text(segment.shortName) },
                    secondaryLabel = { Text(if (segment.supports2D) "2D effects" else "LED strip") },
                    onClick = { onSegmentSelected(segment) },
                    enabled = connectionState == HelmetConnectionState.Ready,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun HelmetControllerScreen(controllerView: HelmetControllerViewModel) {
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    var editingColor by rememberSaveable(segment) { mutableStateOf(false) }
    val pageCount = if (segment == HelmetSegments.Visor) 6 else 5
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
fun EffectSelector(controllerView: HelmetControllerViewModel) {
    val state by controllerView.controller.state.collectAsStateWithLifecycle()
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    val effectId = state?.getSegment(segment)?.getState(SegmentVars.Effect)?.toInt() ?: 0
    val scrollState = rememberScalingLazyListState()

    ScalingLazyColumn(state = scrollState) {
        item { Text("${segment.shortName} · Effect", textAlign = TextAlign.Center) }
        items(HelmetEffects.availableFor(segment)) { effect ->
            val select = { controllerView.controller.setSegmentEffect(segment, effect) }
            SplitToggleChip(
                label = { Text(effect.prettyName, textAlign = TextAlign.Center) },
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
fun HelmetBrightnessControl(controllerView: HelmetControllerViewModel) {
    val state by controllerView.controller.state.collectAsStateWithLifecycle()
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    val deviceBrightness = state?.getSegment(segment)?.getState(SegmentVars.Brightness)?.toFloat() ?: 0f
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
        Text("${segment.shortName} · Brightness", modifier = Modifier.align(Alignment.TopCenter))
        Text("%.0f%%\nRotate crown".format(round(brightness * 100)), textAlign = TextAlign.Center)
    }
}

@Composable
fun HelmetColorSummary(controllerView: HelmetControllerViewModel, onEdit: () -> Unit) {
    val state by controllerView.controller.state.collectAsStateWithLifecycle()
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    val segmentState = state?.getSegment(segment)
    val hue = (segmentState?.getState(SegmentVars.Hue)?.toFloat() ?: 0f) * 360f
    val saturation = segmentState?.getState(SegmentVars.Saturation)?.toFloat() ?: 1f
    val color = Color.hsv(hue, saturation, 1f)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Text("${segment.shortName} · Color", modifier = Modifier.align(Alignment.TopCenter))
        Box(Modifier.size(66.dp).background(color, CircleShape))
        CompactChip(
            label = { Text("Edit color") },
            onClick = onEdit,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HelmetColorEditor(controllerView: HelmetControllerViewModel, onClose: () -> Unit) {
    val state by controllerView.controller.state.collectAsStateWithLifecycle()
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    val segmentState = state?.getSegment(segment)
    val hue = (segmentState?.getState(SegmentVars.Hue)?.toFloat() ?: 0f) * 360f
    val saturation = segmentState?.getState(SegmentVars.Saturation)?.toFloat() ?: 1f
    var color by remember(hue, saturation, segment) { mutableStateOf(Color.hsv(hue, saturation, 1f)) }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Choose ${segment.shortName} color")
        ColorPicker(
            type = ColorPickerType.Circle(showBrightnessBar = false, showAlphaBar = false),
            modifier = Modifier.size(130.dp),
        ) { color = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompactChip(label = { Text("Cancel") }, onClick = onClose)
            CompactChip(
                label = { Text("Apply") },
                onClick = {
                    controllerView.controller.setSegmentColor(segment, color)
                    onClose()
                },
            )
        }
    }
}

@Composable
fun WearPresetScreen(controllerView: HelmetControllerViewModel) {
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
            val effect = HelmetEffects.fromId(
                preset.state.getSegment(HelmetSegments.Visor).getState(SegmentVars.Effect).toInt(),
            )?.prettyName ?: "Custom"
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
fun SegmentOptionsControl(controllerView: HelmetControllerViewModel) {
    val state by controllerView.controller.state.collectAsStateWithLifecycle()
    val segment by controllerView.activeSegment.collectAsStateWithLifecycle()
    val segmentState = state?.getSegment(segment)
    var speed by remember(segment, segmentState?.getState(SegmentVars.Speed)) {
        mutableFloatStateOf(segmentState?.getState(SegmentVars.Speed)?.toFloat() ?: 1f)
    }
    var fade by remember(segment, segmentState?.getState(SegmentVars.FTime)) {
        mutableFloatStateOf(segmentState?.getState(SegmentVars.FTime)?.toFloat() ?: 0f)
    }
    val enabled = (segmentState?.getState(SegmentVars.Switch) ?: 1.0) >= 0.5
    val scrollState = rememberScalingLazyListState()
    val haptics = LocalHapticFeedback.current

    ScalingLazyColumn(state = scrollState) {
        item { Text("${segment.shortName} · Options", textAlign = TextAlign.Center) }
        if (segment == HelmetSegments.Visor) {
            item {
                Chip(
                    label = { Text("Emergency blackout") },
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        controllerView.controller.setAllSegmentsEnabled(false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CompactChip(
                    label = { Text("Enable all segments") },
                    onClick = { controllerView.controller.setAllSegmentsEnabled(true) },
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
fun VisorMessageScreen(controllerView: HelmetControllerViewModel) {
    val state by controllerView.controller.state.collectAsStateWithLifecycle()
    val message = state?.getMessage()?.value.orEmpty()
    var userInput by remember(message) { mutableStateOf(message) }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        UserInputBox(text = userInput, onInput = { userInput = MessageState.sanitize(it) })
        CompactChip(
            label = { Text("Set message") },
            onClick = { controllerView.controller.setMessageText(userInput) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
