package com.cbrl.pixelblaze.data

import android.annotation.SuppressLint
import android.content.Context
import com.cbrl.pixelblaze.device.PixelblazeEndpoint
import com.cbrl.pixelblaze.helmet.HelmetPreset
import com.cbrl.pixelblaze.helmet.HelmetPresets
import com.cbrl.pixelblaze.helmet.HelmetProtocol
import com.cbrl.pixelblaze.helmet.HelmetState
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

/**
 * Last-value sync between the phone and its paired Wear OS app.
 *
 * Each item carries the time of the change. The receiver keeps the newer value (device, state) or
 * merges by preset (presets). The receiver never writes a synced state to a live helmet: an app
 * that is connected to the helmet reads the helmet itself.
 */
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
        const val NO_DEVICE = Int.MIN_VALUE
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

    /** The device that the other app selected. A null value means that it forgot its device. */
    val deviceUpdates: Flow<Timestamped<PixelblazeEndpoint?>> = dataMaps(DEVICE_PATH).mapNotNull { map ->
        val id = map.getInt(KEY_DEVICE_ID, NO_DEVICE)
        val address = map.getString(KEY_DEVICE_ADDRESS, "")
        val endpoint = if (id == NO_DEVICE || address.isBlank()) null else PixelblazeEndpoint(id, address)
        Timestamped(endpoint, map.getLong(KEY_TIMESTAMP, 0L))
    }

    val stateUpdates: Flow<Timestamped<HelmetState>> = dataMaps(STATE_PATH).mapNotNull { map ->
        HelmetProtocol.stateFromJson(map.getString(KEY_STATE, ""))
            ?.let { Timestamped(it, map.getLong(KEY_TIMESTAMP, 0L)) }
    }

    /** The other app's presets, including tombstones. */
    val presetUpdates: Flow<List<HelmetPreset>> = dataMaps(PRESETS_PATH).mapNotNull { map ->
        map.getString(KEY_PRESETS, "").takeIf(String::isNotBlank)?.let(HelmetPresets::fromJson)
    }

    fun publishDevice(endpoint: PixelblazeEndpoint?, updatedAt: Long) {
        publish(DEVICE_PATH, updatedAt) {
            putInt(KEY_DEVICE_ID, endpoint?.id ?: NO_DEVICE)
            putString(KEY_DEVICE_ADDRESS, endpoint?.address.orEmpty())
        }
    }

    fun publishState(state: HelmetState, updatedAt: Long) {
        publish(STATE_PATH, updatedAt) {
            putString(KEY_STATE, HelmetProtocol.stateToJson(state).toString())
        }
    }

    fun publishPresets(presets: List<HelmetPreset>) {
        publish(PRESETS_PATH, System.currentTimeMillis()) {
            putString(KEY_PRESETS, HelmetPresets.toJson(presets).toString())
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

    private fun publish(path: String, updatedAt: Long, values: DataMap.() -> Unit) {
        runCatching {
            val request = PutDataMapRequest.create(path).apply {
                dataMap.putString(KEY_SOURCE, sourceId)
                dataMap.putLong(KEY_TIMESTAMP, updatedAt)
                dataMap.apply(values)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request)
        }
    }
}
