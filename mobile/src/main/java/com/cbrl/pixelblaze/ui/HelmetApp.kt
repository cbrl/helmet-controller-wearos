package com.cbrl.pixelblaze.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.ui.components.ConnectionChip
import com.cbrl.pixelblaze.ui.devices.DevicesScreen
import com.cbrl.pixelblaze.ui.helmet.HelmetScreen
import com.cbrl.pixelblaze.ui.presets.PresetsScreen

private enum class Destination(val label: String, val icon: ImageVector) {
    Helmet("Helmet", Icons.Filled.SportsMotorsports),
    Presets("Presets", Icons.Filled.Bookmarks),
    Devices("Devices", Icons.Filled.Router),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelmetApp(viewModel: HelmetViewModel = viewModel()) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val device by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val discovery by viewModel.discovery.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.Helmet) }
    val snackbar = remember { SnackbarHostState() }
    val pages = rememberSaveableStateHolder()
    val needsAttention = device == null ||
        connection is HelmetConnectionState.Error ||
        connection is HelmetConnectionState.MissingPattern

    BackHandler(enabled = destination != Destination.Helmet) { destination = Destination.Helmet }

    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.surface, colors.surfaceContainerLowest)))
            .drawBehind {
                // A faint glow behind the top bar.
                drawCircle(
                    Brush.radialGradient(
                        listOf(colors.primary.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.2f, 0f),
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.2f, 0f),
                )
            },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            // A transparent container gives no content color, so text outside a card would be black.
            contentColor = colors.onSurface,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("HELMET", style = MaterialTheme.typography.titleLarge, letterSpacing = 4.sp)
                            Text(
                                status?.info?.name?.ifBlank { null } ?: device?.address ?: "No helmet selected",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        ConnectionChip(
                            connection = connection,
                            hasDevice = device != null,
                            wifiConnected = discovery.wifiConnected,
                            onClick = { destination = Destination.Devices },
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = colors.surfaceContainer) {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = {
                                BadgedBox(badge = { if (item == Destination.Devices && needsAttention) Badge() }) {
                                    Icon(item.icon, contentDescription = null)
                                }
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            AnimatedContent(
                targetState = destination,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
                label = "destination",
            ) { page ->
                // Each tab keeps its scroll position when the user comes back to it.
                pages.SaveableStateProvider(page.name) {
                    when (page) {
                        Destination.Helmet -> HelmetScreen(
                            viewModel = viewModel,
                            snackbar = snackbar,
                            onOpenDevices = { destination = Destination.Devices },
                            onOpenPresets = { destination = Destination.Presets },
                        )
                        Destination.Presets -> PresetsScreen(viewModel, snackbar)
                        Destination.Devices -> DevicesScreen(
                            viewModel = viewModel,
                            snackbar = snackbar,
                            onDeviceSelected = { destination = Destination.Helmet },
                        )
                    }
                }
            }
        }
    }
}
