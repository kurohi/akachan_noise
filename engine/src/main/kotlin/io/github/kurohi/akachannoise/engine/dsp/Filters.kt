package io.github.kurohi.akachannoise.engine.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Simple one-pole low-pass / high-pass pair. Cheap and stable at any cutoff. */
class OnePole {
    private var z = 0f
    private var lowCoef = 0.5f
    private var cutoff = 1000f

    fun setCutoff(cutoffHz: Float) {
        cutoff = cutoffHz
    }

    /** Must be called once after [setCutoff] to (re)compute the coefficient. */
    fun updateCoefficients(sampleRate: Int) {
        lowCoef = (1.0f - kotlin.math.exp(-2.0 * PI * cutoff / sampleRate)).toFloat()
    }

    fun low(x: Float): Float {
        z += lowCoef * (x - z)
        return z
    }

    fun high(x: Float): Float = x - low(x)

    fun reset() {
        z = 0f
    }
}

/** Removes DC offset with a very low corner frequency. */
class DcBlocker {
    private var x1 = 0f
    private var y1 = 0f

    fun process(x: Float): Float {
        val y = x - x1 + 0.995f * y1
        x1 = x
        y1 = y
        return y
    }

    fun reset() {
        x1 = 0f
        y1 = 0f
    }
}

/**
 * RBJ audio-EQ-cookbook biquad. Coefficients are computed on the calling
 * thread and cached; [process] only does multiplies, so it is safe for the
 * render loop.
 */
class Biquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun lowPass(sampleRate: Int, cutoffHz: Float, q: Float = INVERSE_SQRT2) = configure(sampleRate, cutoffHz, q, LOWPASS)
    fun highPass(sampleRate: Int, cutoffHz: Float, q: Float = INVERSE_SQRT2) = configure(sampleRate, cutoffHz, q, HIGHPASS)
    fun bandPass(sampleRate: Int, cutoffHz: Float, q: Float = 1f) = configure(sampleRate, cutoffHz, q, BANDPASS)

    private fun configure(sampleRate: Int, cutoffHz: Float, q: Float, type: Int) {
        val w0 = 2.0 * PI * cutoffHz / sampleRate
        val cosw0 = cos(w0).toFloat()
        val alpha = (sin(w0) / (2.0 * q)).toFloat()
        when (type) {
            LOWPASS -> setCoefficients(
                (1f - cosw0) / 2f,
                1f - cosw0,
                (1f - cosw0) / 2f,
                1f + alpha,
                -2f * cosw0,
                1f - alpha,
            )

            HIGHPASS -> setCoefficients(
                (1f + cosw0) / 2f,
                -(1f + cosw0),
                (1f + cosw0) / 2f,
                1f + alpha,
                -2f * cosw0,
                1f - alpha,
            )

            BANDPASS -> setCoefficients(
                alpha,
                0f,
                -alpha,
                1f + alpha,
                -2f * cosw0,
                1f - alpha,
            )
        }
    }

    private fun setCoefficients(nb0: Float, nb1: Float, nb2: Float, na0: Float, na1: Float, na2: Float) {
        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    fun reset() {
        x1 = 0f
        x2 = 0f
        y1 = 0f
        y2 = 0f
    }

    private companion object {
        const val LOWPASS = 1
        const val HIGHPASS = 2
        const val BANDPASS = 3
        val INVERSE_SQRT2 = 0.70710678f
    }
}

/**
 * Chamberlin state-variable filter. Unity slope at the knee, stays stable when
 * the cutoff is modulated per sample. Valid for cutoffs well below sr/6.
 */
class Svf {
    private var low = 0f
    private var band = 0f

    fun process(x: Float, cutoffHz: Float, sampleRate: Int, q: Float = 1f): Float {
        val f = 2.0 * PI * cutoffHz / sampleRate
        low += (f * band).toFloat()
        val high = x - low - q * band
        band += (f * high).toFloat()
        return low
    }

    /** Same update as [process], returning the band-pass output. */
    fun bandPass(x: Float, cutoffHz: Float, sampleRate: Int, q: Float = 1f): Float {
        val f = 2.0 * PI * cutoffHz / sampleRate
        low += (f * band).toFloat()
        val high = x - low - q * band
        band += (f * high).toFloat()
        return band
    }

    fun high(x: Float, cutoffHz: Float, sampleRate: Int, q: Float = 1f): Float = x - process(x, cutoffHz, sampleRate, q)

    fun reset() {
        low = 0f
        band = 0f
    }
}
