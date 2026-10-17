package com.cbrl.pixelblaze.helmet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HelmetProtocolTest {
    private val pattern: String by lazy {
        listOf(File("contrib/helmet_pattern.txt"), File("../contrib/helmet_pattern.txt"))
            .firstOrNull(File::isFile)
            ?.readText()
            ?: error("Unable to locate helmet_pattern.txt")
    }

    private fun patternInt(name: String): Int =
        Regex("""var $name = (\d+)""").find(pattern)?.groupValues?.get(1)?.toInt()
            ?: error("Pattern has no 'var $name = <number>'")

    // Contract between the Kotlin protocol and contrib/helmet_pattern.txt

    @Test
    fun pattern_versionMatches() {
        assertEquals(HelmetProtocol.VERSION, patternInt(HelmetProtocol.VAR_VERSION))
    }

    @Test
    fun pattern_exportsEveryProtocolVariable() {
        listOf(
            HelmetProtocol.VAR_VERSION,
            HelmetProtocol.VAR_SEGMENT_COUNT,
            HelmetProtocol.VAR_EXPECTED_PIXELS,
            HelmetProtocol.VAR_LAYOUT_VALID,
            HelmetProtocol.VAR_LAYOUT,
            HelmetProtocol.VAR_MASTER,
            HelmetProtocol.VAR_BLACKOUT,
            HelmetProtocol.VAR_MASTER_FADE,
            HelmetProtocol.VAR_SESSION,
            HelmetProtocol.VAR_MESSAGE_REV,
            HelmetProtocol.VAR_MESSAGE,
            HelmetProtocol.VAR_MESSAGE_LENGTH,
        ).forEach { name ->
            assertTrue("pattern exports $name", Regex("""export var $name\b""").containsMatchIn(pattern))
        }
        HelmetSegment.entries.forEach { segment ->
            val name = HelmetProtocol.segmentVar(segment)
            assertTrue("pattern exports $name", pattern.contains("export var $name = array(__n_controls)"))
        }
        assertEquals(HelmetSegment.entries.size, patternInt("__max_segments"))
    }

    @Test
    fun pattern_controlFieldIndicesMatch() {
        assertEquals(HelmetProtocol.FIELD_ON, patternInt("__on"))
        assertEquals(HelmetProtocol.FIELD_HUE, patternInt("__hue"))
        assertEquals(HelmetProtocol.FIELD_SAT, patternInt("__sat"))
        assertEquals(HelmetProtocol.FIELD_BRI, patternInt("__bri"))
        assertEquals(HelmetProtocol.FIELD_EFFECT, patternInt("__effect"))
        assertEquals(HelmetProtocol.FIELD_SPEED, patternInt("__speed"))
        assertEquals(HelmetProtocol.FIELD_FADE, patternInt("__ftime"))
        assertEquals(HelmetProtocol.CONTROL_COUNT, patternInt("__n_controls"))
    }

    @Test
    fun pattern_rendererTableMatchesEffects() {
        val registrations = Regex("""SetRenderer\((\d+),\s*([A-Za-z0-9_]+),""")
            .findAll(pattern)
            .associate { it.groupValues[1].toInt() to it.groupValues[2] }

        assertEquals(HelmetEffect.entries.map { it.id }.toSet(), registrations.keys)
        assertEquals(HelmetEffect.entries.size, patternInt("__n_effects"))
        assertEquals("preScanner", registrations.getValue(HelmetEffect.Scanner.id))
        assertEquals("preEyes", registrations.getValue(HelmetEffect.Eyes.id))
        assertEquals("preSnake", registrations.getValue(HelmetEffect.Snake.id))
        assertEquals("preRenderText", registrations.getValue(HelmetEffect.Text.id))

        val first2D = patternInt("__first_2d_effect")
        HelmetEffect.entries.forEach { effect ->
            assertEquals("${effect.label} requires2D", effect.id >= first2D, effect.requires2D)
        }
    }

    @Test
    fun pattern_layoutMatchesDefaultLayout() {
        val layout = Regex("""SetSegSize\((\d+), (\d+), (\d+)\)""")
            .findAll(pattern)
            .map { it.groupValues.drop(1).map(String::toInt) }
            .sortedBy { it[0] }
            .map { (_, size, width) -> SegmentLayout(size, width, maxOf(1, size / width)) }
            .toList()
        assertEquals(HelmetLayout.Default.segments, layout)
        assertEquals(HelmetLayout.Default.pixelCount, patternInt(HelmetProtocol.VAR_EXPECTED_PIXELS))
    }

    // Effects

    @Test
    fun effectIds_areCompleteStableAndUnique() {
        assertEquals((0..25).toList(), HelmetEffect.entries.map { it.id }.sorted())
        assertEquals(20, HelmetEffect.Scanner.id)
        assertEquals(25, HelmetEffect.Text.id)
    }

    @Test
    fun matrixEffects_areOnlyOfferedForTheVisor() {
        assertTrue(HelmetEffect.Text in HelmetEffect.availableFor(HelmetSegment.Visor))
        assertFalse(HelmetEffect.Text in HelmetEffect.availableFor(HelmetSegment.LeftEar))
        assertTrue(HelmetEffect.SolidColor in HelmetEffect.availableFor(HelmetSegment.LeftEar))
    }

    // Decoding getVars replies

    @Test
    fun decode_readsAValidV4Reply() {
        val state = HelmetState.Default
            .with(HelmetSegment.LeftEar, SegmentState(on = false, hue = 0.25f, effect = HelmetEffect.Glitter, speed = 2f))
            .copy(message = "ROBOT ROCK", master = 0.5f, blackout = true)

        val decoded = HelmetProtocol.decode(patternVars(state, session = 1234, messageRev = 9))

        val snapshot = (decoded as HelmetProtocol.Decoded.Valid).snapshot
        assertEquals(state, snapshot.state)
        assertEquals(HelmetLayout.Default, snapshot.layout)
        assertEquals(1234, snapshot.session)
        assertEquals(9, snapshot.messageRev)
    }

    @Test
    fun decode_clampsUntrustedValues() {
        val vars = patternVars(HelmetState.Default, session = 1)
            .put("z_0", JSONArray(listOf(1, -0.25, 7, 2, 999, -5, 99)))
            .put(HelmetProtocol.VAR_MESSAGE, JSONArray(listOf(65, 999, 66)))
            .put(HelmetProtocol.VAR_MESSAGE_LENGTH, 100)
            .put(HelmetProtocol.VAR_MASTER, 3)

        val snapshot = (HelmetProtocol.decode(vars) as HelmetProtocol.Decoded.Valid).snapshot
        val visor = snapshot.state[HelmetSegment.Visor]
        assertEquals(0.75f, visor.hue, 1e-6f)
        assertEquals(1f, visor.saturation, 0f)
        assertEquals(1f, visor.brightness, 0f)
        assertEquals(HelmetEffect.SolidColor, visor.effect)
        assertEquals(SegmentState.MIN_SPEED, visor.speed, 0f)
        assertEquals(SegmentState.MAX_FADE_SECONDS, visor.fadeSeconds, 0f)
        assertEquals("A?B", snapshot.state.message)
        assertEquals(1f, snapshot.state.master, 0f)
    }

    @Test
    fun decode_rejectsOtherPatternsAndVersions() {
        assertEquals(HelmetProtocol.Decoded.WrongVersion(null), HelmetProtocol.decode(JSONObject().put("speed", 1)))
        assertEquals(
            HelmetProtocol.Decoded.WrongVersion(3),
            HelmetProtocol.decode(patternVars(HelmetState.Default, 1).put(HelmetProtocol.VAR_VERSION, 3)),
        )
        assertEquals(
            HelmetProtocol.Decoded.LayoutInvalid(496),
            HelmetProtocol.decode(patternVars(HelmetState.Default, 1).put(HelmetProtocol.VAR_LAYOUT_VALID, 0)),
        )
    }

    @Test
    fun decode_usesTheDefaultLayoutWhenTheLayoutIsBad() {
        val vars = patternVars(HelmetState.Default, 1).put(HelmetProtocol.VAR_LAYOUT, JSONArray(listOf(1, 0, 1)))
        val snapshot = (HelmetProtocol.decode(vars) as HelmetProtocol.Decoded.Valid).snapshot
        assertEquals(HelmetLayout.Default, snapshot.layout)
    }

    // Encoding setVars values

    @Test
    fun encodeSegment_roundTrips() {
        val state = SegmentState(on = false, hue = 0.72f, saturation = 0.9f, brightness = 0.15f, effect = HelmetEffect.Eyes, speed = 2.5f, fadeSeconds = 1.5f)
        val encoded = HelmetProtocol.encodeSegment(state)
        assertEquals(HelmetProtocol.CONTROL_COUNT, encoded.length())
        assertEquals(0, encoded.getInt(HelmetProtocol.FIELD_ON))
        assertEquals(HelmetEffect.Eyes.id, encoded.getInt(HelmetProtocol.FIELD_EFFECT))
        assertEquals(0.15, encoded.getDouble(HelmetProtocol.FIELD_BRI), 0.0)
        assertEquals(state, HelmetProtocol.decodeSegment(encoded))
    }

    @Test
    fun messageVars_padTheArrayAndSanitize() {
        val vars = HelmetProtocol.messageVars("Hé!", rev = 5)
        val codes = vars.getValue(HelmetProtocol.VAR_MESSAGE) as JSONArray
        assertEquals(32, codes.length())
        assertEquals(listOf(72, 63, 33, 32), List(4) { codes.getInt(it) })
        assertEquals(3, vars[HelmetProtocol.VAR_MESSAGE_LENGTH])
        assertEquals(5, vars[HelmetProtocol.VAR_MESSAGE_REV])
    }

    @Test
    fun stateVars_containEveryAppOwnedVariable() {
        val vars = HelmetProtocol.stateVars(HelmetState.Default, messageRev = 2)
        val expected = HelmetSegment.entries.map(HelmetProtocol::segmentVar).toSet() + setOf(
            HelmetProtocol.VAR_MESSAGE,
            HelmetProtocol.VAR_MESSAGE_LENGTH,
            HelmetProtocol.VAR_MESSAGE_REV,
            HelmetProtocol.VAR_MASTER,
            HelmetProtocol.VAR_BLACKOUT,
        )
        assertEquals(expected, vars.keys)
    }

    @Test
    fun nextCounter_staysInRange() {
        assertEquals(1, HelmetProtocol.nextCounter(0))
        assertEquals(6, HelmetProtocol.nextCounter(5))
        assertEquals(1, HelmetProtocol.nextCounter(HelmetProtocol.MAX_COUNTER))
        assertEquals(1, HelmetProtocol.nextCounter(-4))
    }

    @Test
    fun messageSanitization_enforcesFontAndCapacity() {
        val sanitized = HelmetMessage.sanitize("ABCé🙂" + "x".repeat(40))
        assertEquals(HelmetMessage.MAX_LENGTH, sanitized.length)
        assertTrue(sanitized.startsWith("ABC??x"))
        assertTrue(sanitized.all { it.code in 32..126 })
    }

    // Storage format

    @Test
    fun storedState_roundTrips() {
        val state = HelmetState.Default
            .with(HelmetSegment.Visor, SegmentState(effect = HelmetEffect.Text, hue = 0.33f, speed = 3f))
            .copy(message = "HELLO", master = 0.6f, blackout = true)
        assertEquals(state, HelmetProtocol.stateFromJson(HelmetProtocol.stateToJson(state).toString()))
    }

    @Test
    fun storedState_rejectsBadInput() {
        assertNull(HelmetProtocol.stateFromJson("not json"))
        assertNull(HelmetProtocol.stateFromJson("{}"))
        assertNull(HelmetProtocol.stateFromJson("""{"segments":[]}"""))
    }

    @Test
    fun legacyV3State_isMigrated() {
        // v3 arrays: [switch, hue, sat, bri, effect, size, speed, fade, width, height]
        val legacy = JSONObject()
            .put("__ver", 3)
            .put("__n_segments", 7)
            .put("z_0", JSONArray(listOf(1, 0.5, 0.9, 0.15, HelmetEffect.Text.id, 440, 2, 1.5, 44, 10)))
            .put("z_1", JSONArray(listOf(0, 0.1, 1, 1, HelmetEffect.RainbowDown.id, 10, 2, 0, 10, 1)))
            .put("message", JSONArray(listOf(72, 73)))
            .put("messageLength", 2)

        val state = HelmetProtocol.stateFromJson(legacy.toString())

        assertNotNull(state)
        val visor = state!![HelmetSegment.Visor]
        assertEquals(HelmetEffect.Text, visor.effect)
        assertEquals("Scrolling Text speed was already a rate", 2f, visor.speed, 0f)
        assertEquals(1.5f, visor.fadeSeconds, 0f)
        val side = state[HelmetSegment.VisorSide1]
        assertFalse(side.on)
        assertEquals("v3 speed 2 meant half as fast", 0.5f, side.speed, 0f)
        assertEquals("HI", state.message)
        assertEquals(1f, state.master, 0f)
        assertFalse(state.blackout)
    }
}
