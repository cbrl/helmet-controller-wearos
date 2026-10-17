package com.cbrl.pixelblaze.device

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector

/** Listens for the UDP discovery beacons that each Pixelblaze broadcasts. */
class PixelblazeDiscovery {
    companion object {
        const val PORT = 1889
        const val BEACON_ID = 42
        private const val MIN_BEACON_BYTES = 12

        fun parseBeacon(bytes: ByteArray, address: String, network: Network? = null): PixelblazeEndpoint? {
            if (bytes.size < MIN_BEACON_BYTES) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val receivedType = buffer.getInt()
            val receivedID = buffer.getInt()
            return if (receivedType == BEACON_ID) PixelblazeEndpoint(receivedID, address, network) else null
        }
    }

    /**
     * Emits an endpoint for each beacon. With a [network], the socket listens on that network only.
     * With no network, it listens on the default route, which includes this phone's hotspot.
     *
     * Each collection opens new socket resources, so discovery can restart.
     */
    fun beacons(network: Network?): Flow<PixelblazeEndpoint> = callbackFlow {
        val discoveryJob = launch(Dispatchers.IO) {
            val selector = Selector.open()
            val dataChannel = DatagramChannel.open()
            try {
                dataChannel.configureBlocking(false)
                // A restart can bind before the previous socket closes.
                dataChannel.socket().reuseAddress = true
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
                        parseBeacon(bytes, sender.hostString, network)?.let { trySend(it) }
                    }
                }
            } catch (exception: Exception) {
                if (isActive) close(exception)
            } finally {
                withContext(Dispatchers.IO) {
                    runCatching { dataChannel.close() }
                    runCatching { selector.close() }
                }
            }
        }

        awaitClose { discoveryJob.cancel() }
    }
}
