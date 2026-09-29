package io.github.kurohi.akachannoise.engine.model

import io.github.kurohi.akachannoise.engine.generators.BloodFlowGenerator
import io.github.kurohi.akachannoise.engine.generators.CarRideGenerator
import io.github.kurohi.akachannoise.engine.generators.FanGenerator
import io.github.kurohi.akachannoise.engine.generators.HairDryerGenerator
import io.github.kurohi.akachannoise.engine.generators.HeartbeatGenerator
import io.github.kurohi.akachannoise.engine.generators.NoiseGenerator
import io.github.kurohi.akachannoise.engine.generators.OceanGenerator
import io.github.kurohi.akachannoise.engine.generators.PlasticBagGenerator
import io.github.kurohi.akachannoise.engine.generators.PulseClock
import io.github.kurohi.akachannoise.engine.generators.RainGenerator
import io.github.kurohi.akachannoise.engine.generators.ScaledGenerator
import io.github.kurohi.akachannoise.engine.generators.ShushGenerator
import io.github.kurohi.akachannoise.engine.generators.SoundGenerator
import io.github.kurohi.akachannoise.engine.generators.StreamGenerator
import io.github.kurohi.akachannoise.engine.generators.VacuumGenerator
import io.github.kurohi.akachannoise.engine.generators.WindGenerator
import io.github.kurohi.akachannoise.engine.generators.WombRumbleGenerator
import kotlin.math.sqrt

/**
 * Registry of all built-in sounds. Generators for ids not listed here
 * resolve to null and are skipped by the mixer (e.g. a deleted custom
 * sound referenced by an old mix).
 */
object SoundCatalog {

    /** Target RMS every sound is calibrated to at 100% volume (-20 dBFS). */
    const val TARGET_RMS = 0.1f

    val specs: List<SoundSpec> = listOf(
        noiseSpec(SoundId.NOISE_WHITE, NoiseGenerator.COLOR_WHITE),
        noiseSpec(SoundId.NOISE_PINK, NoiseGenerator.COLOR_PINK),
        noiseSpec(SoundId.NOISE_BROWN, NoiseGenerator.COLOR_BROWN),
        heartbeatSpec(),
        bloodFlowSpec(),
        wombRumbleSpec(),
        oceanSpec(),
        rainSpec(),
        streamSpec(),
        windSpec(),
        fanSpec(),
        hairDryerSpec(),
        vacuumSpec(),
        carRideSpec(),
        plasticBagSpec(),
        shushSpec(),
    )

    fun specFor(soundId: String): SoundSpec? = specs.firstOrNull { it.id.id == soundId }

    /**
     * Creates a generator for [soundId] with [params] applied and wrapped in
     * a loudness-calibration scale so every sound sits at [TARGET_RMS] at
     * full volume. Noise generators calibrate themselves internally.
     */
    fun createGenerator(
        soundId: String,
        context: EngineContext,
        params: Map<String, Float>,
    ): SoundGenerator? {
        val spec = specFor(soundId) ?: return null
        val generator = spec.factory(context)
        spec.params.forEach { generator.setParam(it.id, params[it.id] ?: it.default) }
        val seconds = spec.calibrationSeconds
        if (seconds <= 0.0) return generator
        // Measure a scratch instance on its own beat clock, so measuring does
        // not advance the shared clock the live generator is locked to.
        val scratchClock = PulseClock(context.sampleRate, context.rng)
        val scratch = spec.factory(EngineContext(context.sampleRate, context.rng, scratchClock))
        spec.params.forEach { scratch.setParam(it.id, params[it.id] ?: it.default) }
        val rms = measureRms(scratch, scratchClock, context.sampleRate, seconds)
        val scale = if (rms < 1e-9f) 1f else (TARGET_RMS / rms).coerceAtMost(MAX_SCALE)
        return ScaledGenerator(generator, scale)
    }

    private fun measureRms(
        generator: SoundGenerator,
        clock: PulseClock,
        sampleRate: Int,
        seconds: Double,
    ): Float {
        val totalFrames = (seconds * sampleRate).toInt()
        val buffer = FloatArray(CALIBRATION_BLOCK * 2)
        var rendered = 0
        var sumSq = 0.0
        var count = 0
        while (rendered < totalFrames) {
            val frames = minOf(CALIBRATION_BLOCK, totalFrames - rendered)
            clock.advanceBlock(frames)
            generator.render(buffer, frames)
            val n = frames * 2
            for (i in 0 until n) {
                val v = buffer[i].toDouble()
                sumSq += v * v
            }
            count += n
            rendered += frames
        }
        return sqrt(sumSq / count).toFloat()
    }

    private fun noiseSpec(id: SoundId, defaultColor: Float) = SoundSpec(
        id = id,
        category = SoundCategory.NOISE,
        params = listOf(
            ParamSpec(NoiseGenerator.PARAM_COLOR, 0f, 1f, defaultColor),
            ParamSpec(NoiseGenerator.PARAM_WIDTH, 0f, 1f, NoiseGenerator.DEFAULT_WIDTH),
        ),
        factory = { ctx -> NoiseGenerator(ctx.sampleRate, ctx.rng, defaultColor) },
        // NoiseGenerator calibrates itself; no extra measurement needed.
        calibrationSeconds = 0.0,
    )

    private fun heartbeatSpec() = SoundSpec(
        id = SoundId.HEARTBEAT,
        category = SoundCategory.WOMB,
        params = listOf(
            ParamSpec(HeartbeatGenerator.PARAM_BPM, 50f, 120f, HeartbeatGenerator.DEFAULT_BPM),
            ParamSpec(HeartbeatGenerator.PARAM_MUFFLE, 0f, 1f, 0.6f),
            ParamSpec(HeartbeatGenerator.PARAM_VARIABILITY, 0f, 1f, 0.2f),
        ),
        factory = { ctx -> HeartbeatGenerator(ctx.sampleRate, ctx.clock, ctx.rng) },
        calibrationSeconds = 5.0,
    )

    private fun bloodFlowSpec() = SoundSpec(
        id = SoundId.BLOOD_FLOW,
        category = SoundCategory.WOMB,
        params = listOf(
            ParamSpec(BloodFlowGenerator.PARAM_DEPTH, 0f, 1f, 0.7f),
            ParamSpec(BloodFlowGenerator.PARAM_MUFFLE, 0f, 1f, 0.85f),
        ),
        factory = { ctx -> BloodFlowGenerator(ctx.sampleRate, ctx.clock, ctx.rng) },
        calibrationSeconds = 5.0,
    )

    private fun wombRumbleSpec() = SoundSpec(
        id = SoundId.WOMB_RUMBLE,
        category = SoundCategory.WOMB,
        params = emptyList(),
        factory = { ctx -> WombRumbleGenerator(ctx.sampleRate, ctx.rng) },
        // The undulation is ~7.7 s long; measure a full cycle.
        calibrationSeconds = 10.0,
    )

    private fun oceanSpec() = SoundSpec(
        id = SoundId.OCEAN,
        category = SoundCategory.NATURE,
        params = listOf(
            ParamSpec(OceanGenerator.PARAM_PERIOD, 5f, 15f, 8f),
            ParamSpec(OceanGenerator.PARAM_SIZE, 0f, 1f, 0.6f),
            ParamSpec(OceanGenerator.PARAM_DISTANCE, 0f, 1f, 0.3f),
        ),
        factory = { ctx -> OceanGenerator(ctx.sampleRate, ctx.rng) },
        calibrationSeconds = 25.0,
    )

    private fun rainSpec() = SoundSpec(
        id = SoundId.RAIN,
        category = SoundCategory.NATURE,
        params = listOf(
            ParamSpec(RainGenerator.PARAM_INTENSITY, 0f, 1f, 0.5f),
            ParamSpec(RainGenerator.PARAM_DENSITY, 0f, 1f, 0.5f),
        ),
        factory = { ctx -> RainGenerator(ctx.sampleRate, ctx.rng) },
        calibrationSeconds = 3.0,
    )

    private fun streamSpec() = SoundSpec(
        id = SoundId.STREAM,
        category = SoundCategory.NATURE,
        params = listOf(
            ParamSpec(StreamGenerator.PARAM_INTENSITY, 0f, 1f, 0.6f),
        ),
        factory = { ctx -> StreamGenerator(ctx.sampleRate, ctx.rng) },
        calibrationSeconds = 3.0,
    )

    private fun windSpec() = SoundSpec(
        id = SoundId.WIND,
        category = SoundCategory.NATURE,
        params = listOf(
            ParamSpec(WindGenerator.PARAM_GUSTINESS, 0f, 1f, 0.4f),
        ),
        factory = { ctx -> WindGenerator(ctx.sampleRate, ctx.rng) },
        // Gusts run on a slow random walk; average several of them.
        calibrationSeconds = 30.0,
    )

    private fun fanSpec() = SoundSpec(
        id = SoundId.FAN,
        category = SoundCategory.HOME,
        params = listOf(
            ParamSpec(FanGenerator.PARAM_SPEED, 0f, 1f, 0.5f),
        ),
        factory = { ctx -> FanGenerator(ctx.sampleRate, ctx.rng) },
        calibrationSeconds = 2.0,
    )

    private fun hairDryerSpec() = SoundSpec(
        id = SoundId.HAIR_DRYER,
        category = SoundCategory.HOME,
        params = emptyList(),
        factory = { ctx -> HairDryerGenerator(ctx.sampleRate, ctx.rng) },
        calibrationSeconds = 2.0,
    )

    private fun vacuumSpec() = SoundSpec(
        id = SoundId.VACUUM,
        category = SoundCategory.HOME,
        params = emptyList(),
        factory = { ctx -> VacuumGenerator(ctx.sampleRate, ctx.rng) },
        calibrationSeconds = 2.0,
    )

    private fun carRideSpec() = SoundSpec(
        id = SoundId.CAR_RIDE,
        category = SoundCategory.HOME,
        params = listOf(
            ParamSpec(CarRideGenerator.PARAM_SPEED, 0f, 1f, 0.5f),
            ParamSpec(CarRideGenerator.PARAM_TEXTURE, 0f, 1f, 0.5f),
        ),
        factory = { ctx -> CarRideGenerator(ctx.sampleRate, ctx.rng) },
        calibrationSeconds = 4.0,
    )

    private fun plasticBagSpec() = SoundSpec(
        id = SoundId.PLASTIC_BAG,
        category = SoundCategory.HOME,
        params = listOf(
            ParamSpec(PlasticBagGenerator.PARAM_RATE, 0f, 1f, 0.5f),
        ),
        factory = { ctx -> PlasticBagGenerator(ctx.sampleRate, ctx.rng) },
        calibrationSeconds = 5.0,
    )

    private fun shushSpec() = SoundSpec(
        id = SoundId.SHUSH,
        category = SoundCategory.VOICE,
        params = listOf(
            ParamSpec(ShushGenerator.PARAM_RHYTHM, 0f, 2f, 1f),
            ParamSpec(ShushGenerator.PARAM_TONE, 0f, 1f, 0.5f),
        ),
        factory = { ctx -> ShushGenerator(ctx.sampleRate, ctx.rng) },
        calibrationSeconds = 5.0,
    )

    private const val MAX_SCALE = 64f
    private const val CALIBRATION_BLOCK = 1024
}
