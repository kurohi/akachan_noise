package io.github.kurohi.akachannoise.engine.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place radix-2 FFT with precomputed twiddle factors, sized once and
 * reused so the cry detector never allocates on the audio thread.
 * Real input, full complex spectrum out.
 */
class Fft(val size: Int) {

    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)
    private val reversed = IntArray(size)
    private val re = FloatArray(size)
    private val im = FloatArray(size)

    init {
        require(size > 1 && size and (size - 1) == 0) { "size must be a power of two" }
        for (i in 0 until size / 2) {
            val angle = -2.0 * PI * i / size
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
        var j = 0
        for (i in 0 until size) {
            reversed[i] = j
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
        }
    }

    /**
     * Fills [power] (size / 2 + 1 bins) with the magnitude squared of the
     * spectrum of [input] (size samples). No windowing is applied; callers
     * that need it can pre-window.
     */
    fun powerSpectrum(input: FloatArray, power: FloatArray) {
        for (i in 0 until size) {
            re[i] = input[i]
            im[i] = 0f
        }
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var i = 0
            while (i < size) {
                var k = 0
                var twiddle = 0
                while (k < half) {
                    val evenRe = re[i + k]
                    val evenIm = im[i + k]
                    val oddRe = re[i + k + half] * cosTable[twiddle] -
                        im[i + k + half] * sinTable[twiddle]
                    val oddIm = re[i + k + half] * sinTable[twiddle] +
                        im[i + k + half] * cosTable[twiddle]
                    re[i + k] = evenRe + oddRe
                    im[i + k] = evenIm + oddIm
                    re[i + k + half] = evenRe - oddRe
                    im[i + k + half] = evenIm - oddIm
                    k++
                    twiddle += step
                }
                i += len
            }
            len = len shl 1
        }
        val bins = size / 2 + 1
        for (i in 0 until bins) {
            power[i] = re[i] * re[i] + im[i] * im[i]
        }
    }
}
