package com.cbrl.pixelblaze.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.cbrl.pixelblaze.helmet.ActionResult
import com.cbrl.pixelblaze.helmet.HelmetSegment
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.presentation.devices.DevicesScreen
import com.cbrl.pixelblaze.presentation.home.HomeScreen
import com.cbrl.pixelblaze.presentation.home.MasterBrightnessScreen
import com.cbrl.pixelblaze.presentation.presets.PresetsScreen
import com.cbrl.pixelblaze.presentation.segments.ColorScreen
import com.cbrl.pixelblaze.presentation.segments.EffectScreen
import com.cbrl.pixelblaze.presentation.segments.SegmentBrightnessScreen
import com.cbrl.pixelblaze.presentation.segments.SegmentScreen
import com.cbrl.pixelblaze.presentation.segments.SegmentsScreen
import com.cbrl.pixelblaze.presentation.theme.PixelblazeTheme

object Routes {
    const val HOME = "home"
    const val DEVICES = "devices"
    const val PRESETS = "presets"
    const val MASTER = "master"
    const val SEGMENTS = "segments"
    private const val SEGMENT_ARG = "segment"
    const val SEGMENT = "segment/{$SEGMENT_ARG}"
    const val EFFECT = "segment/{$SEGMENT_ARG}/effect"
    const val COLOR = "segment/{$SEGMENT_ARG}/color"
    const val BRIGHTNESS = "segment/{$SEGMENT_ARG}/brightness"

    fun segment(segment: HelmetSegment) = "segment/${segment.id}"

    fun effect(segment: HelmetSegment) = "segment/${segment.id}/effect"

    fun color(segment: HelmetSegment) = "segment/${segment.id}/color"

    fun brightness(segment: HelmetSegment) = "segment/${segment.id}/brightness"

    fun segmentOf(entry: NavBackStackEntry): HelmetSegment =
        entry.arguments?.getString(SEGMENT_ARG)?.toIntOrNull()?.let(HelmetSegment::fromId) ?: HelmetSegment.Visor
}

@Composable
fun WearApp(viewModel: HelmetViewModel) {
    PixelblazeTheme {
        AppScaffold {
            val navController = rememberSwipeDismissableNavController()
            val open: (String) -> Unit = { route -> navController.navigate(route) { launchSingleTop = true } }
            SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(viewModel, onOpen = open) }
                composable(Routes.DEVICES) {
                    DevicesScreen(viewModel, onDeviceChosen = { navController.popBackStack(Routes.HOME, inclusive = false) })
                }
                composable(Routes.PRESETS) { PresetsScreen(viewModel) }
                composable(Routes.MASTER) { MasterBrightnessScreen(viewModel) }
                composable(Routes.SEGMENTS) { SegmentsScreen(viewModel, onOpen = { open(Routes.segment(it)) }) }
                composable(Routes.SEGMENT) { entry -> SegmentScreen(viewModel, Routes.segmentOf(entry), onOpen = open) }
                composable(Routes.EFFECT) { entry -> EffectScreen(viewModel, Routes.segmentOf(entry)) }
                composable(Routes.COLOR) { entry -> ColorScreen(viewModel, Routes.segmentOf(entry)) }
                composable(Routes.BRIGHTNESS) { entry -> SegmentBrightnessScreen(viewModel, Routes.segmentOf(entry)) }
            }
            ActionResultDialogs(viewModel)
        }
    }
}

/** Confirms an action from the tile, or says why it failed. */
@Composable
private fun ActionResultDialogs(viewModel: HelmetViewModel) {
    val result by viewModel.actionResult.collectAsStateWithLifecycle()
    // Keep the last result, so that the text stays during the exit animation.
    var shown by remember { mutableStateOf<ActionResult?>(null) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(result) {
        val current = result ?: return@LaunchedEffect
        shown = current
        haptics.performHapticFeedback(if (current.success) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }
    val text = shown?.message.orEmpty()
    val style = ConfirmationDialogDefaults.curvedTextStyle
    SuccessConfirmationDialog(
        visible = result?.success == true,
        onDismissRequest = viewModel::clearActionResult,
        curvedText = { confirmationDialogCurvedText(text, style) },
    )
    FailureConfirmationDialog(
        visible = result?.success == false,
        onDismissRequest = viewModel::clearActionResult,
        curvedText = { confirmationDialogCurvedText(text, style) },
    )
}
