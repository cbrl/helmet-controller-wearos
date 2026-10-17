package com.cbrl.pixelblaze.presentation

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.random.Random

private val Context.helmetDataStore by preferencesDataStore(name = "helmet_controller")

data class SavedPixelblaze(val id: Int, val address: String)

class HelmetPreferences(private val context: Context) {
    private companion object {
        val SELECTED_DEVICE_ID = intPreferencesKey("selected_device_id")
        val SELECTED_DEVICE_ADDRESS = stringPreferencesKey("selected_device_address")
        val HELMET_STATE = stringPreferencesKey("helmet_state")
        val PRESETS = stringPreferencesKey("presets")
    }

    val selectedDevice: Flow<SavedPixelblaze?> = context.helmetDataStore.data.map { preferences ->
        val id = preferences[SELECTED_DEVICE_ID]
        val address = preferences[SELECTED_DEVICE_ADDRESS]
        if (id != null && address != null) SavedPixelblaze(id, address) else null
    }

    val helmetState: Flow<HelmetState?> = context.helmetDataStore.data.map { preferences ->
        preferences[HELMET_STATE]?.let(HelmetState::fromPersistedJson)
    }

    val presets: Flow<List<HelmetPreset>> = context.helmetDataStore.data.map { preferences ->
        preferences[PRESETS]?.let(HelmetPreset::listFromJSON).orEmpty()
    }

    suspend fun saveSelectedDevice(device: PixelblazeDevice) {
        saveSelectedDevice(SavedPixelblaze(device.id, device.address))
    }

    suspend fun saveSelectedDevice(device: SavedPixelblaze) {
        context.helmetDataStore.edit { preferences ->
            preferences[SELECTED_DEVICE_ID] = device.id
            preferences[SELECTED_DEVICE_ADDRESS] = device.address
        }
    }

    suspend fun saveHelmetState(state: HelmetState) {
        context.helmetDataStore.edit { preferences ->
            preferences[HELMET_STATE] = state.toJSON().toString()
        }
    }

    suspend fun savePresets(presets: List<HelmetPreset>) {
        context.helmetDataStore.edit { preferences ->
            preferences[PRESETS] = HelmetPreset.listToJSON(presets).toString()
        }
    }
}

data class DeviceDiscoveryState(
    val wifiAvailable: Boolean = false,
    val devices: Map<Int, PixelblazeDevice> = emptyMap(),
    val rememberedDevice: SavedPixelblaze? = null,
    val errorMessage: String? = null,
)

class PixelblazeDeviceViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val DEVICE_TTL_MILLIS = 6_000L
    }

    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val preferences = HelmetPreferences(application)
    private val companionSync = CompanionSync(application)
    private val locator = DeviceLocator()
    private val _state = MutableStateFlow(DeviceDiscoveryState())
    val state: StateFlow<DeviceDiscoveryState> = _state

    private val lastSeen = mutableMapOf<Int, Long>()
    private var discoveryJob: Job? = null
    private var activeNetwork: Network? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            activeNetwork = network
            if (!connectivityManager.bindProcessToNetwork(network)) {
                _state.update { it.copy(errorMessage = "Unable to bind Pixelblaze traffic to Wi-Fi") }
                return
            }
            _state.update { it.copy(wifiAvailable = true, errorMessage = null) }
            startDiscovery(network)
        }

        override fun onLost(network: Network) {
            if (activeNetwork != network) return
            activeNetwork = null
            connectivityManager.bindProcessToNetwork(null)
            discoveryJob?.cancel()
            discoveryJob = null
            lastSeen.clear()
            _state.update { it.copy(wifiAvailable = false, devices = emptyMap()) }
        }

        override fun onUnavailable() {
            _state.update { it.copy(errorMessage = "No saved Wi-Fi network is available") }
        }
    }

    init {
        viewModelScope.launch {
            preferences.selectedDevice.collect { remembered ->
                _state.update { it.copy(rememberedDevice = remembered) }
            }
        }
        viewModelScope.launch {
            companionSync.deviceUpdates.collect { companionDevice ->
                if (_state.value.rememberedDevice != companionDevice) {
                    preferences.saveSelectedDevice(companionDevice)
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                removeExpiredDevices()
            }
        }

        runCatching {
            connectivityManager.requestNetwork(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(),
                networkCallback,
            )
        }.onFailure { exception ->
            _state.update { it.copy(errorMessage = "Wi-Fi request failed: ${exception.message}") }
        }
    }

    private fun startDiscovery(network: Network) {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            locator.getDevices(network)
                .catch { exception ->
                    _state.update { it.copy(errorMessage = "Discovery failed: ${exception.message}") }
                }
                .collect { discovered ->
                    lastSeen[discovered.id] = System.currentTimeMillis()
                    _state.update { current ->
                        val existing = current.devices[discovered.id]
                        if (existing?.address == discovered.address) current
                        else current.copy(devices = current.devices + (discovered.id to discovered))
                    }
                }
        }
    }

    private fun removeExpiredDevices() {
        val cutoff = System.currentTimeMillis() - DEVICE_TTL_MILLIS
        val expired = lastSeen.filterValues { it < cutoff }.keys
        if (expired.isEmpty()) return
        expired.forEach(lastSeen::remove)
        _state.update { current -> current.copy(devices = current.devices - expired) }
    }

    override fun onCleared() {
        discoveryJob?.cancel()
        connectivityManager.bindProcessToNetwork(null)
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }
}

sealed interface HelmetConnectionState {
    data object Idle : HelmetConnectionState
    data object Connecting : HelmetConnectionState
    data class Reconnecting(val attempt: Int, val reason: String?) : HelmetConnectionState
    data object Ready : HelmetConnectionState
    data class MissingPattern(val message: String) : HelmetConnectionState
    data class Error(val message: String) : HelmetConnectionState
}

@OptIn(FlowPreview::class)
class HelmetControllerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    val controller = HelmetController(viewModelScope)
    private val preferences = HelmetPreferences(application)
    private val companionSync = CompanionSync(application)

    private val _connectionState = MutableStateFlow<HelmetConnectionState>(HelmetConnectionState.Idle)
    val connectionState: StateFlow<HelmetConnectionState> = _connectionState

    private val _activeSegment = MutableStateFlow(
        HelmetSegments.fromId(savedStateHandle["active_segment"] ?: 0) ?: HelmetSegments.Visor,
    )
    val activeSegment: StateFlow<HelmetSegments> = _activeSegment

    private val _presets = MutableStateFlow<List<HelmetPreset>>(emptyList())
    val presets: StateFlow<List<HelmetPreset>> = _presets

    private var connectionJob: Job? = null
    private var connectionKey: Pair<Int, String>? = null
    private var desiredState: HelmetState? = null
    private val presetMutex = Mutex()

    init {
        viewModelScope.launch {
            controller.state.filterNotNull().debounce(500L).collect { state ->
                desiredState = state.clone()
                preferences.saveHelmetState(state)
                companionSync.publishState(state)
            }
        }
        viewModelScope.launch {
            desiredState = preferences.helmetState.first()
        }
        viewModelScope.launch {
            companionSync.stateUpdates.collect { companionState ->
                desiredState = companionState.clone()
                preferences.saveHelmetState(companionState)
                if (_connectionState.value == HelmetConnectionState.Ready &&
                    controller.currentState() != companionState
                ) {
                    controller.applyState(companionState)
                }
            }
        }
        viewModelScope.launch {
            preferences.presets.collect { savedPresets ->
                if (_presets.value != savedPresets) {
                    _presets.value = savedPresets.map(HelmetPreset::clone)
                }
            }
        }
        viewModelScope.launch {
            companionSync.presetUpdates.collect { companionPresets ->
                if (_presets.value != companionPresets) {
                    _presets.value = companionPresets.map(HelmetPreset::clone)
                    preferences.savePresets(companionPresets)
                }
            }
        }
    }

    fun selectSegment(segment: HelmetSegments) {
        _activeSegment.value = segment
        savedStateHandle["active_segment"] = segment.id
    }

    fun savePreset(name: String) {
        val state = controller.currentState() ?: desiredState ?: return
        mutatePresets { current ->
            listOf(HelmetPreset(name = HelmetPreset.sanitizeName(name), state = state.clone())) + current
        }
    }

    fun renamePreset(id: String, name: String) {
        mutatePresets { current ->
            current.map { preset ->
                if (preset.id == id) preset.copy(
                    name = HelmetPreset.sanitizeName(name),
                    updatedAt = System.currentTimeMillis(),
                ) else preset
            }
        }
    }

    fun updatePreset(id: String) {
        val state = controller.currentState() ?: desiredState ?: return
        mutatePresets { current ->
            current.map { preset ->
                if (preset.id == id) preset.copy(
                    state = state.clone(),
                    updatedAt = System.currentTimeMillis(),
                ) else preset
            }
        }
    }

    fun deletePreset(id: String) {
        mutatePresets { current -> current.filterNot { it.id == id } }
    }

    fun loadPreset(id: String) {
        val preset = _presets.value.firstOrNull { it.id == id } ?: return
        desiredState = preset.state.clone()
        controller.applyState(preset.state)
    }

    private fun mutatePresets(transform: (List<HelmetPreset>) -> List<HelmetPreset>) {
        viewModelScope.launch {
            presetMutex.withLock {
                val updated = transform(_presets.value.map(HelmetPreset::clone))
                    .distinctBy { it.id }
                    .sortedByDescending { it.updatedAt }
                    .take(HelmetPreset.MAX_PRESETS)
                _presets.value = updated
                preferences.savePresets(updated)
                companionSync.publishPresets(updated)
            }
        }
    }

    fun open(device: PixelblazeDevice) {
        val key = device.id to device.address
        if (connectionKey == key && connectionJob?.isActive == true) return
        connectionKey = key
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            preferences.saveSelectedDevice(device)
            companionSync.publishDevice(SavedPixelblaze(device.id, device.address))
            var attempt = 0
            var reconnectState = controller.currentState() ?: desiredState ?: preferences.helmetState.first()

            while (isActive) {
                _connectionState.value = if (attempt == 0) {
                    HelmetConnectionState.Connecting
                } else {
                    HelmetConnectionState.Reconnecting(attempt, null)
                }

                val connection = PixelblazeDevice(device.address, device.id)
                try {
                    var configured = false
                    connection.start().collect {
                        if (!configured) {
                            controller.attach(connection, desiredState ?: reconnectState)
                            reconnectState = controller.currentState()
                            desiredState = reconnectState
                            configured = true
                            attempt = 0
                            _connectionState.value = HelmetConnectionState.Ready
                        }
                    }
                    error("Pixelblaze connection ended")
                } catch (missing: NoSuchElementException) {
                    _connectionState.value = HelmetConnectionState.MissingPattern(
                        missing.message ?: "The Helmet pattern is not installed",
                    )
                    return@launch
                } catch (incompatible: IncompatibleHelmetPatternException) {
                    _connectionState.value = HelmetConnectionState.Error(
                        incompatible.message ?: "Helmet pattern is incompatible",
                    )
                    return@launch
                } catch (exception: Exception) {
                    if (!isActive) return@launch
                    controller.detach()
                    reconnectState = controller.currentState() ?: desiredState ?: reconnectState
                    attempt += 1
                    _connectionState.value = HelmetConnectionState.Reconnecting(
                        attempt = attempt,
                        reason = exception.message,
                    )
                    val baseDelay = min(30_000L, 1_000L shl min(attempt - 1, 5))
                    delay(baseDelay + Random.nextLong(0L, 500L))
                } finally {
                    connection.close()
                }
            }
        }
    }

    fun retry() {
        val key = connectionKey ?: return
        connectionKey = null
        open(PixelblazeDevice(address = key.second, id = key.first))
    }

    override fun onCleared() {
        connectionJob?.cancel()
        controller.detach()
        super.onCleared()
    }
}
