package com.cbrl.pixelblaze.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cbrl.pixelblaze.helmet.HelmetConnectionState
import com.cbrl.pixelblaze.ui.theme.HelmetText
import com.cbrl.pixelblaze.ui.theme.Led

/** A short name and an LED color for each connection state. */
data class ConnectionLabel(val text: String, val color: Color, val busy: Boolean)

@Composable
fun connectionLabel(connection: HelmetConnectionState, hasDevice: Boolean): ConnectionLabel {
    val colors = MaterialTheme.colorScheme
    return when {
        !hasDevice -> ConnectionLabel("No helmet", colors.outline, busy = false)
        else -> when (connection) {
            HelmetConnectionState.Ready -> ConnectionLabel("Live", Led.Green, busy = false)
            HelmetConnectionState.Connecting -> ConnectionLabel("Connecting", Led.Amber, busy = true)
            is HelmetConnectionState.Reconnecting -> ConnectionLabel("Reconnecting", Led.Amber, busy = true)
            is HelmetConnectionState.MissingPattern -> ConnectionLabel("No pattern", Led.Red, busy = false)
            is HelmetConnectionState.Error -> ConnectionLabel("Error", Led.Red, busy = false)
            HelmetConnectionState.Idle -> ConnectionLabel("Idle", colors.outline, busy = false)
        }
    }
}

/**
 * The connection state in the top bar, as a status LED. The icon shows the network: Wi-Fi, or the
 * default network (for example this phone's hotspot).
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
        color = Color.Transparent,
    ) {
        Row(
            Modifier
                .hairline(CircleShape)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(color, pulsing = label.busy)
            Text(label.text.uppercase(), style = HelmetText.section, color = MaterialTheme.colorScheme.onSurface)
            Icon(
                if (wifiConnected) Icons.Filled.Wifi else Icons.Filled.WifiTethering,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** A status LED with a soft glow. A busy LED pulses. */
@Composable
fun StatusDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val level = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "status pulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "status level",
        )
        value
    } else {
        1f
    }
    Canvas(modifier.size(size * 2)) {
        val radius = size.toPx() / 2f
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f * level), Color.Transparent), center, radius * 2f))
        drawCircle(color.copy(alpha = 0.35f + 0.65f * level), radius)
    }
}
