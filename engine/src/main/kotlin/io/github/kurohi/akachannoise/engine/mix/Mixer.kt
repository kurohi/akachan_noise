package io.github.kurohi.akachannoise.engine.mix

import io.github.kurohi.akachannoise.engine.dsp.GainRamp
import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.ParamSmoother
import io.github.kurohi.akachannoise.engine.dsp.SoftClip
import io.github.kurohi.akachannoise.engine.generators.SoundGenerator
import io.github.kurohi.akachannoise.engine.model.EngineContext
import io.github.kurohi.akachannoise.engine.model.MixSpec
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Renders the active layers into the master bus with per-layer gain ramps,
 * crossfades, master gain automation (fades, "soothe -> settle" step-downs),
 * warmth filtering, optional mono fold-down and a safety soft clipper.
 *
 * All mutation happens through commands drained at the start of
 * [render] — which only ever runs on the render thread. No locks and no
 * allocations inside [render].
 */
class Mixer(
    private val sampleRate: Int,
    private val context: EngineContext,
    private val resolver: (String, Map<String, Float>) -> SoundGenerator?,
) {
    private val layers = ArrayList<Layer>(MixSpec.MAX_LAYERS)
    private var scratch = FloatArray(DEFAULT_BLOCK * 2)

    private val masterVolumeSmoother = ParamSmoother(60.0, sampleRate)
    private val masterRamp = GainRamp()
    private var stopPending = false

    private val warmthL = OnePole()
    private val warmthR = OnePole()
    private var warmth = 0f
    private var warmthCoefDirty = false

    private val monoSmoother = ParamSmoother(100.0, sampleRate)
    private var masterGain = 1f

    /** Updated at the end of every block; read by the playback layer to decide parking. */
    @Volatile var isAudible = false
        private set

    fun start(mix: MixSpec, fadeInMs: Int) {
        switchLayers(mix, fadeInMs)
        masterVolumeSmoother.setTargetImmediately(perceptualToGain(mix.masterVolume))
        masterRamp.begin(0f, perceptualToGain(mix.masterVolume), fadeInMs, sampleRate)
        stopPending = false
        applyTone(mix)
    }

    fun switchTo(mix: MixSpec, crossfadeMs: Int) {
        switchLayers(mix, crossfadeMs)
        masterVolumeSmoother.setTarget(perceptualToGain(mix.masterVolume))
        stopPending = false
        applyTone(mix)
    }

    fun stop(fadeOutMs: Int) {
        fadeMaster(0f, fadeOutMs)
        stopPending = true
    }

    fun fadeMaster(toVolume: Float, durationMs: Int) {
        val target = perceptualToGain(toVolume)
        val current = masterRamp.currentValue() * masterVolumeSmoother.current()
        masterRamp.begin(current, target, durationMs, sampleRate)
    }

    fun setMasterVolume(volume: Float) {
        masterVolumeSmoother.setTarget(perceptualToGain(volume))
    }

    fun setLayerVolume(soundId: String, volume: Float) {
        layer(soundId)?.let {
            it.targetGain = perceptualToGain(volume)
        }
    }

    fun setLayerParam(soundId: String, paramId: String, value: Float) {
        layer(soundId)?.generator?.setParam(paramId, value)
    }

    fun setWarmth(value: Float) {
        val w = value.coerceIn(0f, 1f)
        if (w != warmth) {
            warmth = w
            warmthCoefDirty = true
        }
    }

    fun setMono(mono: Boolean) {
        monoSmoother.setTarget(if (mono) 1f else 0f)
    }

    fun render(out: FloatArray, frames: Int) {
        val n = frames * 2
        if (n > scratch.size) scratch = FloatArray(n)
        context.clock.advanceBlock(frames)

        if (layers.isEmpty()) {
            java.util.Arrays.fill(out, 0, n, 0f)
            isAudible = false
            return
        }

        java.util.Arrays.fill(out, 0, n, 0f)

        val it = layers.iterator()
        while (it.hasNext()) {
            val layer = it.next()
            layer.generator.render(scratch, frames)
            var i = 0
            var j = 0
            while (i < frames) {
                val g = layer.nextGain()
                out[j] += scratch[j] * g
                out[j + 1] += scratch[j + 1] * g
                i++
                j += 2
            }
            if (layer.dead) it.remove()
        }

        if (warmthCoefDirty) {
            val cutoff = WARMTH_MAX_HZ * Math.pow(
                (WARMTH_MIN_HZ / WARMTH_MAX_HZ).toDouble(),
                warmth.toDouble(),
            ).toFloat()
            warmthL.setCutoff(cutoff)
            warmthR.setCutoff(cutoff)
            warmthL.updateCoefficients(sampleRate)
            warmthR.updateCoefficients(sampleRate)
            warmthCoefDirty = false
        }
        val useWarmth = warmth > 0.001f

        val volumeGain = masterVolumeSmoother.next()
        var i = 0
        var j = 0
        while (i < frames) {
            val g = volumeGain * masterRamp.next()
            var l = out[j] * g
            var r = out[j + 1] * g
            if (useWarmth) {
                l = warmthL.low(l)
                r = warmthR.low(r)
            }
            val m = monoSmoother.next()
            if (m > 0.001f) {
                val mid = (l + r) * 0.5f
                l = l + (mid - l) * m
                r = r + (mid - r) * m
            }
            out[j] = SoftClip.process(l)
            out[j + 1] = SoftClip.process(r)
            i++
            j += 2
        }

        if (stopPending && !masterRamp.isActive && masterRamp.currentValue() <= 0f) {
            layers.clear()
        }
        isAudible = layers.isNotEmpty() && masterRamp.currentValue() > 0.0001f && volumeGain > 0f
    }

    private fun switchLayers(mix: MixSpec, fadeMs: Int) {
        val specs = mix.layers.take(MixSpec.MAX_LAYERS)

        // Fade out and remove layers that are no longer in the mix.
        for (layer in layers) {
            if (layer.dying) continue
            if (specs.none { it.soundId == layer.soundId }) {
                layer.dying = true
                layer.ramp.begin(layer.gain, 0f, fadeMs, sampleRate)
            }
        }

        for (spec in specs) {
            val existing = layer(spec.soundId)
            if (existing != null) {
                spec.params.forEach { (k, v) -> existing.generator.setParam(k, v) }
                val target = perceptualToGain(spec.volume)
                existing.dying = false
                existing.targetGain = target
                if (existing.gain <= 0f) {
                    existing.ramp.begin(existing.gain, target, fadeMs, sampleRate)
                }
            } else {
                val generator = resolver(spec.soundId, spec.params) ?: continue
                spec.params.forEach { (k, v) -> generator.setParam(k, v) }
                val layer = Layer(spec.soundId, generator, sampleRate)
                layer.targetGain = perceptualToGain(spec.volume)
                layer.gain = 0f
                layer.ramp.begin(0f, layer.targetGain, fadeMs, sampleRate)
                layers.add(layer)
            }
        }
    }

    private fun applyTone(mix: MixSpec) {
        setWarmth(mix.warmth)
        setMono(mix.mono)
    }

    private fun layer(soundId: String): Layer? = layers.firstOrNull { it.soundId == soundId }

    private fun perceptualToGain(volume: Float): Float {
        val v = volume.coerceIn(0f, 1f)
        return v * v
    }

    /** One active sound with its gain automation. Lives and dies on the render thread. */
    private class Layer(val soundId: String, val generator: SoundGenerator, sampleRate: Int) {
        var targetGain = 0f
        var gain = 0f
        var dying = false
        var dead = false
        val ramp = GainRamp()

        /** One-pole smoothing coefficient for volume-slider moves (~30 ms). */
        private val coef = (1.0 - Math.exp(-1000.0 / (LAYER_SMOOTH_MS * sampleRate))).toFloat()

        fun nextGain(): Float {
            if (ramp.isActive) {
                gain = ramp.next()
            } else {
                gain += (targetGain - gain) * coef
            }
            if (dying && !ramp.isActive && gain <= 0f) dead = true
            return gain
        }
    }

    private companion object {
        const val DEFAULT_BLOCK = 512
        const val LAYER_SMOOTH_MS = 30f
        const val WARMTH_MAX_HZ = 18000f
        const val WARMTH_MIN_HZ = 2000f
    }
}
