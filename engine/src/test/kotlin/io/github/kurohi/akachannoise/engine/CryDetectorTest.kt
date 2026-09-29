package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.cry.CryDetector
import io.github.kurohi.akachannoise.engine.dsp.Rng
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The cry detector must fire on harmonic, voiced, burst-like sounds in the
 * 250-800 Hz band and stay quiet for noise, fan, ocean and low-pitched
 * speech-like sounds.
 */
class CryDetectorTest {
    private val sampleRate = CryDetector.DEFAULT_SAMPLE_RATE
    private val rng = Rng(2026)

    /** A cry: harmonic tone around [f0] with a slow attack/decay envelope. */
    private fun cryBurst(f0: Float, seconds: Double, amplitude: Float = 0.25f): FloatArray {
        val frames = (seconds * sampleRate).toInt()
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-3.0 * t) * (1.0 - exp(-25.0 * t))
            var v = 0.0
            v += sin(2.0 * PI * f0 * t)
            v += 0.6 * sin(2.0 * PI * 2 * f0 * t)
            v += 0.4 * sin(2.0 * PI * 3 * f0 * t)
            v += 0.25 * sin(2.0 * PI * 4 * f0 * t)
            // Slight vibrato, as a real cry has.
            v *= 1.0 + 0.05 * sin(2.0 * PI * 6.0 * t)
            out[i] = (v * envelope * amplitude).toFloat() + rng.nextBipolar() * 0.002f
        }
        return out
    }

    private fun quietNoise(seconds: Double, level: Float = 0.002f): FloatArray {
        val frames = (seconds * sampleRate).toInt()
        return FloatArray(frames) { rng.nextBipolar() * level }
    }

    private fun concat(vararg parts: FloatArray): FloatArray {
        val total = parts.sumOf { it.size }
        val out = FloatArray(total)
        var offset = 0
        for (part in parts) {
            System.arraycopy(part, 0, out, offset, part.size)
            offset += part.size
        }
        return out
    }

    private fun feed(detector: CryDetector, signal: FloatArray, chunk: Int = 512): Boolean {
        var index = 0
        while (index < signal.size) {
            val count = minOf(chunk, signal.size - index)
            val chunkArray = signal.copyOfRange(index, index + count)
            if (detector.process(chunkArray, count)) return true
            index += count
        }
        return false
    }

    @Test
    fun `detects two cry bursts`() {
        val detector = CryDetector(sampleRate, sensitivity = 0.5f)
        val signal = concat(
            quietNoise(0.5),
            cryBurst(400f, 1.0),
            quietNoise(0.5),
            cryBurst(400f, 1.0),
            quietNoise(0.5),
        )
        assertTrue("a two-burst cry was not detected", feed(detector, signal))
    }

    @Test
    fun `detects a cry at the edges of the pitch band`() {
        for (f0 in listOf(280f, 550f, 750f)) {
            val detector = CryDetector(sampleRate, sensitivity = 0.5f)
            val signal = concat(
                quietNoise(0.4),
                cryBurst(f0, 1.0),
                quietNoise(0.4),
                cryBurst(f0, 1.0),
            )
            assertTrue("cry at $f0 Hz was not detected", feed(detector, signal))
        }
    }

    @Test
    fun `a single burst is not enough`() {
        val detector = CryDetector(sampleRate, sensitivity = 0.5f)
        val signal = concat(
            quietNoise(0.5),
            cryBurst(400f, 1.2),
            quietNoise(1.5),
        )
        assertFalse("one burst should not restart playback", feed(detector, signal))
    }

    @Test
    fun `silence and steady noise do not trigger`() {
        val detector = CryDetector(sampleRate, sensitivity = 0.5f)
        assertFalse("silence triggered", feed(detector, quietNoise(6.0, 0.0005f)))
    }

    @Test
    fun `white noise does not trigger`() {
        val detector = CryDetector(sampleRate, sensitivity = 0.5f)
        val frames = (8.0 * sampleRate).toInt()
        val white = FloatArray(frames) { rng.nextBipolar() * 0.2f }
        assertFalse("white noise triggered", feed(detector, white))
    }

    @Test
    fun `fan noise does not trigger`() {
        val detector = CryDetector(sampleRate, sensitivity = 0.5f)
        val frames = (8.0 * sampleRate).toInt()
        val fan = FloatArray(frames) { i ->
            val t = i.toDouble() / sampleRate
            val hum = sin(2.0 * PI * 60.0 * t) + 0.5 * sin(2.0 * PI * 120.0 * t)
            (hum * 0.1 + rng.nextBipolar() * 0.08).toFloat()
        }
        assertFalse("fan noise triggered", feed(detector, fan))
    }

    @Test
    fun `ocean-like noise does not trigger`() {
        val detector = CryDetector(sampleRate, sensitivity = 0.5f)
        val frames = (8.0 * sampleRate).toInt()
        var low = 0f
        val ocean = FloatArray(frames) { i ->
            val t = i.toDouble() / sampleRate
            low += 0.02f * (rng.nextBipolar() - low)
            val swell = 0.5 + 0.5 * sin(2.0 * PI * 0.12 * t)
            (low * 3.0 * swell).toFloat()
        }
        assertFalse("ocean noise triggered", feed(detector, ocean))
    }

    @Test
    fun `low pitched speech-like sound does not trigger`() {
        val detector = CryDetector(sampleRate, sensitivity = 0.5f)
        val signal = concat(
            quietNoise(0.4),
            cryBurst(120f, 1.2, amplitude = 0.3f),
            quietNoise(0.4),
            cryBurst(120f, 1.2, amplitude = 0.3f),
            quietNoise(0.4),
        )
        assertFalse("a 120 Hz voice triggered the detector", feed(detector, signal))
    }

    @Test
    fun `level and pitch are reported for the meter`() {
        val detector = CryDetector(sampleRate, sensitivity = 0.5f)
        feed(detector, concat(quietNoise(0.2), cryBurst(400f, 0.6)))
        assertTrue("level should have moved", detector.level > 0f)
        assertTrue("pitch should be reported", detector.pitchHz in 300f..520f)
        assertTrue("flatness should be tonal", detector.flatness < 0.6f)
    }

    @Test
    fun `reset clears the window so a stale cry cannot fire`() {
        val detector = CryDetector(sampleRate, sensitivity = 0.5f)
        feed(detector, concat(quietNoise(0.3), cryBurst(400f, 1.0)))
        detector.reset()
        assertFalse(feed(detector, quietNoise(2.0)))
    }
}
