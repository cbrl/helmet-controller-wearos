package com.cbrl.pixelblaze.presentation

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapNotNull
import java.util.UUID

/** Persistent, last-value synchronization between the phone and its paired Wear OS app. */
@SuppressLint("UseKtx")
class CompanionSync(context: Context) {
    private companion object {
        const val DEVICE_PATH = "/pixelblaze/selected-device"
        const val STATE_PATH = "/pixelblaze/helmet-state"
        const val PRESETS_PATH = "/pixelblaze/presets"
        const val KEY_SOURCE = "source"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_ADDRESS = "device_address"
        const val KEY_STATE = "state"
        const val KEY_PRESETS = "presets"
    }

    private val appContext = context.applicationContext
    private val dataClient by lazy { Wearable.getDataClient(appContext) }
    private val sourceId: String = appContext
        .getSharedPreferences("companion_sync", Context.MODE_PRIVATE)
        .let { preferences ->
            preferences.getString(KEY_SOURCE, null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString(KEY_SOURCE, it).apply()
            }
        }

    val deviceUpdates: Flow<SavedPixelblaze> = dataMaps(DEVICE_PATH).mapNotNull { map ->
        val id = map.getInt(KEY_DEVICE_ID, Int.MIN_VALUE)
        val address = map.getString(KEY_DEVICE_ADDRESS, "")
        if (id == Int.MIN_VALUE || address.isBlank()) null else SavedPixelblaze(id, address)
    }

    val stateUpdates: Flow<HelmetState> = dataMaps(STATE_PATH).mapNotNull { map ->
        HelmetState.fromPersistedJson(map.getString(KEY_STATE, ""))
    }

    val presetUpdates: Flow<List<HelmetPreset>> = dataMaps(PRESETS_PATH).mapNotNull { map ->
        map.getString(KEY_PRESETS, "").takeIf(String::isNotBlank)?.let(HelmetPreset::listFromJSON)
    }

    fun publishDevice(device: SavedPixelblaze) {
        publish(DEVICE_PATH) {
            putInt(KEY_DEVICE_ID, device.id)
            putString(KEY_DEVICE_ADDRESS, device.address)
        }
    }

    fun publishState(state: HelmetState) {
        publish(STATE_PATH) {
            putString(KEY_STATE, state.toJSON().toString())
        }
    }

    fun publishPresets(presets: List<HelmetPreset>) {
        publish(PRESETS_PATH) {
            putString(KEY_PRESETS, HelmetPreset.listToJSON(presets).toString())
        }
    }

    private fun dataMaps(path: String): Flow<DataMap> = callbackFlow {
        fun offer(dataMap: DataMap) {
            if (dataMap.getString(KEY_SOURCE, "") != sourceId) trySend(dataMap)
        }
        val listener = DataClient.OnDataChangedListener { events ->
            events.use {
                events
                    .filter { event ->
                        event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == path
                    }
                    .map { event -> DataMapItem.fromDataItem(event.dataItem).dataMap }
                    .forEach(::offer)
            }
        }
        runCatching { dataClient.addListener(listener) }
            .onFailure { close(it) }
        runCatching {
            dataClient.dataItems.addOnSuccessListener { items ->
                try {
                    items
                        .filter { item -> item.uri.path == path }
                        .map { item -> DataMapItem.fromDataItem(item).dataMap }
                        .forEach(::offer)
                } finally {
                    items.release()
                }
            }
        }
        awaitClose { runCatching { dataClient.removeListener(listener) } }
    }

    private fun publish(path: String, values: DataMap.() -> Unit) {
        runCatching {
            val request = PutDataMapRequest.create(path).apply {
                dataMap.putString(KEY_SOURCE, sourceId)
                dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                dataMap.apply(values)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request)
        }
    }
}
