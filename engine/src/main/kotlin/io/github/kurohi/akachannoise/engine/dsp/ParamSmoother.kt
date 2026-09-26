package io.github.kurohi.akachannoise.engine.dsp

import kotlin.math.exp

/**
 * One-pole parameter smoother. Removes zipper noise when the user drags a
 * volume or sound slider. Call [next] once per sample in the render loop.
 */
class ParamSmoother(timeConstantMs: Double, sampleRate: Int) {
    private var target = 0f
    private var value = 0f
    private val coef = (1.0 - exp(-1000.0 / (timeConstantMs * sampleRate))).toFloat()

    fun setTargetImmediately(t: Float) {
        target = t
        value = t
    }

    fun setTarget(t: Float) {
        target = t
    }

    fun current(): Float = value

    fun next(): Float {
        value += (target - value) * coef
        return value
    }
}

/**
 * Linear amplitude ramp that reaches its target exactly (unlike a one-pole
 * smoother). Used for fades, crossfades and mix switches so that "fade out
 * over 30 s" really ends in silence after 30 s.
 */
class GainRamp {
    private var start = 0f
    private var end = 0f
    private var remaining = 0
    private var total = 0

    val isActive: Boolean get() = remaining > 0

    fun begin(from: Float, to: Float, durationSamples: Int) {
        start = from
        end = to
        remaining = durationSamples
        total = durationSamples
    }

    fun begin(from: Float, to: Float, durationMs: Int, sampleRate: Int) = begin(from, to, (durationMs * sampleRate) / 1000)

    /**
     * Advances one sample and returns the gain. When the ramp finishes the
     * value is exactly [end] and [isActive] becomes false.
     */
    fun next(): Float {
        if (remaining <= 0) return end
        remaining--
        if (remaining == 0) return end
        val progress = (total - remaining).toFloat() / total
        return start + (end - start) * progress
    }

    fun currentValue(): Float = if (remaining > 0) {
        val progress = (total - remaining).toFloat() / total
        start + (end - start) * progress
    } else {
        end
    }
}
