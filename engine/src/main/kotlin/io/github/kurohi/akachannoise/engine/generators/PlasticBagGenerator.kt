package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.ExpEnvelope
import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.PoissonScheduler
import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.dsp.lerp

/**
 * Plastic-bag rustle (ビニール袋 — a well-known Japanese calming trick):
 * sparse Poisson crackles of high-passed noise with randomized attack,
 * decay and strength over a faint brushed-noise bed.
 *
 * Params: [PARAM_RATE] 0-1 (crackle density).
 */
class PlasticBagGenerator(
    private val sampleRate: Int,
    private val rng: Rng,
) : SoundGenerator {

    private var rate = 0.5f
    private val hpL = OnePole()
    private val hpR = OnePole()
    private val crackleEnv = ExpEnvelope(sampleRate)
    private val scheduler = PoissonScheduler(rng)
    private var framesToNextCrackle = 0
    private var crackleGain = 1f

    init {
        hpL.setCutoff(CRACKLE_HP_HZ)
        hpL.updateCoefficients(sampleRate)
        hpR.setCutoff(CRACKLE_HP_HZ)
        hpR.updateCoefficients(sampleRate)
    }

    override fun render(out: FloatArray, frames: Int) {
        val ratePerSecond = lerp(1.5f, 18f, rate)
        var i = 0
        var j = 0
        while (i < frames) {
            if (framesToNextCrackle <= 0) {
                framesToNextCrackle = scheduler.nextInterval(ratePerSecond, sampleRate)
                val attack = 0.3f + rng.nextFloat() * 1.2f
                val decay = 10f + rng.nextFloat() * 80f
                crackleEnv.trigger(attack, decay)
                crackleGain = 0.5f + rng.nextFloat() * 0.5f
            }
            framesToNextCrackle--
            val rawL = rng.nextBipolar()
            val rawR = rng.nextBipolar()
            val e = crackleEnv.next() * crackleGain
            out[j] = hpL.high(rawL) * e * CRACKLE_GAIN + hpL.high(rawL) * BED_GAIN
            out[j + 1] = hpR.high(rawR) * e * CRACKLE_GAIN + hpR.high(rawR) * BED_GAIN
            i++
            j += 2
        }
    }

    override fun setParam(id: String, value: Float) {
        if (id == PARAM_RATE) rate = value.coerceIn(0f, 1f)
    }

    override fun reset() {
        hpL.reset()
        hpR.reset()
        crackleEnv.reset()
        framesToNextCrackle = 0
    }

    companion object {
        const val PARAM_RATE = "rate"
        private const val CRACKLE_HP_HZ = 1200f
        private const val CRACKLE_GAIN = 2.2f
        private const val BED_GAIN = 0.04f
    }
}
