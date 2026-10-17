package com.cbrl.pixelblaze.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.cbrl.pixelblaze.R

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
    surfaceContainerHighest = Color(0xFF252C39),
    onSurface = Color(0xFFF2F5FF),
    onSurfaceVariant = Color(0xFFADB8C8),
    outline = Color(0xFF46515F),
    outlineVariant = Color(0xFF2A323E),
    error = Color(0xFFFF6B72),
    errorContainer = Color(0xFF5C1A20),
    onErrorContainer = Color(0xFFFFDAD8),
)

/** The color of an LED that is off, on the dark panels of the helmet diagram. */
val LedOff = Color(0xFF1E2430)

/** The background of the helmet diagram. */
val DiagramPanel = Color(0xFF05070B)

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val spaceGrotesk = GoogleFont("Space Grotesk")

/**
 * The heading typeface. Google Play services downloads it once and caches it. Until the download
 * completes, or on a device without Play services, the text uses the system font.
 */
private val DisplayFamily = FontFamily(
    Font(googleFont = spaceGrotesk, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = spaceGrotesk, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = spaceGrotesk, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = spaceGrotesk, fontProvider = fontProvider, weight = FontWeight.Bold),
)

private val HelmetTypography = Typography().run {
    fun TextStyle.display(weight: FontWeight = FontWeight.SemiBold) = copy(fontFamily = DisplayFamily, fontWeight = weight)
    copy(
        displayLarge = displayLarge.display(),
        displayMedium = displayMedium.display(),
        displaySmall = displaySmall.display(),
        headlineLarge = headlineLarge.display(),
        headlineMedium = headlineMedium.display(),
        headlineSmall = headlineSmall.display(),
        titleLarge = titleLarge.display(FontWeight.Bold).copy(letterSpacing = 0.5.sp),
        titleMedium = titleMedium.display(FontWeight.Bold),
        titleSmall = titleSmall.display(),
        labelLarge = labelLarge.display(),
    )
}

@Composable
fun PixelblazePhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HelmetColors, typography = HelmetTypography, content = content)
}
