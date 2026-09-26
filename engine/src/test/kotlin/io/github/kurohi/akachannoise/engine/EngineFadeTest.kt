package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.engine.model.SoundId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fade behavior and silence guarantees, tested through the real engine. */
class EngineFadeTest {
    private val sampleRate = 48000
    private val block = 512

    private fun mix(vararg soundIds: SoundId) = MixSpec(
        id = "test",
        name = "test",
        layers = soundIds.map { LayerSpec(soundId = it.id, volume = 1f) },
        masterVolume = 1f,
    )

    @Test
    fun `fade out ends in exact silence and parks the engine`() {
        val engine = NoiseEngine(sampleRate)
        engine.start(mix(SoundId.NOISE_WHITE), fadeInMs = 0)
        val buffer = FloatArray(block * 2)

        // Warm up: fade-in applied, engine audible.
        repeat(sampleRate / block) { engine.render(buffer, block) }
        assertTrue(engine.isAudible)

        // Fade out over 500 ms.
        engine.fadeMaster(0f, 500)
        val fadeFrames = sampleRate / 2
        val windowFrames = sampleRate / 20 // 50 ms RMS windows
        val windows = fadeFrames / windowFrames + 2
        val rms = FloatArray(windows)
        var rendered = 0
        var w = 0
        while (rendered < fadeFrames + windowFrames * 2) {
            engine.render(buffer, block)
            var i = 0
            while (i < block && rendered < fadeFrames + windowFrames * 2) {
                val window = rendered / windowFrames
                if (window < windows) {
                    rms[window] += buffer[i * 2] * buffer[i * 2]
                }
                i++
                rendered++
            }
        }
        for (i in rms.indices) rms[i] = kotlin.math.sqrt(rms[i] / windowFrames)

        // The final window must be exactly silent.
        assertEquals(0f, rms[windows - 1], 0f)
        // The engine reports inaudible after the fade completes.
        engine.render(buffer, block)
        assertFalse(engine.isAudible)
        // And the fade itself is roughly monotone (noise jitter < 8%).
        var violations = 0
        for (i in 1 until windows - 2) {
            if (rms[i] > rms[i - 1] * 1.08f) violations++
        }
        assertTrue("fade not monotone ($violations violations: ${rms.joinToString()})", violations <= 1)
    }

    @Test
    fun `stop fades out and clears layers`() {
        val engine = NoiseEngine(sampleRate)
        engine.start(mix(SoundId.NOISE_PINK), fadeInMs = 0)
        val buffer = FloatArray(block * 2)
        repeat(sampleRate / block) { engine.render(buffer, block) }
        assertTrue(engine.isAudible)

        engine.stop(fadeOutMs = 200)
        repeat(sampleRate / block) { engine.render(buffer, block) } // 1 s
        assertFalse(engine.isAudible)
        // Further renders are silent and cheap.
        repeat(4) { engine.render(buffer, block) }
        var max = 0f
        for (v in buffer) max = maxOf(max, kotlin.math.abs(v))
        assertEquals(0f, max, 0f)
    }

    @Test
    fun `switching mixes crossfades without clicks`() {
        val engine = NoiseEngine(sampleRate)
        engine.start(mix(SoundId.NOISE_WHITE), fadeInMs = 0)
        val buffer = FloatArray(block * 2)
        repeat(sampleRate / block) { engine.render(buffer, block) }

        engine.switchTo(mix(SoundId.NOISE_BROWN), crossfadeMs = 1500)
        // During the crossfade output stays bounded and non-silent.
        var max = 0f
        var silent = true
        repeat(sampleRate / block / 2) {
            // 0.5 s into the crossfade
            engine.render(buffer, block)
            for (v in buffer) {
                assertTrue(!v.isNaN())
                max = maxOf(max, kotlin.math.abs(v))
                if (kotlin.math.abs(v) > 0.001f) silent = false
            }
        }
        assertTrue("crossfade went silent", !silent)
        assertTrue("max $max out of bounds", max <= 1.0f)
    }
}
