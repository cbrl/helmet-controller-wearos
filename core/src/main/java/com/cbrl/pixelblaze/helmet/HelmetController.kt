package com.cbrl.pixelblaze.helmet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.random.Random

/** The connection operations that [HelmetController] needs. */
interface HelmetLink {
    /** Exported pattern variables from each up-to-date getVars reply. */
    val vars: Flow<JSONObject>

    fun setVars(vars: JSONObject)
}

/**
 * Pattern variables that wait to be written. A new value for a variable replaces the waiting value,
 * so a slider drag sends only the latest position.
 */
class PendingWrites {
    private val values = LinkedHashMap<String, Any>()
    private var inFlight = false

    @Synchronized
    fun put(vars: Map<String, Any>) {
        values.putAll(vars)
    }

    /** Takes every waiting value. Call [sent] after the values go to the connection. */
    @Synchronized
    fun take(): Map<String, Any> {
        val batch = LinkedHashMap(values)
        values.clear()
        inFlight = batch.isNotEmpty()
        return batch
    }

    @Synchronized
    fun sent() {
        inFlight = false
    }

    /** True when no value waits and no batch is between [take] and [sent]. */
    @Synchronized
    fun isIdle(): Boolean = values.isEmpty() && !inFlight

    @Synchronized
    fun clear() {
        values.clear()
        inFlight = false
    }
}

/**
 * Keeps the helmet state and writes changes to the pattern.
 *
 * Changes update [state] at once (optimistic), then go to the Pixelblaze at most every
 * [FLUSH_INTERVAL_MILLIS]. Replies from the Pixelblaze replace [state] only when no write waits.
 */
class HelmetController(
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {
    companion object {
        const val FLUSH_INTERVAL_MILLIS = 40L
        const val SNAPSHOT_TIMEOUT_MILLIS = 5_000L
    }

    private val _state = MutableStateFlow<HelmetState?>(null)

    /** The last known helmet state, or null before the first connection. */
    val state: StateFlow<HelmetState?> = _state

    private val _layout = MutableStateFlow(HelmetLayout.Default)
    val layout: StateFlow<HelmetLayout> = _layout

    private val pending = PendingWrites()
    private val flushSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var link: HelmetLink? = null
    private var linkJob: Job? = null
    private var messageRev = 0

    /**
     * Connects to a helmet.
     *
     * If the pattern restarted (__session is 0), the controller writes [restore] and a new session
     * token. If another app already restored the pattern, the controller adopts the helmet state.
     *
     * @throws PatternIncompatibleException when the pattern does not send a valid v4 state.
     */
    suspend fun attach(link: HelmetLink, restore: HelmetState?) {
        detach()
        val snapshot = awaitSnapshot(link)
        _layout.value = snapshot.layout
        messageRev = snapshot.messageRev
        this.link = link
        if (snapshot.session == 0) {
            restoreState(restore ?: snapshot.state)
        } else {
            _state.value = snapshot.state
        }
        linkJob = scope.launch {
            launch { flushLoop(link) }
            link.vars.collect(::onVars)
        }
    }

    /** Stops writes and updates. [state] keeps the last known state. */
    fun detach() {
        linkJob?.cancel()
        linkJob = null
        link = null
        pending.clear()
    }

    fun setSegmentEnabled(segment: HelmetSegment, enabled: Boolean) =
        updateSegment(segment) { it.copy(on = enabled) }

    /** Sets the effect. A matrix-only effect is ignored on a 1D segment. */
    fun setSegmentEffect(segment: HelmetSegment, effect: HelmetEffect) = updateSegment(segment) {
        if (effect.requires2D && !_layout.value[segment].is2D) it else it.copy(effect = effect)
    }

    fun setSegmentColor(segment: HelmetSegment, hue: Float, saturation: Float) =
        updateSegment(segment) { it.copy(hue = hue, saturation = saturation) }

    fun setSegmentBrightness(segment: HelmetSegment, brightness: Float) =
        updateSegment(segment) { it.copy(brightness = brightness) }

    fun setSegmentSpeed(segment: HelmetSegment, speed: Float) =
        updateSegment(segment) { it.copy(speed = speed) }

    fun setSegmentFadeTime(segment: HelmetSegment, seconds: Float) =
        updateSegment(segment) { it.copy(fadeSeconds = seconds) }

    /** Copies every control of [source] to the other segments. A matrix-only effect stays on 2D segments. */
    fun copyAppearanceToAll(source: HelmetSegment) {
        val sourceState = _state.value?.get(source) ?: return
        updateAllSegments { target, current ->
            val effect = if (!sourceState.effect.requires2D || _layout.value[target].is2D) {
                sourceState.effect
            } else {
                current.effect
            }
            sourceState.copy(effect = effect)
        }
    }

    fun updateSegment(segment: HelmetSegment, transform: (SegmentState) -> SegmentState) {
        val current = _state.value ?: return
        if (link == null) return
        val updated = transform(current[segment]).normalized()
        if (updated == current[segment]) return
        _state.value = current.with(segment, updated)
        write(HelmetProtocol.segmentVars(segment, updated))
    }

    fun updateAllSegments(transform: (HelmetSegment, SegmentState) -> SegmentState) {
        val current = _state.value ?: return
        if (link == null) return
        val updated = current.copy(
            segments = HelmetSegment.entries.map { transform(it, current[it]).normalized() },
        )
        if (updated == current) return
        _state.value = updated
        write(
            buildMap {
                HelmetSegment.entries
                    .filter { updated[it] != current[it] }
                    .forEach { putAll(HelmetProtocol.segmentVars(it, updated[it])) }
            },
        )
    }

    /** Sets the master brightness. The pattern fades to it. */
    fun setMaster(level: Float) = updateGlobal { it.copy(master = level.coerceIn(0f, 1f)) }

    /** Fades all output to black, or back. The segment state does not change. */
    fun setBlackout(enabled: Boolean) = updateGlobal { it.copy(blackout = enabled) }

    fun setMessage(text: String) {
        val current = _state.value ?: return
        if (link == null) return
        val message = HelmetMessage.sanitize(text)
        if (message == current.message) return
        _state.value = current.copy(message = message)
        messageRev = HelmetProtocol.nextCounter(messageRev)
        write(HelmetProtocol.messageVars(message, messageRev))
    }

    /** Shows a preset look. The master brightness and blackout do not change. */
    fun applyLook(look: HelmetLook) {
        val current = _state.value ?: return
        if (link == null) return
        val updated = current.withLook(
            HelmetLook(look.segments.map(SegmentState::normalized), HelmetMessage.sanitize(look.message)),
        )
        if (updated == current) return
        _state.value = updated
        write(
            buildMap {
                HelmetSegment.entries.forEach { putAll(HelmetProtocol.segmentVars(it, updated[it])) }
                if (updated.message != current.message) {
                    messageRev = HelmetProtocol.nextCounter(messageRev)
                    putAll(HelmetProtocol.messageVars(updated.message, messageRev))
                }
            },
        )
    }

    private fun updateGlobal(transform: (HelmetState) -> HelmetState) {
        val current = _state.value ?: return
        if (link == null) return
        val updated = transform(current)
        if (updated == current) return
        _state.value = updated
        write(HelmetProtocol.masterVars(updated.master, updated.blackout))
    }

    /** Writes [state] and a new session token, so that other apps adopt the state. */
    private fun restoreState(state: HelmetState) {
        _state.value = state
        messageRev = HelmetProtocol.nextCounter(messageRev)
        val token = random.nextInt(1, HelmetProtocol.MAX_COUNTER + 1)
        write(HelmetProtocol.stateVars(state, messageRev) + (HelmetProtocol.VAR_SESSION to token))
    }

    private fun write(vars: Map<String, Any>) {
        if (vars.isEmpty()) return
        pending.put(vars)
        flushSignal.trySend(Unit)
    }

    private suspend fun flushLoop(link: HelmetLink) {
        for (signal in flushSignal) {
            val batch = pending.take()
            if (batch.isEmpty()) continue
            try {
                link.setVars(JSONObject(batch))
            } finally {
                pending.sent()
            }
            delay(FLUSH_INTERVAL_MILLIS)
        }
    }

    private fun onVars(vars: JSONObject) {
        // A reply does not contain writes that still wait, so it would undo them on screen.
        if (!pending.isIdle()) return
        val snapshot = (HelmetProtocol.decode(vars) as? HelmetProtocol.Decoded.Valid)?.snapshot ?: return
        if (_layout.value != snapshot.layout) _layout.value = snapshot.layout
        messageRev = snapshot.messageRev
        if (snapshot.session == 0) {
            // The pattern restarted while connected, for example after an edit in the Pixelblaze editor.
            restoreState(_state.value ?: snapshot.state)
            return
        }
        if (_state.value != snapshot.state) _state.value = snapshot.state
    }

    private suspend fun awaitSnapshot(link: HelmetLink): HelmetProtocol.Snapshot {
        var last: HelmetProtocol.Decoded? = null
        val valid = withTimeoutOrNull(SNAPSHOT_TIMEOUT_MILLIS) {
            link.vars
                .map(HelmetProtocol::decode)
                .onEach { last = it }
                .filterIsInstance<HelmetProtocol.Decoded.Valid>()
                .first()
        }
        if (valid != null) return valid.snapshot
        val message = when (val decoded = last) {
            is HelmetProtocol.Decoded.WrongVersion -> if (decoded.found == null) {
                "The running pattern is not the ${HelmetProtocol.PATTERN_NAME} pattern."
            } else {
                "The ${HelmetProtocol.PATTERN_NAME} pattern on the Pixelblaze is v${decoded.found}. " +
                    "This app needs v${HelmetProtocol.VERSION}."
            }
            is HelmetProtocol.Decoded.LayoutInvalid ->
                "The helmet needs ${decoded.expectedPixels} pixels. Set the pixel count in the Pixelblaze settings."
            else -> "The ${HelmetProtocol.PATTERN_NAME} pattern did not send its state."
        }
        throw PatternIncompatibleException(message)
    }
}
