package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.GustLfo
import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.dsp.Svf
import io.github.kurohi.akachannoise.engine.dsp.lerp

/**
 * Stream: two gently wandering resonant bands over noise, amplitude-wobbled
 * by a slow random walk — the burble of a small brook.
 *
 * Params: [PARAM_INTENSITY] 0-1 (level and brightness).
 */
class StreamGenerator(
    private val sampleRate: Int,
    private val rng: Rng,
) : SoundGenerator {

    private var intensity = 0.6f
    private val bp1L = Svf()
    private val bp1R = Svf()
    private val bp2L = Svf()
    private val bp2R = Svf()
    private val softenL = OnePole()
    private val softenR = OnePole()
    private val wobble = GustLfo(rng, sampleRate)
    private var coefDirty = true

    init {
        softenL.setCutoff(SOFTEN_HZ)
        softenL.updateCoefficients(sampleRate)
        softenR.setCutoff(SOFTEN_HZ)
        softenR.updateCoefficients(sampleRate)
    }

    override fun render(out: FloatArray, frames: Int) {
        var i = 0
        var j = 0
        while (i < frames) {
            // Wandering resonance centers keep the burble alive.
            val wander = wobble.next(WANDER_RATE_HZ)
            val c1 = BP1_HZ * (1f + 0.2f * wander)
            val c2 = BP2_HZ * (1f - 0.25f * wander)
            val rawL = rng.nextBipolar()
            var rawR = rng.nextBipolar()
            val l = softenL.low(bp1L.bandPass(rawL, c1, sampleRate, 1.4f) * 0.65f + bp2L.bandPass(rawL, c2, sampleRate, 1.8f) * 0.45f)
            val r = softenR.low(bp1R.bandPass(rawR, c1, sampleRate, 1.4f) * 0.65f + bp2R.bandPass(rawR, c2, sampleRate, 1.8f) * 0.45f)
            val amp = lerp(0.3f, 1.1f, intensity)
            out[j] = l * amp
            out[j + 1] = r * amp
            i++
            j += 2
        }
        coefDirty = false
    }

    override fun setParam(id: String, value: Float) {
        if (id == PARAM_INTENSITY) intensity = value.coerceIn(0f, 1f)
    }

    override fun reset() {
        bp1L.reset()
        bp1R.reset()
        bp2L.reset()
        bp2R.reset()
        softenL.reset()
        softenR.reset()
        wobble.reset()
    }

    companion object {
        const val PARAM_INTENSITY = "intensity"
        private const val BP1_HZ = 650f
        private const val BP2_HZ = 1800f
        private const val SOFTEN_HZ = 5000f
        private const val WANDER_RATE_HZ = 2.5f
    }
}
