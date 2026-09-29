package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.dsp.lerp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Fan: a low motor hum with harmonics plus a broadband blade wash whose
 * amplitude follows the blade-pass rate. Speed scales hum pitch, blade
 * rate and wash level together.
 *
 * Params: [PARAM_SPEED] 0-1.
 */
class FanGenerator(
    private val sampleRate: Int,
    private val rng: Rng,
) : SoundGenerator {

    private var speed = 0.5f
    private var humPhase = 0.0
    private var bladePhase = 0.0
    private val washLpL = OnePole()
    private val washLpR = OnePole()

    init {
        washLpL.setCutoff(WASH_LP_HZ)
        washLpL.updateCoefficients(sampleRate)
        washLpR.setCutoff(WASH_LP_HZ)
        washLpR.updateCoefficients(sampleRate)
    }

    override fun render(out: FloatArray, frames: Int) {
        val f0 = lerp(45f, 95f, speed)
        val bladeRate = f0 * 1.5f
        var i = 0
        var j = 0
        while (i < frames) {
            humPhase += f0 / sampleRate
            humPhase -= humPhase.toLong()
            bladePhase += bladeRate / sampleRate
            bladePhase -= bladePhase.toLong()
            val hum = (
                sin(humPhase * TWO_PI) +
                    HARMONIC_2 * sin(humPhase * 2.0 * TWO_PI) +
                    HARMONIC_3 * sin(humPhase * 3.0 * TWO_PI)
                ).toFloat() * HUM_GAIN
            val washAmp = 0.7f + 0.3f * sin((bladePhase * TWO_PI).toFloat())
            val wash = washLpL.low(rng.nextBipolar()) * washAmp
            val washR = washLpR.low(rng.nextBipolar()) * washAmp
            out[j] = hum + wash * WASH_GAIN
            out[j + 1] = hum + washR * WASH_GAIN
            i++
            j += 2
        }
    }

    override fun setParam(id: String, value: Float) {
        if (id == PARAM_SPEED) speed = value.coerceIn(0f, 1f)
    }

    override fun reset() {
        humPhase = 0.0
        bladePhase = 0.0
        washLpL.reset()
        washLpR.reset()
    }

    companion object {
        const val PARAM_SPEED = "speed"
        private const val HARMONIC_2 = 0.5
        private const val HARMONIC_3 = 0.25
        private const val HUM_GAIN = 0.32f
        private const val WASH_GAIN = 0.75f
        private const val WASH_LP_HZ = 900f
        private const val TWO_PI = PI * 2.0
    }
}
