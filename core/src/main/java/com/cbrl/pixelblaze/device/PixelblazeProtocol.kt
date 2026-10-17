package com.cbrl.pixelblaze.device

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

data class PatternInfo(val name: String, val id: String)

/** Device details from the getConfig reply. */
data class PixelblazeInfo(
    val name: String,
    val firmware: String,
    val pixelCount: Int,
    val chipId: Int?,
) {
    companion object {
        /** Returns null when [json] is not a getConfig reply. */
        fun fromConfig(json: JSONObject): PixelblazeInfo? {
            if (!json.has("ver") || !json.has("pixelCount")) return null
            return PixelblazeInfo(
                name = json.optString("name"),
                firmware = json.optString("ver"),
                pixelCount = json.optInt("pixelCount"),
                chipId = if (json.has("chipId")) json.optInt("chipId") else null,
            )
        }
    }
}

/** Binary frame types that the Pixelblaze sends. The first byte of each frame is the type. */
internal object FrameType {
    /** Pixel colors: 3 bytes (RGB) for each pixel. Sent after {"sendUpdates": true}. */
    const val PREVIEW = 0x05

    /** Part of the program list. See [ProgramListAccumulator]. */
    const val PROGRAM_LIST = 0x07
}

/** Accumulates the documented 0x07 multi-frame program-list response. */
class ProgramListAccumulator {
    private val payload = ByteArrayOutputStream()
    private var complete = false

    fun accept(frame: ByteArray): Boolean {
        if (frame.size < 2 || frame[0].toInt() != FrameType.PROGRAM_LIST) return false

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

/**
 * Finds getVars replies that are older than the latest write.
 *
 * The Pixelblaze answers requests in order. A reply to a getVars that was sent before a setVars
 * does not contain that write. If the app used it, the app would show old values for a moment.
 *
 * Call [onWrite] and [onRead] in the order that the requests are sent, and [onReply] for each
 * getVars reply.
 */
class VarsReadTracker(private val maxPending: Int = 8) {
    private var writeCount = 0L
    private val pending = ArrayDeque<Long>()

    @Synchronized
    fun onWrite() {
        writeCount++
    }

    @Synchronized
    fun onRead() {
        while (pending.size >= maxPending) pending.removeFirst()
        pending.addLast(writeCount)
    }

    /** Returns true when the reply includes every write sent so far. */
    @Synchronized
    fun onReply(): Boolean {
        val writesBeforeRead = pending.removeFirstOrNull() ?: return false
        return writesBeforeRead == writeCount
    }

    @Synchronized
    fun pendingReads(): Int = pending.size
}
