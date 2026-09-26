package io.github.kurohi.akachannoise.engine.dsp

/**
 * Fast, seedable pseudo-random number generator (xoroshiro128++).
 * Deterministic for a given seed so renders and tests are reproducible.
 * NOT for cryptography — only for audio noise.
 */
class Rng(seed: Long) {
    private var s0 = seed
    private var s1 = seed xor 0x9E3779B97F4A7C15uL.toLong()

    fun nextLong(): Long {
        var l0 = s0
        var l1 = s1
        val result = java.lang.Long.rotateLeft(l0 + l1, 17) + l0
        l1 = l1 xor l0
        s0 = (java.lang.Long.rotateLeft(l0, 49) xor l1 xor (l1 shl 21))
        s1 = java.lang.Long.rotateLeft(l1, 28)
        return result
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40) * (1.0f / (1 shl 24))

    /** Uniform in [-1, 1). */
    fun nextBipolar(): Float = nextFloat() * 2.0f - 1.0f

    /** Uniform in [min, max). */
    fun nextRange(min: Float, max: Float): Float = min + (max - min) * nextFloat()
}
