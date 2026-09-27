package io.github.kurohi.akachannoise.playback

import android.app.PendingIntent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts the [MediaSession] in the foreground while sound plays. Kept as a
 * thin shell — all playback logic lives in [PlaybackController] so the
 * in-process UI talks to the same instance.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var controller: PlaybackController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val controller = PlaybackController.get(this)
        this.controller = controller
        val player = NoisePlayer(controller)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val builder = MediaSession.Builder(this, player)
        if (sessionActivity != null) {
            builder.setSessionActivity(sessionActivity)
        }
        mediaSession = builder.build()

        // Mirror controller state into the session so the notification and
        // lock-screen controls reflect what the engine is doing.
        scope.launch {
            controller.state.collect {
                player.onControllerStateChanged()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        controller?.shutdown()
        controller = null
        super.onDestroy()
    }
}
