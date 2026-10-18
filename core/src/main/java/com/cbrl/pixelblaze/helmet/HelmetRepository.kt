package com.cbrl.pixelblaze.helmet

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.cbrl.pixelblaze.data.CompanionSync
import com.cbrl.pixelblaze.data.HelmetPreferences
import com.cbrl.pixelblaze.device.NetworkMonitor
import com.cbrl.pixelblaze.device.PixelblazeClient
import com.cbrl.pixelblaze.device.PixelblazeDiscovery
import com.cbrl.pixelblaze.device.PixelblazeEndpoint
import com.cbrl.pixelblaze.device.PixelblazeInfo
import com.cbrl.pixelblaze.device.PixelblazeSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.min
import kotlin.random.Random

sealed interface HelmetConnectionState {
    /** No Pixelblaze is selected, or the app is in the background. */
    data object Idle : HelmetConnectionState
    data object Connecting : HelmetConnectionState
    data class Reconnecting(val attempt: Int, val reason: String?) : HelmetConnectionState
    data object Ready : HelmetConnectionState

    /** The Pixelblaze has no Helmet pattern. The app waits for a retry. */
    data class MissingPattern(val message: String) : HelmetConnectionState

    /** The Helmet pattern is not compatible. The app waits for a retry. */
    data class Error(val message: String) : HelmetConnectionState
}

data class DiscoveryState(
    /** True when the device has a Wi-Fi client network. Without one, discovery uses the default route (hotspot). */
    val wifiConnected: Boolean = false,
    val devices: Map<Int, PixelblazeEndpoint> = emptyMap(),
    val errorMessage: String? = null,
)

/** Details of the connected Pixelblaze. */
data class PixelblazeStatus(val info: PixelblazeInfo?, val fps: Float?)

/**
 * Owns the network, discovery, the connection to the selected Pixelblaze, persistence and companion
 * sync. There is one instance for each process.
 *
 * The repository runs while the app is in the foreground, and stops [STOP_DELAY_MILLIS] after the
 * app goes to the background, which releases Wi-Fi on the watch.
 */
@OptIn(FlowPreview::class)
class HelmetRepository private constructor(context: Context) {
    companion object {
        private const val STOP_DELAY_MILLIS = 5_000L
        private const val DEVICE_TTL_MILLIS = 6_000L
        private const val PERSIST_DEBOUNCE_MILLIS = 500L
        private const val MAX_BACKOFF_MILLIS = 30_000L
        private const val DISCOVERY_RETRY_MILLIS = 2_000L

        @Volatile
        private var instance: HelmetRepository? = null

        fun get(context: Context): HelmetRepository = instance ?: synchronized(this) {
            instance ?: HelmetRepository(context.applicationContext).also { instance = it }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = HelmetPreferences(context)
    private val companionSync = CompanionSync(context)
    private val networkMonitor = NetworkMonitor(context)
    private val discoveryService = PixelblazeDiscovery()

    val controller = HelmetController(scope)

    private val _connection = MutableStateFlow<HelmetConnectionState>(HelmetConnectionState.Idle)
    val connection: StateFlow<HelmetConnectionState> = _connection

    private val _discovery = MutableStateFlow(DiscoveryState())
    val discovery: StateFlow<DiscoveryState> = _discovery

    val selectedDevice: StateFlow<PixelblazeEndpoint?> =
        preferences.selectedDevice.stateIn(scope, SharingStarted.Eagerly, null)

    private val savedState: StateFlow<HelmetState?> =
        preferences.desiredState.stateIn(scope, SharingStarted.Eagerly, null)

    private val allPresets: StateFlow<List<HelmetPreset>> =
        preferences.presets.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The presets that are not deleted, newest first. */
    val presets: StateFlow<List<HelmetPreset>> =
        allPresets.map(HelmetPresets::active).stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _status = MutableStateFlow<PixelblazeStatus?>(null)
    val status: StateFlow<PixelblazeStatus?> = _status

    private val _previewFrames = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** RGB bytes for each pixel, while at least one screen holds the preview (see [acquirePreview]). */
    val previewFrames: SharedFlow<ByteArray> = _previewFrames

    private val actionQueue = ActionQueue(
        scope = scope,
        ready = connection.map { it == HelmetConnectionState.Ready },
        perform = ::perform,
    )

    /** The action that waits for the helmet (see [runWhenReady]), or null. */
    val waitingAction: StateFlow<HelmetAction?> = actionQueue.waiting

    /** The result of the last [runWhenReady] action, until [clearActionResult]. */
    val actionResult: StateFlow<ActionResult?> = actionQueue.result

    private val previewUsers = MutableStateFlow(0)
    private val retryRequests = MutableStateFlow(0)
    private val lastSeen = mutableMapOf<Int, Long>()
    private var activeJob: Job? = null
    private var stopJob: Job? = null

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            stopJob?.cancel()
            stopJob = null
            start()
        }

        override fun onStop(owner: LifecycleOwner) {
            stopJob = scope.launch {
                delay(STOP_DELAY_MILLIS)
                stop()
            }
        }
    }

    init {
        scope.launch {
            controller.state.filterNotNull().debounce(PERSIST_DEBOUNCE_MILLIS).collect { state ->
                val now = System.currentTimeMillis()
                preferences.saveDesiredState(state, now)
                companionSync.publishState(state, now)
            }
        }
        scope.launch {
            companionSync.stateUpdates.collect { remote ->
                preferences.saveDesiredState(remote.value, remote.updatedAt, onlyIfNewer = true)
            }
        }
        scope.launch {
            companionSync.deviceUpdates.collect { remote ->
                preferences.saveSelectedDevice(remote.value, remote.updatedAt, onlyIfNewer = true)
            }
        }
        scope.launch {
            // The other app gets the built-in presets through the preset merge.
            preferences.addBuiltInPresets()?.let(companionSync::publishPresets)
        }
        scope.launch {
            companionSync.presetUpdates.collect { remote ->
                val merged = preferences.updatePresets { local -> HelmetPresets.merge(local, remote) }
                // Send the merge back when the other app does not have it yet.
                if (merged != HelmetPresets.normalize(remote)) companionSync.publishPresets(merged)
            }
        }
        scope.launch { ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver) }
    }

    fun selectDevice(endpoint: PixelblazeEndpoint) {
        scope.launch { saveSelection(endpoint) }
    }

    /** Selects a Pixelblaze by address, for networks where discovery beacons do not arrive. */
    fun connectByAddress(address: String) {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return
        val known = _discovery.value.devices.values.firstOrNull { it.address == trimmed }
        selectDevice(known ?: PixelblazeEndpoint(PixelblazeEndpoint.UNKNOWN_ID, trimmed))
    }

    fun forgetDevice() {
        scope.launch { saveSelection(null) }
    }

    /** Connects again after an error, or at once during a reconnect delay. */
    fun retry() {
        retryRequests.update { it + 1 }
    }

    fun savePreset(name: String) {
        val state = controller.state.value ?: savedState.value ?: return
        mutatePresets { current ->
            listOf(HelmetPreset(name = HelmetPresets.sanitizeName(name), look = state.look)) + current
        }
    }

    fun renamePreset(id: String, name: String) = mutatePreset(id) {
        it.copy(name = HelmetPresets.sanitizeName(name))
    }

    /** Replaces the look of a preset with the current helmet look. */
    fun updatePreset(id: String) {
        val state = controller.state.value ?: return
        mutatePreset(id) { it.copy(look = state.look) }
    }

    fun deletePreset(id: String) = mutatePreset(id) { it.copy(deleted = true) }

    /** Undoes [deletePreset]. */
    fun restorePreset(id: String) = mutatePreset(id) { it.copy(deleted = false) }

    /** Shows a preset on the helmet. Does nothing when the helmet is not connected. */
    fun loadPreset(id: String) {
        val preset = presets.value.firstOrNull { it.id == id } ?: return
        controller.applyLook(preset.look)
    }

    /**
     * Runs [action] when the helmet is ready, for example after the watch tile opens the app.
     * The action fails after [ActionQueue.TIMEOUT_MILLIS].
     */
    fun runWhenReady(action: HelmetAction) = actionQueue.submit(action)

    fun clearActionResult() = actionQueue.clearResult()

    private fun perform(action: HelmetAction): ActionResult = when (action) {
        is HelmetAction.SetBlackout -> {
            controller.setBlackout(action.enabled)
            ActionResult(action, success = true, message = if (action.enabled) "Blackout on" else "Lights on")
        }
        is HelmetAction.LoadPreset -> {
            val preset = presets.value.firstOrNull { it.id == action.id }
            if (preset == null) {
                ActionResult(action, success = false, message = "Preset missing")
            } else {
                controller.applyLook(preset.look)
                ActionResult(action, success = true, message = "Preset loaded")
            }
        }
    }

    /** Asks the Pixelblaze for preview frames. Call [releasePreview] when the screen stops showing them. */
    fun acquirePreview() {
        previewUsers.update { it + 1 }
    }

    fun releasePreview() {
        previewUsers.update { (it - 1).coerceAtLeast(0) }
    }

    private fun mutatePreset(id: String, transform: (HelmetPreset) -> HelmetPreset) {
        mutatePresets { current ->
            current.map { if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()) else it }
        }
    }

    private fun mutatePresets(transform: (List<HelmetPreset>) -> List<HelmetPreset>) {
        scope.launch {
            val updated = preferences.updatePresets { HelmetPresets.normalize(transform(it)) }
            companionSync.publishPresets(updated)
        }
    }

    private suspend fun saveSelection(endpoint: PixelblazeEndpoint?) {
        val now = System.currentTimeMillis()
        val stored = endpoint?.copy(network = null)
        preferences.saveSelectedDevice(stored, now)
        companionSync.publishDevice(stored, now)
    }

    private fun start() {
        if (activeJob?.isActive == true) return
        networkMonitor.start()
        activeJob = scope.launch {
            launch { runDiscovery() }
            launch { expireDevices() }
            launch { runConnection() }
            launch {
                networkMonitor.error.collect { error ->
                    if (error != null) _discovery.update { it.copy(errorMessage = error) }
                }
            }
        }
    }

    private fun stop() {
        activeJob?.cancel()
        activeJob = null
        networkMonitor.stop()
        controller.detach()
        lastSeen.clear()
        _discovery.update { it.copy(wifiConnected = false, devices = emptyMap()) }
        _connection.value = HelmetConnectionState.Idle
        _status.value = null
    }

    private suspend fun runDiscovery() {
        networkMonitor.wifiNetwork.collectLatest { network ->
            lastSeen.clear()
            _discovery.update { it.copy(wifiConnected = network != null, devices = emptyMap()) }
            discoveryService.beacons(network)
                .retryWhen { cause, _ ->
                    _discovery.update { it.copy(errorMessage = "Discovery failed: ${cause.message}") }
                    delay(DISCOVERY_RETRY_MILLIS)
                    true
                }
                .collect(::onBeacon)
        }
    }

    private fun onBeacon(endpoint: PixelblazeEndpoint) {
        lastSeen[endpoint.id] = System.currentTimeMillis()
        _discovery.update { current ->
            if (current.devices[endpoint.id] == endpoint && current.errorMessage == null) {
                current
            } else {
                current.copy(devices = current.devices + (endpoint.id to endpoint), errorMessage = null)
            }
        }

        // Follow the selected device to a new address, and learn the ID of a device entered by address.
        val selected = selectedDevice.value ?: return
        val moved = selected.id == endpoint.id && selected.address != endpoint.address
        val identified = selected.id == PixelblazeEndpoint.UNKNOWN_ID && selected.address == endpoint.address
        if (moved || identified) scope.launch { saveSelection(endpoint) }
    }

    private suspend fun expireDevices() {
        while (true) {
            delay(1_000L)
            val cutoff = System.currentTimeMillis() - DEVICE_TTL_MILLIS
            val expired = lastSeen.filterValues { it < cutoff }.keys
            if (expired.isEmpty()) continue
            expired.forEach(lastSeen::remove)
            _discovery.update { it.copy(devices = it.devices - expired) }
        }
    }

    private data class ConnectionTarget(val endpoint: PixelblazeEndpoint?, val retry: Int)

    private suspend fun runConnection() {
        combine(selectedDevice, networkMonitor.wifiNetwork, _discovery, retryRequests) { selected, wifi, discovery, retry ->
            val endpoint = selected?.let {
                val found = discovery.devices[it.id]?.takeIf { device -> device.address == it.address }
                it.copy(network = found?.network ?: wifi)
            }
            ConnectionTarget(endpoint, retry)
        }
            .distinctUntilChanged()
            .collectLatest { target ->
                controller.detach()
                val endpoint = target.endpoint
                if (endpoint == null) {
                    _connection.value = HelmetConnectionState.Idle
                } else {
                    connectLoop(endpoint)
                }
            }
    }

    private suspend fun connectLoop(endpoint: PixelblazeEndpoint) {
        var attempt = 0
        var reason: String? = null
        while (true) {
            _connection.value = if (attempt == 0) {
                HelmetConnectionState.Connecting
            } else {
                HelmetConnectionState.Reconnecting(attempt, reason)
            }
            try {
                PixelblazeClient.connect(endpoint) { session -> runSession(session) }
            } catch (missing: PatternMissingException) {
                _connection.value = HelmetConnectionState.MissingPattern(missing.message.orEmpty())
                return
            } catch (incompatible: PatternIncompatibleException) {
                _connection.value = HelmetConnectionState.Error(incompatible.message.orEmpty())
                return
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                reason = cancelled.message
            } catch (exception: Exception) {
                reason = exception.message ?: exception.javaClass.simpleName
            } finally {
                controller.detach()
                _status.value = null
            }
            attempt += 1
            _connection.value = HelmetConnectionState.Reconnecting(attempt, reason)
            delay(min(MAX_BACKOFF_MILLIS, 1_000L shl min(attempt - 1, 5)) + Random.nextLong(0L, 500L))
        }
    }

    private suspend fun runSession(session: PixelblazeSession): Nothing = coroutineScope {
        launch {
            combine(session.info, session.fps) { info, fps -> PixelblazeStatus(info, fps) }
                .collect { _status.value = it }
        }
        launch { session.previewFrames.collect { _previewFrames.emit(it) } }
        launch {
            previewUsers.map { it > 0 }.distinctUntilChanged().collect { session.setPreviewEnabled(it) }
        }

        activateHelmetPattern(session)
        controller.attach(session.asHelmetLink(), savedState.value ?: preferences.desiredState.first())
        _connection.value = HelmetConnectionState.Ready
        awaitCancellation()
    }

    /**
     * Starts the Helmet pattern, unless it already runs. A start resets the pattern state.
     * The web editor reports a running program as "Editor: <name>", so the IDs are compared too.
     */
    private suspend fun activateHelmetPattern(session: PixelblazeSession) {
        val active = session.awaitActiveProgram()
        if (active?.name == HelmetProtocol.PATTERN_NAME) return
        val helmet = session.programs()[HelmetProtocol.PATTERN_NAME]
            ?: throw PatternMissingException("The \"${HelmetProtocol.PATTERN_NAME}\" pattern is not installed on this Pixelblaze.")
        if (active?.id == helmet.id) return
        session.setActiveProgram(helmet.id)
    }

    private fun PixelblazeSession.asHelmetLink(): HelmetLink {
        val session = this
        return object : HelmetLink {
            override val vars: Flow<JSONObject> = session.vars
            override fun setVars(vars: JSONObject) = session.setVars(vars)
        }
    }
}
