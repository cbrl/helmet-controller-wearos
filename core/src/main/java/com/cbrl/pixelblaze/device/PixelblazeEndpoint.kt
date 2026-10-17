package com.cbrl.pixelblaze.device

import android.net.Network

/**
 * A Pixelblaze on the local network.
 *
 * [network] is the Wi-Fi network that the device is on. It is null when the device is on the
 * default route, for example a device on this phone's hotspot.
 */
data class PixelblazeEndpoint(
    val id: Int,
    val address: String,
    val network: Network? = null,
) {
    /** The device ID as the Pixelblaze shows it, for example "646B0E". */
    val displayId: String get() = if (id == UNKNOWN_ID) "?" else id.toUInt().toString(16).uppercase()

    /** True when both endpoints have the same device ID and address. The network is ignored. */
    fun sameDevice(other: PixelblazeEndpoint?): Boolean =
        other != null && id == other.id && address == other.address

    companion object {
        /** The ID of an endpoint that the user entered by address, before the device reports its ID. */
        const val UNKNOWN_ID = 0
    }
}
