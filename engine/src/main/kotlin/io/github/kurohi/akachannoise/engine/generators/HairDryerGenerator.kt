package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.GustLfo
import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.Rng
import kotlin.math.PI
import kotlin.math.sin

/**
 * Hair dryer: a bright, steady rushing noise (high-passed) over a higher
 * motor tone with a harmonic, plus a faint fast wobble. No parameters —
 * a hair dryer is a hair dryer.
 */
class HairDryerGenerator(
    private val sampleRate: Int,
    private val rng: Rng,
) : SoundGenerator {

    private var motorPhase = 0.0
    private val hpL = OnePole()
    private val hpR = OnePole()
    private val lpL = OnePole()
    private val lpR = OnePole()
    private val wobble = GustLfo(rng, sampleRate)

    init {
        hpL.setCutoff(HP_HZ)
        hpL.updateCoefficients(sampleRate)
        hpR.setCutoff(HP_HZ)
        hpR.updateCoefficients(sampleRate)
        lpL.setCutoff(LP_HZ)
        lpL.updateCoefficients(sampleRate)
        lpR.setCutoff(LP_HZ)
        lpR.updateCoefficients(sampleRate)
    }

    override fun render(out: FloatArray, frames: Int) {
        var i = 0
        var j = 0
        while (i < frames) {
            motorPhase += MOTOR_HZ / sampleRate
            motorPhase -= motorPhase.toLong()
            val motor = (
                sin(motorPhase * TWO_PI) + 0.35 * sin(motorPhase * 2.0 * TWO_PI)
                ).toFloat() * MOTOR_GAIN
            val amp = 0.95f + 0.05f * wobble.next(WOBBLE_HZ)
            out[j] = (motor + lpL.low(hpL.high(rng.nextBipolar())) * NOISE_GAIN) * amp
            out[j + 1] = (motor + lpR.low(hpR.high(rng.nextBipolar())) * NOISE_GAIN) * amp
            i++
            j += 2
        }
    }

    override fun setParam(id: String, value: Float) = Unit

    override fun reset() {
        motorPhase = 0.0
        hpL.reset()
        hpR.reset()
        lpL.reset()
        lpR.reset()
        wobble.reset()
    }

    companion object {
        private const val MOTOR_HZ = 210f
        private const val MOTOR_GAIN = 0.28f
        private const val NOISE_GAIN = 1.4f
        private const val HP_HZ = 350f
        private const val LP_HZ = 9500f
        private const val WOBBLE_HZ = 6f
        private const val TWO_PI = PI * 2.0
    }
}
