package com.cbrl.pixelblaze.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Requests a Wi-Fi client network and reports it.
 *
 * On Wear OS the request turns on Wi-Fi, so call [stop] when the app goes to the background.
 * The app does not need a Wi-Fi network: with no Wi-Fi network (for example when this phone is the
 * hotspot), connections use the default route.
 */
class NetworkMonitor(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val _wifiNetwork = MutableStateFlow<Network?>(null)
    val wifiNetwork: StateFlow<Network?> = _wifiNetwork

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (callback != null) return
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _wifiNetwork.value = network
                _error.value = null
            }

            override fun onLost(network: Network) {
                if (_wifiNetwork.value == network) _wifiNetwork.value = null
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching { connectivity.requestNetwork(request, networkCallback) }
            .onSuccess { callback = networkCallback }
            .onFailure { _error.value = "Wi-Fi request failed: ${it.message}" }
    }

    fun stop() {
        callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        callback = null
        _wifiNetwork.value = null
    }
}
