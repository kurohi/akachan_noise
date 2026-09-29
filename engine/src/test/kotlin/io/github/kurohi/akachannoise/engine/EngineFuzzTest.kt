package io.github.kurohi.akachannoise.engine

import io.github.kurohi.akachannoise.engine.dsp.lerp
import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.engine.model.SoundCatalog
import io.github.kurohi.akachannoise.engine.model.SoundId
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Randomized stress test: 60 s of audio with random layers, volumes,
 * parameters and mid-stream mix switches. Output must stay finite and
 * bounded by the soft clipper.
 */
class EngineFuzzTest {
    private val sampleRate = 48000
    private val block = 512

    @Test
    fun `random operation stream stays finite and bounded`() {
        val random = Random(20260927)
        val engine = NoiseEngine(sampleRate, seed = 99)
        val buffer = FloatArray(block * 2)
        val soundIds = SoundCatalog.specs.map { it.id.id }

        fun randomMix(): MixSpec {
            val count = random.nextInt(1, MixSpec.MAX_LAYERS + 1)
            val chosen = soundIds.shuffled(random).take(count)
            return MixSpec(
                id = "fuzz",
                name = "fuzz",
                layers = chosen.map { id ->
                    val spec = SoundCatalog.specFor(id)!!
                    LayerSpec(
                        soundId = id,
                        volume = random.nextFloat(),
                        params = spec.params.associate { p ->
                            p.id to lerp(p.min, p.max, random.nextFloat())
                        },
                    )
                },
                masterVolume = random.nextFloat(),
                warmth = random.nextFloat(),
                mono = random.nextBoolean(),
            )
        }

        engine.start(randomMix(), fadeInMs = 0)
        val totalBlocks = 60 * sampleRate / block
        for (blockIndex in 0 until totalBlocks) {
            when (random.nextInt(50)) {
                0 -> engine.switchTo(randomMix(), crossfadeMs = random.nextInt(0, 3000))

                1 -> engine.setMasterVolume(random.nextFloat())

                2 -> engine.fadeMaster(random.nextFloat(), random.nextInt(0, 2000))

                3 -> soundIds.random(random).let {
                    engine.setLayerVolume(it, random.nextFloat())
                }

                4 -> soundIds.random(random).let { id ->
                    val spec = SoundCatalog.specFor(id) ?: return@let
                    val param = spec.params.randomOrNull(random) ?: return@let
                    engine.setLayerParam(id, param.id, lerp(param.min, param.max, random.nextFloat()))
                }

                5 -> engine.setWarmth(random.nextFloat())

                6 -> engine.setMono(random.nextBoolean())
            }
            engine.render(buffer, block)
            for (v in buffer) {
                assertTrue("NaN at block $blockIndex", !v.isNaN())
                assertTrue("Inf at block $blockIndex", !v.isInfinite())
                assertTrue("out of range $v at block $blockIndex", abs(v) <= 1.0f)
            }
        }
    }

    @Test
    fun `unknown sound ids are skipped gracefully`() {
        val engine = NoiseEngine(sampleRate)
        val mix = MixSpec(
            id = "x",
            name = "x",
            layers = listOf(
                LayerSpec(soundId = "does_not_exist", volume = 1f),
                LayerSpec(soundId = SoundId.NOISE_WHITE.id, volume = 1f),
            ),
        )
        engine.start(mix, fadeInMs = 0)
        val buffer = FloatArray(block * 2)
        repeat(10) { engine.render(buffer, block) }
        assertTrue(engine.isAudible)
    }
}
