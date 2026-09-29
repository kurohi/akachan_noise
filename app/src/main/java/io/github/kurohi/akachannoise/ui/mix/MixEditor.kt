package io.github.kurohi.akachannoise.ui.mix

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import io.github.kurohi.akachannoise.data.repo.MixRepository
import io.github.kurohi.akachannoise.data.repo.SettingsRepository
import io.github.kurohi.akachannoise.engine.model.BuiltInPresets
import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.engine.model.SoundCatalog
import io.github.kurohi.akachannoise.playback.PlaybackController
import io.github.kurohi.akachannoise.playback.PlaybackService
import io.github.kurohi.akachannoise.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The mix the user is currently listening to / editing. Sound edits are
 * applied to the running engine live (the mixer fades individual layers),
 * and everything is persisted only when the user saves.
 */
class MixEditor(
    private val appContext: Context,
    private val playback: PlaybackController,
    private val mixes: MixRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val mediaController: () -> MediaController?,
) {
    private val _mix = MutableStateFlow(BuiltInPresets.presets.first())
    val mix: StateFlow<MixSpec> = _mix.asStateFlow()

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playback.state

    init {
        scope.launch {
            val lastId = mixes.lastUsedMixId.filterNotNull().first()
            mixes.mixById(lastId)?.let { load(it) }
        }
    }

    fun load(mix: MixSpec) {
        val capped = mix.copy(
            masterVolume = mix.masterVolume.coerceAtMost(settings.settings.value.volumeCap),
        )
        _mix.value = capped
        _dirty.value = false
        playback.prepare(capped)
    }

    fun isPlaying(): Boolean = playback.state.value.playing

    fun play() {
        val current = _mix.value
        // Load the mix into the session first so the notification has an item,
        // then play through Media3 so the service goes to the foreground.
        playback.prepare(current)
        val controller = mediaController()
        if (controller != null) {
            controller.play()
        } else {
            // The session is still connecting (a very fast first tap): start
            // the service ourselves so the notification and foreground state
            // appear anyway.
            runCatching {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, PlaybackService::class.java),
                )
            }
            playback.play(current)
        }
        startDefaultTimerIfNeeded()
        scope.launch { mixes.setLastUsed(current.id) }
    }

    /** The timer is on by default, per the safety guidance. */
    private fun startDefaultTimerIfNeeded() {
        if (playback.state.value.timerRemainingMs != null) return
        val prefs = settings.settings.value
        if (prefs.defaultTimerMinutes <= 0) return
        startTimer(prefs.defaultTimerMinutes, prefs.timerFadeSeconds, prefs.stepDownEnabled)
    }

    fun pause() {
        val controller = mediaController()
        if (controller != null) controller.pause() else playback.pause()
    }

    fun togglePlayPause() {
        if (isPlaying()) pause() else play()
    }

    fun addSound(soundId: String) {
        val current = _mix.value
        if (current.layers.size >= MixSpec.MAX_LAYERS) return
        if (current.layers.any { it.soundId == soundId }) return
        val spec = SoundCatalog.specFor(soundId)
        val layer = LayerSpec(
            soundId = soundId,
            volume = DEFAULT_LAYER_VOLUME,
            params = spec?.params?.associate { it.id to it.default } ?: emptyMap(),
        )
        applyEdit(current.copy(layers = current.layers + layer)) { playback.switchTo(it) }
    }

    fun removeSound(soundId: String) {
        val current = _mix.value
        if (current.layers.none { it.soundId == soundId }) return
        applyEdit(current.copy(layers = current.layers.filterNot { it.soundId == soundId })) {
            playback.switchTo(it)
        }
    }

    fun toggleSound(soundId: String) {
        if (_mix.value.layers.any { it.soundId == soundId }) removeSound(soundId) else addSound(soundId)
    }

    fun setLayerVolume(soundId: String, volume: Float) {
        val current = _mix.value
        applyEdit(
            current.copy(
                layers = current.layers.map {
                    if (it.soundId == soundId) it.copy(volume = volume) else it
                },
            ),
        ) {
            playback.setLayerVolume(soundId, volume)
        }
    }

    fun setLayerParam(soundId: String, paramId: String, value: Float) {
        val current = _mix.value
        applyEdit(
            current.copy(
                layers = current.layers.map {
                    if (it.soundId == soundId) it.copy(params = it.params + (paramId to value)) else it
                },
            ),
        ) {
            playback.setLayerParam(soundId, paramId, value)
        }
    }

    fun setMasterVolume(volume: Float) {
        val capped = volume.coerceAtMost(settings.settings.value.volumeCap)
        val current = _mix.value
        applyEdit(current.copy(masterVolume = capped)) { playback.setMasterVolume(capped) }
    }

    fun setWarmth(warmth: Float) {
        val current = _mix.value
        applyEdit(current.copy(warmth = warmth)) { playback.setWarmth(warmth) }
    }

    fun setMono(mono: Boolean) {
        val current = _mix.value
        applyEdit(current.copy(mono = mono)) { playback.setMono(mono) }
    }

    fun renameCurrent(name: String) {
        _mix.value = _mix.value.copy(name = name.trim().ifEmpty { _mix.value.name })
        _dirty.value = true
    }

    /** Starts the sleep timer using the user's configured defaults. */
    fun startTimer(minutes: Int, fadeSeconds: Int, stepDown: Boolean) {
        playback.setTimer(
            durationMs = minutes * 60_000L,
            fadeOutMs = fadeSeconds * 1_000,
            stepDown = stepDown,
        )
    }

    fun cancelTimer() = playback.cancelTimer()

    /** Fire-and-forget favourite toggle for UI callbacks. */
    fun setFavorite(id: String, favorite: Boolean) {
        scope.launch { mixes.setFavorite(id, favorite) }
    }

    fun addTimerMinutes(minutes: Int) {
        val state = playback.state.value
        val remaining = state.timerRemainingMs ?: 0L
        val fadeSeconds = settings.settings.value.timerFadeSeconds
        playback.setTimer(
            durationMs = remaining + minutes * 60_000L,
            fadeOutMs = fadeSeconds * 1_000,
            stepDown = state.sootheToSettle,
        )
    }

    /**
     * Saves the current mix: preset-based edits are saved as a new mix, a
     * user mix is updated in place. Returns the saved mix.
     */
    suspend fun saveCurrent(name: String? = null): MixSpec {
        val current = _mix.value
        val requested = name?.trim().orEmpty().ifEmpty { current.name }
        val target = if (MixRepository.isPresetId(current.id)) {
            // Saving an edited preset creates the user's own copy.
            current.copy(id = MixRepository.newMixId(), name = mixes.uniqueMixName(requested))
        } else {
            current.copy(name = requested)
        }
        mixes.saveMix(target)
        mixes.setLastUsed(target.id)
        _mix.value = target
        _dirty.value = false
        return target
    }

    /** Plays [mix] from the library (preset or saved). */
    fun playMix(mix: MixSpec) {
        load(mix)
        play()
    }

    private fun applyEdit(next: MixSpec, apply: (MixSpec) -> Unit) {
        _mix.value = next
        _dirty.value = true
        if (playback.state.value.playing) apply(next) else playback.prepare(next)
    }

    companion object {
        const val DEFAULT_LAYER_VOLUME = 0.75f
    }
}
