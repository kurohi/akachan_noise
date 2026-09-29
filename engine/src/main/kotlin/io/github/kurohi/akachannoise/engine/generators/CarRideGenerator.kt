package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.GustLfo
import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.dsp.lerp

/**
 * Car ride: a deep, gently bumping rumble (road surface through the
 * chassis) plus a mid-band tire/road texture. Speed scales the bump rate
 * and texture brightness; texture scales the road surface amount.
 *
 * Params: [PARAM_SPEED] 0-1, [PARAM_TEXTURE] 0-1.
 */
class CarRideGenerator(
    private val sampleRate: Int,
    private val rng: Rng,
) : SoundGenerator {

    private var speed = 0.5f
    private var texture = 0.5f
    private var brownL = 0f
    private var brownR = 0f
    private val rumbleLpL = OnePole()
    private val rumbleLpR = OnePole()
    private val roadLpL = OnePole()
    private val roadLpR = OnePole()
    private val bumps = GustLfo(rng, sampleRate)
    private var coefDirty = true

    init {
        rumbleLpL.setCutoff(RUMBLE_LP_HZ)
        rumbleLpL.updateCoefficients(sampleRate)
        rumbleLpR.setCutoff(RUMBLE_LP_HZ)
        rumbleLpR.updateCoefficients(sampleRate)
    }

    override fun render(out: FloatArray, frames: Int) {
        if (coefDirty) {
            val roadCutoff = lerp(500f, 2200f, texture)
            roadLpL.setCutoff(roadCutoff)
            roadLpL.updateCoefficients(sampleRate)
            roadLpR.setCutoff(roadCutoff)
            roadLpR.updateCoefficients(sampleRate)
            coefDirty = false
        }
        val bumpRate = lerp(1.5f, 8f, speed)
        var i = 0
        var j = 0
        while (i < frames) {
            val bump01 = ((bumps.next(bumpRate) + 1f) * 0.5f).coerceIn(0f, 1f)
            val rumbleAmp = 0.55f + 0.45f * bump01
            val roadAmp = 0.3f + 0.7f * texture
            brownL = (brownL + rng.nextBipolar() * BROWN_INPUT) * BROWN_LEAK
            brownR = (brownR + rng.nextBipolar() * BROWN_INPUT) * BROWN_LEAK
            out[j] = rumbleLpL.low(brownL) * rumbleAmp * 1.4f + roadLpL.low(rng.nextBipolar()) * roadAmp
            out[j + 1] = rumbleLpR.low(brownR) * rumbleAmp * 1.4f + roadLpR.low(rng.nextBipolar()) * roadAmp
            i++
            j += 2
        }
    }

    override fun setParam(id: String, value: Float) {
        when (id) {
            PARAM_SPEED -> speed = value.coerceIn(0f, 1f)

            PARAM_TEXTURE -> {
                texture = value.coerceIn(0f, 1f)
                coefDirty = true
            }
        }
    }

    override fun reset() {
        brownL = 0f
        brownR = 0f
        rumbleLpL.reset()
        rumbleLpR.reset()
        roadLpL.reset()
        roadLpR.reset()
        bumps.reset()
    }

    companion object {
        const val PARAM_SPEED = "speed"
        const val PARAM_TEXTURE = "texture"
        private const val RUMBLE_LP_HZ = 85f
        private const val BROWN_INPUT = 0.02f
        private const val BROWN_LEAK = 1f / 1.02f
    }
}
