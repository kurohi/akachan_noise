package io.github.kurohi.akachannoise.engine.generators

/**
 * Read-only random access to 16-bit PCM audio: the bridge between a
 * user-imported or recorded file and the real-time [SampleLoop].
 *
 * Implementations may be backed by a memory-mapped file, so a five minute
 * recording costs no heap. [sample] is called from the render thread and
 * must not allocate.
 */
interface PcmSource {
    val frames: Int
    val channels: Int
    val sampleRate: Int

    /** Sample in [-1, 1] for [frame] (0-based) on [channel]. */
    fun sample(channel: Int, frame: Int): Float
}
