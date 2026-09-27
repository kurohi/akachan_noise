package io.github.kurohi.akachannoise.playback.audio

import android.content.Context
import android.os.PowerManager
import io.github.kurohi.akachannoise.engine.NoiseEngine
import kotlin.concurrent.thread

/**
 * Owns the audio render thread. Renders blocks from the engine into the
 * AudioTrack; parks (releasing the wake lock) whenever the engine goes
 * silent, so a paused/stopped player costs zero CPU.
 *
 * Park/wake protocol: the thread sleeps while there is neither a render
 * request nor audible output. [requestRender] wakes it; it re-parks by
 * itself once the engine reports silence (fades always render to their
 * end before that happens).
 */
class RenderThread(
    context: Context,
    private val engine: NoiseEngine,
    private val sink: AudioTrackSink,
) {
    private val appContext = context.applicationContext
    private val lock = Object()
    private var renderRequested = false
    private var parked = true
    private var shutdown = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val thread = thread(start = true, name = "akachan-audio", isDaemon = true) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
        run()
    }

    /** Wakes the thread after playback (or a fade) has been started. */
    fun requestRender() {
        synchronized(lock) {
            renderRequested = true
            parked = false
            lock.notifyAll()
        }
    }

    fun shutdown() {
        synchronized(lock) {
            shutdown = true
            renderRequested = true
            lock.notifyAll()
        }
        thread.join(SHUTDOWN_JOIN_MS)
        // Releasing the sink unblocks a thread still stuck in write(); the
        // render loop catches the resulting IllegalStateException below.
        sink.release()
    }

    private fun run() {
        val buffer = FloatArray(BLOCK_FRAMES * 2)
        while (true) {
            synchronized(lock) {
                while (!shutdown && !renderRequested && !engine.isAudible) {
                    if (!parked) {
                        parked = true
                        releaseWakeLock()
                        sink.pause()
                    }
                    lock.wait()
                }
                if (shutdown) return
                parked = false
            }
            acquireWakeLock()
            sink.play()
            while (!shutdown) {
                synchronized(lock) {
                    if (!renderRequested && !engine.isAudible) break
                    renderRequested = false
                }
                try {
                    engine.render(buffer, BLOCK_FRAMES)
                    sink.write(buffer, BLOCK_FRAMES)
                } catch (e: IllegalStateException) {
                    // The sink was released by shutdown() while we were writing.
                    if (shutdown) return
                    throw e
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = appContext.getSystemService(PowerManager::class.java) ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "akachan:playback").apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.takeIf { !it.isHeld }?.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    companion object {
        const val BLOCK_FRAMES = 512
        private const val SHUTDOWN_JOIN_MS = 2_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L // 12 h safety cap
    }
}
