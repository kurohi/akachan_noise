package io.github.kurohi.akachannoise.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.engine.generators.BloodFlowGenerator
import io.github.kurohi.akachannoise.engine.generators.CarRideGenerator
import io.github.kurohi.akachannoise.engine.generators.FanGenerator
import io.github.kurohi.akachannoise.engine.generators.HeartbeatGenerator
import io.github.kurohi.akachannoise.engine.generators.NoiseGenerator
import io.github.kurohi.akachannoise.engine.generators.OceanGenerator
import io.github.kurohi.akachannoise.engine.generators.PlasticBagGenerator
import io.github.kurohi.akachannoise.engine.generators.RainGenerator
import io.github.kurohi.akachannoise.engine.generators.ShushGenerator
import io.github.kurohi.akachannoise.engine.generators.StreamGenerator
import io.github.kurohi.akachannoise.engine.generators.WindGenerator
import io.github.kurohi.akachannoise.engine.model.ParamSpec
import io.github.kurohi.akachannoise.engine.model.SoundCategory
import io.github.kurohi.akachannoise.engine.model.SoundId
import kotlin.math.roundToInt

/** Localized name of a sound. Unknown ids fall back to the raw id. */
@Composable
fun soundLabel(soundId: String): String = when (SoundId.fromId(soundId)) {
    SoundId.HEARTBEAT -> stringResource(R.string.sound_heartbeat)
    SoundId.BLOOD_FLOW -> stringResource(R.string.sound_blood_flow)
    SoundId.WOMB_RUMBLE -> stringResource(R.string.sound_womb_rumble)
    SoundId.NOISE_WHITE -> stringResource(R.string.sound_noise_white)
    SoundId.NOISE_PINK -> stringResource(R.string.sound_noise_pink)
    SoundId.NOISE_BROWN -> stringResource(R.string.sound_noise_brown)
    SoundId.OCEAN -> stringResource(R.string.sound_ocean)
    SoundId.RAIN -> stringResource(R.string.sound_rain)
    SoundId.STREAM -> stringResource(R.string.sound_stream)
    SoundId.WIND -> stringResource(R.string.sound_wind)
    SoundId.FAN -> stringResource(R.string.sound_fan)
    SoundId.HAIR_DRYER -> stringResource(R.string.sound_hair_dryer)
    SoundId.VACUUM -> stringResource(R.string.sound_vacuum)
    SoundId.CAR_RIDE -> stringResource(R.string.sound_car_ride)
    SoundId.PLASTIC_BAG -> stringResource(R.string.sound_plastic_bag)
    SoundId.SHUSH -> stringResource(R.string.sound_shush)
    SoundId.CUSTOM, null -> stringResource(R.string.sound_custom)
}

/** Localized category name. */
@Composable
fun categoryLabel(category: SoundCategory): String = when (category) {
    SoundCategory.WOMB -> stringResource(R.string.category_womb)
    SoundCategory.NOISE -> stringResource(R.string.category_noise)
    SoundCategory.NATURE -> stringResource(R.string.category_nature)
    SoundCategory.HOME -> stringResource(R.string.category_home)
    SoundCategory.VOICE -> stringResource(R.string.category_voice)
    SoundCategory.CUSTOM -> stringResource(R.string.category_custom)
}

/**
 * Display name for a mix: presets are localized by id, the user's own mixes
 * keep the name they typed.
 */
@Composable
fun mixDisplayName(id: String, fallback: String): String = presetNameRes(id)?.let { stringResource(it) } ?: fallback

/** Non-composable variant for widgets, tiles and shortcuts. */
fun mixDisplayNameFor(context: android.content.Context, id: String, fallback: String): String = presetNameRes(id)?.let { context.getString(it) } ?: fallback

@androidx.annotation.StringRes
private fun presetNameRes(id: String): Int? = when (id) {
    "preset.womb" -> R.string.preset_womb
    "preset.womb_ocean" -> R.string.preset_womb_ocean
    "preset.tv_static" -> R.string.preset_tv_static
    "preset.pink_rain" -> R.string.preset_pink_rain
    "preset.car_ride" -> R.string.preset_car_ride
    "preset.shush" -> R.string.preset_shush
    "preset.fan_brown" -> R.string.preset_fan_brown
    "preset.hair_dryer" -> R.string.preset_hair_dryer
    "preset.deep_brown" -> R.string.preset_deep_brown
    else -> null
}

/** Localized parameter name. */
@Composable
fun paramLabel(paramId: String): String = when (paramId) {
    HeartbeatGenerator.PARAM_BPM -> stringResource(R.string.param_bpm)

    HeartbeatGenerator.PARAM_MUFFLE, BloodFlowGenerator.PARAM_MUFFLE ->
        stringResource(R.string.param_muffle)

    HeartbeatGenerator.PARAM_VARIABILITY -> stringResource(R.string.param_variability)

    BloodFlowGenerator.PARAM_DEPTH -> stringResource(R.string.param_depth)

    OceanGenerator.PARAM_PERIOD -> stringResource(R.string.param_period)

    OceanGenerator.PARAM_SIZE -> stringResource(R.string.param_size)

    OceanGenerator.PARAM_DISTANCE -> stringResource(R.string.param_distance)

    RainGenerator.PARAM_INTENSITY, StreamGenerator.PARAM_INTENSITY ->
        stringResource(R.string.param_intensity)

    RainGenerator.PARAM_DENSITY -> stringResource(R.string.param_density)

    WindGenerator.PARAM_GUSTINESS -> stringResource(R.string.param_gustiness)

    FanGenerator.PARAM_SPEED, CarRideGenerator.PARAM_SPEED -> stringResource(R.string.param_speed)

    CarRideGenerator.PARAM_TEXTURE -> stringResource(R.string.param_texture)

    PlasticBagGenerator.PARAM_RATE -> stringResource(R.string.param_rate)

    ShushGenerator.PARAM_RHYTHM -> stringResource(R.string.param_rhythm)

    ShushGenerator.PARAM_TONE -> stringResource(R.string.param_tone)

    NoiseGenerator.PARAM_COLOR -> stringResource(R.string.param_color)

    NoiseGenerator.PARAM_WIDTH -> stringResource(R.string.param_width)

    else -> paramId
}

/** Localized, human-readable parameter value (e.g. "72 bpm", "60%", "Slow"). */
@Composable
fun paramValueLabel(param: ParamSpec, value: Float): String = when (param.id) {
    HeartbeatGenerator.PARAM_BPM -> stringResource(R.string.param_value_bpm, value.roundToInt())

    OceanGenerator.PARAM_PERIOD -> stringResource(R.string.param_value_seconds, value.roundToInt())

    ShushGenerator.PARAM_RHYTHM -> when {
        value < 0.5f -> stringResource(R.string.param_rhythm_continuous)
        value < 1.5f -> stringResource(R.string.param_rhythm_slow)
        else -> stringResource(R.string.param_rhythm_fast)
    }

    NoiseGenerator.PARAM_COLOR -> when {
        value < 0.25f -> stringResource(R.string.param_color_white)
        value < 0.75f -> stringResource(R.string.param_color_pink)
        else -> stringResource(R.string.param_color_brown)
    }

    else -> {
        val fraction = if (param.max > param.min) {
            ((value - param.min) / (param.max - param.min)).coerceIn(0f, 1f)
        } else {
            0f
        }
        stringResource(R.string.param_value_percent, (fraction * 100).roundToInt())
    }
}
