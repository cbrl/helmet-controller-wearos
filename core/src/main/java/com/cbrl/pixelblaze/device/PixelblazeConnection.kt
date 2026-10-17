package com.cbrl.pixelblaze.device

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The connection to a Pixelblaze failed, or the Pixelblaze stopped responding. */
class PixelblazeException(message: String) : IOException(message)

/** Opens WebSocket sessions with a Pixelblaze. */
object PixelblazeClient {
    const val API_PORT = 81
    private const val CONNECT_TIMEOUT_SECONDS = 5L

    /**
     * Connects to [endpoint] and runs [block] while the session is alive.
     *
     * When the connection fails or the Pixelblaze stops responding, [block] is cancelled and this
     * function throws. When [block] returns, the session closes.
     */
    suspend fun <T> connect(endpoint: PixelblazeEndpoint, block: suspend (PixelblazeSession) -> T): T {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // The session watchdog finds a silent connection, so reads have no timeout.
            .readTimeout(0, TimeUnit.SECONDS)
            .apply { endpoint.network?.let { socketFactory(it.socketFactory) } }
            .build()
        val client = HttpClient(OkHttp) {
            engine { preconfigured = okHttp }
            install(WebSockets)
        }
        try {
            val socket = client.webSocketSession(host = endpoint.address, port = API_PORT, path = "/")
            try {
                return coroutineScope {
                    val session = PixelblazeSession(socket)
                    val loops = launch { session.run() }
                    try {
                        block(session)
                    } finally {
                        loops.cancel()
                    }
                }
            } finally {
                withContext(NonCancellable) { runCatching { socket.close() } }
            }
        } finally {
            client.close()
        }
    }
}

/**
 * One WebSocket session with a Pixelblaze.
 *
 * All commands go through one ordered queue. The session polls getVars once per second, drops
 * replies that are older than the latest write, and fails when no frame arrives for 5 seconds.
 */
class PixelblazeSession internal constructor(private val socket: WebSocketSession) {
    private companion object {
        const val POLL_INTERVAL_MILLIS = 1_000L
        const val WATCHDOG_TIMEOUT_MILLIS = 5_000L
        const val REQUEST_TIMEOUT_MILLIS = 5_000L
        const val MAX_PENDING_READS = 2
    }

    private enum class Kind { Read, Write, Other }

    private val outgoing = Channel<String>(Channel.UNLIMITED)
    private val sendLock = Any()
    private val reads = VarsReadTracker()

    @Volatile
    private var lastFrameNanos = System.nanoTime()

    private val _vars = MutableSharedFlow<JSONObject>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** The exported variables from each up-to-date getVars reply. */
    val vars: SharedFlow<JSONObject> = _vars

    private val _info = MutableStateFlow<PixelblazeInfo?>(null)
    val info: StateFlow<PixelblazeInfo?> = _info

    private val _activeProgram = MutableStateFlow<PatternInfo?>(null)
    val activeProgram: StateFlow<PatternInfo?> = _activeProgram

    private val _fps = MutableStateFlow<Float?>(null)
    val fps: StateFlow<Float?> = _fps

    private val _previewFrames = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** RGB bytes, 3 for each pixel. The Pixelblaze sends them only after [setPreviewEnabled] (true). */
    val previewFrames: SharedFlow<ByteArray> = _previewFrames

    private val programList = ProgramListAccumulator()

    @Volatile
    private var programListResult: CompletableDeferred<Map<String, PatternInfo>>? = null

    fun setVars(vars: JSONObject) = enqueue(JSONObject().put("setVars", vars), Kind.Write)

    /** Starts a pattern. The replies to earlier getVars requests become out of date. */
    fun setActiveProgram(id: String) = enqueue(JSONObject().put("activeProgramId", id), Kind.Write)

    fun setPreviewEnabled(enabled: Boolean) = enqueue(JSONObject().put("sendUpdates", enabled), Kind.Other)

    /** Returns the running pattern, or null when the Pixelblaze does not reply in time. */
    suspend fun awaitActiveProgram(): PatternInfo? {
        enqueue(JSONObject().put("getConfig", true), Kind.Other)
        return withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { activeProgram.filterNotNull().first() }
    }

    /** Returns the installed patterns by name. */
    suspend fun programs(): Map<String, PatternInfo> {
        val result = CompletableDeferred<Map<String, PatternInfo>>()
        programListResult = result
        enqueue(JSONObject().put("listPrograms", true), Kind.Other)
        return withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { result.await() }
            ?: throw PixelblazeException("The Pixelblaze did not send its program list")
    }

    private fun enqueue(command: JSONObject, kind: Kind) {
        // The tracker must see requests in the order that they are sent.
        synchronized(sendLock) {
            when (kind) {
                Kind.Read -> reads.onRead()
                Kind.Write -> reads.onWrite()
                Kind.Other -> Unit
            }
            outgoing.trySend(command.toString())
        }
    }

    internal suspend fun run(): Nothing = coroutineScope {
        launch { receiveLoop() }
        launch { for (text in outgoing) socket.send(text) }
        launch { pollLoop() }
        watchdog()
    }

    private suspend fun receiveLoop() {
        for (frame in socket.incoming) {
            lastFrameNanos = System.nanoTime()
            when (frame) {
                is Frame.Text -> handleText(frame.readText())
                is Frame.Binary -> handleBinary(frame.readBytes())
                else -> Unit
            }
        }
        throw PixelblazeException("The Pixelblaze closed the connection")
    }

    private fun handleText(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when {
            json.has("vars") -> {
                val vars = json.optJSONObject("vars")
                if (reads.onReply() && vars != null) _vars.tryEmit(vars)
            }
            json.has("fps") -> _fps.value = json.optDouble("fps").toFloat()
            json.has("activeProgram") -> json.optJSONObject("activeProgram")?.let {
                _activeProgram.value = PatternInfo(name = it.optString("name"), id = it.optString("activeProgramId"))
            }
            else -> PixelblazeInfo.fromConfig(json)?.let { _info.value = it }
        }
    }

    private fun handleBinary(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        when (bytes[0].toInt() and 0xFF) {
            FrameType.PREVIEW -> _previewFrames.tryEmit(bytes.copyOfRange(1, bytes.size))
            FrameType.PROGRAM_LIST -> if (programList.accept(bytes)) {
                programListResult?.complete(programList.patterns())
            }
        }
    }

    private suspend fun pollLoop() {
        while (true) {
            if (reads.pendingReads() < MAX_PENDING_READS) {
                enqueue(JSONObject().put("getVars", true), Kind.Read)
            }
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private suspend fun watchdog(): Nothing {
        while (true) {
            delay(1_000L)
            val silentMillis = (System.nanoTime() - lastFrameNanos) / 1_000_000L
            if (silentMillis > WATCHDOG_TIMEOUT_MILLIS) {
                throw PixelblazeException("The Pixelblaze stopped responding")
            }
        }
    }
}
