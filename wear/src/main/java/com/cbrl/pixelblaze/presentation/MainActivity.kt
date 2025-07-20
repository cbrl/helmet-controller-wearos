// https://newsletter.jorgecastillo.dev/p/jetpack-compose-effect-handlers
// https://developer.android.com/design/ui/wear/guides/components/buttons
// https://foso.github.io/Jetpack-Compose-Playground/

package com.cbrl.pixelblaze.presentation

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
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
import androidx.wear.compose.foundation.rememberActiveFocusRequester
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
import androidx.wear.tooling.preview.devices.WearDevices
import com.cbrl.pixelblaze.presentation.theme.PixelblazeTheme
import io.mhssn.colorpicker.ColorPicker
import io.mhssn.colorpicker.ColorPickerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    NavigationStack()
}

class MainActivity : ComponentActivity() {
    var connectivityManager: ConnectivityManager? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        try {
            requestWifiConnection()
        }
        catch (e: Exception) {
            Toast.makeText(this, "WiFi request failed: $e", Toast.LENGTH_SHORT).show()
        }

        setContent {
            NavigationStack()
        }
    }

    override fun onDestroy() {
        connectivityManager?.let {
            it.bindProcessToNetwork(null)
            connectivityCallback?.let {
                connectivityManager!!.unregisterNetworkCallback(it)
            }
        }

        super.onDestroy()
    }

    private fun requestWifiConnection() {
        connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        connectivityCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // The Wi-Fi network has been acquired. Bind it to use this network by default.
                connectivityManager!!.bindProcessToNetwork(network)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                // Called when a network disconnects or otherwise no longer satisfies this request or callback.
            }
        }
        connectivityManager!!.requestNetwork(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            connectivityCallback!!
        )
    }
}

class PixelblazeDeviceViewModel : ViewModel() {
    private val locator = DeviceLocator()

    var deviceList = mutableStateMapOf<Int, PixelblazeDevice>()

    init {
        getDevices()
    }

    private fun getDevices() {
        viewModelScope.launch {
            //withTimeoutOrNull(2000) {
            locator.getDevices()
                .catch { e -> Log.e("PixelblazeDeviceViewModel", e.toString()) }
                .collect { value -> deviceList[value.id] = value }
            //}
        }
    }
}

class HelmetControllerViewModel(private val scope: LifecycleCoroutineScope) : ViewModel() {
    var controller = HelmetController(scope)

    var connectionStatus = MutableStateFlow<AsyncResult<Unit>>(AsyncResult.Loading)
    var activeSegment = HelmetSegments.Visor

    fun open(device: PixelblazeDevice) {
        scope.launch {
            device.start(scope).asAsyncResult().collect {
                when (it) {
                    is AsyncResult.Success<Unit> -> launch {
                        connectionStatus.emitAll(controller.start(device).asAsyncResult())
                    }
                    is AsyncResult.Error -> connectionStatus.emit(it)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun NavigationStack() {
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope

    val navController = rememberNavController()

    val deviceView = PixelblazeDeviceViewModel()
    val helmetControllerView = HelmetControllerViewModel(lifecycleScope)

    NavHost(navController = navController, startDestination = Screen.DeviceList.route) {
        composable(route = Screen.DeviceList.route) {
            DeviceListScreen(
                deviceViewModel = deviceView,
                onDeviceSelected = {
                    navController.navigate(route = Screen.SegmentSelect.route + "?device=${it.id}")
                }
            )
        }
        composable(
            route = Screen.SegmentSelect.route + "?device={device}",
            arguments = listOf(
                navArgument("device") {
                    type = NavType.IntType
                }
            )
        ) {
            // This argument should always be present
            val deviceID = it.arguments!!.getInt("device")

            LaunchedEffect(deviceID) {
                helmetControllerView.open(deviceView.deviceList[deviceID]!!)
            }

            SegmentSelectScreen(onSegmentSelected = { segment ->
                helmetControllerView.activeSegment = segment
                navController.navigate(route = Screen.HelmetController.route + "?segment=${segment.ordinal}")
            })
        }
        composable(
            route = Screen.HelmetController.route + "?segment={segment}",
            arguments = listOf(
                navArgument("segment") {
                    type = NavType.IntType
                }
            )
        ) {
            AsyncResultHandler(
                helmetControllerView.connectionStatus.collectAsState().value,
                onSuccess = { _ -> HelmetControllerScreen(helmetControllerView) }
            )
        }
    }
}

@Composable
fun DeviceListScreen(
    deviceViewModel: PixelblazeDeviceViewModel = viewModel(),
    onDeviceSelected: (device: PixelblazeDevice) -> Unit
) {
    val deviceList = remember { deviceViewModel.deviceList }

    PixelblazeTheme {
        Box(modifier = Modifier, contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Device List",
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )

                DeviceChips(deviceList, onDeviceSelected)
            }
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
fun DeviceChips(
    devices: Map<Int, PixelblazeDevice>,
    onDeviceSelected: (device: PixelblazeDevice) -> Unit
) {
    val scrollState = rememberScalingLazyListState()

    ScalingLazyColumn(state = scrollState) {
        items(items = devices.values.toList()) { device ->
            Chip(
                label = { Text(device.id.toHexString(HexFormat.UpperCase)) },
                secondaryLabel = { Text(text = device.address) },
                onClick = { onDeviceSelected(device) },
                //contentPadding = PaddingValues(horizontal = 32.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SegmentSelectScreen(onSegmentSelected: (segment: HelmetSegments) -> Unit) {
    val scrollState = rememberScalingLazyListState()

    PixelblazeTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ScalingLazyColumn(state = scrollState) {
                items(items = HelmetSegments.entries) { segment ->
                    Chip(
                        label = { Text(segment.prettyName) },
                        onClick = { onSegmentSelected(segment) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun HelmetControllerScreen(controllerView: HelmetControllerViewModel) {
    val pagerState = rememberPagerState(pageCount = { 4 })

    var selectedPage by remember { mutableIntStateOf(0) }
    var finalValue by remember { mutableIntStateOf(0) }

    val animatedSelectedPage by animateFloatAsState(
        targetValue = selectedPage.toFloat(),
    ) {
        finalValue = it.toInt()
    }

    val pageIndicatorState: PageIndicatorState = remember {
        object : PageIndicatorState {
            override val pageOffset: Float
                get() = animatedSelectedPage - finalValue

            override val selectedPage: Int
                get() = finalValue

            override val pageCount: Int
                get() = pagerState.pageCount
        }
    }

    PixelblazeTheme {
        HorizontalPager(state = pagerState) {page ->
            selectedPage = page
            when (page) {
                0 -> EffectSelector(controllerView)
                1 -> HelmetBrightnessControl(controllerView)
                2 -> HelmetColorControl(controllerView)
                3 -> VisorMessageScreen(controllerView)
            }
        }
        HorizontalPageIndicator(pageIndicatorState = pageIndicatorState)
    }
}

@Composable
fun EffectSelector(controllerView: HelmetControllerViewModel) {
    val scrollState = rememberScalingLazyListState()

    val state = controllerView.controller.state.collectAsState(initial = HelmetState())
    val effect = state.value.getSegment(HelmetSegments.Visor).getState(SegmentVars.Effect).toInt()

    var currentEffect by remember(effect) { mutableIntStateOf(effect) }

    ScalingLazyColumn(state = scrollState) {
        items(HelmetEffects.entries.reversed()) {
            effect -> SplitToggleChip(
                label = { Text(effect.name, textAlign = TextAlign.Center) },
                onClick = { /* TODO: Navigate to effect-specific controls */ },
                modifier = Modifier.fillMaxWidth(),
                checked = (currentEffect == effect.ordinal),
                toggleControl = { RadioButton(selected = (currentEffect == effect.ordinal)) },
                onCheckedChange = {
                    controllerView.controller.setSegmentEffect(controllerView.activeSegment, effect.ordinal)
                    currentEffect = effect.ordinal
                }
            )
        }
    }
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun HelmetBrightnessControl(controllerView: HelmetControllerViewModel) {
    val state = controllerView.controller.state.collectAsState(initial = HelmetState())
    val br = state.value.getSegment(HelmetSegments.Visor).getState(SegmentVars.Brightness).toFloat()

    var brightness by remember(br) { mutableFloatStateOf(br) }

    val animatedBrightness by animateFloatAsState(
        targetValue = brightness,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )

    val focusRequester = rememberActiveFocusRequester()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .onRotaryScrollEvent {
                brightness = when {
                    it.verticalScrollPixels > 0 -> min(brightness + 0.05f, 1.0f)
                    it.verticalScrollPixels < 0 -> max(brightness - 0.05f, 0.0f)
                    else -> brightness
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        CircularProgressIndicator(
            progress = animatedBrightness,
            modifier = Modifier.fillMaxSize().padding(all = 1.dp),
            strokeWidth = 5.dp, //ProgressIndicatorDefaults.FullScreenStrokeWidth (internal?)
            startAngle = 290.0f,
            endAngle =  250.0f,
        )
        Text(
            text = "%.0f%%".format(round(brightness * 100)),
            modifier = Modifier.align(Alignment.TopCenter)
        )
        CompactChip(
            label = { Text("Set Brightness") },
            modifier = Modifier.align(Alignment.Center),
            onClick = { controllerView.controller.setSegmentBrightness(controllerView.activeSegment, brightness) }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HelmetColorControl(controllerView: HelmetControllerViewModel) {
    var color by remember { mutableStateOf(Color.Red) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ColorPicker(
            type = ColorPickerType.Circle(
                showBrightnessBar = false,
                showAlphaBar = false
            ),
            modifier = Modifier.fillMaxSize(fraction = 0.75f).align(Alignment.CenterHorizontally)
        ) {
            color = it
        }
        CompactChip(
            label = { Text("Set Color") },
            onClick = { controllerView.controller.setSegmentColor(controllerView.activeSegment, color) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/*
@Composable
fun VisorMessageEditor(controllerView: HelmetControllerViewModel) {
    val state = controllerView.controller.state.collectAsState(initial = HelmetState())
    var message by remember(state.value.getMessage().value) { mutableStateOf(state.value.getMessage().value) }

    Box(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = message,
            onValueChange = { newValue -> message = newValue },
            modifier = Modifier.align(Alignment.Center)
        )
        CompactChip(
            label = { Text("Set Message") },
            onClick = { },
            //onCLick = { controllerView.stateMgr.setVisorMessage(message) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
*/

@Composable
fun UserInputBox(
    modifier: Modifier = Modifier,
    text: String = "",
    onInput: (input: String) -> Unit
) {
    val inputTextKey = "input_text"

    val remoteInputs: List<RemoteInput> = listOf(
        RemoteInput.Builder(inputTextKey)
            .setLabel("Input")
            .wearableExtender {
                setEmojisAllowed(false)
                setInputActionType(EditorInfo.IME_ACTION_DONE)
            }
            .build(),
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        it.data?.let { data ->
            val results: Bundle = RemoteInput.getResultsFromIntent(data)
            val newInputText: CharSequence? = results.getCharSequence(inputTextKey)
            val inputString = newInputText?.toString() ?: ""
            onInput(inputString)
        }
    }

    val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
    RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)

    Box(modifier = modifier) {
        Row(modifier = modifier.fillMaxWidth(0.75f)) {
            Text(text = text, Modifier.weight(1f))
            CompactButton(
                onClick = { launcher.launch(intent) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "User input"
                )
            }
        }
    }
}

@Composable
fun VisorMessageScreen(controllerView: HelmetControllerViewModel) {
    val state = controllerView.controller.state.collectAsState(initial = HelmetState())
    val msgValue = state.value.getMessage().value
    var userInput by remember { mutableStateOf(msgValue) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        UserInputBox(
            text = msgValue,
            onInput = { input -> userInput = input }
        )
        CompactChip(
            label = { Text("Set Message") },
            onClick = { controllerView.controller.setMessageText(userInput) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
