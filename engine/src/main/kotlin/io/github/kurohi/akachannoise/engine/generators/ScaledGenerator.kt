package io.github.kurohi.akachannoise.engine.generators

/**
 * Applies a constant loudness-calibration scale to another generator so
 * every sound sits at the same target RMS at full volume.
 */
class ScaledGenerator(
    private val inner: SoundGenerator,
    private val scale: Float,
) : SoundGenerator {

    override fun render(out: FloatArray, frames: Int) {
        inner.render(out, frames)
        val n = frames * 2
        for (i in 0 until n) out[i] *= scale
    }

    override fun setParam(id: String, value: Float) = inner.setParam(id, value)

    override fun reset() = inner.reset()
}
