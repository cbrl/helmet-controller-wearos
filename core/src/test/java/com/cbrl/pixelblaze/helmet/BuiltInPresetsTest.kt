package com.cbrl.pixelblaze.helmet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInPresetsTest {
    private val now = BuiltInPresets.presets.first().updatedAt + 1_000L

    @Test
    fun presets_areValidForThePattern() {
        val presets = BuiltInPresets.presets
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        presets.forEach { preset ->
            assertEquals(HelmetPresets.sanitizeName(preset.name), preset.name)
            assertEquals(HelmetMessage.sanitize(preset.look.message), preset.look.message)
            HelmetSegment.entries.forEach { segment ->
                val state = preset.look[segment]
                assertEquals("${preset.name} $segment", state.normalized(), state)
                assertTrue("${preset.name} $segment", state.effect in HelmetEffect.availableFor(segment))
            }
        }
    }

    @Test
    fun presets_surviveJson() {
        val decoded = HelmetPresets.fromJson(HelmetPresets.toJson(BuiltInPresets.presets).toString(), now)
        assertEquals(BuiltInPresets.presets, decoded)
    }

    @Test
    fun addNew_putsThePresetsAfterTheUserPresets() {
        val own = HelmetPreset(id = "own", name = "Own", look = HelmetState.Default.look, updatedAt = now)

        val presets = BuiltInPresets.addNew(listOf(own), seenVersion = 0, now = now)

        assertEquals(listOf("own") + BuiltInPresets.presets.map { it.id }, presets.map { it.id })
    }

    @Test
    fun addNew_keepsChangesAndDeletes() {
        val (first, second) = BuiltInPresets.presets
        val renamed = first.copy(name = "Mine", updatedAt = now)
        val deleted = second.copy(deleted = true, updatedAt = now)

        val presets = BuiltInPresets.addNew(listOf(renamed, deleted), seenVersion = 0, now = now)

        assertEquals("Mine", presets.single { it.id == first.id }.name)
        assertTrue(presets.single { it.id == second.id }.deleted)
        assertEquals(BuiltInPresets.presets.size, presets.size)
    }

    @Test
    fun addNew_addsNothingForTheCurrentVersion() {
        assertTrue(BuiltInPresets.addNew(emptyList(), BuiltInPresets.VERSION, now).isEmpty())
    }
}
