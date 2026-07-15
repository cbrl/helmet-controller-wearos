package com.cbrl.pixelblaze.presentation

import android.net.Network
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.charset.StandardCharsets

data class PixelblazeDevice(val address: String, val id: Int) : Closeable {
    companion object {
        const val API_PORT = 81
        private const val RESPONSE_TIMEOUT_MILLIS = 5_000L
    }

    private val clientDelegate = lazy {
        HttpClient(CIO) {
            install(WebSockets)
        }
    }
    private val client by clientDelegate

    private val _variableState = MutableSharedFlow<JSONObject>(replay = 1)
    val variableState: SharedFlow<JSONObject> = _variableState

    private val commandFlow = MutableSharedFlow<JSONObject>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _patterns = MutableStateFlow<Map<String, PatternInfo>>(emptyMap())
    val patternState: StateFlow<Map<String, PatternInfo>> = _patterns
    val patterns: Map<String, PatternInfo> get() = _patterns.value

    /**
     * Opens one WebSocket session. Collection completes with an error when the session is lost,
     * allowing the repository/ViewModel to apply an explicit reconnect policy.
     */
    fun start(): Flow<Unit> = callbackFlow {
        val connectionJob = launch {
            try {
                client.webSocket(host = address, port = API_PORT) {
                    // Disable preview frames before requesting a potentially multi-frame program list.
                    send(JSONObject().put("sendUpdates", false).toString())
                    _patterns.value = getPatternInfo(this)

                    val loops = listOf(
                        launch { runReceiveLoop(this@webSocket) },
                        launch { runSendLoop(this@webSocket) },
                        launch { runFetchStateLoop() },
                    )

                    trySend(Unit).getOrThrow()
                    loops.joinAll()
                }

                close(IOException("Pixelblaze closed the WebSocket connection"))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                close(exception)
            }
        }

        awaitClose { connectionJob.cancel() }
    }

    fun trySetPatternByName(patternName: String): Boolean {
        val pattern = patterns[patternName] ?: return false
        return trySetPatternByID(pattern.id)
    }

    fun trySetPatternByID(patternID: String): Boolean = trySendCommand(
        JSONObject().put("activeProgramId", patternID),
    )

    suspend fun setPatternByName(patternName: String) {
        val pattern = patterns[patternName]
            ?: throw NoSuchElementException("Pattern '$patternName' is not installed")
        setPatternByID(pattern.id)
    }

    suspend fun setPatternByID(patternID: String) {
        sendCommand(JSONObject().put("activeProgramId", patternID))
    }

    fun trySetSendUpdates(enabled: Boolean): Boolean = trySendCommand(
        JSONObject().put("sendUpdates", enabled),
    )

    suspend fun setSendUpdates(enabled: Boolean) {
        sendCommand(JSONObject().put("sendUpdates", enabled))
    }

    fun trySetVars(variables: JSONObject): Boolean = trySendCommand(
        JSONObject().put("setVars", variables),
    )

    suspend fun setVars(variables: JSONObject) {
        sendCommand(JSONObject().put("setVars", variables))
    }

    fun trySendCommand(command: String): Boolean = trySendCommand(JSONObject(command))

    fun trySendCommand(command: JSONObject): Boolean = commandFlow.tryEmit(command)

    suspend fun sendCommand(command: String) = sendCommand(JSONObject(command))

    suspend fun sendCommand(command: JSONObject) {
        commandFlow.emit(command)
    }

    private suspend fun runFetchStateLoop(intervalMillis: Long = 1_000L) {
        while (true) {
            commandFlow.emit(JSONObject().put("getVars", true))
            delay(intervalMillis)
        }
    }

    private suspend fun runSendLoop(session: WebSocketSession) {
        commandFlow.collect { session.send(it.toString()) }
    }

    private suspend fun runReceiveLoop(session: WebSocketSession) {
        for (frame in session.incoming) {
            if (frame is Frame.Text) {
                val response = runCatching { JSONObject(frame.readText()) }.getOrNull() ?: continue
                if (response.optJSONObject("vars") != null) {
                    _variableState.emit(response)
                }
            }
        }
        throw IOException("Pixelblaze receive channel closed")
    }

    private suspend fun getPatternInfo(session: WebSocketSession): Map<String, PatternInfo> {
        val accumulator = ProgramListAccumulator()
        session.send(JSONObject().put("listPrograms", true).toString())

        withTimeout(RESPONSE_TIMEOUT_MILLIS) {
            while (true) {
                when (val response = session.incoming.receive()) {
                    is Frame.Binary -> if (accumulator.accept(response.readBytes())) {
                        return@withTimeout
                    }
                    else -> Unit // Status messages are asynchronous and may arrive between fragments.
                }
            }
        }

        return accumulator.patterns()
    }

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }
}

data class PatternInfo(val name: String, val id: String)

/** Accumulates the documented 0x07 multi-frame program-list response. */
class ProgramListAccumulator {
    private val payload = ByteArrayOutputStream()
    private var complete = false

    fun accept(frame: ByteArray): Boolean {
        if (frame.size < 2 || frame[0].toInt() != 0x07) return false

        val flags = frame[1].toInt()
        if (flags and START_FLAG != 0) {
            payload.reset()
            complete = false
        }
        payload.write(frame, 2, frame.size - 2)
        complete = flags and END_FLAG != 0
        return complete
    }

    fun patterns(): Map<String, PatternInfo> {
        check(complete) { "Program list is incomplete" }
        return parsePrograms(payload.toByteArray())
    }

    companion object {
        private const val START_FLAG = 0x01
        private const val END_FLAG = 0x04

        fun parsePrograms(bytes: ByteArray): Map<String, PatternInfo> = buildMap {
            bytes.toString(StandardCharsets.UTF_8)
                .lineSequence()
                .filter { it.isNotBlank() }
                .forEach { programData ->
                    val pair = programData.split('\t', limit = 2)
                    if (pair.size == 2 && pair[0].isNotBlank() && pair[1].isNotBlank()) {
                        put(pair[1], PatternInfo(name = pair[1], id = pair[0]))
                    }
                }
        }
    }
}

class DeviceLocator {
    companion object {
        const val PORT = 1889
        const val BEACON_ID = 42
        private const val MIN_BEACON_BYTES = 12

        fun parseBeacon(bytes: ByteArray, address: String): PixelblazeDevice? {
            if (bytes.size < MIN_BEACON_BYTES) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val receivedType = buffer.getInt()
            val receivedID = buffer.getInt()
            return if (receivedType == BEACON_ID) PixelblazeDevice(address, receivedID) else null
        }
    }

    /** Creates fresh selector/socket resources for every collection so discovery can restart. */
    fun getDevices(network: Network? = null): Flow<PixelblazeDevice> = callbackFlow {
        val discoveryJob = launch(Dispatchers.IO) {
            val selector = Selector.open()
            val dataChannel = DatagramChannel.open()
            try {
                dataChannel.configureBlocking(false)
                network?.bindSocket(dataChannel.socket())
                dataChannel.bind(InetSocketAddress(PORT))
                dataChannel.register(selector, SelectionKey.OP_READ)

                val buffer = ByteBuffer.allocate(64)
                while (isActive) {
                    if (selector.select(250) <= 0) continue

                    val keys = selector.selectedKeys().iterator()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        keys.remove()
                        if (!key.isReadable) continue

                        buffer.clear()
                        val sender = dataChannel.receive(buffer) as? InetSocketAddress ?: continue
                        val received = buffer.position()
                        if (received < MIN_BEACON_BYTES) continue

                        val bytes = ByteArray(received)
                        buffer.flip()
                        buffer.get(bytes)
                        parseBeacon(bytes, sender.hostString)?.let { trySend(it) }
                    }
                }
            } finally {
                withContext(Dispatchers.IO) {
                    dataChannel.close()
                    selector.close()
                }
            }
        }

        awaitClose { discoveryJob.cancel() }
    }
}
