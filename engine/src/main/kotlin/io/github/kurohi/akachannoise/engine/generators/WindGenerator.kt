package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.GustLfo
import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.dsp.lerp

/**
 * Wind: low-passed noise whose cutoff and level follow a smooth random
 * gust walk. Gustiness raises both how often gusts come and how deep
 * they swell.
 *
 * Params: [PARAM_GUSTINESS] 0-1.
 */
class WindGenerator(
    private val sampleRate: Int,
    private val rng: Rng,
) : SoundGenerator {

    private var gustiness = 0.4f
    private val gust = GustLfo(rng, sampleRate)
    private val lpL = OnePole()
    private val lpR = OnePole()
    private var coefDirty = true

    override fun render(out: FloatArray, frames: Int) {
        val gustRate = lerp(0.08f, 0.5f, gustiness)
        val gustDepth = lerp(0.3f, 1f, gustiness)
        var i = 0
        var j = 0
        var blockGust01 = -1f
        while (i < frames) {
            if (i % COEF_UPDATE_FRAMES == 0) {
                blockGust01 = ((gust.next(gustRate) + 1f) * 0.5f).coerceIn(0f, 1f)
                applyCutoff(blockGust01)
            } else {
                // Keep the walk advancing even between coefficient updates.
                gust.next(gustRate)
            }
            val amp = 0.45f + 0.55f * (gustDepth * blockGust01 + (1f - gustDepth) * 0.5f)
            out[j] = lpL.low(rng.nextBipolar()) * amp
            out[j + 1] = lpR.low(rng.nextBipolar()) * amp
            i++
            j += 2
        }
        coefDirty = false
    }

    private fun applyCutoff(gust01: Float) {
        val cutoff = lerp(300f, 2200f, gust01)
        lpL.setCutoff(cutoff)
        lpL.updateCoefficients(sampleRate)
        lpR.setCutoff(cutoff)
        lpR.updateCoefficients(sampleRate)
    }

    override fun setParam(id: String, value: Float) {
        if (id == PARAM_GUSTINESS) gustiness = value.coerceIn(0f, 1f)
    }

    override fun reset() {
        gust.reset()
        lpL.reset()
        lpR.reset()
    }

    companion object {
        const val PARAM_GUSTINESS = "gustiness"
        private const val COEF_UPDATE_FRAMES = 128
    }
}
