package com.cbrl.pixelblaze.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.cbrl.pixelblaze.helmet.HelmetViewModel
import com.cbrl.pixelblaze.tile.HelmetTile

class MainActivity : ComponentActivity() {
    private val viewModel: HelmetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // After a configuration change, the intent holds an action that already ran.
        if (savedInstanceState == null) runTileAction(intent)
        addOnNewIntentListener(::runTileAction)
        setContent { WearApp(viewModel) }
    }

    /** Queues the action of a tile button. The repository runs it when the helmet is ready. */
    private fun runTileAction(intent: Intent) {
        HelmetTile.actionFrom(intent)?.let(viewModel::runWhenReady)
    }
}
