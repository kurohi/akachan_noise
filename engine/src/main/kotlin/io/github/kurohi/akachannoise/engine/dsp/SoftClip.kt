package io.github.kurohi.akachannoise.engine.dsp

import kotlin.math.abs
import kotlin.math.sign

/**
 * Master safety soft clipper. Unity gain below the knee, then a smooth
 * saturating curve that approaches (but never exceeds) 1.0. The slope is
 * continuous at the knee so the clipping itself never clicks.
 */
object SoftClip {
    private const val KNEE = 0.85f

    fun process(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        val t = (a - KNEE) / (1f - KNEE)
        val saturated = KNEE + (1f - KNEE) * (t / (1f + t))
        return sign(x) * saturated
    }
}
