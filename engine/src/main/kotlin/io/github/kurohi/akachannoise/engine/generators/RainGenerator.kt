package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.ExpEnvelope
import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.PoissonScheduler
import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.dsp.lerp
import kotlin.math.sqrt

/**
 * Rain: a steady high-passed hiss whose brightness and level follow the
 * intensity, plus Poisson droplet blips layered on top whose rate follows
 * the density.
 *
 * Params: [PARAM_INTENSITY] 0-1, [PARAM_DENSITY] 0-1.
 */
class RainGenerator(
    private val sampleRate: Int,
    private val rng: Rng,
) : SoundGenerator {

    private var intensity = 0.5f
    private var density = 0.5f
    private val hissHpL = OnePole()
    private val hissHpR = OnePole()
    private val hissLpL = OnePole()
    private val hissLpR = OnePole()
    private val dropletHpL = OnePole()
    private val dropletHpR = OnePole()
    private val dropletEnv = ExpEnvelope(sampleRate)
    private val scheduler = PoissonScheduler(rng)
    private var framesToNextDroplet = 0
    private var coefDirty = true

    init {
        hissHpL.setCutoff(HISS_HP_HZ)
        hissHpL.updateCoefficients(sampleRate)
        hissHpR.setCutoff(HISS_HP_HZ)
        hissHpR.updateCoefficients(sampleRate)
        dropletHpL.setCutoff(DROPLET_HP_HZ)
        dropletHpL.updateCoefficients(sampleRate)
        dropletHpR.setCutoff(DROPLET_HP_HZ)
        dropletHpR.updateCoefficients(sampleRate)
    }

    override fun render(out: FloatArray, frames: Int) {
        if (coefDirty) {
            val brightness = lerp(2500f, 9000f, intensity)
            hissLpL.setCutoff(brightness)
            hissLpL.updateCoefficients(sampleRate)
            hissLpR.setCutoff(brightness)
            hissLpR.updateCoefficients(sampleRate)
            coefDirty = false
        }
        val ratePerSecond = lerp(0.5f, 25f, density)
        var i = 0
        var j = 0
        while (i < frames) {
            if (framesToNextDroplet <= 0) {
                framesToNextDroplet = scheduler.nextInterval(ratePerSecond, sampleRate)
                val decay = 20f + rng.nextFloat() * 60f
                dropletEnv.trigger(0.8f, decay)
            }
            framesToNextDroplet--
            val hissAmp = 0.15f + 0.85f * intensity
            val rawL = rng.nextBipolar()
            val rawR = rng.nextBipolar()
            val e = dropletEnv.next()
            var l = hissLpL.low(hissHpL.high(rawL)) * hissAmp
            var r = hissLpR.low(hissHpR.high(rawR)) * hissAmp
            if (e > 0f) {
                l += dropletHpL.high(rawL) * e * DROPLET_GAIN
                r += dropletHpR.high(rawR) * e * DROPLET_GAIN
            }
            out[j] = l
            out[j + 1] = r
            i++
            j += 2
        }
    }

    override fun setParam(id: String, value: Float) {
        when (id) {
            PARAM_INTENSITY -> {
                intensity = value.coerceIn(0f, 1f)
                coefDirty = true
            }

            PARAM_DENSITY -> density = value.coerceIn(0f, 1f)
        }
    }

    override fun reset() {
        hissHpL.reset()
        hissHpR.reset()
        hissLpL.reset()
        hissLpR.reset()
        dropletHpL.reset()
        dropletHpR.reset()
        dropletEnv.reset()
        framesToNextDroplet = 0
    }

    companion object {
        const val PARAM_INTENSITY = "intensity"
        const val PARAM_DENSITY = "density"
        private const val HISS_HP_HZ = 600f
        private const val DROPLET_HP_HZ = 1400f
        private const val DROPLET_GAIN = 1.6f

        /** Rough amplitude of one droplet, used by tests. */
        fun dropletGain(): Float = DROPLET_GAIN * sqrt(1f / 3f)
    }
}
