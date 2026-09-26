package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.generators.NoiseGenerator
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Verifies the spectral signature of each noise color: white flat,
 * pink -3 dB/octave, brown -6 dB/octave.
 */
class NoiseSpectrumTest {
    private val sampleRate = 48000

    private fun renderMono(color: Float, frames: Int = 1 shl 18): FloatArray {
        val rng = Rng(1234)
        val gen = NoiseGenerator(sampleRate, rng, color)
        val buf = FloatArray(frames * 2)
        gen.render(buf, frames)
        val mono = FloatArray(frames)
        for (i in 0 until frames) mono[i] = buf[i * 2]
        return mono
    }

    /** dB difference between two adjacent octave bands. */
    private fun octaveSlopeDb(color: Float, fLow: Double): Double {
        val signal = renderMono(color)
        val power = TestFft.powerSpectrum(signal, sampleRate)
        val fHigh = fLow * 4
        val mid = fLow * 2
        return TestFft.bandDb(power, sampleRate, mid, fHigh) -
            TestFft.bandDb(power, sampleRate, fLow, mid)
    }

    @Test
    fun `white noise is flat within 1_5 dB per octave`() {
        val signal = renderMono(NoiseGenerator.COLOR_WHITE)
        val power = TestFft.powerSpectrum(signal, sampleRate)
        // Average band energy over 200 Hz..12.8 kHz: all within 1.5 dB of the mean.
        val bands = mutableListOf<Double>()
        var f = 200.0
        while (f < 12800) {
            bands += TestFft.bandDb(power, sampleRate, f, f * 2)
            f *= 2
        }
        val mean = bands.average()
        bands.forEach { assertTrue("band $it vs mean $mean", abs(it - mean) <= 1.5) }
    }

    @Test
    fun `pink noise falls at 3 dB per octave`() {
        val slope = octaveSlopeDb(NoiseGenerator.COLOR_PINK, fLow = 100.0)
        assertTrue("slope was $slope", abs(slope + 3.0) <= 0.75)
    }

    @Test
    fun `brown noise falls at 6 dB per octave`() {
        val slope = octaveSlopeDb(NoiseGenerator.COLOR_BROWN, fLow = 250.0)
        assertTrue("slope was $slope", abs(slope + 6.0) <= 1.0)
    }

    @Test
    fun `loudness is calibrated for every color`() {
        for (color in listOf(
            NoiseGenerator.COLOR_WHITE,
            NoiseGenerator.COLOR_PINK,
            NoiseGenerator.COLOR_BROWN,
            0.25f,
            0.75f,
        )) {
            val signal = renderMono(color, frames = 1 shl 16)
            val rms = TestFft.rms(signal)
            val target = NoiseGenerator.TARGET_RMS.toDouble()
            val db = 20 * log10(rms / target)
            assertTrue("color $color rms $rms ($db dB off)", abs(db) <= 1.5)
        }
    }

    @Test
    fun `stereo width control works`() {
        val rng = Rng(55)
        val gen = NoiseGenerator(sampleRate, rng)
        gen.setParam(NoiseGenerator.PARAM_WIDTH, 1f)
        val frames = 1 shl 14
        val buf = FloatArray(frames * 2)
        gen.render(buf, frames)
        var correlated = 0
        for (i in 0 until frames) {
            if (buf[i * 2] == buf[i * 2 + 1]) correlated++
        }
        assertTrue("channels look identical", correlated < frames / 100)

        gen.setParam(NoiseGenerator.PARAM_WIDTH, 0f)
        // Width smoothing is ~30 ms; render past it.
        repeat(4) { gen.render(buf, frames) }
        var equal = 0
        for (i in 0 until frames) {
            if (abs(buf[i * 2] - buf[i * 2 + 1]) < 1e-6f) equal++
        }
        assertTrue("width 0 should collapse to mono, equal=$equal", equal >= frames * 99 / 100)
        // Mono fold of decorrelated stereo is -3 dB (single-speaker downmix).
        val mono = FloatArray(frames) { buf[it * 2] }
        val rms = TestFft.rms(mono)
        val db = 20 * log10(rms / NoiseGenerator.TARGET_RMS)
        assertTrue("mono loudness $db dB off", db >= -4.0 && db <= 0.5)
    }
}
