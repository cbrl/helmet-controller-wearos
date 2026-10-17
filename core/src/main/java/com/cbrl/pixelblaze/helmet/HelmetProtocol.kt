package com.cbrl.pixelblaze.helmet

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

/** The Pixelblaze has no pattern named [HelmetProtocol.PATTERN_NAME]. */
class PatternMissingException(message: String) : Exception(message)

/** The Pixelblaze runs a Helmet pattern that this app cannot use. */
class PatternIncompatibleException(message: String) : Exception(message)

/**
 * Protocol v4 of the helmet pattern (`contrib/helmet_pattern.txt`), and the JSON format that the
 * apps use to store helmet states and presets.
 */
object HelmetProtocol {
    const val VERSION = 4
    const val PATTERN_NAME = "Helmet"

    // Exported variables of the pattern
    const val VAR_VERSION = "__ver"
    const val VAR_SEGMENT_COUNT = "__n_segments"
    const val VAR_EXPECTED_PIXELS = "__expected_pixels"
    const val VAR_LAYOUT_VALID = "__layout_valid"
    const val VAR_LAYOUT = "__layout"
    const val VAR_MASTER = "__master"
    const val VAR_BLACKOUT = "__blackout"
    const val VAR_MASTER_FADE = "__master_fade"
    const val VAR_SESSION = "__session"
    const val VAR_MESSAGE_REV = "__msg_rev"
    const val VAR_MESSAGE = "message"
    const val VAR_MESSAGE_LENGTH = "messageLength"

    // Field indices in the segment control arrays z_0 .. z_6
    const val FIELD_ON = 0
    const val FIELD_HUE = 1
    const val FIELD_SAT = 2
    const val FIELD_BRI = 3
    const val FIELD_EFFECT = 4
    const val FIELD_SPEED = 5
    const val FIELD_FADE = 6
    const val CONTROL_COUNT = 7

    /** Session tokens and message revisions stay below this value. Pixelblaze numbers are 16.16 fixed point. */
    const val MAX_COUNTER = 30_000

    fun segmentVar(segment: HelmetSegment): String = "z_${segment.id}"

    /** The helmet state in one getVars reply. [session] is 0 when the pattern restarted. */
    data class Snapshot(
        val state: HelmetState,
        val layout: HelmetLayout,
        val session: Int,
        val messageRev: Int,
    )

    sealed interface Decoded {
        data class Valid(val snapshot: Snapshot) : Decoded

        /** The pattern is not protocol v4. [found] is null when the pattern has no version. */
        data class WrongVersion(val found: Int?) : Decoded

        /** The Pixelblaze pixel count does not match the helmet layout. */
        data class LayoutInvalid(val expectedPixels: Int) : Decoded
    }

    /** Reads the exported variables of a getVars reply. Values are clamped to valid ranges. */
    fun decode(vars: JSONObject): Decoded {
        if (!vars.has(VAR_VERSION)) return Decoded.WrongVersion(null)
        val version = vars.optDouble(VAR_VERSION, 0.0).roundToInt()
        if (version != VERSION) return Decoded.WrongVersion(version)
        if (vars.optDouble(VAR_LAYOUT_VALID, 1.0) < 0.5) {
            return Decoded.LayoutInvalid(vars.optInt(VAR_EXPECTED_PIXELS, HelmetLayout.Default.pixelCount))
        }

        val segments = HelmetSegment.entries.map { segment ->
            vars.optJSONArray(segmentVar(segment))?.let(::decodeSegment) ?: SegmentState()
        }
        val message = vars.optJSONArray(VAR_MESSAGE)
            ?.let { decodeMessage(it, vars.optInt(VAR_MESSAGE_LENGTH, 0)) }
            .orEmpty()
        val state = HelmetState(
            segments = segments,
            message = message,
            master = vars.optDouble(VAR_MASTER, 1.0).toFloat().coerceIn(0f, 1f),
            blackout = vars.optDouble(VAR_BLACKOUT, 0.0) >= 0.5,
        )
        return Decoded.Valid(
            Snapshot(
                state = state,
                layout = decodeLayout(vars.optJSONArray(VAR_LAYOUT)) ?: HelmetLayout.Default,
                session = vars.optDouble(VAR_SESSION, 0.0).roundToInt(),
                messageRev = vars.optDouble(VAR_MESSAGE_REV, 0.0).roundToInt(),
            ),
        )
    }

    fun decodeSegment(array: JSONArray): SegmentState = SegmentState(
        on = array.optDouble(FIELD_ON, 1.0) >= 0.5,
        hue = array.optDouble(FIELD_HUE, 0.0).toFloat(),
        saturation = array.optDouble(FIELD_SAT, 1.0).toFloat(),
        brightness = array.optDouble(FIELD_BRI, 1.0).toFloat(),
        effect = HelmetEffect.fromId(array.optDouble(FIELD_EFFECT, 0.0).toInt()) ?: HelmetEffect.SolidColor,
        speed = array.optDouble(FIELD_SPEED, 1.0).toFloat(),
        fadeSeconds = array.optDouble(FIELD_FADE, 0.0).toFloat(),
    ).normalized()

    fun encodeSegment(state: SegmentState): JSONArray {
        val normalized = state.normalized()
        return JSONArray().apply {
            put(if (normalized.on) 1 else 0)
            put(wire(normalized.hue))
            put(wire(normalized.saturation))
            put(wire(normalized.brightness))
            put(normalized.effect.id)
            put(wire(normalized.speed))
            put(wire(normalized.fadeSeconds))
        }
    }

    private fun decodeLayout(array: JSONArray?): HelmetLayout? {
        if (array == null || array.length() < HelmetSegment.entries.size * 3) return null
        val segments = HelmetSegment.entries.map { segment ->
            val base = segment.id * 3
            val size = array.optInt(base, -1)
            val width = array.optInt(base + 1, 0)
            val height = array.optInt(base + 2, 0)
            if (size < 0 || width < 1 || height < 1) return null
            SegmentLayout(size, width, height)
        }
        return HelmetLayout(segments)
    }

    private fun decodeMessage(codes: JSONArray, length: Int): String {
        val safeLength = length.coerceIn(0, min(HelmetMessage.MAX_LENGTH, codes.length()))
        return buildString(safeLength) {
            repeat(safeLength) { index ->
                val code = codes.optDouble(index, '?'.code.toDouble()).toInt()
                append(if (code in 32..126) code.toChar() else '?')
            }
        }
    }

    // Variables for setVars

    fun segmentVars(segment: HelmetSegment, state: SegmentState): Map<String, Any> =
        mapOf(segmentVar(segment) to encodeSegment(state))

    /** The message, padded with spaces to the full array, and the next revision so the text restarts. */
    fun messageVars(message: String, rev: Int): Map<String, Any> {
        val text = HelmetMessage.sanitize(message)
        val codes = JSONArray()
        repeat(HelmetMessage.MAX_LENGTH) { index -> codes.put(text.getOrElse(index) { ' ' }.code) }
        return mapOf(
            VAR_MESSAGE to codes,
            VAR_MESSAGE_LENGTH to text.length,
            VAR_MESSAGE_REV to rev,
        )
    }

    fun masterVars(master: Float, blackout: Boolean): Map<String, Any> = mapOf(
        VAR_MASTER to wire(master.coerceIn(0f, 1f)),
        VAR_BLACKOUT to if (blackout) 1 else 0,
    )

    /** Every app-owned variable for [state]. */
    fun stateVars(state: HelmetState, messageRev: Int): Map<String, Any> = buildMap {
        HelmetSegment.entries.forEach { putAll(segmentVars(it, state[it])) }
        putAll(messageVars(state.message, messageRev))
        putAll(masterVars(state.master, state.blackout))
    }

    /** The next session token or message revision: 1 .. [MAX_COUNTER]. */
    fun nextCounter(value: Int): Int = (value.coerceAtLeast(0) % MAX_COUNTER) + 1

    /** Rounds a value for the wire. The Pixelblaze stores 16.16 fixed point, so more digits are noise. */
    private fun wire(value: Float): Double = Math.round(value * 100_000.0) / 100_000.0

    // Storage format for states and presets

    private const val STORAGE_FORMAT = 4
    private const val LEGACY_VERSION = 3

    fun stateToJson(state: HelmetState): JSONObject = lookToJson(state.look)
        .put("master", wire(state.master))
        .put("blackout", state.blackout)

    /** Reads [stateToJson] output, or a helmet state that an app with protocol v3 stored. */
    fun stateFromJson(json: JSONObject): HelmetState? {
        val look = lookFromJson(json) ?: return null
        return HelmetState(
            segments = look.segments,
            message = look.message,
            master = json.optDouble("master", 1.0).toFloat().coerceIn(0f, 1f),
            blackout = json.optBoolean("blackout", false),
        )
    }

    fun stateFromJson(value: String): HelmetState? =
        runCatching { stateFromJson(JSONObject(value)) }.getOrNull()

    fun lookToJson(look: HelmetLook): JSONObject = JSONObject()
        .put("format", STORAGE_FORMAT)
        .put("segments", JSONArray().apply { look.segments.forEach { put(segmentToJson(it)) } })
        .put("message", look.message)

    /** Reads [lookToJson] output, or the look in a v3 state or preset. */
    fun lookFromJson(json: JSONObject): HelmetLook? = when {
        json.has("segments") -> {
            val array = json.optJSONArray("segments")
            if (array == null || array.length() != HelmetSegment.entries.size) {
                null
            } else {
                val segments = List(array.length()) { index ->
                    array.optJSONObject(index)?.let(::segmentFromJson) ?: return null
                }
                HelmetLook(segments, HelmetMessage.sanitize(json.optString("message")))
            }
        }
        json.has("z_0") -> legacyLookFromJson(json)
        else -> null
    }

    private fun segmentToJson(state: SegmentState): JSONObject = JSONObject()
        .put("on", state.on)
        .put("hue", wire(state.hue))
        .put("sat", wire(state.saturation))
        .put("bri", wire(state.brightness))
        .put("effect", state.effect.id)
        .put("speed", wire(state.speed))
        .put("fade", wire(state.fadeSeconds))

    private fun segmentFromJson(json: JSONObject): SegmentState = SegmentState(
        on = json.optBoolean("on", true),
        hue = json.optDouble("hue", 0.0).toFloat(),
        saturation = json.optDouble("sat", 1.0).toFloat(),
        brightness = json.optDouble("bri", 1.0).toFloat(),
        effect = HelmetEffect.fromId(json.optInt("effect", 0)) ?: HelmetEffect.SolidColor,
        speed = json.optDouble("speed", 1.0).toFloat(),
        fadeSeconds = json.optDouble("fade", 0.0).toFloat(),
    ).normalized()

    /**
     * Converts a v3 look. v3 arrays were [switch, hue, sat, bri, effect, size, speed, fade, width,
     * height]. v4 drops the layout fields. v3 speed was a period multiplier for every effect except
     * Scrolling Text, so the rate is 1 / speed.
     */
    private fun legacyLookFromJson(json: JSONObject): HelmetLook? {
        if (json.optInt(VAR_VERSION, LEGACY_VERSION) != LEGACY_VERSION) return null
        val segments = HelmetSegment.entries.map { segment ->
            json.optJSONArray(segmentVar(segment))?.let(::legacySegment) ?: SegmentState()
        }
        val message = json.optJSONArray(VAR_MESSAGE)
            ?.let { decodeMessage(it, json.optInt(VAR_MESSAGE_LENGTH, 0)) }
            .orEmpty()
        return HelmetLook(segments, message)
    }

    private fun legacySegment(array: JSONArray): SegmentState {
        val effect = HelmetEffect.fromId(array.optDouble(4, 0.0).toInt()) ?: HelmetEffect.SolidColor
        val legacySpeed = array.optDouble(6, 1.0).toFloat().coerceIn(SegmentState.MIN_SPEED, SegmentState.MAX_SPEED)
        return SegmentState(
            on = array.optDouble(0, 1.0) >= 0.5,
            hue = array.optDouble(1, 0.0).toFloat(),
            saturation = array.optDouble(2, 1.0).toFloat(),
            brightness = array.optDouble(3, 1.0).toFloat(),
            effect = effect,
            speed = if (effect == HelmetEffect.Text) legacySpeed else 1f / legacySpeed,
            fadeSeconds = array.optDouble(7, 0.0).toFloat(),
        ).normalized()
    }
}
