package com.cbrl.pixelblaze.helmet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelmetPresetsTest {
    private val look = HelmetState.Default.look

    private fun preset(id: String, updatedAt: Long, name: String = "Preset $id", deleted: Boolean = false) =
        HelmetPreset(id = id, name = name, look = look, updatedAt = updatedAt, deleted = deleted)

    @Test
    fun jsonRoundTrip_keepsEveryField() {
        val custom = HelmetState.Default
            .with(HelmetSegment.LeftEar, SegmentState(effect = HelmetEffect.Glitter, hue = 0.4f))
            .copy(message = "ROBOT ROCK")
            .look
        val presets = listOf(
            HelmetPreset(id = "stage", name = "  Stage   look  ", look = custom, updatedAt = 42L),
            preset("gone", 41L, deleted = true),
        )

        val decoded = HelmetPresets.fromJson(HelmetPresets.toJson(presets).toString(), now = 50L)

        assertEquals(2, decoded.size)
        assertEquals("Stage look", decoded[0].name)
        assertEquals(custom, decoded[0].look)
        assertEquals(42L, decoded[0].updatedAt)
        assertTrue(decoded[1].deleted)
    }

    @Test
    fun legacyV3Preset_isMigrated() {
        val legacyState = JSONObject()
            .put("__ver", 3)
            .put("z_0", JSONArray(listOf(1, 0.5, 1, 0.2, HelmetEffect.Plasma.id, 440, 4, 0, 44, 10)))
        val legacy = JSONArray().put(
            JSONObject().put("id", "old").put("name", "Old").put("updated_at", 7L).put("state", legacyState),
        )

        val decoded = HelmetPresets.fromJson(legacy.toString()).single()

        assertEquals("old", decoded.id)
        assertEquals(HelmetEffect.Plasma, decoded.look[HelmetSegment.Visor].effect)
        assertEquals(0.25f, decoded.look[HelmetSegment.Visor].speed, 0f)
        assertFalse(decoded.deleted)
    }

    @Test
    fun fromJson_skipsBadEntries() {
        val json = """[{"id":""}, {"id":"x"}, 5, {"id":"y","look":{"segments":[]}}]"""
        assertTrue(HelmetPresets.fromJson(json).isEmpty())
        assertTrue(HelmetPresets.fromJson("garbage").isEmpty())
    }

    @Test
    fun merge_keepsTheNewerCopyOfEachPreset() {
        val local = listOf(preset("a", 10, name = "Local A"), preset("b", 20))
        val remote = listOf(preset("a", 30, name = "Remote A"), preset("c", 5))

        val merged = HelmetPresets.merge(local, remote, now = 100)

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals("Remote A", merged.first { it.id == "a" }.name)
    }

    @Test
    fun merge_aNewerDeleteRemovesThePreset() {
        val local = listOf(preset("a", 10))
        val remote = listOf(preset("a", 20, deleted = true))

        val merged = HelmetPresets.merge(local, remote, now = 100)

        assertTrue(merged.single().deleted)
        assertTrue(HelmetPresets.active(merged).isEmpty())
        // The tombstone stops an old copy from coming back.
        assertTrue(HelmetPresets.merge(merged, local, now = 100).single().deleted)
    }

    @Test
    fun merge_aNewerEditRestoresADeletedPreset() {
        val merged = HelmetPresets.merge(listOf(preset("a", 10, deleted = true)), listOf(preset("a", 20)), now = 100)
        assertFalse(merged.single().deleted)
    }

    @Test
    fun normalize_dropsOldTombstonesAndBoundsActivePresets() {
        val now = HelmetPresets.TOMBSTONE_TTL_MILLIS * 2
        val active = (0 until HelmetPresets.MAX_PRESETS + 5).map { preset("p$it", now - it) }
        val oldTombstone = preset("old", 0, deleted = true)
        val newTombstone = preset("new", now - 1, deleted = true)

        val normalized = HelmetPresets.normalize(active + oldTombstone + newTombstone, now)

        assertEquals(HelmetPresets.MAX_PRESETS, HelmetPresets.active(normalized).size)
        assertEquals("p0", normalized.first().id)
        assertFalse(normalized.any { it.id == "old" })
        assertTrue(normalized.any { it.id == "new" })
    }

    @Test
    fun sanitizeName_trimsCollapsesAndBounds() {
        assertEquals("Untitled preset", HelmetPresets.sanitizeName("   "))
        assertEquals("A B", HelmetPresets.sanitizeName("  A \n B "))
        assertEquals(HelmetPresets.MAX_NAME_LENGTH, HelmetPresets.sanitizeName("x".repeat(100)).length)
    }
}
