package com.cbrl.pixelblaze.presentation

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.charset.Charset
import kotlin.coroutines.coroutineContext

class PixelblazeDevice(val address: String, val id: Int) {
    companion object {
        const val API_PORT = 81
    }

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    private val _variableState = MutableSharedFlow<JSONObject>(replay = 1) //need replay to allow fetching the last value
    val variableState: SharedFlow<JSONObject> = _variableState

    private val commandFlow = MutableSharedFlow<JSONObject>(replay = 3)

    private val _patterns: MutableMap<String, PatternInfo> = mutableMapOf()
    val patterns: Map<String, PatternInfo> = _patterns

    fun start(scope: CoroutineScope): Flow<Unit> = callbackFlow {
        scope.launch {
            client.webSocket(host = address, port = API_PORT) {
                getPatternInfo(this).map { entry -> _patterns[entry.key] = entry.value }

                val mainLoop = listOf(
                    launch { runFetchStateLoop(this@webSocket) },
                    launch { runSendLoop(this@webSocket) },
                )

                // Notify listeners that setup has completed
                send(Unit)

                mainLoop.joinAll()
            }
        }

        awaitClose {
            close()
        }
    }

    fun trySetPatternByName(patternName: String): Boolean {
        return trySetPatternByID(patterns.getValue(patternName).id)
    }

    fun trySetPatternByID(patternID: String): Boolean {
        val command = "{\"activeProgramId\": \"${patternID}\"}"
        return trySendCommand(command)
    }

    suspend fun setPatternByName(patternName: String) {
        setPatternByID(patterns.getValue(patternName).id)
    }

    suspend fun setPatternByID(patternID: String) {
        val setCmd = "{\"activeProgramId\": \"${patternID}\"}"
        sendCommand(setCmd)
    }

    fun trySetSendUpdates(enabled: Boolean): Boolean {
        val command = "{\"sendUpdates\": ${enabled}}"
        return trySendCommand(command)
    }

    suspend fun setSendUpdates(enabled: Boolean) {
        val command = "{\"sendUpdates\": ${enabled}}"
        sendCommand(command)
    }

    fun trySetVars(variables: JSONObject): Boolean {
        val command = JSONObject().put("setVars", variables)
        return trySendCommand(command)
    }

    suspend fun setVars(variables: JSONObject) {
        val command = JSONObject().put("setVars", variables)
        sendCommand(command)
    }

    fun trySendCommand(command: String): Boolean {
        return trySendCommand(JSONObject(command))
    }

    fun trySendCommand(command: JSONObject): Boolean {
        return commandFlow.tryEmit(command)
    }

    suspend fun sendCommand(command: String) {
        sendCommand(JSONObject(command))
    }

    suspend fun sendCommand(command: JSONObject) {
        commandFlow.emit(command)
    }

    private suspend fun runFetchStateLoop(session: WebSocketSession, intervalMillis: Long = 250) {
        // Poll for updated variable state
        while (true) {
            getVariableState(session)?.let { _variableState.emit(it) }
            delay(intervalMillis)
        }
    }

    private suspend fun runSendLoop(session: WebSocketSession) {
        // Send queued commands to device
        commandFlow.collect {
            session.send(it.toString())
        }
    }

    private suspend fun getPatternInfo(session: WebSocketSession): Map<String, PatternInfo> {
        val listCmd = "{\"listPrograms\": true}"

        // Try multiple times in case status reports are received instead
        for (i in 1..50) {
            session.send(listCmd)
            val response = session.incoming.receive() as? Frame.Binary

            response?.readBytes()?.let { res ->
                if (res.size > 2 && res[0].toInt() == 0x07) {
                    return PatternInfo.fromPixelblazeResponse(res.sliceArray(2..<res.size))
                }
            }

        }

        return mapOf()
    }

    private suspend fun getVariableState(session: WebSocketSession): JSONObject? {
        val varCmd = "{\"getVars\": true}"

        // Try multiple times in case status reports are received as well
        for (i in 1..50) {
            sendCommand(varCmd)
            val response = session.incoming.receive() as? Frame.Text

            response?.readText()?.let {
                if (it.contains("vars")) {
                    return JSONObject(it)
                }
            }
        }

        return null
    }
}

data class PatternInfo(val name: String, val id: String) {
    companion object {
        fun fromPixelblazeResponse(bytes: ByteArray): Map<String, PatternInfo> {
            val info = mutableMapOf<String, PatternInfo>()

            val programs = bytes.toString(Charset.forName("utf8")).split("\n")

            for (programData in programs) {
                val pair = programData.split("\t")
                if (pair.size == 2) {
                    info[pair[1]] = PatternInfo(pair[1], pair[0])
                }
            }

            return info
        }
    }
}

class DeviceLocator {
    companion object {
        const val PORT = 1889
        const val BEACON_ID = 42
    }

    private val dataChannel: DatagramChannel = DatagramChannel.open().apply {
        bind(InetSocketAddress(PORT))
        configureBlocking(false)
    }

    private val selector: Selector = Selector.open()

    init {
        dataChannel.register(selector, SelectionKey.OP_READ)
    }

    fun getDevices(): Flow<PixelblazeDevice> = callbackFlow {
        // Start listening in IO context
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    getDevice()?.let { send(it) }
                }
                catch (e: Exception) {
                    close(e)
                }
            }
        }

        awaitClose {
            dataChannel.close()
            selector.close()
        }
    }

    private fun getDevice(timeout: Long = 100): PixelblazeDevice? {
        // Wait for incoming packets with timeout
        if (selector.select(timeout) <= 0) {
            return null
        }

        selector.selectedKeys().filter { it.isReadable }.forEach { _ ->
            val buffer = ByteBuffer.allocate(65536)
            val sender = dataChannel.receive(buffer) as InetSocketAddress?

            if (sender != null && buffer.limit() >= 12) {
                val receivedType = buffer.order(ByteOrder.LITTLE_ENDIAN).getInt(0)
                val receivedID = buffer.order(ByteOrder.LITTLE_ENDIAN).getInt(4)

                if (receivedType == BEACON_ID) {
                    return PixelblazeDevice(sender.hostString, receivedID)
                }
            }
        }

        return null
    }
}
