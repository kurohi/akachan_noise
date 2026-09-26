package io.github.kurohi.akachannoise.engine.generators

/**
 * A real-time sound generator. All methods are called from the render thread
 * only (parameters are routed through the engine command queue), so
 * implementations never need to synchronize or allocate in [render].
 */
interface SoundGenerator {
    /**
     * Renders [frames] interleaved stereo frames (L,R,L,R,...) into [out].
     * [out].size must be at least frames * 2. Implementations must not
     * allocate and must produce bounded samples (no NaN/Inf).
     */
    fun render(out: FloatArray, frames: Int)

    /** Sets a parameter (see [io.github.kurohi.akachannoise.engine.model.ParamSpec]). */
    fun setParam(id: String, value: Float)

    /** Restarts the sound from its beginning (phase reset). */
    fun reset()
}
