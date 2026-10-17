package com.cbrl.pixelblaze.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.ui.components.ConnectionChip
import com.cbrl.pixelblaze.ui.components.EarPanelMark
import com.cbrl.pixelblaze.ui.components.Hairline
import com.cbrl.pixelblaze.ui.components.LedText
import com.cbrl.pixelblaze.ui.components.ledGrid
import com.cbrl.pixelblaze.ui.devices.DevicesScreen
import com.cbrl.pixelblaze.ui.helmet.HelmetScreen
import com.cbrl.pixelblaze.ui.helmet.visorInk
import com.cbrl.pixelblaze.ui.presets.PresetsScreen
import com.cbrl.pixelblaze.ui.theme.HelmetText
import com.cbrl.pixelblaze.ui.theme.Led

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

    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            // The LED wall of the Alive 2007 stage, behind the top of the screen.
            .ledGrid(pitch = 12.dp, alpha = 0.08f, fadeTo = 0.35f),
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
            containerColor = Color.Transparent,
            // A transparent container gives no content color, so text outside a card would be black.
            contentColor = colors.onSurface,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val ink = visorInk(state)
                            EarPanelMark(lit = ink.lit, modifier = Modifier.padding(end = 12.dp))
                            Column {
                                LedText(
                                    "HELMET",
                                    style = HelmetText.wordmark,
                                    color = ink.color,
                                    brush = ink.brush,
                                    glow = ink.lit,
                                    modifier = Modifier.semantics { heading() },
                                )
                                Text(
                                    (status?.info?.name?.ifBlank { null } ?: device?.address ?: "No helmet selected").uppercase(),
                                    style = HelmetText.section.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = colors.surface.copy(alpha = 0.96f),
                    ),
                    scrollBehavior = topBarScroll,
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = colors.surface,
                    modifier = Modifier.drawBehind {
                        drawLine(Hairline, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                    },
                ) {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = {
                                BadgedBox(badge = { if (item == Destination.Devices && needsAttention) Badge(containerColor = Led.Red) }) {
                                    Icon(item.icon, contentDescription = null)
                                }
                            },
                            label = { Text(item.label.uppercase(), style = HelmetText.section.copy(fontSize = 9.sp)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.onPrimary,
                                indicatorColor = colors.primary,
                                selectedTextColor = colors.onSurface,
                                unselectedIconColor = colors.onSurfaceVariant,
                                unselectedTextColor = colors.onSurfaceVariant,
                            ),
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
