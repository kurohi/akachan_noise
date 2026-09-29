package io.github.kurohi.akachannoise.engine.model

import io.github.kurohi.akachannoise.engine.generators.BloodFlowGenerator
import io.github.kurohi.akachannoise.engine.generators.HeartbeatGenerator
import io.github.kurohi.akachannoise.engine.generators.NoiseGenerator
import io.github.kurohi.akachannoise.engine.generators.OceanGenerator
import io.github.kurohi.akachannoise.engine.generators.ShushGenerator

/**
 * The built-in presets, in display order. Stable ids — saved favorites and
 * the widget reference them. Names are English defaults; localized display
 * names come from resources in :app.
 */
object BuiltInPresets {

    val presets: List<MixSpec> = listOf(
        womb(),
        wombAndOcean(),
        tvStatic(),
        pinkRain(),
        carRide(),
        shush(),
        fanAndBrown(),
        hairDryer(),
        deepBrown(),
    )

    fun byId(id: String): MixSpec? = presets.firstOrNull { it.id == id }

    private fun womb() = MixSpec(
        id = "preset.womb",
        name = "Womb",
        layers = listOf(
            LayerSpec(
                soundId = SoundId.HEARTBEAT.id,
                volume = 0.9f,
                params = mapOf(
                    HeartbeatGenerator.PARAM_BPM to 72f,
                    HeartbeatGenerator.PARAM_MUFFLE to 0.7f,
                    HeartbeatGenerator.PARAM_VARIABILITY to 0.2f,
                ),
            ),
            LayerSpec(
                soundId = SoundId.BLOOD_FLOW.id,
                volume = 0.8f,
                params = mapOf(
                    BloodFlowGenerator.PARAM_DEPTH to 0.7f,
                    BloodFlowGenerator.PARAM_MUFFLE to 0.85f,
                ),
            ),
            LayerSpec(soundId = SoundId.WOMB_RUMBLE.id, volume = 0.7f),
        ),
    )

    private fun wombAndOcean() = MixSpec(
        id = "preset.womb_ocean",
        name = "Womb & Ocean",
        layers = listOf(
            LayerSpec(
                soundId = SoundId.HEARTBEAT.id,
                volume = 0.7f,
                params = mapOf(
                    HeartbeatGenerator.PARAM_BPM to 68f,
                    HeartbeatGenerator.PARAM_MUFFLE to 0.8f,
                ),
            ),
            LayerSpec(
                soundId = SoundId.OCEAN.id,
                volume = 0.85f,
                params = mapOf(
                    OceanGenerator.PARAM_PERIOD to 11f,
                    OceanGenerator.PARAM_SIZE to 0.7f,
                    OceanGenerator.PARAM_DISTANCE to 0.5f,
                ),
            ),
        ),
    )

    private fun tvStatic() = MixSpec(
        id = "preset.tv_static",
        name = "TV Static",
        layers = listOf(
            LayerSpec(
                soundId = SoundId.NOISE_WHITE.id,
                volume = 0.75f,
                params = mapOf(NoiseGenerator.PARAM_COLOR to NoiseGenerator.COLOR_WHITE),
            ),
        ),
    )

    private fun pinkRain() = MixSpec(
        id = "preset.pink_rain",
        name = "Pink Rain",
        layers = listOf(
            LayerSpec(
                soundId = SoundId.NOISE_PINK.id,
                volume = 0.7f,
                params = mapOf(NoiseGenerator.PARAM_COLOR to NoiseGenerator.COLOR_PINK),
            ),
            LayerSpec(
                soundId = SoundId.RAIN.id,
                volume = 0.8f,
                params = mapOf("intensity" to 0.55f, "density" to 0.5f),
            ),
        ),
    )

    private fun carRide() = MixSpec(
        id = "preset.car_ride",
        name = "Car Ride",
        layers = listOf(
            LayerSpec(
                soundId = SoundId.CAR_RIDE.id,
                volume = 0.85f,
                params = mapOf("speed" to 0.55f, "texture" to 0.55f),
            ),
        ),
    )

    private fun shush() = MixSpec(
        id = "preset.shush",
        name = "Shush",
        layers = listOf(
            LayerSpec(
                soundId = SoundId.SHUSH.id,
                volume = 0.9f,
                params = mapOf(
                    ShushGenerator.PARAM_RHYTHM to 1f,
                    ShushGenerator.PARAM_TONE to 0.5f,
                ),
            ),
        ),
    )

    private fun fanAndBrown() = MixSpec(
        id = "preset.fan_brown",
        name = "Fan & Brown",
        layers = listOf(
            LayerSpec(
                soundId = SoundId.FAN.id,
                volume = 0.8f,
                params = mapOf("speed" to 0.45f),
            ),
            LayerSpec(
                soundId = SoundId.NOISE_BROWN.id,
                volume = 0.6f,
                params = mapOf(NoiseGenerator.PARAM_COLOR to NoiseGenerator.COLOR_BROWN),
            ),
        ),
    )

    private fun hairDryer() = MixSpec(
        id = "preset.hair_dryer",
        name = "Hair Dryer",
        layers = listOf(
            LayerSpec(soundId = SoundId.HAIR_DRYER.id, volume = 0.8f),
        ),
    )

    private fun deepBrown() = MixSpec(
        id = "preset.deep_brown",
        name = "Deep Brown",
        layers = listOf(
            LayerSpec(
                soundId = SoundId.NOISE_BROWN.id,
                volume = 0.8f,
                params = mapOf(
                    NoiseGenerator.PARAM_COLOR to NoiseGenerator.COLOR_BROWN,
                    NoiseGenerator.PARAM_WIDTH to 0.7f,
                ),
            ),
        ),
    )
}
