package com.cbrl.pixelblaze.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val HelmetColors = Colors(
    primary = Color(0xFF4DE7FF),
    primaryVariant = Color(0xFF00A8C2),
    secondary = Color(0xFFFF5AC8),
    secondaryVariant = Color(0xFFB92A8A),
    background = Color(0xFF080A0F),
    surface = Color(0xFF151922),
    error = Color(0xFFFF6B72),
    onPrimary = Color(0xFF002E35),
    onSecondary = Color(0xFF3E002D),
    onBackground = Color(0xFFF2F5FF),
    onSurface = Color(0xFFF2F5FF),
    onError = Color(0xFF400006),
)

@Composable
fun PixelblazeTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colors = HelmetColors,
        content = content,
    )
}
