package com.cbrl.pixelblaze.tile

import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import com.cbrl.pixelblaze.data.HelmetPreferences
import com.cbrl.pixelblaze.tile.HelmetTile.layout
import kotlinx.coroutines.flow.first

/**
 * The Helmet tile. The tile does not refresh by itself: [HelmetTile.updateOnChanges] asks for a
 * new tile when the presets or the blackout state change.
 */
class HelmetTileService : Material3TileService(
    allowDynamicTheme = false,
    defaultColorScheme = HelmetTile.colorScheme,
) {
    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val data = HelmetTileData.flow(HelmetPreferences(this@HelmetTileService)).first()
        return tile(timeline = Timeline.fromLayoutElement(layout(data)))
    }
}
