package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.generators.FloatPcmSource
import io.github.kurohi.akachannoise.engine.generators.SampleLoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * User audio playback: seamless looping, pitch-preserving resampling and the
 * optional womb filter.
 */
class SampleLoopTest {
    private val sampleRate = 48000

    private fun sineSource(
        freqHz: Float,
        seconds: Double,
        rate: Int,
        channels: Int = 1,
    ): FloatPcmSource {
        val frames = (seconds * rate).toInt()
        val data = FloatArray(frames * channels)
        for (i in 0 until frames) {
            val v = sin(2.0 * PI * freqHz * i / rate).toFloat()
            for (c in 0 until channels) data[i * channels + c] = v
        }
        return FloatPcmSource(data, channels, rate)
    }

    private fun renderMono(gen: SampleLoop, seconds: Double): FloatArray {
        val frames = (seconds * sampleRate).toInt()
        val mono = FloatArray(frames)
        val buf = FloatArray(1024 * 2)
        var rendered = 0
        while (rendered < frames) {
            val n = minOf(1024, frames - rendered)
            gen.render(buf, n)
            for (i in 0 until n) mono[rendered + i] = buf[i * 2]
            rendered += n
        }
        return mono
    }

    @Test
    fun `loop seam is smooth`() {
        // 0.5 s of a 200 Hz tone loops many times over two seconds.
        val gen = SampleLoop(sineSource(200f, 0.5, sampleRate), sampleRate)
        val mono = renderMono(gen, 2.0)
        var maxJump = 0f
        for (i in 1 until mono.size) maxJump = maxOf(maxJump, abs(mono[i] - mono[i - 1]))
        // A 200 Hz sine at 48 kHz steps by at most ~0.026 per sample.
        assertTrue("max jump $maxJump suggests a click at the loop seam", maxJump < 0.05f)
    }

    @Test
    fun `resampling keeps the pitch`() {
        // 200 Hz at 24 kHz played at 48 kHz must still be 200 Hz.
        val gen = SampleLoop(sineSource(200f, 1.0, 24000), sampleRate)
        val mono = renderMono(gen, 0.5)
        var crossings = 0
        for (i in 1 until mono.size) {
            if (mono[i - 1] <= 0f && mono[i] > 0f) crossings++
        }
        // 0.5 s at 200 Hz = 100 cycles = 100 rising crossings.
        assertEquals(100.0, crossings.toDouble(), 2.0)
    }

    @Test
    fun `womb filter attenuates high frequencies`() {
        val source = sineSource(6000f, 0.5, sampleRate)
        val open = SampleLoop(source, sampleRate)
        val muffled = SampleLoop(source, sampleRate)
        muffled.setParam(SampleLoop.PARAM_WOMB_FILTER, 1f)

        fun rms(signal: FloatArray) = kotlin.math.sqrt(signal.sumOf { (it.toDouble()) * it } / signal.size)

        val openRms = rms(renderMono(open, 0.5))
        val muffledRms = rms(renderMono(muffled, 0.5))
        assertTrue("open $openRms muffled $muffledRms", muffledRms < openRms * 0.2)
    }

    @Test
    fun `stereo source keeps both channels`() {
        val frames = 4800
        val data = FloatArray(frames * 2)
        for (i in 0 until frames) {
            data[i * 2] = 0.5f
            data[i * 2 + 1] = -0.5f
        }
        val gen = SampleLoop(FloatPcmSource(data, 2, sampleRate), sampleRate)
        val buf = FloatArray(1024 * 2)
        gen.render(buf, 1024)
        assertTrue(buf[0] > 0.4f)
        assertTrue(buf[1] < -0.4f)
    }

    @Test
    fun `engine plays a custom sound through the resolver`() {
        val source = sineSource(300f, 0.4, sampleRate)
        val engine = NoiseEngine(
            sampleRate,
            customResolver = { soundId, _, _ ->
                if (soundId == "custom.test") SampleLoop(source, sampleRate) else null
            },
        )
        engine.start(
            io.github.kurohi.akachannoise.engine.model.MixSpec(
                id = "m",
                name = "m",
                layers = listOf(
                    io.github.kurohi.akachannoise.engine.model.LayerSpec("custom.test", 1f),
                ),
            ),
            fadeInMs = 0,
        )
        val buf = FloatArray(512 * 2)
        var peak = 0f
        repeat(40) {
            engine.render(buf, 512)
            for (v in buf) peak = maxOf(peak, abs(v))
        }
        assertTrue("custom sound produced no audio (peak $peak)", peak > 0.1f)
    }
}
