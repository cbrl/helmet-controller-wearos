package com.cbrl.pixelblaze.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.min

enum class SegmentVars(val id: Int) {
    Switch(0),
    Hue(1),
    Saturation(2),
    Brightness(3),
    Effect(4),
    Size(5),
    Speed(6),
    FTime(7),
    Width(8),
    Height(9);

    companion object {
        fun fromId(value: Int): SegmentVars? = entries.firstOrNull { it.id == value }
    }
}

enum class HelmetSegments(val id: Int, val prettyName: String) {
    Visor(0, "Visor"),
    VisorSideSeg1(1, "Visor Side Segment 1"),
    VisorSideSeg2(2, "Visor Side Segment 2"),
    VisorSideSeg3(3, "Visor Side Segment 3"),
    VisorSideSeg4(4, "Visor Side Segment 4"),
    LeftEar(5, "Left Ear"),
    RightEar(6, "Right Ear");

    companion object {
        fun fromId(value: Int): HelmetSegments? = entries.firstOrNull { it.id == value }
    }

    val varName get() = "z_$id"
    val supports2D get() = this == Visor
}

enum class HelmetEffects(
    val id: Int,
    val prettyName: String,
    val requires2D: Boolean = false,
    val supportsSpeed: Boolean = true,
) {
    Default(0, "Solid Color", supportsSpeed = false),
    Glitter(1, "Glitter"),
    RBounce(2, "Rainbow Bounce"),
    KITT(3, "KITT Scanner"),
    Breathe(4, "Breathe"),
    SlowColor(5, "Slow Color"),
    Snow(6, "Snow"),
    ChaserUp(7, "Chaser Up"),
    ChaserDown(8, "Chaser Down"),
    Strobe(9, "Strobe"),
    WipeUp(10, "Wipe Up"),
    WipeDown(11, "Wipe Down"),
    SpringyTheater(12, "Springy Theater"),
    ColorTwinkles(13, "Color Twinkles"),
    Plasma(14, "Plasma"),
    Ripples(15, "Ripples"),
    SpinCycle(16, "Spin Cycle"),
    RainbowUp(17, "Rainbow Up"),
    RainbowDown(18, "Rainbow Down"),
    Xorcery(19, "Xorcery"),
    Scanner(20, "Matrix Scanner", requires2D = true),
    TunnelSquares(21, "Tunnel Squares", requires2D = true),
    LineDancer(22, "Line Dancer", requires2D = true),
    Eyes(23, "Eyes", requires2D = true),
    Snake(24, "Snake", requires2D = true),
    Text(25, "Scrolling Text", requires2D = true);

    companion object {
        fun fromId(value: Int): HelmetEffects? = entries.firstOrNull { it.id == value }
        fun availableFor(segment: HelmetSegments): List<HelmetEffects> = entries.filter {
            !it.requires2D || segment.supports2D
        }
    }
}

data class HelmetMetadata(
    val version: Int = 3,
    val segmentCount: Int = 7,
    val maxSegments: Int = 7,
    val effectCount: Int = 26,
    val expectedPixelCount: Int = 496,
)

class IncompatibleHelmetPatternException(message: String) : IllegalStateException(message)

class MessageState(message: String = "") {
    companion object {
        const val MAX_LENGTH = 32

        fun fromJSON(json: JSONArray, length: Int): MessageState {
            val safeLength = length.coerceIn(0, min(MAX_LENGTH, json.length()))
            val value = buildString(safeLength) {
                repeat(safeLength) { index ->
                    val code = json.optDouble(index, '?'.code.toDouble()).toInt()
                    append(if (code in 32..126) code.toChar() else '?')
                }
            }
            return MessageState(value)
        }

        fun sanitize(message: String): String = message
            .take(MAX_LENGTH)
            .map { if (it.code in 32..126) it else '?' }
            .joinToString(separator = "")
    }

    var value: String = sanitize(message)
        set(newValue) {
            field = sanitize(newValue)
        }

    fun getStateArray(): List<Double> = value.map { it.code.toDouble() }

    override fun equals(other: Any?): Boolean =
        this === other || (other is MessageState && other.value == value)

    override fun hashCode(): Int = value.hashCode()

    fun clone(): MessageState = MessageState(value)
}

data class SegmentState(
    private val list: MutableList<Double> = MutableList(SegmentVars.entries.size) { 0.0 },
) {
    companion object {
        fun fromJSON(json: JSONArray): SegmentState {
            val state = SegmentState()
            repeat(min(json.length(), SegmentVars.entries.size)) { index ->
                SegmentVars.fromId(index)?.let { state.setState(it, json.optDouble(index, 0.0)) }
            }
            return state
        }
    }

    init {
        require(list.size == SegmentVars.entries.size)
    }

    fun getState(varType: SegmentVars): Double = list[varType.id]

    fun setState(varType: SegmentVars, value: Double) {
        list[varType.id] = when (varType) {
            SegmentVars.Switch -> if (value >= 0.5) 1.0 else 0.0
            SegmentVars.Hue -> ((value % 1.0) + 1.0) % 1.0
            SegmentVars.Saturation, SegmentVars.Brightness -> value.coerceIn(0.0, 1.0)
            SegmentVars.Effect -> floor(value).coerceIn(0.0, (HelmetEffects.entries.size - 1).toDouble())
            SegmentVars.Size -> floor(value).coerceAtLeast(0.0)
            SegmentVars.Speed -> value.coerceIn(0.05, 10.0)
            SegmentVars.FTime -> value.coerceIn(0.0, 30.0)
            SegmentVars.Width, SegmentVars.Height -> floor(value).coerceAtLeast(1.0)
        }
    }

    fun getStateArray(): List<Double> = list.toList()

    fun clone(): SegmentState = SegmentState(list.toMutableList())
}

data class HelmetState(
    private val segments: List<SegmentState> = List(HelmetSegments.entries.size) { SegmentState() },
    private val message: MessageState = MessageState(),
) {
    companion object {
        private val segmentRegex = Regex("""z_(\d+)""")

        fun fromPixelblazeVars(json: JSONObject): HelmetState? =
            json.optJSONObject("vars")?.let(::fromVarsObject)

        fun fromPersistedJson(value: String): HelmetState? = runCatching {
            fromVarsObject(JSONObject(value))
        }.getOrNull()

        private fun fromVarsObject(vars: JSONObject): HelmetState? {
            val version = vars.optInt("__ver", HelmetMetadata().version)
            val segmentCount = vars.optInt("__n_segments", HelmetSegments.entries.size)
            if (version != HelmetMetadata().version || segmentCount != HelmetSegments.entries.size) {
                return null
            }
            if (vars.has("__layout_valid") && vars.optDouble("__layout_valid", 0.0) < 0.5) {
                return null
            }

            val segments = MutableList(HelmetSegments.entries.size) { SegmentState() }
            vars.keys().asSequence()
                .mapNotNull { key -> segmentRegex.matchEntire(key)?.let { key to it } }
                .forEach { (key, match) ->
                    val segmentIndex = match.groupValues[1].toIntOrNull() ?: return@forEach
                    if (segmentIndex !in segments.indices) return@forEach
                    vars.optJSONArray(key)?.let { segments[segmentIndex] = SegmentState.fromJSON(it) }
                }

            val messageLength = vars.optInt("messageLength", 0)
            val message = vars.optJSONArray("message")
                ?.let { MessageState.fromJSON(it, messageLength) }
                ?: MessageState()
            return HelmetState(segments, message)
        }
    }

    fun getSegment(segment: HelmetSegments): SegmentState = segments[segment.id]

    fun getMessage(): MessageState = message

    fun toJSON(): JSONObject = JSONObject().apply {
        put("__ver", HelmetMetadata().version)
        put("__n_segments", HelmetSegments.entries.size)
        HelmetSegments.entries.forEach { segment ->
            put(segment.varName, JSONArray(segments[segment.id].getStateArray()))
        }
        put("message", JSONArray(message.getStateArray()))
        put("messageLength", message.value.length)
    }

    fun toJSONForSegment(segment: HelmetSegments): JSONObject = JSONObject().put(
        segment.varName,
        JSONArray(segments[segment.id].getStateArray()),
    )

    fun toJSONForMessage(): JSONObject = JSONObject()
        .put("message", JSONArray(message.getStateArray()))
        .put("messageLength", message.value.length)

    fun clone(): HelmetState = HelmetState(segments.map { it.clone() }, message.clone())
}

class HelmetController(private val scope: CoroutineScope) {
    val metadata = HelmetMetadata()

    private val _state = MutableStateFlow<HelmetState?>(null)
    val state: StateFlow<HelmetState?> = _state

    private val updateMutex = Mutex()
    private var device: PixelblazeDevice? = null
    private var observationJob: Job? = null

    suspend fun attach(device: PixelblazeDevice, restoredState: HelmetState? = null) {
        observationJob?.cancel()
        this.device = device
        device.setSendUpdates(false)
        device.setPatternByName("Helmet")

        val initialState = try {
            withTimeout(5_000L) {
                device.variableState.mapNotNull { HelmetState.fromPixelblazeVars(it) }.first()
            }
        } catch (_: TimeoutCancellationException) {
            throw IncompatibleHelmetPatternException(
                "Helmet pattern exports or 496-pixel layout are incompatible",
            )
        }
        _state.value = initialState

        if (restoredState != null) {
            device.setVars(restoredState.toJSON())
            _state.value = restoredState.clone()
        }

        observationJob = scope.launch {
            device.variableState.drop(1).mapNotNull { HelmetState.fromPixelblazeVars(it) }.collect {
                _state.value = it
            }
        }
    }

    fun detach() {
        observationJob?.cancel()
        observationJob = null
        device = null
    }

    fun currentState(): HelmetState? = _state.value?.clone()

    fun setSegmentEffect(segment: HelmetSegments, effect: HelmetEffects) = updateSegment(segment) {
        setState(SegmentVars.Effect, effect.id.toDouble())
    }

    fun setSegmentColor(segment: HelmetSegments, color: Color) {
        val hsv = floatArrayOf(0f, 0f, 0f)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        updateSegment(segment) {
            setState(SegmentVars.Hue, hsv[0].toDouble() / 360.0)
            setState(SegmentVars.Saturation, hsv[1].toDouble())
        }
    }

    fun setSegmentBrightness(segment: HelmetSegments, brightness: Float) = updateSegment(segment) {
        setState(SegmentVars.Brightness, brightness.toDouble())
    }

    fun setSegmentSpeed(segment: HelmetSegments, speed: Float) = updateSegment(segment) {
        setState(SegmentVars.Speed, speed.toDouble())
    }

    fun setSegmentFadeTime(segment: HelmetSegments, seconds: Float) = updateSegment(segment) {
        setState(SegmentVars.FTime, seconds.toDouble())
    }

    fun setSegmentEnabled(segment: HelmetSegments, enabled: Boolean) = updateSegment(segment) {
        setState(SegmentVars.Switch, if (enabled) 1.0 else 0.0)
    }

    fun setAllSegmentsEnabled(enabled: Boolean) {
        scope.launch {
            updateMutex.withLock {
                val current = _state.value?.clone() ?: return@withLock
                HelmetSegments.entries.forEach { segment ->
                    current.getSegment(segment).setState(
                        SegmentVars.Switch,
                        if (enabled) 1.0 else 0.0,
                    )
                }
                _state.value = current
                device?.setVars(current.toJSON())
            }
        }
    }

    fun setMessageText(message: String) {
        scope.launch {
            updateMutex.withLock {
                val current = _state.value?.clone() ?: return@withLock
                current.getMessage().value = message
                _state.value = current
                device?.setVars(current.toJSONForMessage())
            }
        }
    }

    private fun updateSegment(segment: HelmetSegments, transform: SegmentState.() -> Unit) {
        scope.launch {
            updateMutex.withLock {
                val current = _state.value?.clone() ?: return@withLock
                current.getSegment(segment).transform()
                _state.value = current
                device?.setVars(current.toJSONForSegment(segment))
            }
        }
    }
}
