package io.github.kurohi.akachannoise.engine.cry

import io.github.kurohi.akachannoise.engine.dsp.Fft
import io.github.kurohi.akachannoise.engine.dsp.lerp
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * On-device baby-cry detector. Pure Kotlin so it can be unit tested with
 * synthetic signals; audio is analysed frame by frame in memory and never
 * stored.
 *
 * A 32 ms frame counts as cry-like when it is loud enough above the adaptive
 * noise floor, its spectrum is tonal rather than flat, and it has a voiced
 * pitch in the 250-800 Hz band with harmonics. The detector fires when at
 * least half of a ~3 s window is cry-like and at least two separate bursts
 * were heard — a single cough or a door slam will not do it.
 *
 * Sensitivity (0 = only clear cries, 1 = very eager) maps to the three
 * thresholds.
 */
class CryDetector(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    sensitivity: Float = 0.5f,
) {
    private var sensitivity = sensitivity.coerceIn(0f, 1f)

    private val fft = Fft(FRAME_SIZE)
    private val power = FloatArray(FRAME_SIZE / 2 + 1)
    private val frame = FloatArray(FRAME_SIZE)
    private val difference = FloatArray(MAX_LAG + 1)
    private val normalizedDifference = FloatArray(MAX_LAG + 1)
    private var frameFill = 0

    private var noiseFloor = 1e-4f

    private val window = BooleanArray(WINDOW_FRAMES)
    private var windowIndex = 0
    private var windowFilled = 0
    private var cryFrames = 0
    private var bursts = 0
    private var quietRun = 0
    private var cooldown = 0

    /** Last frame level, 0-1, for a live meter. */
    var level: Float = 0f
        private set

    /** Last detected pitch in Hz, 0 when unvoiced. */
    var pitchHz: Float = 0f
        private set

    /** Last spectral flatness in the cry band (0 tonal … 1 noise). */
    var flatness: Float = 1f
        private set

    /** True while the last analysed frame looked like a cry. */
    var frameIsCry: Boolean = false
        private set

    fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0f, 1f)
    }

    /**
     * Feeds [count] mono samples. Returns true on the frame where a cry is
     * confirmed (the caller should restart playback and then [reset]).
     */
    fun process(samples: FloatArray, count: Int): Boolean {
        var triggered = false
        var index = 0
        while (index < count) {
            val take = minOf(FRAME_SIZE - frameFill, count - index)
            System.arraycopy(samples, index, frame, frameFill, take)
            frameFill += take
            index += take
            if (frameFill == FRAME_SIZE) {
                frameFill = 0
                if (analyseFrame()) triggered = true
            }
        }
        return triggered
    }

    fun reset() {
        frameFill = 0
        window.fill(false)
        windowIndex = 0
        windowFilled = 0
        cryFrames = 0
        bursts = 0
        quietRun = 0
        cooldown = 0
        frameIsCry = false
    }

    /** Forgets the learned background level, e.g. when listening restarts. */
    fun resetNoiseFloor() {
        noiseFloor = 1e-4f
    }

    private fun analyseFrame(): Boolean {
        var sumSq = 0.0
        for (i in 0 until FRAME_SIZE) {
            val v = frame[i].toDouble()
            sumSq += v * v
        }
        val rms = sqrt(sumSq / FRAME_SIZE).toFloat()
        level = (rms * 4f).coerceIn(0f, 1f)

        // Adaptive floor: drops quickly, rises slowly, so a cry does not
        // teach the detector that loud is normal.
        if (rms < noiseFloor) {
            noiseFloor += (rms - noiseFloor) * FLOOR_DOWN
        } else {
            noiseFloor += (rms - noiseFloor) * FLOOR_UP
        }

        val energyDb = 20f * log10((rms + 1e-9f) / (noiseFloor + 1e-9f))
        val loudEnough = rms > ABSOLUTE_GATE && energyDb > lerp(ENERGY_DB_EAGER, ENERGY_DB_STRICT, 1f - sensitivity)

        var cryLike = false
        if (loudEnough) {
            fft.powerSpectrum(frame, power)
            flatness = bandFlatness()
            val pitch = detectPitch()
            pitchHz = pitch.first
            val voiced = pitch.second
            val hasHarmonics = hasHarmonics(pitch.first)
            cryLike = voiced &&
                flatness < lerp(FLATNESS_STRICT, FLATNESS_EAGER, sensitivity) &&
                hasHarmonics
        } else {
            pitchHz = 0f
            flatness = 1f
        }
        frameIsCry = cryLike

        if (cooldown > 0) {
            cooldown--
            return false
        }

        // Sliding window of cry decisions.
        val previous = window[windowIndex]
        window[windowIndex] = cryLike
        if (previous) cryFrames--
        if (cryLike) cryFrames++
        windowIndex = (windowIndex + 1) % WINDOW_FRAMES
        if (windowFilled < WINDOW_FRAMES) windowFilled++

        // A burst is a run of cry frames that follows a quiet gap.
        if (cryLike) {
            if (!frameIsCryPrevious && quietRun >= MIN_GAP_FRAMES) bursts++
            quietRun = 0
        } else {
            quietRun++
        }
        frameIsCryPrevious = cryLike

        val enoughCry = windowFilled >= MIN_WINDOW_FRAMES &&
            cryFrames.toFloat() / windowFilled >= CRY_FRACTION
        if (enoughCry && bursts >= MIN_BURSTS) {
            cooldown = COOLDOWN_FRAMES
            window.fill(false)
            cryFrames = 0
            windowFilled = 0
            bursts = 0
            return true
        }
        return false
    }

    private var frameIsCryPrevious = false

    /** Spectral flatness over the cry band: low for tonal sounds. */
    private fun bandFlatness(): Float {
        val binHz = sampleRate.toFloat() / FRAME_SIZE
        val lo = (BAND_LOW_HZ / binHz).toInt().coerceAtLeast(1)
        val hi = (BAND_HIGH_HZ / binHz).toInt().coerceAtMost(power.size - 1)
        if (hi <= lo) return 1f
        var logSum = 0.0
        var sum = 0.0
        var count = 0
        for (i in lo..hi) {
            val p = power[i].toDouble() + 1e-12
            logSum += ln(p)
            sum += p
            count++
        }
        val arithmetic = sum / count
        if (arithmetic <= 0.0) return 1f
        val geometric = kotlin.math.exp(logSum / count)
        return (geometric / arithmetic).toFloat().coerceIn(0f, 1f)
    }

    /**
     * YIN-style pitch detection. Returns the frequency in Hz and whether the
     * frame is voiced enough to trust.
     */
    private fun detectPitch(): Pair<Float, Boolean> {
        val window = FRAME_SIZE - MAX_LAG
        for (tau in 0..MAX_LAG) {
            var sum = 0.0
            for (i in 0 until window) {
                val diff = frame[i] - frame[i + tau]
                sum += diff * diff
            }
            difference[tau] = sum.toFloat()
        }
        normalizedDifference[0] = 1f
        var running = 0f
        for (tau in 1..MAX_LAG) {
            running += difference[tau]
            normalizedDifference[tau] = if (running > 0f) {
                difference[tau] * tau / running
            } else {
                1f
            }
        }
        var bestTau = -1
        var best = Float.MAX_VALUE
        for (tau in MIN_LAG..MAX_LAG) {
            if (normalizedDifference[tau] < best) {
                best = normalizedDifference[tau]
                bestTau = tau
            }
        }
        if (bestTau < 0 || best > VOICED_LIMIT) return 0f to false
        // Parabolic interpolation around the minimum for a finer estimate.
        val refined = if (bestTau > MIN_LAG && bestTau < MAX_LAG) {
            val a = normalizedDifference[bestTau - 1]
            val b = normalizedDifference[bestTau]
            val c = normalizedDifference[bestTau + 1]
            val denominator = 2f * (2f * b - a - c)
            if (abs(denominator) > 1e-9f) bestTau + (c - a) / denominator else bestTau.toFloat()
        } else {
            bestTau.toFloat()
        }
        val f0 = sampleRate / refined
        return f0 to true
    }

    /** Cries are harmonic: the second partial should carry real energy. */
    private fun hasHarmonics(f0: Float): Boolean {
        if (f0 < BAND_LOW_HZ || f0 > BAND_HIGH_HZ) return false
        val binHz = sampleRate.toFloat() / FRAME_SIZE
        val fundamental = peakNear(f0 / binHz)
        val second = peakNear(2f * f0 / binHz)
        val third = peakNear(3f * f0 / binHz)
        if (fundamental <= 0f) return false
        return second > HARMONIC_RATIO * fundamental || third > HARMONIC_RATIO * fundamental
    }

    private fun peakNear(bin: Float): Float {
        val center = bin.toInt()
        var max = 0f
        for (i in (center - 1)..(center + 1)) {
            if (i in power.indices) max = maxOf(max, power[i])
        }
        return max
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 16000
        const val FRAME_SIZE = 512
        const val WINDOW_FRAMES = 94
        const val MIN_WINDOW_FRAMES = 40
        const val CRY_FRACTION = 0.5f
        const val MIN_BURSTS = 2
        const val MIN_GAP_FRAMES = 4
        const val COOLDOWN_FRAMES = 156
        const val MIN_F0_HZ = 250f
        const val MAX_F0_HZ = 800f
        const val BAND_LOW_HZ = 250f
        const val BAND_HIGH_HZ = 4000f

        private const val MIN_LAG = 20
        private const val MAX_LAG = 64
        private const val VOICED_LIMIT = 0.35f
        private const val HARMONIC_RATIO = 0.04f
        private const val ABSOLUTE_GATE = 0.0015f
        private const val ENERGY_DB_STRICT = 12f
        private const val ENERGY_DB_EAGER = 4f
        private const val FLATNESS_STRICT = 0.2f
        private const val FLATNESS_EAGER = 0.45f
        private const val FLOOR_DOWN = 0.25f
        private const val FLOOR_UP = 0.0008f
    }
}
