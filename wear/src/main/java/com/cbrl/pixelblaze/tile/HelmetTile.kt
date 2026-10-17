package com.cbrl.pixelblaze.tile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.material3.ButtonDefaults.filledButtonColors
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.ButtonGroupDefaults
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.button
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.TileService
import com.cbrl.pixelblaze.data.HelmetPreferences
import com.cbrl.pixelblaze.design.HelmetPalette
import com.cbrl.pixelblaze.helmet.HelmetAction
import com.cbrl.pixelblaze.helmet.HelmetPresets
import com.cbrl.pixelblaze.presentation.MainActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** What the tile shows: the saved blackout state and the newest presets. */
data class HelmetTileData(val blackout: Boolean, val presets: List<Preset>) {
    data class Preset(val id: String, val name: String)

    companion object {
        const val MAX_PRESETS = 3

        fun flow(preferences: HelmetPreferences): Flow<HelmetTileData> =
            combine(preferences.presets, preferences.desiredState) { presets, state ->
                HelmetTileData(
                    blackout = state?.blackout == true,
                    presets = HelmetPresets.active(presets).take(MAX_PRESETS).map { Preset(it.id, it.name) },
                )
            }.distinctUntilChanged()
    }
}

object HelmetTile {
    /** The intent extra with the [HelmetAction] token of a tile button. */
    const val EXTRA_ACTION = "com.cbrl.pixelblaze.extra.HELMET_ACTION"

    /** Screens shorter than this show two presets instead of three. */
    private const val THREE_PRESETS_MIN_HEIGHT_DP = 225

    fun actionFrom(intent: Intent?): HelmetAction? = HelmetAction.fromToken(intent?.getStringExtra(EXTRA_ACTION))

    /** Asks the system for a new tile each time the presets or the blackout state change. */
    suspend fun updateOnChanges(context: Context) {
        HelmetTileData.flow(HelmetPreferences(context)).collect {
            TileService.getUpdater(context).requestUpdate(HelmetTileService::class.java)
        }
    }

    /** The colors of the app: chrome buttons, black glass, and a red LED for Blackout. */
    val colorScheme = ColorScheme(
        primary = HelmetPalette.LIGHT.argb,
        primaryDim = HelmetPalette.LIGHT_DIM.argb,
        primaryContainer = HelmetPalette.GRAPHITE.argb,
        onPrimary = HelmetPalette.ON_LIGHT.argb,
        onPrimaryContainer = HelmetPalette.ON_GRAPHITE.argb,
        secondary = HelmetPalette.LIGHT_DIM.argb,
        secondaryDim = HelmetPalette.MUTED.argb,
        secondaryContainer = HelmetPalette.GRAPHITE.argb,
        onSecondary = HelmetPalette.ON_LIGHT.argb,
        onSecondaryContainer = HelmetPalette.ON_GRAPHITE.argb,
        tertiary = HelmetPalette.LED_AMBER.argb,
        tertiaryDim = HelmetPalette.LED_AMBER_DIM.argb,
        tertiaryContainer = HelmetPalette.LED_AMBER_CONTAINER.argb,
        onTertiary = HelmetPalette.ON_LED_AMBER.argb,
        onTertiaryContainer = HelmetPalette.ON_LED_AMBER_CONTAINER.argb,
        surfaceContainerLow = HelmetPalette.SURFACE_MID.argb,
        surfaceContainer = HelmetPalette.SURFACE_HIGH.argb,
        surfaceContainerHigh = HelmetPalette.SURFACE_HIGHEST.argb,
        onSurface = HelmetPalette.ON_SURFACE.argb,
        onSurfaceVariant = HelmetPalette.MUTED.argb,
        outline = HelmetPalette.OUTLINE.argb,
        outlineVariant = HelmetPalette.OUTLINE_VARIANT.argb,
        background = HelmetPalette.BLACK.argb,
        onBackground = HelmetPalette.ON_SURFACE.argb,
        error = HelmetPalette.LED_RED.argb,
        errorDim = HelmetPalette.LED_RED_DIM.argb,
        errorContainer = HelmetPalette.LED_RED_CONTAINER.argb,
        onError = HelmetPalette.ON_LED_RED.argb,
        onErrorContainer = HelmetPalette.ON_LED_RED_CONTAINER.argb,
    )

    /**
     * The tile: up to three presets and a Blackout or Lights-on edge button. Each button opens the
     * app, which runs the action when the helmet is ready.
     */
    fun MaterialScope.layout(data: HelmetTileData): LayoutElement {
        val maxPresets = if (deviceConfiguration.screenHeightDp >= THREE_PRESETS_MIN_HEIGHT_DP) 3 else 2
        val presets = data.presets.take(maxPresets)
        return primaryLayout(
            titleSlot = { text("Helmet".layoutString) },
            mainSlot = {
                if (presets.isEmpty()) {
                    text(
                        "No presets yet. Save one in the app.".layoutString,
                        typography = Typography.BODY_MEDIUM,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                } else {
                    val buttons = presets.mapIndexed { index, preset ->
                        button(
                            onClick = clickable(launch(HelmetAction.LoadPreset(preset.id)), id = "preset_$index"),
                            labelContent = { text(preset.name.layoutString, maxLines = 1) },
                            width = expand(),
                            height = expand(),
                            colors = filledTonalButtonColors(),
                        )
                    }
                    val spaced = buttons.flatMapIndexed { index, button ->
                        if (index == 0) listOf(button) else listOf(ButtonGroupDefaults.DEFAULT_SPACER_BETWEEN_BUTTON_GROUPS, button)
                    }
                    column(*spaced.toTypedArray(), width = expand(), height = expand())
                }
            },
            bottomSlot = {
                textEdgeButton(
                    onClick = clickable(launch(HelmetAction.SetBlackout(!data.blackout)), id = "blackout"),
                    colors = if (data.blackout) {
                        filledButtonColors()
                    } else {
                        ButtonColors(
                            containerColor = colorScheme.errorContainer,
                            iconColor = colorScheme.error,
                            labelColor = colorScheme.onErrorContainer,
                            secondaryLabelColor = colorScheme.onErrorContainer,
                        )
                    },
                ) { text((if (data.blackout) "Lights on" else "Blackout").layoutString) }
            },
        )
    }

    private fun MaterialScope.launch(action: HelmetAction): ActionBuilders.LaunchAction =
        ActionBuilders.launchAction(
            ComponentName(context, MainActivity::class.java),
            mapOf(EXTRA_ACTION to ActionBuilders.stringExtra(action.toToken())),
        )
}
