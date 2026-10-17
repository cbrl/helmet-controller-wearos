package com.cbrl.pixelblaze.presentation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HelmetProtocolTest {
    @Test
    fun effectIds_areCompleteStableAndUnique() {
        assertEquals((0..25).toList(), HelmetEffects.entries.map { it.id }.sorted())
        assertEquals(20, HelmetEffects.Scanner.id)
        assertEquals(23, HelmetEffects.Eyes.id)
        assertEquals(24, HelmetEffects.Snake.id)
        assertEquals(25, HelmetEffects.Text.id)
    }

    @Test
    fun kotlinEffectIds_matchPixelblazeRendererTable() {
        val sourceFile = listOf(
            File("contrib/helmet_pattern.txt"),
            File("../contrib/helmet_pattern.txt"),
        ).firstOrNull(File::isFile) ?: error("Unable to locate helmet_pattern.txt")
        val source = sourceFile.readText()
        val registrations = Regex("SetRenderer\\((\\d+),\\s*([A-Za-z0-9_]+),")
            .findAll(source)
            .associate { it.groupValues[1].toInt() to it.groupValues[2] }

        assertEquals(HelmetEffects.entries.map { it.id }.toSet(), registrations.keys)
        assertEquals("preScanner", registrations.getValue(HelmetEffects.Scanner.id))
        assertEquals("preEyes", registrations.getValue(HelmetEffects.Eyes.id))
        assertEquals("preSnake", registrations.getValue(HelmetEffects.Snake.id))
        assertEquals("preRenderText", registrations.getValue(HelmetEffects.Text.id))
    }

    @Test
    fun matrixEffects_areOnlyOfferedForVisor() {
        assertTrue(HelmetEffects.Text in HelmetEffects.availableFor(HelmetSegments.Visor))
        assertFalse(HelmetEffects.Text in HelmetEffects.availableFor(HelmetSegments.LeftEar))
        assertTrue(HelmetEffects.Default in HelmetEffects.availableFor(HelmetSegments.LeftEar))
    }

    @Test
    fun messageSanitization_enforcesPixelblazeFontAndCapacity() {
        val source = "ABCé🙂" + "x".repeat(40)
        val sanitized = MessageState.sanitize(source)

        assertEquals(MessageState.MAX_LENGTH, sanitized.length)
        assertTrue(sanitized.startsWith("ABC??"))
        assertTrue(sanitized.all { it.code in 32..126 })
    }

    @Test
    fun stateParser_ignoresUnknownSegmentsAndBoundsUntrustedArrays() {
        val oversizedSegment = JSONArray().apply {
            repeat(12) { put(0.0) }
            put(SegmentVars.Effect.id, 999)
            put(SegmentVars.Brightness.id, 2)
            put(SegmentVars.Speed.id, -5)
        }
        val vars = JSONObject()
            .put("__ver", 3)
            .put("__n_segments", 7)
            .put("z_0", oversizedSegment)
            .put("z_99", JSONArray(listOf(1, 2, 3)))
            .put("message", JSONArray(listOf(65, 999, 66)))
            .put("messageLength", 100)

        val state = HelmetState.fromPixelblazeVars(JSONObject().put("vars", vars))!!
        val visor = state.getSegment(HelmetSegments.Visor)
        assertEquals(25.0, visor.getState(SegmentVars.Effect), 0.0)
        assertEquals(1.0, visor.getState(SegmentVars.Brightness), 0.0)
        assertEquals(0.05, visor.getState(SegmentVars.Speed), 0.0)
        assertEquals("A?B", state.getMessage().value)
    }

    @Test
    fun stateParser_rejectsIncompatiblePatternVersion() {
        val vars = JSONObject().put("__ver", 2).put("__n_segments", 7)
        assertNull(HelmetState.fromPixelblazeVars(JSONObject().put("vars", vars)))
    }

    @Test
    fun presetRoundTrip_preservesTheCompleteHelmetState() {
        val state = HelmetState().apply {
            HelmetSegments.entries.forEach { segment ->
                getSegment(segment).apply {
                    setState(SegmentVars.Size, 1.0)
                    setState(SegmentVars.Speed, 1.0)
                    setState(SegmentVars.Width, 1.0)
                    setState(SegmentVars.Height, 1.0)
                }
            }
            getSegment(HelmetSegments.Visor).apply {
                setState(SegmentVars.Effect, HelmetEffects.Text.id.toDouble())
                setState(SegmentVars.Hue, 0.72)
                setState(SegmentVars.Brightness, 0.61)
                setState(SegmentVars.Speed, 2.4)
            }
            getSegment(HelmetSegments.LeftEar).apply {
                setState(SegmentVars.Effect, HelmetEffects.Glitter.id.toDouble())
                setState(SegmentVars.Switch, 1.0)
            }
            getMessage().value = "ROBOT ROCK"
        }
        val preset = HelmetPreset(id = "stage", name = "  Stage   look  ", state = state, updatedAt = 42L)

        val decoded = HelmetPreset.listFromJSON(HelmetPreset.listToJSON(listOf(preset)).toString()).single()

        assertEquals("stage", decoded.id)
        assertEquals("Stage look", decoded.name)
        assertEquals(42L, decoded.updatedAt)
        assertEquals(state, decoded.state)
    }

    @Test
    fun presetCollection_isBoundedSortedAndSafelyNamed() {
        val presets = (0 until HelmetPreset.MAX_PRESETS + 5).map { index ->
            HelmetPreset(id = "$index", name = if (index == 0) "   " else "Preset $index", state = HelmetState(), updatedAt = index.toLong())
        }

        val decoded = HelmetPreset.listFromJSON(HelmetPreset.listToJSON(presets).toString())

        assertEquals(HelmetPreset.MAX_PRESETS, decoded.size)
        assertEquals((HelmetPreset.MAX_PRESETS + 4).toString(), decoded.first().id)
        assertTrue(decoded.all { it.name.isNotBlank() && it.name.length <= HelmetPreset.MAX_NAME_LENGTH })
        assertEquals("Untitled preset", HelmetPreset.sanitizeName("   "))
    }
}
