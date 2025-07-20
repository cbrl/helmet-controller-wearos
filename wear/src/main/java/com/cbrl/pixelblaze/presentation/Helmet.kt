package com.cbrl.pixelblaze.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

enum class SegmentVars {
    Switch,
    Hue,
    Saturation,
    Brightness,
    Effect,
    Size,
    Speed,
    FTime,
    Width,
    Height;

    companion object {
        fun fromInt(value: Int) = entries.first { it.ordinal == value }
    }
}

enum class HelmetSegments(val prettyName: String) {
    Visor("Visor"),
    VisorSideSeg1("Visor Side Segment 1"),
    VisorSideSeg2("Visor Side Segment 2"),
    VisorSideSeg3("Visor Side Segment 3"),
    VisorSideSeg4("Visor Side Segment 4"),
    LeftEar("Left Ear"),
    RightEar("Right Ear");

    companion object {
        fun fromInt(value: Int) = entries.first { it.ordinal == value }
    }

    val varName get() = "z_$ordinal"
}

enum class HelmetEffects {
    Default,
    Glitter,
    RBounce,
    KITT,
    Breathe,
    SlowColor,
    Snow,
    ChaserUp,
    ChaserDown,
    Strobe,
    WipeUp,
    WipeDown,
    SpringyTheater,
    ColorTwinkles,
    Plasma,
    Ripples,
    SpinCycle,
    RainbowUp,
    RainbowDown,
    Xorcery,
    Eyes,
    Snake,
    Text;

    companion object {
        fun fromInt(value: Int) = entries.first { it.ordinal == value }
    }
}

data class HelmetMetadata(
    val segmentCount: Int = 7,
    val maxSegments: Int = 12,
    val effectCount: Int = 26,
)

class MessageState(message: String = "") {
    companion object {
        private const val MAX_LENGTH = 24

        fun fromJSON(json: JSONArray, length: Int): MessageState {
            val array = CharArray(length)

            for (i in 0..<min(length, json.length())) {
                array[i] = json.getDouble(i).toInt().toChar()
            }

            return MessageState(array.concatToString())
        }
    }

    var value: String = message.slice(0..<min(message.length, MAX_LENGTH))
        set(newValue) {
            field = newValue.slice(0..<min(newValue.length, MAX_LENGTH))
        }

    fun getStateArray(): List<Double> {
        return value.toCharArray().map { it.code.toDouble() }
    }

    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is MessageState && other.value == this.value)
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    fun clone(): MessageState {
        return MessageState(value)
    }
}

data class SegmentState(
    private val list: MutableList<Double> = MutableList(SegmentVars.entries.size) { 0.0 }
) {
    companion object {
        fun fromJSON(json: JSONArray): SegmentState {
            val state = SegmentState()

            for (i in 0..<json.length()) {
                state.setState(SegmentVars.fromInt(i), json.getDouble(i))
            }

            return state
        }
    }

    init {
        assert(list.size == SegmentVars.entries.size)
    }

    fun getState(varType: SegmentVars): Double {
        return list[varType.ordinal]
    }

    fun setState(varType: SegmentVars, value: Double) {
        list[varType.ordinal] = value
    }

    fun getStateArray(): List<Double> {
        return list
    }

    fun clone(): SegmentState {
        return SegmentState(list.map { it } as MutableList)
    }
}

data class HelmetState(
    private val segments: List<SegmentState> = MutableList(HelmetSegments.entries.size) { SegmentState() },
    private val message: MessageState = MessageState()
) {
    companion object {
        fun fromPixelblazeVars(json: JSONObject): HelmetState? {
            val segmentRegex = Regex("""z_(\d+)""")

            // TODO: additional input validation on "z_N" arrays and "message" array
            json.optJSONObject("vars")?.let { vars ->
                val segments = MutableList(HelmetSegments.entries.size) { SegmentState() }
                var message = MessageState()

                // Get 'z_N' arrays
                vars.keys().asSequence()
                    .filter { key -> segmentRegex.matches(key) }
                    .forEach { key ->
                        val segIdx = segmentRegex.matchEntire(key)!!.groups[1]!!.value.toInt()

                        vars.optJSONArray(key)?.let { array ->
                            segments[segIdx] = SegmentState.fromJSON(array)
                        }
                    }

                // Get 'message' array
                val messageLen = vars.optInt("messageLength")
                val msgArray = vars.optJSONArray("message")
                if (messageLen > 0 && msgArray != null) {
                    message = MessageState.fromJSON(msgArray, messageLen)
                }

                return HelmetState(segments, message)
            }

            return null
        }
    }

    fun getSegment(segment: HelmetSegments): SegmentState {
        return segments[segment.ordinal]
    }

    fun getMessage(): MessageState {
        return message
    }

    fun toJSON(): JSONObject {
        val obj = JSONObject()

        for (segment in HelmetSegments.entries) {
            obj.put(segment.varName, JSONArray(segments[segment.ordinal].getStateArray()))
        }

        obj.put("message", JSONArray(message.getStateArray()))
        obj.put("messageLength", message.value.length)

        return obj
    }

    fun toJSONForSegment(segment: HelmetSegments): JSONObject {
        val obj = JSONObject()

        obj.put(segment.varName, JSONArray(segments[segment.ordinal].getStateArray()))

        return obj
    }

    fun clone(): HelmetState {
        return HelmetState(segments.map { it.clone() }, message.clone())
    }
}

class HelmetController(
    private val scope: CoroutineScope
) {
    val metadata = HelmetMetadata()

    private val _state = MutableSharedFlow<HelmetState>(replay = 1) //need replay to allow fetching the last value
    val state: SharedFlow<HelmetState> = _state

    fun start(device: PixelblazeDevice): Flow<Unit> = callbackFlow {
        scope.launch {
            setupHelmet(device)
            send(Unit)
            runSendLoop(device)
        }

        awaitClose {
        }
    }

    private suspend fun runSendLoop(device: PixelblazeDevice) {
        // Send updated state to device (dropping first value from setup step)
        state.drop(1).collect {
            device.setVars(it.toJSON())
        }
    }

    private suspend fun setupHelmet(device: PixelblazeDevice) {
        // Don't send 2D preview updates
        device.setSendUpdates(false)

        // Will throw exception if pattern name not found
        // TODO: display failure message
        device.setPatternByName("Helmet")

        // Get initial state
        withTimeout(5000) {
            device.variableState.mapNotNull {
                HelmetState.fromPixelblazeVars(it)
            }.first {
                _state.emit(it)
                true
            }
        }
    }

    fun setSegmentEffect(segment: HelmetSegments, newEffect: Int) {
        scope.launch {
            val curState = state.first().clone()
            val segmentState = curState.getSegment(segment)

            segmentState.setState(SegmentVars.Effect, newEffect.toDouble())

            _state.emit(curState) //emit modified state
        }
    }

    fun setSegmentColor(segment: HelmetSegments, color: Color) {
        val array = floatArrayOf(0.0f, 0.0f, 0.0f)
        android.graphics.Color.colorToHSV(color.toArgb(), array)

        scope.launch {
            val curState = state.first().clone()
            val segmentState = curState.getSegment(segment)

            segmentState.setState(SegmentVars.Hue, array[0].toDouble() / 360)
            segmentState.setState(SegmentVars.Saturation, array[1].toDouble())

            _state.emit(curState)
        }
    }

    fun setSegmentBrightness(segment: HelmetSegments, brightness: Float) {
        scope.launch {
            val curState = state.first().clone()
            val segmentState = curState.getSegment(segment)

            segmentState.setState(SegmentVars.Brightness, brightness.toDouble())

            _state.emit(curState)
        }
    }

    fun setMessageText(message: String) {
        scope.launch {
            val curState = state.first().clone()
            curState.getMessage().value = message
            _state.emit(curState)
        }
    }
}
