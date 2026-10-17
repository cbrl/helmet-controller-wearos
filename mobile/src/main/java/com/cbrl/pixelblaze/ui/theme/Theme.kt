package com.cbrl.pixelblaze.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cbrl.pixelblaze.design.HelmetPalette
import com.cbrl.pixelblaze.core.R as CoreR

private val HelmetColors = darkColorScheme(
    primary = Color(HelmetPalette.LIGHT),
    onPrimary = Color(HelmetPalette.ON_LIGHT),
    primaryContainer = Color(HelmetPalette.GRAPHITE),
    onPrimaryContainer = Color(HelmetPalette.ON_GRAPHITE),
    inversePrimary = Color(HelmetPalette.GRAPHITE),
    secondary = Color(HelmetPalette.LIGHT_DIM),
    onSecondary = Color(HelmetPalette.ON_LIGHT),
    secondaryContainer = Color(HelmetPalette.GRAPHITE),
    onSecondaryContainer = Color(HelmetPalette.ON_GRAPHITE),
    tertiary = Color(HelmetPalette.LED_AMBER),
    onTertiary = Color(HelmetPalette.ON_LED_AMBER),
    tertiaryContainer = Color(HelmetPalette.LED_AMBER_CONTAINER),
    onTertiaryContainer = Color(HelmetPalette.ON_LED_AMBER_CONTAINER),
    background = Color(HelmetPalette.SURFACE),
    onBackground = Color(HelmetPalette.ON_SURFACE),
    surface = Color(HelmetPalette.SURFACE),
    onSurface = Color(HelmetPalette.ON_SURFACE),
    surfaceVariant = Color(HelmetPalette.SURFACE_HIGH),
    onSurfaceVariant = Color(HelmetPalette.MUTED),
    surfaceTint = Color(HelmetPalette.LIGHT),
    inverseSurface = Color(HelmetPalette.LIGHT),
    inverseOnSurface = Color(HelmetPalette.ON_LIGHT),
    error = Color(HelmetPalette.LED_RED),
    onError = Color(HelmetPalette.ON_LED_RED),
    errorContainer = Color(HelmetPalette.LED_RED_CONTAINER),
    onErrorContainer = Color(HelmetPalette.ON_LED_RED_CONTAINER),
    outline = Color(HelmetPalette.OUTLINE),
    outlineVariant = Color(HelmetPalette.OUTLINE_VARIANT),
    scrim = Color.Black,
    surfaceBright = Color(HelmetPalette.SURFACE_HIGHEST),
    surfaceDim = Color(HelmetPalette.SURFACE),
    surfaceContainerLowest = Color(HelmetPalette.BLACK),
    surfaceContainerLow = Color(HelmetPalette.SURFACE_LOW),
    surfaceContainer = Color(HelmetPalette.SURFACE_MID),
    surfaceContainerHigh = Color(HelmetPalette.SURFACE_HIGH),
    surfaceContainerHighest = Color(HelmetPalette.SURFACE_HIGHEST),
)

/** LED colors outside the Material roles. */
object Led {
    val Off = Color(HelmetPalette.LED_OFF)
    val Red = Color(HelmetPalette.LED_RED)
    val Amber = Color(HelmetPalette.LED_AMBER)
    val Green = Color(HelmetPalette.LED_GREEN)

    /** A lit LED with no color of its own, such as a level meter. */
    val White = Color(0xFFF4F6F9)
}

/** The color of an LED that is off, on the dark panels of the helmet diagram. */
val LedOff = Led.Off

/** The background of the helmet diagram: the black visor. */
val DiagramPanel = Color(HelmetPalette.BLACK)

private fun dotoWeight(weight: Int) = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("ROND", 100f))

/** Round LED dots, as on the visor. For the wordmark and for large values. */
val LedFamily = FontFamily(
    Font(CoreR.font.doto, FontWeight.Bold, variationSettings = dotoWeight(700)),
    Font(CoreR.font.doto, FontWeight.Black, variationSettings = dotoWeight(900)),
)

/** Wide capitals, for headings and section labels. */
val WideFamily = FontFamily(Font(CoreR.font.michroma, FontWeight.Normal))

/** The text face for everything else. */
val TextFamily = FontFamily(
    Font(CoreR.font.space_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(CoreR.font.space_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(CoreR.font.space_grotesk, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(CoreR.font.space_grotesk, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Text styles outside the Material type scale. */
object HelmetText {
    /** A small heading above a group of controls. */
    val section = TextStyle(fontFamily = WideFamily, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.6.sp)

    /** The app name in the top bar. */
    val wordmark = TextStyle(fontFamily = LedFamily, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = 2.sp)

    /** A large value, such as the master brightness. */
    val ledLarge = TextStyle(fontFamily = LedFamily, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 36.sp)

    /** The value of a slider. */
    val ledSmall = TextStyle(fontFamily = LedFamily, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 22.sp)
}

private val HelmetTypography = Typography().run {
    fun TextStyle.text(weight: FontWeight) = copy(fontFamily = TextFamily, fontWeight = weight)
    fun TextStyle.wide(size: Int) = copy(fontFamily = WideFamily, fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = (size + 8).sp)
    fun TextStyle.led() = copy(fontFamily = LedFamily, fontWeight = FontWeight.Black)
    copy(
        displayLarge = displayLarge.led(),
        displayMedium = displayMedium.led(),
        displaySmall = displaySmall.led(),
        headlineLarge = headlineLarge.wide(26),
        headlineMedium = headlineMedium.wide(22),
        headlineSmall = headlineSmall.wide(18),
        titleLarge = titleLarge.text(FontWeight.SemiBold),
        titleMedium = titleMedium.text(FontWeight.SemiBold),
        titleSmall = titleSmall.text(FontWeight.SemiBold),
        bodyLarge = bodyLarge.text(FontWeight.Normal),
        bodyMedium = bodyMedium.text(FontWeight.Normal),
        bodySmall = bodySmall.text(FontWeight.Normal),
        labelLarge = labelLarge.text(FontWeight.SemiBold),
        labelMedium = labelMedium.text(FontWeight.Medium),
        labelSmall = labelSmall.text(FontWeight.Medium),
    )
}

@Composable
fun PixelblazePhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HelmetColors, typography = HelmetTypography, content = content)
}
