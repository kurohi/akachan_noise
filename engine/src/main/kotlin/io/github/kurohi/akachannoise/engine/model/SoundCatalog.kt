package io.github.kurohi.akachannoise.engine.model

import io.github.kurohi.akachannoise.engine.generators.NoiseGenerator

/**
 * Registry of all built-in sounds. Generators for ids not listed here
 * resolve to null and are skipped by the mixer (e.g. a deleted custom
 * sound referenced by an old mix).
 */
object SoundCatalog {
    val specs: List<SoundSpec> = listOf(
        noiseSpec(SoundId.NOISE_WHITE, NoiseGenerator.COLOR_WHITE),
        noiseSpec(SoundId.NOISE_PINK, NoiseGenerator.COLOR_PINK),
        noiseSpec(SoundId.NOISE_BROWN, NoiseGenerator.COLOR_BROWN),
    )

    fun specFor(soundId: String): SoundSpec? = specs.firstOrNull { it.id.id == soundId }

    fun createGenerator(soundId: String, context: EngineContext, params: Map<String, Float>): io.github.kurohi.akachannoise.engine.generators.SoundGenerator? {
        val spec = specFor(soundId) ?: return null
        return spec.factory(context).also { gen ->
            spec.params.forEach { gen.setParam(it.id, params[it.id] ?: it.default) }
        }
    }

    private fun noiseSpec(id: SoundId, defaultColor: Float) = SoundSpec(
        id = id,
        category = SoundCategory.NOISE,
        params = listOf(
            ParamSpec(NoiseGenerator.PARAM_COLOR, 0f, 1f, defaultColor),
            ParamSpec(NoiseGenerator.PARAM_WIDTH, 0f, 1f, NoiseGenerator.DEFAULT_WIDTH),
        ),
        factory = { ctx -> NoiseGenerator(ctx.sampleRate, ctx.rng, defaultColor) },
    )
}
