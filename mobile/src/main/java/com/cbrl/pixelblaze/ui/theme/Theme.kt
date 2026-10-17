package com.cbrl.pixelblaze.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HelmetColors = darkColorScheme(
    primary = Color(0xFF4DE7FF),
    onPrimary = Color(0xFF00363E),
    primaryContainer = Color(0xFF004E59),
    onPrimaryContainer = Color(0xFF9CF0FF),
    secondary = Color(0xFFFF5AC8),
    secondaryContainer = Color(0xFF672052),
    tertiary = Color(0xFFFFB44D),
    background = Color(0xFF080A0F),
    surface = Color(0xFF080A0F),
    surfaceContainerLowest = Color(0xFF05070B),
    surfaceContainerLow = Color(0xFF10131A),
    surfaceContainer = Color(0xFF151922),
    surfaceContainerHigh = Color(0xFF1C222D),
    onSurface = Color(0xFFF2F5FF),
    onSurfaceVariant = Color(0xFFADB8C8),
    outline = Color(0xFF46515F),
    error = Color(0xFFFF6B72),
)

@Composable
fun PixelblazePhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HelmetColors, content = content)
}
