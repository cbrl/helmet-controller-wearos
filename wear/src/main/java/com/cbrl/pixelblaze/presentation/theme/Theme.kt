package com.cbrl.pixelblaze.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** The neon palette of the phone app, as ARGB values for the Compose theme and for the tile. */
object HelmetPalette {
    const val PRIMARY = 0xFF4DE7FF
    const val PRIMARY_DIM = 0xFF2FBFD6
    const val PRIMARY_CONTAINER = 0xFF004E59
    const val ON_PRIMARY = 0xFF00363E
    const val ON_PRIMARY_CONTAINER = 0xFF9CF0FF
    const val SECONDARY = 0xFFFF5AC8
    const val SECONDARY_DIM = 0xFFD9479F
    const val SECONDARY_CONTAINER = 0xFF672052
    const val ON_SECONDARY = 0xFF3E002D
    const val ON_SECONDARY_CONTAINER = 0xFFFFD8EE
    const val TERTIARY = 0xFFFFB44D
    const val TERTIARY_DIM = 0xFFD9963A
    const val TERTIARY_CONTAINER = 0xFF5C3B00
    const val ON_TERTIARY = 0xFF452B00
    const val ON_TERTIARY_CONTAINER = 0xFFFFDDB3
    const val SURFACE_CONTAINER_LOW = 0xFF10131A
    const val SURFACE_CONTAINER = 0xFF151922
    const val SURFACE_CONTAINER_HIGH = 0xFF1C222D
    const val ON_SURFACE = 0xFFF2F5FF
    const val ON_SURFACE_VARIANT = 0xFFADB8C8
    const val OUTLINE = 0xFF46515F
    const val OUTLINE_VARIANT = 0xFF2A323E
    const val BACKGROUND = 0xFF000000
    const val ON_BACKGROUND = 0xFFF2F5FF
    const val ERROR = 0xFFFF6B72
    const val ERROR_DIM = 0xFFD9535A
    const val ERROR_CONTAINER = 0xFF5C1A20
    const val ON_ERROR = 0xFF400006
    const val ON_ERROR_CONTAINER = 0xFFFFDAD8
}

private val HelmetColorScheme = ColorScheme(
    primary = Color(HelmetPalette.PRIMARY),
    primaryDim = Color(HelmetPalette.PRIMARY_DIM),
    primaryContainer = Color(HelmetPalette.PRIMARY_CONTAINER),
    onPrimary = Color(HelmetPalette.ON_PRIMARY),
    onPrimaryContainer = Color(HelmetPalette.ON_PRIMARY_CONTAINER),
    secondary = Color(HelmetPalette.SECONDARY),
    secondaryDim = Color(HelmetPalette.SECONDARY_DIM),
    secondaryContainer = Color(HelmetPalette.SECONDARY_CONTAINER),
    onSecondary = Color(HelmetPalette.ON_SECONDARY),
    onSecondaryContainer = Color(HelmetPalette.ON_SECONDARY_CONTAINER),
    tertiary = Color(HelmetPalette.TERTIARY),
    tertiaryDim = Color(HelmetPalette.TERTIARY_DIM),
    tertiaryContainer = Color(HelmetPalette.TERTIARY_CONTAINER),
    onTertiary = Color(HelmetPalette.ON_TERTIARY),
    onTertiaryContainer = Color(HelmetPalette.ON_TERTIARY_CONTAINER),
    surfaceContainerLow = Color(HelmetPalette.SURFACE_CONTAINER_LOW),
    surfaceContainer = Color(HelmetPalette.SURFACE_CONTAINER),
    surfaceContainerHigh = Color(HelmetPalette.SURFACE_CONTAINER_HIGH),
    onSurface = Color(HelmetPalette.ON_SURFACE),
    onSurfaceVariant = Color(HelmetPalette.ON_SURFACE_VARIANT),
    outline = Color(HelmetPalette.OUTLINE),
    outlineVariant = Color(HelmetPalette.OUTLINE_VARIANT),
    background = Color(HelmetPalette.BACKGROUND),
    onBackground = Color(HelmetPalette.ON_BACKGROUND),
    error = Color(HelmetPalette.ERROR),
    errorDim = Color(HelmetPalette.ERROR_DIM),
    errorContainer = Color(HelmetPalette.ERROR_CONTAINER),
    onError = Color(HelmetPalette.ON_ERROR),
    onErrorContainer = Color(HelmetPalette.ON_ERROR_CONTAINER),
)

/** Apply once, at the root of the app. */
@Composable
fun PixelblazeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HelmetColorScheme, content = content)
}
