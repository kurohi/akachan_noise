package io.github.kurohi.akachannoise.engine.model

import kotlinx.serialization.Serializable

/**
 * A single sound layer inside a mix. [volume] is perceptual (0..1) and mapped
 * to gain by the mixer. [params] holds per-sound parameters keyed by
 * [ParamSpec.id]; unknown keys are ignored, missing keys use defaults.
 */
@Serializable
data class LayerSpec(
    val soundId: String,
    val volume: Float = DEFAULT_VOLUME,
    val params: Map<String, Float> = emptyMap(),
) {
    companion object {
        const val DEFAULT_VOLUME = 0.8f
    }
}

/**
 * A saved / playable mix. Pure data — no behavior — so it can be persisted,
 * exported and versioned (see [UserData] schema in :data).
 */
@Serializable
data class MixSpec(
    val id: String,
    val name: String,
    val layers: List<LayerSpec> = emptyList(),
    val masterVolume: Float = 1f,
    val warmth: Float = 0f,
    val mono: Boolean = false,
) {
    companion object {
        const val MAX_LAYERS = 8
    }
}
