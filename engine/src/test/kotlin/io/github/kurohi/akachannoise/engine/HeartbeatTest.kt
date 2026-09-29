package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.generators.HeartbeatGenerator
import io.github.kurohi.akachannoise.engine.generators.PulseClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The heartbeat must keep the promised tempo: beat onsets within ±1% of the
 * BPM, with the lub stronger than the dub in every beat.
 */
class HeartbeatTest {
    private val sampleRate = 48000
    private val block = 512

    private fun renderHeartbeat(bpm: Float, seconds: Double): FloatArray {
        val clock = PulseClock(sampleRate, Rng(1))
        clock.bpm = bpm
        clock.variability = 0f
        val gen = HeartbeatGenerator(sampleRate, clock, Rng(2))
        gen.setParam(HeartbeatGenerator.PARAM_BPM, bpm)
        gen.setParam(HeartbeatGenerator.PARAM_MUFFLE, 0.6f)
        val totalFrames = (seconds * sampleRate).toInt()
        val mono = FloatArray(totalFrames)
        val buf = FloatArray(block * 2)
        var rendered = 0
        while (rendered < totalFrames) {
            val frames = minOf(block, totalFrames - rendered)
            clock.advanceBlock(frames)
            gen.render(buf, frames)
            for (i in 0 until frames) mono[rendered + i] = buf[i * 2]
            rendered += frames
        }
        return mono
    }

    private fun onsets(mono: FloatArray, refractorySec: Double): List<Int> {
        var maxAbs = 0f
        for (v in mono) maxAbs = maxOf(maxAbs, abs(v))
        val threshold = maxAbs * 0.4f
        val refractory = (refractorySec * sampleRate).toInt()
        val result = mutableListOf<Int>()
        var lastOnset = -refractory - 1
        var above = false
        for (i in mono.indices) {
            if (abs(mono[i]) > threshold) {
                if (!above && i - lastOnset >= refractory) {
                    result.add(i)
                    lastOnset = i
                }
                above = true
            } else {
                above = false
            }
        }
        return result
    }

    private fun meanInterval(onsets: List<Int>): Double = (onsets.last() - onsets.first()).toDouble() / (onsets.size - 1)

    @Test
    fun `beat onsets match bpm within one percent`() {
        val bpm = 72f
        val mono = renderHeartbeat(bpm, seconds = 12.0)
        val beats = onsets(mono, refractorySec = 0.4 * 60.0 / bpm)
        assertTrue("only ${beats.size} beats found", beats.size >= 10)
        val expected = 60.0 / bpm * sampleRate
        val actual = meanInterval(beats)
        assertEquals("mean interval $actual vs $expected", expected, actual, expected * 0.01)
    }

    @Test
    fun `bpm parameter changes the tempo`() {
        val mono60 = renderHeartbeat(60f, seconds = 10.0)
        val mono110 = renderHeartbeat(110f, seconds = 10.0)
        val interval60 = meanInterval(onsets(mono60, 0.4 * 60.0 / 60.0))
        val interval110 = meanInterval(onsets(mono110, 0.4 * 60.0 / 110.0))
        val ratio = interval110 / interval60
        assertEquals(60.0 / 110.0, ratio, 0.02)
    }

    @Test
    fun `lub is stronger than dub in every beat`() {
        val bpm = 72f
        val period = (60.0 / bpm * sampleRate).toInt()
        val mono = renderHeartbeat(bpm, seconds = 12.0)
        val beats = onsets(mono, refractorySec = 0.4 * 60.0 / bpm)
        var beatsChecked = 0
        for (onset in beats) {
            if (onset + period / 2 >= mono.size) break
            // First half of the beat contains lub then dub; split at the
            // expected S1-S2 split (~0.18 beats) plus envelope tails.
            val split = onset + (period * 0.32).toInt()
            var lubMax = 0f
            var dubMax = 0f
            for (i in onset until split) lubMax = maxOf(lubMax, abs(mono[i]))
            for (i in split until onset + period / 2) dubMax = maxOf(dubMax, abs(mono[i]))
            if (lubMax > 0f && dubMax > 0f) {
                assertTrue("lub $lubMax not stronger than dub $dubMax at $onset", lubMax > dubMax)
                beatsChecked++
            }
        }
        assertTrue("only $beatsChecked beats checked", beatsChecked >= 8)
    }
}
