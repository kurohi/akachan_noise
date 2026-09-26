package io.github.kurohi.akachannoise.engine.model

import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.generators.PulseClock
import io.github.kurohi.akachannoise.engine.generators.SoundGenerator

/** Categories used by the sound picker UI. */
enum class SoundCategory { WOMB, NOISE, NATURE, HOME, VOICE, CUSTOM }

/** All built-in sounds. Stable ids — persisted mixes reference them. */
enum class SoundId(val id: String) {
    HEARTBEAT("heartbeat"),
    BLOOD_FLOW("blood_flow"),
    WOMB_RUMBLE("womb_rumble"),
    NOISE_WHITE("noise_white"),
    NOISE_PINK("noise_pink"),
    NOISE_BROWN("noise_brown"),
    OCEAN("ocean"),
    RAIN("rain"),
    STREAM("stream"),
    WIND("wind"),
    FAN("fan"),
    HAIR_DRYER("hair_dryer"),
    VACUUM("vacuum"),
    CAR_RIDE("car_ride"),
    PLASTIC_BAG("plastic_bag"),
    SHUSH("shush"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromId(id: String): SoundId? = entries.firstOrNull { it.id == id }
    }
}

/** Range and default of a single tunable sound parameter. */
data class ParamSpec(
    val id: String,
    val min: Float,
    val max: Float,
    val default: Float,
)

/** Handed to generator factories so they can share the RNG stream and clock. */
class EngineContext(
    val sampleRate: Int,
    val rng: Rng,
    val clock: PulseClock,
)

/**
 * Describes one built-in sound: identity, category, tunable parameters and a
 * factory that creates the real-time generator.
 */
class SoundSpec(
    val id: SoundId,
    val category: SoundCategory,
    val params: List<ParamSpec>,
    val factory: (EngineContext) -> SoundGenerator,
)
