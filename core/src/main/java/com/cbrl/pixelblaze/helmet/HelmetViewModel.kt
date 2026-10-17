package com.cbrl.pixelblaze.helmet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.cbrl.pixelblaze.device.PixelblazeEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** The screen-facing view of [HelmetRepository] for the phone and watch apps. */
class HelmetViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private companion object {
        const val KEY_ACTIVE_SEGMENT = "active_segment"
    }

    private val repository = HelmetRepository.get(application)

    val controller: HelmetController = repository.controller
    val connection: StateFlow<HelmetConnectionState> = repository.connection
    val state: StateFlow<HelmetState?> = controller.state
    val layout: StateFlow<HelmetLayout> = controller.layout
    val discovery: StateFlow<DiscoveryState> = repository.discovery
    val selectedDevice: StateFlow<PixelblazeEndpoint?> = repository.selectedDevice
    val presets: StateFlow<List<HelmetPreset>> = repository.presets
    val status: StateFlow<PixelblazeStatus?> = repository.status
    val previewFrames: SharedFlow<ByteArray> = repository.previewFrames
    val waitingAction: StateFlow<HelmetAction?> = repository.waitingAction
    val actionResult: StateFlow<ActionResult?> = repository.actionResult

    private val _activeSegment = MutableStateFlow(
        HelmetSegment.fromId(savedStateHandle[KEY_ACTIVE_SEGMENT] ?: 0) ?: HelmetSegment.Visor,
    )
    val activeSegment: StateFlow<HelmetSegment> = _activeSegment

    private var previewHolds = 0

    fun selectSegment(segment: HelmetSegment) {
        _activeSegment.value = segment
        savedStateHandle[KEY_ACTIVE_SEGMENT] = segment.id
    }

    fun selectDevice(endpoint: PixelblazeEndpoint) = repository.selectDevice(endpoint)

    fun connectByAddress(address: String) = repository.connectByAddress(address)

    fun forgetDevice() = repository.forgetDevice()

    fun retry() = repository.retry()

    fun savePreset(name: String) = repository.savePreset(name)

    fun renamePreset(id: String, name: String) = repository.renamePreset(id, name)

    fun updatePreset(id: String) = repository.updatePreset(id)

    fun deletePreset(id: String) = repository.deletePreset(id)

    fun restorePreset(id: String) = repository.restorePreset(id)

    fun loadPreset(id: String) = repository.loadPreset(id)

    fun runWhenReady(action: HelmetAction) = repository.runWhenReady(action)

    fun clearActionResult() = repository.clearActionResult()

    fun acquirePreview() {
        previewHolds++
        repository.acquirePreview()
    }

    fun releasePreview() {
        if (previewHolds == 0) return
        previewHolds--
        repository.releasePreview()
    }

    override fun onCleared() {
        repeat(previewHolds) { repository.releasePreview() }
        previewHolds = 0
        super.onCleared()
    }
}
