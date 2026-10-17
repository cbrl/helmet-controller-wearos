package com.cbrl.pixelblaze.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cbrl.pixelblaze.helmet.HelmetConnectionState

/** A short name and a color for each connection state. */
data class ConnectionLabel(val text: String, val color: Color, val busy: Boolean)

@Composable
fun connectionLabel(connection: HelmetConnectionState, hasDevice: Boolean): ConnectionLabel {
    val colors = MaterialTheme.colorScheme
    return when {
        !hasDevice -> ConnectionLabel("No helmet", colors.outline, busy = false)
        else -> when (connection) {
            HelmetConnectionState.Ready -> ConnectionLabel("Live", colors.primary, busy = false)
            HelmetConnectionState.Connecting -> ConnectionLabel("Connecting", colors.tertiary, busy = true)
            is HelmetConnectionState.Reconnecting -> ConnectionLabel("Reconnecting", colors.tertiary, busy = true)
            is HelmetConnectionState.MissingPattern -> ConnectionLabel("No pattern", colors.error, busy = false)
            is HelmetConnectionState.Error -> ConnectionLabel("Error", colors.error, busy = false)
            HelmetConnectionState.Idle -> ConnectionLabel("Idle", colors.outline, busy = false)
        }
    }
}

/**
 * The connection state in the top bar. The icon shows the network: Wi-Fi, or the default network
 * (for example this phone's hotspot).
 */
@Composable
fun ConnectionChip(
    connection: HelmetConnectionState,
    hasDevice: Boolean,
    wifiConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = connectionLabel(connection, hasDevice)
    val color by animateColorAsState(label.color, label = "connection color")
    val network = if (wifiConnected) "Wi-Fi" else "hotspot or other network"
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "Helmet ${label.text}, on $network. Open devices." },
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusDot(color, pulsing = label.busy)
            Text(label.text, style = MaterialTheme.typography.labelLarge, color = color)
            Icon(
                if (wifiConnected) Icons.Filled.Wifi else Icons.Filled.WifiTethering,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun StatusDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "status pulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "status alpha",
        )
        value
    } else {
        1f
    }
    Box(
        modifier
            .size(8.dp)
            .alpha(alpha)
            .background(color, CircleShape)
            .border(2.dp, color.copy(alpha = 0.3f), CircleShape),
    )
}
