package com.cbrl.pixelblaze

import android.app.Application
import com.cbrl.pixelblaze.helmet.HelmetRepository
import com.cbrl.pixelblaze.tile.HelmetTile
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class WearApplication : Application() {
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        // The repository syncs presets with the phone while the process runs, also when only the tile
        // started the process. It connects to the helmet only while the app is in the foreground.
        HelmetRepository.get(this)
        scope.launch { HelmetTile.updateOnChanges(this@WearApplication) }
    }
}
