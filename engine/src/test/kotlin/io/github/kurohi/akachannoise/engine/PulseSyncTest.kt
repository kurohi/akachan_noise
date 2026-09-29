package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.engine.model.SoundId
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The blood-flow whoosh must swell in time with the shared PulseClock:
 * energy peaks right after each heartbeat instead of drifting.
 */
class PulseSyncTest {
    private val sampleRate = 48000
    private val block = 512

    private fun renderMono(mix: MixSpec, seconds: Double, seed: Long = 42): FloatArray {
        val engine = NoiseEngine(sampleRate, seed = seed)
        engine.start(mix, fadeInMs = 0)
        val totalFrames = (seconds * sampleRate).toInt()
        val mono = FloatArray(totalFrames)
        val buf = FloatArray(block * 2)
        var rendered = 0
        while (rendered < totalFrames) {
            val frames = minOf(block, totalFrames - rendered)
            engine.render(buf, frames)
            for (i in 0 until frames) mono[rendered + i] = buf[i * 2]
            rendered += frames
        }
        return mono
    }

    private fun beatOnsets(mono: FloatArray, bpm: Float): List<Int> {
        var maxAbs = 0f
        for (v in mono) maxAbs = maxOf(maxAbs, abs(v))
        val threshold = maxAbs * 0.4f
        val refractory = (0.4 * 60.0 / bpm * sampleRate).toInt()
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

    @Test
    fun `blood flow swells with each heartbeat`() {
        val bpm = 72f
        val heartbeatMix = MixSpec(
            id = "hb",
            name = "hb",
            layers = listOf(
                LayerSpec(
                    soundId = SoundId.HEARTBEAT.id,
                    volume = 1f,
                    params = mapOf("bpm" to bpm, "muffle" to 0.6f, "variability" to 0f),
                ),
            ),
        )
        val flowMix = MixSpec(
            id = "bf",
            name = "bf",
            layers = listOf(
                LayerSpec(
                    soundId = SoundId.BLOOD_FLOW.id,
                    volume = 1f,
                    params = mapOf("depth" to 0.7f, "muffle" to 0.85f, "bpm" to bpm, "variability" to 0f),
                ),
            ),
        )
        val heartbeat = renderMono(heartbeatMix, seconds = 14.0)
        val flow = renderMono(flowMix, seconds = 14.0)
        val onsets = beatOnsets(heartbeat, bpm)
        assertTrue("only ${onsets.size} beats found", onsets.size >= 12)

        val period = (60.0 / bpm * sampleRate).toInt()
        var systolicSum = 0.0
        var diastolicSum = 0.0
        var windows = 0
        for (onset in onsets) {
            val end = onset + period
            if (end >= flow.size || onset < period) continue
            var sys = 0.0
            var dia = 0.0
            for (i in onset until onset + period / 2) sys += flow[i].toDouble() * flow[i]
            for (i in onset + period / 2 until end) dia += flow[i].toDouble() * flow[i]
            systolicSum += sys
            diastolicSum += dia
            windows++
        }
        assertTrue("too few windows ($windows)", windows >= 10)
        val ratio = systolicSum / diastolicSum
        assertTrue("systolic/diastolic energy ratio $ratio too low", ratio > 2.0)
    }
}
