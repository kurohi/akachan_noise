package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.generators.PulseClock
import io.github.kurohi.akachannoise.engine.mix.Mixer
import io.github.kurohi.akachannoise.engine.model.EngineContext
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.engine.model.SoundCatalog
import java.util.concurrent.ConcurrentLinkedQueue

/** Version metadata for the engine (used in tests and debug screens). */
object EngineInfo {
    const val VERSION: String = "0.1.0"
}

/**
 * The audio engine facade. Thread-safe: every public method except [render]
 * may be called from any thread; commands are queued and applied at the next
 * block boundary on the render thread. [render] itself must only be called
 * from a single (render) thread.
 *
 * The engine is pure — it knows nothing about Android or audio devices.
 */
class NoiseEngine(
    val sampleRate: Int,
    seed: Long = DEFAULT_SEED,
) {
    private val rng = Rng(seed)
    private val clock = PulseClock(sampleRate, rng)
    private val context = EngineContext(sampleRate, rng, clock)
    private val mixer = Mixer(sampleRate, context, ::resolveGenerator)
    private val commands = ConcurrentLinkedQueue<EngineCommand>()

    /**
     * Renders [frames] interleaved stereo float frames into [out]
     * ([out].size >= frames * 2). Called from the render thread only.
     */
    fun render(out: FloatArray, frames: Int) {
        drainCommands()
        mixer.render(out, frames)
        isAudible = mixer.isAudible
    }

    /** True while the engine produces non-silent output; updated per block. */
    @Volatile var isAudible = false
        private set

    /** Starts a mix from silence, fading in over [fadeInMs]. */
    fun start(mix: MixSpec, fadeInMs: Int = DEFAULT_FADE_MS) {
        commands.add(EngineCommand.Start(mix, fadeInMs))
    }

    /** Crossfades from the current layers to [mix] over [crossfadeMs]. */
    fun switchTo(mix: MixSpec, crossfadeMs: Int = DEFAULT_CROSSFADE_MS) {
        commands.add(EngineCommand.Switch(mix, crossfadeMs))
    }

    /** Fades out over [fadeOutMs] and clears all layers. */
    fun stop(fadeOutMs: Int = DEFAULT_FADE_MS) {
        commands.add(EngineCommand.Stop(fadeOutMs))
    }

    /** Fades the master gain to [volume] (perceptual 0..1) over [durationMs]. */
    fun fadeMaster(volume: Float, durationMs: Int) {
        commands.add(EngineCommand.FadeMaster(volume, durationMs))
    }

    fun setMasterVolume(volume: Float) {
        commands.add(EngineCommand.SetMasterVolume(volume))
    }

    fun setLayerVolume(soundId: String, volume: Float) {
        commands.add(EngineCommand.SetLayerVolume(soundId, volume))
    }

    fun setLayerParam(soundId: String, paramId: String, value: Float) {
        commands.add(EngineCommand.SetLayerParam(soundId, paramId, value))
    }

    fun setWarmth(warmth: Float) {
        commands.add(EngineCommand.SetWarmth(warmth))
    }

    fun setMono(mono: Boolean) {
        commands.add(EngineCommand.SetMono(mono))
    }

    private fun drainCommands() {
        while (true) {
            when (val c = commands.poll() ?: return) {
                is EngineCommand.Start -> mixer.start(c.mix, c.fadeMs)
                is EngineCommand.Switch -> mixer.switchTo(c.mix, c.fadeMs)
                is EngineCommand.Stop -> mixer.stop(c.fadeMs)
                is EngineCommand.FadeMaster -> mixer.fadeMaster(c.volume, c.durationMs)
                is EngineCommand.SetMasterVolume -> mixer.setMasterVolume(c.volume)
                is EngineCommand.SetLayerVolume -> mixer.setLayerVolume(c.soundId, c.volume)
                is EngineCommand.SetLayerParam -> mixer.setLayerParam(c.soundId, c.paramId, c.value)
                is EngineCommand.SetWarmth -> mixer.setWarmth(c.warmth)
                is EngineCommand.SetMono -> mixer.setMono(c.mono)
            }
        }
    }

    private fun resolveGenerator(soundId: String, params: Map<String, Float>) = SoundCatalog.createGenerator(soundId, context, params)

    private sealed interface EngineCommand {
        data class Start(val mix: MixSpec, val fadeMs: Int) : EngineCommand
        data class Switch(val mix: MixSpec, val fadeMs: Int) : EngineCommand
        data class Stop(val fadeMs: Int) : EngineCommand
        data class FadeMaster(val volume: Float, val durationMs: Int) : EngineCommand
        data class SetMasterVolume(val volume: Float) : EngineCommand
        data class SetLayerVolume(val soundId: String, val volume: Float) : EngineCommand
        data class SetLayerParam(val soundId: String, val paramId: String, val value: Float) : EngineCommand
        data class SetWarmth(val warmth: Float) : EngineCommand
        data class SetMono(val mono: Boolean) : EngineCommand
    }

    companion object {
        const val DEFAULT_SEED = 0xACCE55L
        const val DEFAULT_FADE_MS = 800
        const val DEFAULT_CROSSFADE_MS = 1500
    }
}
