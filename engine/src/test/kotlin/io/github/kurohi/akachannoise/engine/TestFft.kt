package io.github.kurohi.akachannoise.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

/** Minimal radix-2 FFT for tests. */
object TestFft {
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n and (n - 1) == 0) { "size must be a power of two" }
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
            var m = n shr 1
            while (m in 1..j) {
                j -= m
                m = m shr 1
            }
            j += m
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang)
            val wIm = kotlin.math.sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val evenRe = re[i + k]
                    val evenIm = im[i + k]
                    val oddRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val oddIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = evenRe + oddRe
                    im[i + k] = evenIm + oddIm
                    re[i + k + len / 2] = evenRe - oddRe
                    im[i + k + len / 2] = evenIm - oddIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Power spectrum (magnitude squared) of a real signal with a Hann window. */
    fun powerSpectrum(signal: FloatArray, sampleRate: Int): DoubleArray {
        val n = signal.size
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in 0 until n) {
            val w = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            re[i] = signal[i] * w
        }
        fft(re, im)
        val half = n / 2
        val power = DoubleArray(half)
        for (i in 0 until half) power[i] = re[i] * re[i] + im[i] * im[i]
        return power
    }

    /** Energy (dB) of the octave band [fLow, fHigh). */
    fun bandDb(power: DoubleArray, sampleRate: Int, fLow: Double, fHigh: Double): Double {
        val binHz = sampleRate.toDouble() / (power.size * 2)
        val lo = kotlin.math.max(1, (fLow / binHz).toInt())
        val hi = kotlin.math.min(power.size - 1, (fHigh / binHz).toInt())
        require(hi > lo) { "band too narrow: $fLow..$fHigh" }
        var sum = 0.0
        for (i in lo until hi) sum += power[i]
        return 10.0 * log10(sum / (hi - lo))
    }

    /** RMS of a mono signal. */
    fun rms(signal: FloatArray): Double {
        var s = 0.0
        for (v in signal) s += v.toDouble() * v
        return sqrt(s / signal.size)
    }
}
