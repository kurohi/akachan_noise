package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.dsp.lerp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Womb blood flow: a soft mid/low noise bed that swells with each pulse of
 * the shared [PulseClock] (the systolic "whoosh"), falling quiet during
 * diastole. Sits naturally behind the heartbeat in a mix.
 *
 * Params: [PARAM_DEPTH] 0-1 (how deep the pulse swells),
 * [PARAM_MUFFLE] 0-1 (low-pass amount, "heard from inside").
 */
class BloodFlowGenerator(
    private val sampleRate: Int,
    private val clock: PulseClock,
    private val rng: Rng,
) : SoundGenerator {

    private var depth = 0.7f
    private var muffle = 0.85f
    private val lpL = OnePole()
    private val lpR = OnePole()
    private var coefDirty = true

    override fun render(out: FloatArray, frames: Int) {
        if (coefDirty) {
            val cutoff = lerp(1400f, 220f, muffle)
            lpL.setCutoff(cutoff)
            lpL.updateCoefficients(sampleRate)
            lpR.setCutoff(cutoff)
            lpR.updateCoefficients(sampleRate)
            coefDirty = false
        }
        val bps = clock.beatsPerSample
        val blockStartBeat = clock.beatPos - frames * bps
        var i = 0
        var j = 0
        while (i < frames) {
            val beat = blockStartBeat + i * bps
            val phase = (beat - floor(beat)).toFloat()
            val swell = if (phase < SWELL_SPAN) {
                val t = sin(PI_F * phase / SWELL_SPAN)
                t * t
            } else {
                0f
            }
            val amp = (1f - depth) * FLAT_AMP + depth * (DIASTOLE_AMP + (1f - DIASTOLE_AMP) * swell)
            out[j] = lpL.low(rng.nextBipolar()) * amp
            out[j + 1] = lpR.low(rng.nextBipolar()) * amp
            i++
            j += 2
        }
    }

    override fun setParam(id: String, value: Float) {
        when (id) {
            PARAM_DEPTH -> depth = value.coerceIn(0f, 1f)

            PARAM_MUFFLE -> {
                muffle = value.coerceIn(0f, 1f)
                coefDirty = true
            }
        }
    }

    override fun reset() {
        lpL.reset()
        lpR.reset()
    }

    companion object {
        const val PARAM_DEPTH = "depth"
        const val PARAM_MUFFLE = "muffle"
        private const val SWELL_SPAN = 0.6f
        private const val FLAT_AMP = 0.9f
        private const val DIASTOLE_AMP = 0.22f
        private const val PI_F = 3.14159265f
    }
}
