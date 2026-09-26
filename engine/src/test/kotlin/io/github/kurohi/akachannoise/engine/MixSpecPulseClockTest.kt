package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.generators.PulseClock
import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixSpecSerializationTest {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `round trip preserves the mix`() {
        val mix = MixSpec(
            id = "abc",
            name = "Womb",
            layers = listOf(
                LayerSpec(soundId = "heartbeat", volume = 0.9f, params = mapOf("bpm" to 72f)),
                LayerSpec(soundId = "noise_pink", volume = 0.5f),
            ),
            masterVolume = 0.8f,
            warmth = 0.25f,
            mono = true,
        )
        val decoded = json.decodeFromString<MixSpec>(json.encodeToString(MixSpec.serializer(), mix))
        assertEquals(mix, decoded)
    }

    @Test
    fun `defaults survive decoding`() {
        val decoded = json.decodeFromString<MixSpec>("""{"id":"a","name":"b"}""")
        assertEquals(emptyList<LayerSpec>(), decoded.layers)
        assertEquals(1f, decoded.masterVolume, 0f)
        assertEquals(LayerSpec.DEFAULT_VOLUME, LayerSpec(soundId = "x").volume, 0f)
    }
}

class PulseClockTest {
    @Test
    fun `average rate matches bpm`() {
        val clock = PulseClock(48000, Rng(1))
        clock.bpm = 72f
        val seconds = 60
        val blocks = seconds * 48000 / 512
        repeat(blocks) { clock.advanceBlock(512) }
        assertEquals(72.0, clock.beatPos, 0.5)
    }

    @Test
    fun `bpm is clamped to a safe range`() {
        val clock = PulseClock(48000, Rng(1))
        clock.bpm = 300f
        assertEquals(PulseClock.MAX_BPM, clock.bpm, 0f)
        clock.bpm = 10f
        assertEquals(PulseClock.MIN_BPM, clock.bpm, 0f)
    }

    @Test
    fun `jitter keeps the average rate stable`() {
        val clock = PulseClock(48000, Rng(2))
        clock.bpm = 80f
        clock.variability = 1f
        val blocks = 120 * 48000 / 512
        repeat(blocks) { clock.advanceBlock(512) }
        assertTrue("beatPos ${clock.beatPos}", kotlin.math.abs(clock.beatPos - 160.0) < 2.0)
    }
}
