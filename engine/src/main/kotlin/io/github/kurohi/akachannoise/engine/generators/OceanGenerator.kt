package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.dsp.lerp
import io.github.kurohi.akachannoise.engine.dsp.smoothstep

/**
 * Ocean waves: broadband noise swept by a slow asymmetrical wave cycle —
 * the filter opens as the wave builds, closes as it washes away. A quiet
 * constant bed keeps the sea from ever going fully silent.
 *
 * Params: [PARAM_PERIOD] seconds per wave (5-15), [PARAM_SIZE] 0-1,
 * [PARAM_DISTANCE] 0-1 (further away = more muffled).
 */
class OceanGenerator(
    private val sampleRate: Int,
    private val rng: Rng,
) : SoundGenerator {

    private var periodSec = 8f
    private var size = 0.6f
    private var distance = 0.3f
    private var wavePhase = 0f
    private val lpL = OnePole()
    private val lpR = OnePole()
    private var coefDirty = true

    override fun render(out: FloatArray, frames: Int) {
        // Filter coefficients are updated once per block from the wave
        // envelope (the swell is seconds long, so this is plenty).
        if (coefDirty) {
            val env = waveEnvelope(wavePhase)
            applyCutoff(env)
            coefDirty = false
        }
        val phaseStep = 1f / (periodSec * sampleRate)
        var i = 0
        var j = 0
        while (i < frames) {
            wavePhase += phaseStep
            if (wavePhase >= 1f) wavePhase -= 1f
            val env = waveEnvelope(wavePhase)
            val amp = (BED_AMP + (1f - BED_AMP) * env) * (0.35f + 0.65f * size)
            out[j] = lpL.low(rng.nextBipolar()) * amp
            out[j + 1] = lpR.low(rng.nextBipolar()) * amp
            i++
            j += 2
        }
    }

    private fun applyCutoff(env: Float) {
        val openness = env * (0.4f + 0.6f * size)
        val cutoff = (lerp(250f, 3800f, openness) * (1f - 0.75f * distance)).coerceIn(60f, 8000f)
        lpL.setCutoff(cutoff)
        lpL.updateCoefficients(sampleRate)
        lpR.setCutoff(cutoff)
        lpR.updateCoefficients(sampleRate)
    }

    private fun waveEnvelope(phase: Float): Float {
        val hump = smoothstep(0f, 0.55f, phase) * (1f - smoothstep(0.55f, 1f, phase))
        return hump * hump * hump
    }

    override fun setParam(id: String, value: Float) {
        when (id) {
            PARAM_PERIOD -> periodSec = value.coerceIn(5f, 15f)

            PARAM_SIZE -> {
                size = value.coerceIn(0f, 1f)
                coefDirty = true
            }

            PARAM_DISTANCE -> {
                distance = value.coerceIn(0f, 1f)
                coefDirty = true
            }
        }
    }

    override fun reset() {
        wavePhase = 0f
        lpL.reset()
        lpR.reset()
    }

    companion object {
        const val PARAM_PERIOD = "period"
        const val PARAM_SIZE = "size"
        const val PARAM_DISTANCE = "distance"
        private const val BED_AMP = 0.22f
    }
}
