package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.dsp.Svf
import io.github.kurohi.akachannoise.engine.dsp.lerp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Shush (Karp-style): band-passed noise shaped like a long "shhh" with a
 * breath-like envelope. Rhythm selects continuous, slow or fast pulsing.
 *
 * Params: [PARAM_RHYTHM] 0 = continuous, 1 = slow (~0.55 Hz),
 * 2 = fast (~1.1 Hz); [PARAM_TONE] 0-1 shifts the band center.
 */
class ShushGenerator(
    private val sampleRate: Int,
    private val rng: Rng,
) : SoundGenerator {

    private var rhythm = 1f
    private var tone = 0.5f
    private var breathPhase = 0f
    private var breathFreq = SLOW_HZ
    private val bpL = Svf()
    private val bpR = Svf()
    private val bp2L = Svf()
    private val bp2R = Svf()
    private val lpL = OnePole()
    private val lpR = OnePole()

    init {
        lpL.setCutoff(LP_HZ)
        lpL.updateCoefficients(sampleRate)
        lpR.setCutoff(LP_HZ)
        lpR.updateCoefficients(sampleRate)
    }

    override fun render(out: FloatArray, frames: Int) {
        val center = lerp(1500f, 3000f, tone)
        val continuous = rhythm < 0.5f
        val phaseStep = breathFreq / sampleRate
        var i = 0
        var j = 0
        while (i < frames) {
            if (!continuous) {
                breathPhase += phaseStep
                if (breathPhase >= 1f) breathPhase -= 1f
            }
            val env = if (continuous) {
                1f
            } else {
                val s = sin(PI_F * breathPhase).coerceAtLeast(0f)
                REST_AMP + (1f - REST_AMP) * s * s
            }
            val rawL = rng.nextBipolar()
            val rawR = rng.nextBipolar()
            val shapedL = bpL.bandPass(rawL, center, sampleRate, 1.3f) * 0.7f +
                bp2L.bandPass(rawL, center * 2f, sampleRate, 2f) * 0.3f
            val shapedR = bpR.bandPass(rawR, center, sampleRate, 1.3f) * 0.7f +
                bp2R.bandPass(rawR, center * 2f, sampleRate, 2f) * 0.3f
            out[j] = lpL.low(shapedL) * env
            out[j + 1] = lpR.low(shapedR) * env
            i++
            j += 2
        }
    }

    override fun setParam(id: String, value: Float) {
        when (id) {
            PARAM_RHYTHM -> {
                rhythm = value.coerceIn(0f, 2f)
                breathFreq = when {
                    rhythm < 0.5f -> SLOW_HZ
                    rhythm < 1.5f -> SLOW_HZ
                    else -> FAST_HZ
                }
            }

            PARAM_TONE -> tone = value.coerceIn(0f, 1f)
        }
    }

    override fun reset() {
        breathPhase = 0f
        bpL.reset()
        bpR.reset()
        bp2L.reset()
        bp2R.reset()
        lpL.reset()
        lpR.reset()
    }

    companion object {
        const val PARAM_RHYTHM = "rhythm"
        const val PARAM_TONE = "tone"
        private const val SLOW_HZ = 0.55f
        private const val FAST_HZ = 1.1f
        private const val REST_AMP = 0.1f
        private const val LP_HZ = 6500f
        private const val PI_F = 3.14159265f
    }
}
