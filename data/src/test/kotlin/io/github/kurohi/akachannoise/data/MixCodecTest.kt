package io.github.kurohi.akachannoise.data

import io.github.kurohi.akachannoise.data.codec.MixCodec
import io.github.kurohi.akachannoise.data.model.CustomSoundRef
import io.github.kurohi.akachannoise.data.model.UserData
import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MixCodecTest {

    private val womb = MixSpec(
        id = "preset.womb",
        name = "Womb",
        layers = listOf(
            LayerSpec("heartbeat", 0.9f, mapOf("bpm" to 72f, "muffle" to 0.7f)),
            LayerSpec("blood_flow", 0.8f, mapOf("depth" to 0.7f)),
            LayerSpec("womb_rumble", 0.7f),
        ),
        masterVolume = 0.9f,
        warmth = 0.2f,
        mono = true,
    )

    @Test
    fun `mix json round trips`() {
        val decoded = MixCodec.decodeMixJson(MixCodec.encodeMixJson(womb))
        assertEquals(womb, decoded)
    }

    @Test
    fun `share code round trips`() {
        val code = MixCodec.encodeShareCode(womb)
        assertEquals(womb, MixCodec.decodeShareCode(code))
    }

    @Test
    fun `share code is url safe and compact`() {
        val code = MixCodec.encodeShareCode(womb)
        assertTrue("code contains url-unsafe chars: $code", code.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertTrue("code suspiciously short: ${code.length}", code.length > 20)
        assertTrue("share code is not compact: ${code.length}", code.length < 600)
    }

    @Test
    fun `share uri round trips`() {
        val uri = MixCodec.shareUri(womb)
        assertTrue(uri.startsWith("akachannoise://mix?d="))
        assertEquals(womb, MixCodec.mixFromShareUri(uri))
    }

    @Test
    fun `corrupt input is rejected without throwing`() {
        assertNull(MixCodec.decodeMixJson("{ not json"))
        assertNull(MixCodec.decodeMixJson(""))
        assertNull(MixCodec.decodeShareCode("!!!not-base64!!!"))
        assertNull(MixCodec.decodeShareCode(""))
        assertNull(MixCodec.decodeShareCode("AAAAAAAA"))
        assertNull(MixCodec.mixFromShareUri("akachannoise://mix"))
        assertNull(MixCodec.mixFromShareUri("https://example.com"))
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = """{"id":"user.1","name":"X","futureField":42,"layers":[]}"""
        val mix = MixCodec.decodeMixJson(json)
        assertNotNull(mix)
        assertEquals("user.1", mix!!.id)
        assertEquals(emptyList<LayerSpec>(), mix.layers)
    }

    @Test
    fun `backup round trips including custom sounds`() {
        val data = UserData(
            mixes = listOf(womb.copy(id = "user.1")),
            favoriteIds = listOf("user.1", "preset.deep_brown"),
            lastUsedMixId = "user.1",
            customSounds = listOf(
                CustomSoundRef(
                    id = "snd.1",
                    name = "Mum humming",
                    fileName = "sounds/snd.1.pcm",
                    durationMs = 42_000,
                    sampleRate = 48000,
                    channels = 1,
                    wombFilter = 0.6f,
                ),
            ),
        )
        val decoded = MixCodec.decodeBackup(MixCodec.encodeBackup(data))
        assertEquals(data, decoded)
    }

    @Test
    fun `corrupt backup is rejected`() {
        assertNull(MixCodec.decodeBackup("nope"))
        assertNull(MixCodec.decodeBackup(""))
    }

    @Test
    fun `decoded mixes keep every layer parameter`() {
        val decoded = MixCodec.decodeShareCode(MixCodec.encodeShareCode(womb))!!
        val heartbeat = decoded.layers.first { it.soundId == "heartbeat" }
        assertEquals(72f, heartbeat.params["bpm"]!!, 0f)
        assertEquals(0.7f, heartbeat.params["muffle"]!!, 0f)
        assertFalse(decoded.layers.any { it.soundId.isEmpty() })
    }
}
