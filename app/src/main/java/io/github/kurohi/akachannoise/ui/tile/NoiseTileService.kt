package io.github.kurohi.akachannoise.ui.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.github.kurohi.akachannoise.AppGraph
import io.github.kurohi.akachannoise.MainActivity
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.playback.PlaybackService

/**
 * Quick Settings tile that toggles the white noise.
 *
 * Starting a foreground service from a tile is restricted on Android 14+, so
 * on those versions the tile opens the app with a "play" action instead.
 */
class NoiseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val container = AppGraph.get(this)
        if (container.playback.state.value.playing) {
            container.mixEditor.pause()
            updateTile()
            return
        }
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ forbids starting a foreground service from a tile
            // without a visible activity: hand over to the app instead.
            startActivityAndCollapseCompat()
        } else {
            ContextCompat.startForegroundService(
                this,
                Intent(this, PlaybackService::class.java),
            )
            container.mixEditor.play()
            updateTile()
        }
    }

    @RequiresApi(34)
    private fun startActivityAndCollapseCompat() {
        val intent = Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_PLAY)
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val playing = AppGraph.get(this).playback.state.value.playing
        tile.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.updateTile()
    }
}
