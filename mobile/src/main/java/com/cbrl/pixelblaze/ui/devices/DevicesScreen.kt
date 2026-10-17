package com.cbrl.pixelblaze.ui.devices

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cbrl.pixelblaze.device.PixelblazeEndpoint
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.helmet.PixelblazeStatus
import com.cbrl.pixelblaze.ui.components.SectionCard
import com.cbrl.pixelblaze.ui.components.SectionLabel
import com.cbrl.pixelblaze.ui.components.SectionShape
import com.cbrl.pixelblaze.ui.components.StatusDot
import com.cbrl.pixelblaze.ui.components.connectionLabel
import com.cbrl.pixelblaze.ui.components.showMessage
import kotlin.math.roundToInt

@Composable
fun DevicesScreen(
    viewModel: HelmetViewModel,
    snackbar: SnackbarHostState,
    onDeviceSelected: () -> Unit,
) {
    val discovery by viewModel.discovery.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val found = discovery.devices.values.sortedBy { it.address }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "network") { NetworkCard(discovery.wifiConnected) }

        selected?.let { device ->
            item(key = "selected") {
                SelectedDeviceCard(
                    device = device,
                    connection = connection,
                    status = status,
                    onReconnect = viewModel::retry,
                    onForget = {
                        viewModel.forgetDevice()
                        scope.showMessage(snackbar, "Forgot Pixelblaze ${device.displayId}") { viewModel.selectDevice(device) }
                    },
                )
            }
        }

        item(key = "found-header") {
            Row(Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("On this network", Modifier.weight(1f))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        discovery.errorMessage?.let { error ->
            item(key = "error") {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (found.isEmpty()) {
            item(key = "searching") {
                Text(
                    "Searching… The Pixelblaze and this phone must be on the same network, " +
                        "with discovery on in the Pixelblaze settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        items(found, key = { it.id }) { device ->
            FoundDevice(device, selected = device.sameDevice(selected)) {
                viewModel.selectDevice(device)
                onDeviceSelected()
            }
        }

        item(key = "manual") {
            ManualAddress { address ->
                viewModel.connectByAddress(address)
                onDeviceSelected()
            }
        }
    }
}

@Composable
private fun NetworkCard(wifiConnected: Boolean) {
    val context = LocalContext.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (wifiConnected) Icons.Filled.Wifi else Icons.Filled.WifiTethering,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    if (wifiConnected) "Wi-Fi" else "No Wi-Fi network",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (wifiConnected) {
                        "The app finds Pixelblaze devices on this Wi-Fi network."
                    } else {
                        "The app uses the default network. Use this mode when the helmet joins this phone's hotspot."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(
            onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    // No Wi-Fi settings screen on this device.
                }
            },
            modifier = Modifier.align(Alignment.End),
        ) { Text("Wi-Fi settings") }
    }
}

@Composable
private fun SelectedDeviceCard(
    device: PixelblazeEndpoint,
    connection: HelmetConnectionState,
    status: PixelblazeStatus?,
    onReconnect: () -> Unit,
    onForget: () -> Unit,
) {
    val label = connectionLabel(connection, hasDevice = true)
    val info = status?.info
    SectionCard(title = "Your helmet") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Router, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(info?.name?.ifBlank { null } ?: "Pixelblaze ${device.displayId}", style = MaterialTheme.typography.titleMedium)
                Text(device.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(label.color, pulsing = label.busy)
                Text(label.text, style = MaterialTheme.typography.labelLarge, color = label.color, modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (info != null) {
            val details = buildList {
                add("Firmware ${info.firmware}")
                add("${info.pixelCount} LEDs")
                status.fps?.let { add("${it.roundToInt()} FPS") }
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val message = when (connection) {
            is HelmetConnectionState.Reconnecting -> connection.reason
            is HelmetConnectionState.MissingPattern -> connection.message
            is HelmetConnectionState.Error -> connection.message
            else -> null
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onReconnect, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Text("Reconnect", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onForget, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.LinkOff, null, Modifier.size(18.dp))
                Text("Forget", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun FoundDevice(device: PixelblazeEndpoint, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = SectionShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Router, null, Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Pixelblaze ${device.displayId}", style = MaterialTheme.typography.titleMedium)
                Text(device.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
            } else {
                Text("Connect", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ManualAddress(onConnect: (String) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val connect = {
        if (address.isNotBlank()) {
            focusManager.clearFocus()
            onConnect(address.trim())
        }
    }
    SectionCard(title = "Connect by address") {
        Text(
            "Use this when discovery does not find the helmet, for example on some hotspots.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.filterNot(Char::isWhitespace) },
                modifier = Modifier.weight(1f),
                label = { Text("IP address") },
                placeholder = { Text("192.168.4.1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { connect() }),
            )
            Button(onClick = connect, enabled = address.isNotBlank()) { Text("Connect") }
        }
    }
}
