package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.generators.PulseClock
import io.github.kurohi.akachannoise.engine.model.EngineContext
import io.github.kurohi.akachannoise.engine.model.SoundCatalog
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

/**
 * Every built-in sound must render finite, bounded samples and sit within
 * ±1.5 dB of the target RMS at 100% volume, including at parameter extremes.
 * The shared beat clock is advanced per block here exactly as the mixer does,
 * so pulse-locked sounds (heartbeat, blood flow) render their beats.
 */
class SoundLibraryTest {
    private val sampleRate = 48000

    private fun context(): EngineContext = EngineContext(sampleRate, Rng(1234), PulseClock(sampleRate, Rng(7)))

    /** Renders [frames] in blocks, advancing the shared clock as the mixer does. */
    private fun renderBlocks(
        gen: io.github.kurohi.akachannoise.engine.generators.SoundGenerator,
        clock: PulseClock,
        totalFrames: Int,
        block: Int = 1024,
        onBuffer: ((FloatArray, Int) -> Unit)? = null,
    ) {
        val buf = FloatArray(block * 2)
        var rendered = 0
        while (rendered < totalFrames) {
            val frames = minOf(block, totalFrames - rendered)
            clock.advanceBlock(frames)
            gen.render(buf, frames)
            onBuffer?.invoke(buf, frames)
            rendered += frames
        }
    }

    @Test
    fun `every sound renders finite bounded samples`() {
        for (spec in SoundCatalog.specs) {
            val context = context()
            val gen = SoundCatalog.createGenerator(spec.id.id, context, emptyMap())
                ?: error("no generator for ${spec.id.id}")
            var bad = false
            renderBlocks(gen, context.clock, 2048 * 10) { buf, frames ->
                for (i in 0 until frames * 2) {
                    val v = buf[i]
                    if (v.isNaN() || v.isInfinite() || abs(v) > 4f) {
                        assertTrue("${spec.id.id} bad sample $v", false)
                        bad = true
                    }
                }
            }
            assertTrue("${spec.id.id} produced bad samples", !bad)
        }
    }

    @Test
    fun `every sound is loudness calibrated within one and a half dB`() {
        for (spec in SoundCatalog.specs) {
            val context = context()
            val gen = SoundCatalog.createGenerator(spec.id.id, context, emptyMap())
                ?: error("no generator for ${spec.id.id}")
            val seconds = maxOf(spec.calibrationSeconds, 2.0)
            val totalFrames = (seconds * sampleRate).toInt()
            var sumSq = 0.0
            var count = 0
            renderBlocks(gen, context.clock, totalFrames) { buf, frames ->
                for (i in 0 until frames * 2) {
                    val v = buf[i].toDouble()
                    sumSq += v * v
                }
                count += frames * 2
            }
            val rms = kotlin.math.sqrt(sumSq / count)
            val db = 20 * log10(rms / SoundCatalog.TARGET_RMS)
            assertTrue("${spec.id.id} loudness $db dB off (rms=$rms)", abs(db) <= 1.5)
        }
    }

    @Test
    fun `parameter extremes stay finite`() {
        for (spec in SoundCatalog.specs) {
            for (param in spec.params) {
                for (extreme in listOf(param.min, param.max)) {
                    val context = context()
                    val gen = SoundCatalog.createGenerator(
                        spec.id.id,
                        context,
                        mapOf(param.id to extreme),
                    ) ?: error("no generator for ${spec.id.id}")
                    renderBlocks(gen, context.clock, 1024 * 3) { buf, frames ->
                        for (i in 0 until frames * 2) {
                            val v = buf[i]
                            assertTrue("${spec.id.id}/${param.id}=$extreme NaN", !v.isNaN())
                            assertTrue("${spec.id.id}/${param.id}=$extreme Inf", !v.isInfinite())
                            assertTrue(
                                "${spec.id.id}/${param.id}=$extreme out of range $v",
                                abs(v) <= 8f,
                            )
                        }
                    }
                }
            }
        }
    }
}
