package com.cbrl.pixelblaze.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.cbrl.pixelblaze.design.HelmetPalette
import com.cbrl.pixelblaze.core.R as CoreR

private val HelmetColorScheme = ColorScheme(
    primary = Color(HelmetPalette.LIGHT),
    primaryDim = Color(HelmetPalette.LIGHT_DIM),
    primaryContainer = Color(HelmetPalette.GRAPHITE),
    onPrimary = Color(HelmetPalette.ON_LIGHT),
    onPrimaryContainer = Color(HelmetPalette.ON_GRAPHITE),
    secondary = Color(HelmetPalette.LIGHT_DIM),
    secondaryDim = Color(HelmetPalette.MUTED),
    secondaryContainer = Color(HelmetPalette.GRAPHITE),
    onSecondary = Color(HelmetPalette.ON_LIGHT),
    onSecondaryContainer = Color(HelmetPalette.ON_GRAPHITE),
    tertiary = Color(HelmetPalette.LED_AMBER),
    tertiaryDim = Color(HelmetPalette.LED_AMBER_DIM),
    tertiaryContainer = Color(HelmetPalette.LED_AMBER_CONTAINER),
    onTertiary = Color(HelmetPalette.ON_LED_AMBER),
    onTertiaryContainer = Color(HelmetPalette.ON_LED_AMBER_CONTAINER),
    surfaceContainerLow = Color(HelmetPalette.SURFACE_HIGH),
    surfaceContainer = Color(HelmetPalette.SURFACE_HIGHEST),
    surfaceContainerHigh = Color(HelmetPalette.GRAPHITE),
    onSurface = Color(HelmetPalette.ON_SURFACE),
    onSurfaceVariant = Color(HelmetPalette.MUTED),
    outline = Color(HelmetPalette.OUTLINE),
    outlineVariant = Color(HelmetPalette.OUTLINE_VARIANT),
    background = Color(HelmetPalette.BLACK),
    onBackground = Color(HelmetPalette.ON_SURFACE),
    error = Color(HelmetPalette.LED_RED),
    errorDim = Color(HelmetPalette.LED_RED_DIM),
    errorContainer = Color(HelmetPalette.LED_RED_CONTAINER),
    onError = Color(HelmetPalette.ON_LED_RED),
    onErrorContainer = Color(HelmetPalette.ON_LED_RED_CONTAINER),
)

/** LED colors outside the Material roles. */
object Led {
    val Off = Color(HelmetPalette.LED_OFF)
    val Red = Color(HelmetPalette.LED_RED)
    val Amber = Color(HelmetPalette.LED_AMBER)
    val Green = Color(HelmetPalette.LED_GREEN)
    val White = Color(0xFFF4F6F9)
}

private fun dotoWeight(weight: Int) = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("ROND", 100f))

/** Round LED dots, as on the visor. For the wordmark and for large values. */
val LedFamily = FontFamily(
    Font(CoreR.font.doto, FontWeight.Bold, variationSettings = dotoWeight(700)),
    Font(CoreR.font.doto, FontWeight.Black, variationSettings = dotoWeight(900)),
)

/** Wide capitals, for headings. */
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
    /** The app name at the top of the home screen. */
    val wordmark = TextStyle(fontFamily = LedFamily, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 26.sp, letterSpacing = 1.5.sp)

    /** A short label in wide capitals. */
    val caps = TextStyle(fontFamily = WideFamily, fontSize = 9.sp, lineHeight = 13.sp, letterSpacing = 1.sp)
}

private val HelmetTypography = Typography().run {
    fun TextStyle.text(weight: FontWeight) = copy(fontFamily = TextFamily, fontWeight = weight)
    fun TextStyle.wide(size: TextUnit) = copy(fontFamily = WideFamily, fontWeight = FontWeight.Normal, fontSize = size)
    fun TextStyle.led() = copy(fontFamily = LedFamily, fontWeight = FontWeight.Black)
    copy(
        displayLarge = displayLarge.text(FontWeight.SemiBold),
        displayMedium = displayMedium.text(FontWeight.SemiBold),
        displaySmall = displaySmall.text(FontWeight.SemiBold),
        titleLarge = titleLarge.wide(15.sp),
        titleMedium = titleMedium.wide(13.sp),
        titleSmall = titleSmall.wide(11.sp),
        labelLarge = labelLarge.text(FontWeight.SemiBold),
        labelMedium = labelMedium.text(FontWeight.SemiBold),
        labelSmall = labelSmall.text(FontWeight.Medium),
        bodyLarge = bodyLarge.text(FontWeight.Normal),
        bodyMedium = bodyMedium.text(FontWeight.Normal),
        bodySmall = bodySmall.text(FontWeight.Normal),
        bodyExtraSmall = bodyExtraSmall.text(FontWeight.Normal),
        numeralExtraLarge = numeralExtraLarge.led(),
        numeralLarge = numeralLarge.led(),
        numeralMedium = numeralMedium.led(),
        numeralSmall = numeralSmall.led(),
        numeralExtraSmall = numeralExtraSmall.led(),
    )
}

/** Apply once, at the root of the app. */
@Composable
fun PixelblazeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HelmetColorScheme, typography = HelmetTypography, content = content)
}
