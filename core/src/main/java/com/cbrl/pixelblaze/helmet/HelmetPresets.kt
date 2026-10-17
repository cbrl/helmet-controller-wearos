package com.cbrl.pixelblaze.helmet

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A saved look. A deleted preset stays in the list as a tombstone ([deleted] = true) for
 * [HelmetPresets.TOMBSTONE_TTL_MILLIS], so that a sync merge does not bring it back.
 */
data class HelmetPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val look: HelmetLook,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

object HelmetPresets {
    const val MAX_PRESETS = 24
    const val MAX_NAME_LENGTH = 28
    val TOMBSTONE_TTL_MILLIS: Long = TimeUnit.DAYS.toMillis(30)

    fun sanitizeName(value: String): String = value
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(MAX_NAME_LENGTH)
        .ifBlank { "Untitled preset" }

    /** The presets that are not deleted. */
    fun active(presets: List<HelmetPreset>): List<HelmetPreset> = presets.filterNot { it.deleted }

    /**
     * Combines two preset lists by ID. The copy with the later [HelmetPreset.updatedAt] wins, and a
     * delete is a later copy with [HelmetPreset.deleted] = true.
     */
    fun merge(
        local: List<HelmetPreset>,
        remote: List<HelmetPreset>,
        now: Long = System.currentTimeMillis(),
    ): List<HelmetPreset> {
        val byId = LinkedHashMap<String, HelmetPreset>()
        (local + remote).forEach { preset ->
            val current = byId[preset.id]
            if (current == null || preset.isNewerThan(current)) byId[preset.id] = preset
        }
        return normalize(byId.values, now)
    }

    /** Sorts by time (newest first), keeps [MAX_PRESETS] active presets, and drops old tombstones. */
    fun normalize(presets: Collection<HelmetPreset>, now: Long = System.currentTimeMillis()): List<HelmetPreset> {
        val sorted = presets.distinctBy { it.id }.sortedByDescending { it.updatedAt }
        val active = sorted.filterNot { it.deleted }.take(MAX_PRESETS)
        val tombstones = sorted.filter { it.deleted && now - it.updatedAt < TOMBSTONE_TTL_MILLIS }
        return (active + tombstones).sortedByDescending { it.updatedAt }
    }

    private fun HelmetPreset.isNewerThan(other: HelmetPreset): Boolean =
        updatedAt > other.updatedAt || (updatedAt == other.updatedAt && deleted && !other.deleted)

    fun toJson(presets: List<HelmetPreset>): JSONArray = JSONArray().apply {
        presets.forEach { put(toJson(it)) }
    }

    fun toJson(preset: HelmetPreset): JSONObject = JSONObject()
        .put("id", preset.id)
        .put("name", sanitizeName(preset.name))
        .put("updated_at", preset.updatedAt)
        .put("deleted", preset.deleted)
        .put("look", HelmetProtocol.lookToJson(preset.look))

    /** Reads [toJson] output, or presets that an app with protocol v3 stored. Bad entries are skipped. */
    fun fromJson(value: String, now: Long = System.currentTimeMillis()): List<HelmetPreset> = runCatching {
        val array = JSONArray(value)
        val presets = List(array.length()) { index -> array.optJSONObject(index)?.let(::presetFromJson) }
        normalize(presets.filterNotNull(), now)
    }.getOrDefault(emptyList())

    private fun presetFromJson(json: JSONObject): HelmetPreset? {
        val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
        // v3 presets stored the pattern variables in "state".
        val look = (json.optJSONObject("look") ?: json.optJSONObject("state"))
            ?.let(HelmetProtocol::lookFromJson)
            ?: return null
        return HelmetPreset(
            id = id,
            name = sanitizeName(json.optString("name")),
            look = look,
            updatedAt = json.optLong("updated_at", 0L),
            deleted = json.optBoolean("deleted", false),
        )
    }
}
