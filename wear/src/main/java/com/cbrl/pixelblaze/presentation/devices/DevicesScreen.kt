package com.cbrl.pixelblaze.presentation.devices

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.cbrl.pixelblaze.device.PixelblazeEndpoint
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.presentation.components.PyramidLoader
import com.cbrl.pixelblaze.presentation.components.listItem
import com.cbrl.pixelblaze.presentation.components.rememberTextInput

private const val WEAR_WIFI_SETTINGS = "com.google.android.clockwork.settings.connectivity.wifi.ADD_NETWORK_SETTINGS"

/** The Pixelblaze devices on this network. [onDeviceChosen] runs after a selection. */
@Composable
fun DevicesScreen(viewModel: HelmetViewModel, onDeviceChosen: () -> Unit) {
    val discovery by viewModel.discovery.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val enterAddress = rememberTextInput(label = "Pixelblaze IP address") { address ->
        viewModel.connectByAddress(address)
        onDeviceChosen()
    }
    // The selected device stays in the list while discovery does not see it, for example after a manual address.
    val devices = buildList {
        val found = discovery.devices.values.sortedBy { it.id }
        selected?.takeIf { current -> found.none(current::sameDevice) }?.let(::add)
        addAll(found)
    }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(Modifier.listItem(this, spec), transformation = SurfaceTransformation(spec)) { Text("HELMETS") }
            }
            item { NetworkLine(discovery.wifiConnected, searching = discovery.devices.isEmpty()) }
            discovery.errorMessage?.let { error ->
                item {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (!discovery.wifiConnected) {
                item {
                    FilledTonalButton(
                        onClick = { openWifiSettings(context) },
                        modifier = Modifier.listItem(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
                        label = { Text("Wi-Fi settings") },
                    )
                }
            }
            items(devices, key = { "${it.id}@${it.address}" }) { device ->
                val isSelected = device.sameDevice(selected)
                val content = @Composable {
                    Icon(if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.Router, contentDescription = null)
                }
                if (isSelected) {
                    Button(
                        onClick = onDeviceChosen,
                        modifier = Modifier.listItem(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { content() },
                        secondaryLabel = { Text(device.address) },
                        label = { Text(device.label) },
                    )
                } else {
                    FilledTonalButton(
                        onClick = {
                            viewModel.selectDevice(device)
                            onDeviceChosen()
                        },
                        modifier = Modifier.listItem(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { content() },
                        secondaryLabel = { Text(device.address) },
                        label = { Text(device.label) },
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = enterAddress,
                    modifier = Modifier.listItem(this, spec),
                    transformation = SurfaceTransformation(spec),
                    icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    label = { Text("Enter address") },
                )
            }
            if (selected != null) {
                item {
                    OutlinedButton(
                        onClick = viewModel::forgetDevice,
                        modifier = Modifier.listItem(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(Icons.Rounded.LinkOff, contentDescription = null) },
                        label = { Text("Forget helmet") },
                    )
                }
            }
        }
    }
}

private val PixelblazeEndpoint.label: String
    get() = if (id == PixelblazeEndpoint.UNKNOWN_ID) "Pixelblaze" else "Pixelblaze $displayId"

@Composable
private fun NetworkLine(wifiConnected: Boolean, searching: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (wifiConnected && searching) PyramidLoader(MaterialTheme.colorScheme.onSurface, size = 16.dp)
        Text(
            when {
                !wifiConnected -> "No Wi-Fi network"
                searching -> "Searching…"
                else -> "On this Wi-Fi"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openWifiSettings(context: Context) {
    val intents = listOf(Intent(WEAR_WIFI_SETTINGS), Intent(Settings.ACTION_WIFI_SETTINGS))
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next settings screen.
        }
    }
}
