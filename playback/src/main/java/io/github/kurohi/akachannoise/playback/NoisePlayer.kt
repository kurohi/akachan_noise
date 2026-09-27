package io.github.kurohi.akachannoise.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Bridges Media3 onto [PlaybackController]. Gives us the system media
 * notification, lock-screen controls, headset buttons and correct
 * foreground-service lifecycle without any network player machinery.
 *
 * The UI talks to [PlaybackController] directly (same process); this player
 * only mirrors that state into the media session.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class NoisePlayer(
    private val controller: PlaybackController,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    @Volatile private var playWhenReady = false

    @Volatile private var currentMediaItem: MediaItem? = null

    private val commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            // The notification controller only mirrors the timeline/metadata
            // when these commands are available; without them Media3 sees an
            // empty timeline and never shows the media notification.
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
        )
        .build()

    override fun getState(): State {
        val item = currentMediaItem
        val playlist = if (item == null) {
            emptyList()
        } else {
            listOf(
                // Explicit metadata: SimpleBasePlayer only derives it from
                // the MediaItem otherwise, and the compat layer + notification
                // read the MediaItemData metadata directly.
                MediaItemData.Builder(item)
                    .setMediaMetadata(item.mediaMetadata)
                    .build(),
            )
        }
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (item == null) Player.STATE_IDLE else Player.STATE_READY)
            .setPlaylist(playlist)
            .build()
    }

    override fun handleSetPlayWhenReady(play: Boolean): ListenableFuture<*> {
        playWhenReady = play
        if (play) {
            controller.play()
        } else {
            controller.pause()
        }
        // Pull the controller snapshot so the session never observes a
        // play request against an empty timeline (the state flow collector
        // runs asynchronously and may not have updated the item yet).
        syncFromController()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playWhenReady = false
        currentMediaItem = null
        controller.stop()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    /** Mirrors controller playback state into the session (called by the service). */
    fun onControllerStateChanged() {
        syncFromController()
        invalidateState()
    }

    private fun syncFromController() {
        val s = controller.state.value
        playWhenReady = s.playing
        val mix = s.currentMix
        if (mix != null) {
            currentMediaItem = MediaItem.Builder()
                .setMediaId(mix.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(mix.name)
                        .setArtist(APP_NAME)
                        .build(),
                )
                .build()
        }
    }

    companion object {
        const val APP_NAME = "Akachan Noise"
    }
}
