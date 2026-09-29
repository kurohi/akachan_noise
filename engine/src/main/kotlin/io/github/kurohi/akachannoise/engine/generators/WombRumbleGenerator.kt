package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.dsp.SineLfo

/**
 * Womb ambience: a very deep, slowly breathing low-passed rumble — the
 * "engine room" sound of the womb. No parameters; it is a bed layer.
 */
class WombRumbleGenerator(
    private val sampleRate: Int,
    private val rng: Rng,
) : SoundGenerator {

    private val lpL = OnePole()
    private val lpR = OnePole()
    private val undulation = SineLfo(sampleRate)
    private var brownL = 0f
    private var brownR = 0f

    init {
        lpL.setCutoff(CUTOFF_HZ)
        lpL.updateCoefficients(sampleRate)
        lpR.setCutoff(CUTOFF_HZ)
        lpR.updateCoefficients(sampleRate)
    }

    override fun render(out: FloatArray, frames: Int) {
        var i = 0
        var j = 0
        while (i < frames) {
            val amp = 0.7f + 0.3f * undulation.next(UNDULATION_HZ)
            brownL = (brownL + rng.nextBipolar() * BROWN_INPUT) * BROWN_LEAK
            brownR = (brownR + rng.nextBipolar() * BROWN_INPUT) * BROWN_LEAK
            out[j] = lpL.low(brownL) * amp
            out[j + 1] = lpR.low(brownR) * amp
            i++
            j += 2
        }
    }

    override fun setParam(id: String, value: Float) = Unit

    override fun reset() {
        brownL = 0f
        brownR = 0f
        lpL.reset()
        lpR.reset()
    }

    companion object {
        private const val CUTOFF_HZ = 110f
        private const val UNDULATION_HZ = 0.13f
        private const val BROWN_INPUT = 0.02f
        private const val BROWN_LEAK = 1f / 1.02f
    }
}
