package io.github.kurohi.akachannoise.engine.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/** Linear interpolation. */
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Hermite smoothstep between [edge0] and [edge1], clamped to [0, 1]. */
fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Sinusoidal LFO. Output in [-1, 1]; call [next] once per sample.
 * Cheap enough for the render loop (one sin per sample).
 */
class SineLfo(private val sampleRate: Int) {
    private var phase = 0.0

    fun next(freqHz: Float): Float {
        phase += freqHz / sampleRate
        phase -= phase.toLong()
        return sin(phase * 2.0 * PI).toFloat()
    }

    fun reset() {
        phase = 0.0
    }
}

/**
 * Smooth random-walk LFO for gusts and wobble. The target re-randomizes
 * roughly every 1/rateHz seconds and the value one-pole-glides toward it.
 * Output drifts within [-1, 1].
 */
class GustLfo(private val rng: Rng, private val sampleRate: Int) {
    private var value = 0f
    private var target = 0f
    private var framesLeft = 0

    fun next(rateHz: Float): Float {
        if (framesLeft <= 0) {
            framesLeft = (sampleRate / rateHz.coerceAtLeast(0.01f)).toInt().coerceAtLeast(1)
            target = rng.nextBipolar()
        }
        framesLeft--
        value += (target - value) * GLIDE_COEF
        return value
    }

    fun reset() {
        value = 0f
        target = 0f
        framesLeft = 0
    }

    private companion object {
        const val GLIDE_COEF = 0.0003f
    }
}

/**
 * Poisson event scheduler for crackles, droplets and other sparse events:
 * [nextInterval] returns the number of frames until the next event.
 */
class PoissonScheduler(private val rng: Rng) {

    fun nextInterval(ratePerSecond: Float, sampleRate: Int): Int {
        if (ratePerSecond <= 0f) return NO_MORE_EVENTS
        val u = rng.nextFloat().coerceAtLeast(1e-7f)
        return (ln(1f / u) / ratePerSecond * sampleRate).toInt().coerceAtLeast(1)
    }

    companion object {
        const val NO_MORE_EVENTS = Int.MAX_VALUE / 4
    }
}

/**
 * Exponential attack/decay envelope for thumps, crackles and swells.
 * [trigger] is safe to call from the render thread; overlapping triggers
 * restart the envelope.
 */
class ExpEnvelope(private val sampleRate: Int) {
    private var attackCoef = 0.1f
    private var decayCoef = 0.001f
    private var value = 0f
    private var attacking = false
    private var active = false

    fun trigger(attackMs: Float, decayMs: Float) {
        attackCoef = coefFor(attackMs)
        decayCoef = coefFor(decayMs)
        attacking = true
        active = true
    }

    fun next(): Float {
        if (!active) return 0f
        if (attacking) {
            value += (1f - value) * attackCoef
            if (value > 0.95f) attacking = false
        } else {
            value -= value * decayCoef
            if (value < 1e-4f) {
                value = 0f
                active = false
            }
        }
        return value
    }

    val isIdle: Boolean get() = !active

    fun reset() {
        value = 0f
        active = false
        attacking = false
    }

    private fun coefFor(ms: Float): Float = if (ms <= 0.01f) 1f else 1f - exp(-1000f / (ms * sampleRate))
}
