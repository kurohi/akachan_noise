package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.generators.OceanGenerator
import io.github.kurohi.akachannoise.engine.generators.RainGenerator
import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.engine.model.SoundCatalog
import io.github.kurohi.akachannoise.engine.model.SoundId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Volume, fades and loudness must mean the same thing on every path: the
 * slider, the step-down, a live parameter drag and a reloaded mix.
 */
class EngineConsistencyTest {
    private val sampleRate = 48000
    private val block = 512

    private fun mix(masterVolume: Float = 1f, vararg layers: LayerSpec) = MixSpec(
        id = "test",
        name = "test",
        layers = layers.toList().ifEmpty { listOf(LayerSpec(SoundId.NOISE_PINK.id, volume = 1f)) },
        masterVolume = masterVolume,
    )

    private fun rms(engine: NoiseEngine, seconds: Double): Double {
        val buffer = FloatArray(block * 2)
        var sum = 0.0
        var count = 0
        repeat((seconds * sampleRate / block).toInt()) {
            engine.render(buffer, block)
            for (v in buffer) sum += v.toDouble() * v
            count += buffer.size
        }
        return sqrt(sum / count)
    }

    private fun db(ratio: Double) = 20 * log10(ratio)

    @Test
    fun `master volume is applied once on a perceptual curve`() {
        val levels = listOf(1f, 0.5f).map { volume ->
            NoiseEngine(sampleRate).run {
                start(mix(volume), fadeInMs = 0)
                rms(this, 0.3)
                rms(this, 1.0)
            }
        }
        // 0.5 perceptual -> 0.25 gain -> -12 dB, not -24 dB.
        assertEquals(-12.0, db(levels[1] / levels[0]), 1.0)
    }

    @Test
    fun `step-down glides to half the listening volume, never louder`() {
        val engine = NoiseEngine(sampleRate)
        engine.start(mix(0.5f), fadeInMs = 0)
        rms(engine, 0.3)
        val before = rms(engine, 0.5)
        engine.fadeMaster(0.5f, 200)
        rms(engine, 0.3)
        val after = rms(engine, 1.0)
        assertEquals(-12.0, db(after / before), 1.0)
    }

    @Test
    fun `a slow fade-in keeps the engine rendering from the first block`() {
        val engine = NoiseEngine(sampleRate)
        engine.start(mix(0.2f), fadeInMs = 20_000)
        engine.render(FloatArray(block * 2), block)
        assertTrue("engine reported silence and would park", engine.isAudible)
    }

    @Test
    fun `resuming during a pause fade continues from the current level`() {
        val engine = NoiseEngine(sampleRate)
        engine.start(mix(), fadeInMs = 0)
        rms(engine, 0.5)
        engine.fadeMaster(0f, 1000)
        rms(engine, 0.5) // halfway down
        val mid = rms(engine, 0.02)
        engine.start(mix(), fadeInMs = 1000)
        val resumed = rms(engine, 0.02)
        assertTrue("resume dropped from $mid to $resumed", resumed > mid * 0.7)
    }

    @Test
    fun `a parameter changes loudness the same live and after reload`() {
        fun rain(intensity: Float) = LayerSpec(
            SoundId.RAIN.id,
            volume = 1f,
            params = mapOf(RainGenerator.PARAM_INTENSITY to intensity),
        )
        val reloaded = NoiseEngine(sampleRate).run {
            start(mix(1f, rain(1f)), fadeInMs = 0)
            rms(this, 0.5)
            rms(this, 3.0)
        }
        val live = NoiseEngine(sampleRate).run {
            start(mix(1f, rain(0.2f)), fadeInMs = 0)
            rms(this, 0.5)
            setLayerParam(SoundId.RAIN.id, RainGenerator.PARAM_INTENSITY, 1f)
            rms(this, 0.5)
            rms(this, 3.0)
        }
        assertEquals(0.0, db(live / reloaded), 1.0)
    }

    @Test
    fun `every sound stays finite at 44_1 kHz with extreme parameters`() {
        val rate = 44100
        for (spec in SoundCatalog.specs) {
            for (extreme in listOf(0f, 1f)) {
                val params = spec.params.associate { it.id to (it.min + (it.max - it.min) * extreme) }
                val engine = NoiseEngine(rate)
                engine.start(mix(1f, LayerSpec(spec.id.id, volume = 1f, params = params)), fadeInMs = 0)
                val buffer = FloatArray(block * 2)
                repeat(rate / block) {
                    engine.render(buffer, block)
                    for (v in buffer) {
                        assertTrue("${spec.id.id}@$extreme produced $v", !v.isNaN() && abs(v) <= 1f)
                    }
                }
            }
        }
    }

    @Test
    fun `ocean brightens toward each wave crest`() {
        val ocean = OceanGenerator(sampleRate, Rng(3))
        ocean.setParam(OceanGenerator.PARAM_PERIOD, 5f)
        val buffer = FloatArray(block * 2)
        val brightness = ArrayList<Double>()
        repeat(5 * sampleRate / block) {
            ocean.render(buffer, block)
            var diff = 0.0
            var energy = 0.0
            for (k in 2 until buffer.size step 2) {
                val d = buffer[k] - buffer[k - 2]
                diff += d * d
                energy += buffer[k] * buffer[k]
            }
            brightness.add(diff / energy)
        }
        // High-frequency share of the energy must swing with the swell.
        assertTrue("ocean timbre is static", brightness.max() / brightness.min() > 4.0)
    }
}
