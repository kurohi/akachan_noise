package io.github.kurohi.akachannoise.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Listens for crying after the sleep timer ends and asks the playback
 * controller to start the last mix again.
 *
 * The service is *armed* from the visible app when playback starts, which is
 * what Android requires before a microphone foreground service may run; the
 * microphone itself is only opened once the timer ends, so the detector never
 * hears the mix that is playing. Audio is analysed in memory, frame by frame,
 * and never written anywhere.
 */
class CryMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listener: CryListener? = null
    private var ticker: Job? = null

    private var windowEndsAt = 0L
    private var sensitivity = 0.5f
    private var playMinutes = 10

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ARM -> {
                if (!CryListener.hasPermission(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                sensitivity = intent.getFloatExtra(EXTRA_SENSITIVITY, sensitivity)
                playMinutes = intent.getIntExtra(EXTRA_PLAY_MINUTES, playMinutes)
                val windowMinutes = intent.getIntExtra(EXTRA_WINDOW_MINUTES, 240)
                // Re-arming during an active window keeps the original end
                // time, so restarting the mix does not extend the night.
                if (windowEndsAt <= System.currentTimeMillis()) {
                    windowEndsAt = System.currentTimeMillis() + windowMinutes * 60_000L
                }
                promoteToForeground(armed = true)
                startTicker()
            }

            ACTION_LISTEN -> {
                if (windowEndsAt == 0L) {
                    windowEndsAt = System.currentTimeMillis() + DEFAULT_WINDOW_MINUTES * 60_000L
                }
                promoteToForeground(armed = false)
                startListening()
                startTicker()
            }

            ACTION_PLAY_NOW -> PlaybackController.get(this).restartFromCry(playMinutes)

            ACTION_STOP -> {
                PlaybackController.get(this).onCryMonitoringStopped()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        listener?.stop()
        listener = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startListening() {
        if (listener != null) return
        val cryListener = CryListener(sensitivity) {
            // Called on the listening thread: hop to the main thread before
            // touching playback state.
            mainHandler.post {
                // Close the microphone before the mix starts, so the detector
                // never hears the sound it is playing. Listening resumes when
                // the mix finishes.
                stopListening()
                PlaybackController.get(this).restartFromCry(playMinutes)
            }
        }
        val started = cryListener.start(
            shouldContinue = { System.currentTimeMillis() <= windowEndsAt },
        )
        if (!started) {
            stopSelf()
            return
        }
        listener = cryListener
        updateNotification(armed = false)
    }

    /** Releases the microphone but keeps the service and its window alive. */
    private fun stopListening() {
        listener?.stop()
        listener = null
        updateNotification(armed = true)
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val now = System.currentTimeMillis()
                if (windowEndsAt > 0 && now > windowEndsAt) {
                    PlaybackController.get(this@CryMonitorService).onCryMonitoringStopped()
                    stopSelf()
                    return@launch
                }
                // Resume listening once the mix has finished. The service is
                // already running, so this needs no background service start.
                val playing = PlaybackController.get(this@CryMonitorService).state.value.playing
                if (listener == null && !playing && windowEndsAt > 0) startListening()
                updateNotification(armed = listener == null)
            }
        }
    }

    private fun promoteToForeground(armed: Boolean) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(armed),
            if (Build.VERSION.SDK_INT >= 30) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
    }

    private fun buildNotification(armed: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.cry_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.cry_channel_description)
                },
            )
        }

        val text = if (armed) {
            getString(R.string.cry_notification_armed)
        } else {
            getString(R.string.cry_notification_listening, formatTime(windowEndsAt))
        }

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, CryMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val playIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CryMonitorService::class.java).setAction(ACTION_PLAY_NOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle(getString(R.string.cry_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(0, getString(R.string.cry_action_stop), stopIntent)
            .addAction(0, getString(R.string.cry_action_play_now), playIntent)
            .build()
    }

    private fun updateNotification(armed: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(armed))
    }

    private fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

    companion object {
        private const val CHANNEL_ID = "cry_monitor"
        private const val NOTIFICATION_ID = 2001
        private const val TICK_MS = 30_000L
        private const val DEFAULT_WINDOW_MINUTES = 240

        const val ACTION_ARM = "io.github.kurohi.akachannoise.cry.ARM"
        const val ACTION_LISTEN = "io.github.kurohi.akachannoise.cry.LISTEN"
        const val ACTION_STOP = "io.github.kurohi.akachannoise.cry.STOP"
        const val ACTION_PLAY_NOW = "io.github.kurohi.akachannoise.cry.PLAY_NOW"
        const val EXTRA_WINDOW_MINUTES = "window_minutes"
        const val EXTRA_SENSITIVITY = "sensitivity"
        const val EXTRA_PLAY_MINUTES = "play_minutes"

        /**
         * Arms the monitor while the app is visible (required for the mic
         * type). Never throws: a rejected background start simply leaves the
         * feature off for this session.
         */
        fun arm(context: Context, windowMinutes: Int, sensitivity: Float, playMinutes: Int) {
            val intent = Intent(context, CryMonitorService::class.java)
                .setAction(ACTION_ARM)
                .putExtra(EXTRA_WINDOW_MINUTES, windowMinutes)
                .putExtra(EXTRA_SENSITIVITY, sensitivity)
                .putExtra(EXTRA_PLAY_MINUTES, playMinutes)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        /**
         * Asks the monitor to open the microphone when the sleep timer ends.
         * Android forbids starting a microphone service from the background,
         * so this is best effort — the running service also resumes listening
         * on its own once playback stops.
         */
        fun startListening(context: Context) {
            val intent = Intent(context, CryMonitorService::class.java).setAction(ACTION_LISTEN)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CryMonitorService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
