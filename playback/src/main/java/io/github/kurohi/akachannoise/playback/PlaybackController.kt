package io.github.kurohi.akachannoise.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.kurohi.akachannoise.engine.NoiseEngine
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.playback.audio.AudioTrackSink
import io.github.kurohi.akachannoise.playback.audio.RenderThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI-facing playback state (survives process death of the view layer). */
data class PlaybackState(
    val playing: Boolean = false,
    val currentMix: MixSpec? = null,
    val masterVolume: Float = 1f,
    val timerRemainingMs: Long? = null,
    val timerTotalMs: Long? = null,
    val timerEndsAtElapsed: Long? = null,
    val sootheToSettle: Boolean = false,
)

/**
 * Process-wide playback coordinator: owns the engine, render thread,
 * sleep timer, soothe-to-settle schedule, audio focus and headset handling.
 * The Media3 session ([NoisePlayer]) is a thin adapter over this class.
 */
class PlaybackController private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sink = AudioTrackSink(appContext)
    private val engine = NoiseEngine(sink.sampleRate)
    private val renderThread = RenderThread(appContext, engine, sink)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var stepDownJob: Job? = null
    private var masterVolume = 1f
    private var lastMix: MixSpec? = null
    private var pausedByFocus = false
    private var duckedByFocus = false

    /** True once [shutdown] has run; used by [get] to recreate the singleton. */
    @Volatile private var isShutdown = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause(reason = PauseReason.HEADSET_DISCONNECTED)
            }
        }
    }
    private var noisyReceiverRegistered = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener { change ->
            scope.launch { handleFocusChange(change) }
        }
        .build()

    // ---- Public API (any thread) -------------------------------------------

    /** Loads [mix] as the pending mix without starting audio. */
    fun prepare(mix: MixSpec) {
        lastMix = mix
        engine.setMasterVolume(mix.masterVolume.coerceIn(0f, 1f))
        engine.setWarmth(mix.warmth)
        engine.setMono(mix.mono)
        _state.value = _state.value.copy(currentMix = mix)
    }

    /**
     * Starts playback of [mix] (or the last prepared mix), fading in.
     * Called by NoisePlayer when the session goes to playWhenReady.
     */
    fun play(mix: MixSpec? = null, fadeInMs: Int = FADE_IN_MS) {
        val target = mix ?: lastMix ?: return
        prepare(target)
        requestFocus()
        registerNoisyReceiver()
        if (_state.value.playing) {
            engine.switchTo(target, crossfadeMs = CROSSFADE_MS)
        } else {
            engine.start(target, fadeInMs)
        }
        renderThread.requestRender()
        _state.value = _state.value.copy(playing = true, currentMix = target)
        startStepDownIfEnabled()
    }

    /** Crossfades to [mix] while playing. */
    fun switchTo(mix: MixSpec, crossfadeMs: Int = CROSSFADE_MS) {
        lastMix = mix
        if (_state.value.playing) {
            engine.switchTo(mix, crossfadeMs)
            _state.value = _state.value.copy(currentMix = mix)
        } else {
            prepare(mix)
        }
    }

    fun pause(fadeOutMs: Int = PAUSE_FADE_MS, reason: PauseReason = PauseReason.USER) {
        if (!_state.value.playing) return
        engine.fadeMaster(0f, fadeOutMs)
        renderThread.requestRender() // keep rendering through the fade
        _state.value = _state.value.copy(playing = false)
        if (reason != PauseReason.TRANSIENT_FOCUS) {
            abandonFocus()
            unregisterNoisyReceiver()
            cancelTimer()
        }
    }

    fun stop(fadeOutMs: Int = PAUSE_FADE_MS) {
        pause(reason = PauseReason.USER)
        scope.launch {
            delay(fadeOutMs.toLong())
            engine.stop(0)
        }
        lastMix?.let { _state.value = _state.value.copy(currentMix = it) }
    }

    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        engine.setMasterVolume(masterVolume)
        _state.value = _state.value.copy(masterVolume = masterVolume)
    }

    fun setLayerVolume(soundId: String, volume: Float) = engine.setLayerVolume(soundId, volume)

    fun setLayerParam(soundId: String, paramId: String, value: Float) = engine.setLayerParam(soundId, paramId, value)

    fun setWarmth(warmth: Float) = engine.setWarmth(warmth)

    fun setMono(mono: Boolean) = engine.setMono(mono)

    // ---- Sleep timer --------------------------------------------------------

    /**
     * Schedules a stop after [durationMs] with a [fadeOutMs] fade.
     * Also (optionally) runs the "soothe → settle" volume step-down before
     * the timer ends.
     */
    fun setTimer(durationMs: Long, fadeOutMs: Int = TIMER_FADE_MS, stepDown: Boolean = true) {
        cancelTimer()
        val endsAt = SystemClock.elapsedRealtime() + durationMs
        _state.value = _state.value.copy(
            timerEndsAtElapsed = endsAt,
            timerRemainingMs = durationMs,
            timerTotalMs = durationMs,
            sootheToSettle = stepDown,
        )
        timerJob = scope.launch {
            val fadeStart = (durationMs - fadeOutMs).coerceAtLeast(0)
            delay(fadeStart)
            if (_state.value.playing) {
                engine.fadeMaster(0f, fadeOutMs)
                renderThread.requestRender()
            }
            delay(fadeOutMs.toLong())
            timerEnd()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        stepDownJob?.cancel()
        stepDownJob = null
        _state.value = _state.value.copy(
            timerEndsAtElapsed = null,
            timerRemainingMs = null,
            timerTotalMs = null,
            sootheToSettle = false,
        )
    }

    private fun timerEnd() {
        engine.stop(0)
        _state.value = _state.value.copy(
            playing = false,
            timerEndsAtElapsed = null,
            timerRemainingMs = null,
            timerTotalMs = null,
            sootheToSettle = false,
        )
        abandonFocus()
        unregisterNoisyReceiver()
    }

    private fun startStepDownIfEnabled() {
        stepDownJob?.cancel()
        if (!_state.value.sootheToSettle) return
        stepDownJob = scope.launch {
            delay(STEP_DOWN_HOLD_MS)
            engine.fadeMaster(STEP_DOWN_TARGET_VOLUME, STEP_DOWN_GLIDE_MS)
            renderThread.requestRender()
        }
    }

    // ---- Audio focus --------------------------------------------------------

    private fun handleFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocus = true
                pause(reason = PauseReason.TRANSIENT_FOCUS)
                // Permanent loss: full stop of focus bookkeeping.
                if (_state.value.playing.not()) abandonFocus()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedByFocus = true
                pause(reason = PauseReason.TRANSIENT_FOCUS)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duckedByFocus = true
                engine.fadeMaster(DUCK_VOLUME, DUCK_FADE_MS)
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (duckedByFocus) {
                    duckedByFocus = false
                    engine.fadeMaster(masterVolume, DUCK_FADE_MS)
                }
                if (pausedByFocus) {
                    pausedByFocus = false
                    play()
                }
            }
        }
    }

    private fun requestFocus() {
        audioManager?.requestAudioFocus(focusRequest)
    }

    private fun abandonFocus() {
        audioManager?.abandonAudioFocusRequest(focusRequest)
        pausedByFocus = false
        duckedByFocus = false
    }

    // ---- Headset ------------------------------------------------------------

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { appContext.unregisterReceiver(becomingNoisyReceiver) }
        noisyReceiverRegistered = false
    }

    /** For Media3: maps the session player onto this controller. */
    val engineForPlayer: NoiseEngine get() = engine

    fun shutdown() {
        if (isShutdown) return
        isShutdown = true
        cancelTimer()
        abandonFocus()
        unregisterNoisyReceiver()
        engine.stop(0)
        scope.cancel()
        renderThread.shutdown()
    }

    enum class PauseReason { USER, TRANSIENT_FOCUS, HEADSET_DISCONNECTED, TIMER }

    companion object {
        const val FADE_IN_MS = 800
        const val PAUSE_FADE_MS = 300
        const val CROSSFADE_MS = 1500
        const val TIMER_FADE_MS = 5000
        const val STEP_DOWN_HOLD_MS = 5 * 60 * 1000L
        const val STEP_DOWN_GLIDE_MS = 3 * 60 * 1000
        const val STEP_DOWN_TARGET_VOLUME = 0.5f
        const val DUCK_VOLUME = 0.2f
        const val DUCK_FADE_MS = 300

        @Volatile private var instance: PlaybackController? = null

        fun get(context: Context): PlaybackController = synchronized(this) {
            val current = instance
            if (current == null || current.isShutdown) {
                PlaybackController(context.applicationContext).also { instance = it }
            } else {
                current
            }
        }
    }
}
