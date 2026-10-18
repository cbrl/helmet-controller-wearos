package com.cbrl.pixelblaze.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cbrl.pixelblaze.device.PixelblazeEndpoint
import com.cbrl.pixelblaze.helmet.BuiltInPresets
import com.cbrl.pixelblaze.helmet.HelmetPreset
import com.cbrl.pixelblaze.helmet.HelmetPresets
import com.cbrl.pixelblaze.helmet.HelmetProtocol
import com.cbrl.pixelblaze.helmet.HelmetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.helmetDataStore by preferencesDataStore(name = "helmet_controller")

/** A value and the time of its last change. Sync uses the time to keep the newer value. */
data class Timestamped<T>(val value: T, val updatedAt: Long)

/** Stores the selected Pixelblaze, the helmet state to restore, and the presets. */
class HelmetPreferences(context: Context) {
    private val dataStore = context.applicationContext.helmetDataStore

    private companion object {
        val SELECTED_DEVICE_ID = intPreferencesKey("selected_device_id")
        val SELECTED_DEVICE_ADDRESS = stringPreferencesKey("selected_device_address")
        val SELECTED_DEVICE_UPDATED_AT = longPreferencesKey("selected_device_updated_at")
        val HELMET_STATE = stringPreferencesKey("helmet_state")
        val HELMET_STATE_UPDATED_AT = longPreferencesKey("helmet_state_updated_at")
        val PRESETS = stringPreferencesKey("presets")
        val BUILT_IN_PRESETS_VERSION = intPreferencesKey("built_in_presets_version")
    }

    val selectedDevice: Flow<PixelblazeEndpoint?> = dataStore.data
        .map { it.selectedDevice() }
        .distinctUntilChanged()

    /** The helmet state that the app writes after the pattern restarts. */
    val desiredState: Flow<HelmetState?> = dataStore.data
        .map { preferences -> preferences[HELMET_STATE]?.let(HelmetProtocol::stateFromJson) }
        .distinctUntilChanged()

    /** All presets, including deleted presets (tombstones). */
    val presets: Flow<List<HelmetPreset>> = dataStore.data
        .map { preferences -> preferences[PRESETS]?.let(HelmetPresets::fromJson).orEmpty() }
        .distinctUntilChanged()

    /**
     * Saves the selected device. With [onlyIfNewer], the save happens only when [updatedAt] is later
     * than the stored time. Returns true when the value was saved.
     */
    suspend fun saveSelectedDevice(
        endpoint: PixelblazeEndpoint?,
        updatedAt: Long = System.currentTimeMillis(),
        onlyIfNewer: Boolean = false,
    ): Boolean {
        var saved = false
        dataStore.edit { preferences ->
            if (onlyIfNewer && (preferences[SELECTED_DEVICE_UPDATED_AT] ?: 0L) >= updatedAt) return@edit
            if (endpoint == null) {
                preferences.remove(SELECTED_DEVICE_ID)
                preferences.remove(SELECTED_DEVICE_ADDRESS)
            } else {
                preferences[SELECTED_DEVICE_ID] = endpoint.id
                preferences[SELECTED_DEVICE_ADDRESS] = endpoint.address
            }
            preferences[SELECTED_DEVICE_UPDATED_AT] = updatedAt
            saved = true
        }
        return saved
    }

    /** See [saveSelectedDevice] for [onlyIfNewer]. */
    suspend fun saveDesiredState(
        state: HelmetState,
        updatedAt: Long = System.currentTimeMillis(),
        onlyIfNewer: Boolean = false,
    ): Boolean {
        var saved = false
        dataStore.edit { preferences ->
            if (onlyIfNewer && (preferences[HELMET_STATE_UPDATED_AT] ?: 0L) >= updatedAt) return@edit
            preferences[HELMET_STATE] = HelmetProtocol.stateToJson(state).toString()
            preferences[HELMET_STATE_UPDATED_AT] = updatedAt
            saved = true
        }
        return saved
    }

    /** Changes the stored presets in one atomic edit, and returns the new list. */
    suspend fun updatePresets(transform: (List<HelmetPreset>) -> List<HelmetPreset>): List<HelmetPreset> {
        var updated = emptyList<HelmetPreset>()
        dataStore.edit { preferences ->
            val current = preferences[PRESETS]?.let(HelmetPresets::fromJson).orEmpty()
            updated = transform(current)
            preferences[PRESETS] = HelmetPresets.toJson(updated).toString()
        }
        return updated
    }

    /**
     * Adds the built-in presets that this app has not added yet (see [BuiltInPresets.addNew]).
     * Returns the new list, or null when there was nothing to add.
     */
    suspend fun addBuiltInPresets(): List<HelmetPreset>? {
        var updated: List<HelmetPreset>? = null
        dataStore.edit { preferences ->
            val seenVersion = preferences[BUILT_IN_PRESETS_VERSION] ?: 0
            if (seenVersion >= BuiltInPresets.VERSION) return@edit
            val current = preferences[PRESETS]?.let(HelmetPresets::fromJson).orEmpty()
            val presets = BuiltInPresets.addNew(current, seenVersion)
            preferences[PRESETS] = HelmetPresets.toJson(presets).toString()
            preferences[BUILT_IN_PRESETS_VERSION] = BuiltInPresets.VERSION
            updated = presets
        }
        return updated
    }

    private fun Preferences.selectedDevice(): PixelblazeEndpoint? {
        val id = this[SELECTED_DEVICE_ID] ?: return null
        val address = this[SELECTED_DEVICE_ADDRESS] ?: return null
        return PixelblazeEndpoint(id, address)
    }
}
