package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.ParamSmoother
import io.github.kurohi.akachannoise.engine.dsp.Rng
import kotlin.math.sqrt

/**
 * The classic noise generator: white, pink and brown with a continuous
 * color slider between them, plus a stereo width control.
 *
 * Color 0 = white, 0.5 = pink, 1 = brown. The three sources are
 * crossfaded, and each is loudness-calibrated at construction so the
 * perceived level stays constant across the whole slider.
 */
class NoiseGenerator(
    private val sampleRate: Int,
    private val rng: Rng,
    defaultColor: Float = COLOR_WHITE,
) : SoundGenerator {

    private val colorSmoother = ParamSmoother(SMOOTH_MS.toDouble(), sampleRate)
    private val widthSmoother = ParamSmoother(SMOOTH_MS.toDouble(), sampleRate)

    // Per-channel pink filter state (Paul Kellet's approximation).
    private var pinkB0L = 0f
    private var pinkB1L = 0f
    private var pinkB2L = 0f
    private var pinkB3L = 0f
    private var pinkB4L = 0f
    private var pinkB5L = 0f
    private var pinkB0R = 0f
    private var pinkB1R = 0f
    private var pinkB2R = 0f
    private var pinkB3R = 0f
    private var pinkB4R = 0f
    private var pinkB5R = 0f

    // Per-channel brown (leaky integrator) state.
    private var brownL = 0f
    private var brownR = 0f

    private val scales = FloatArray(5)

    init {
        colorSmoother.setTargetImmediately(defaultColor)
        widthSmoother.setTargetImmediately(DEFAULT_WIDTH)
        // Calibrate loudness at 5 points across the color slider. The two
        // crossfaded sources are correlated, so a plain equal-power mix would
        // dip in loudness mid-slider; interpolating the measured scales keeps
        // the level constant for every color.
        for (i in 0 until 5) {
            scales[i] = calibrate(COLOR_STEPS[i])
        }
    }

    override fun render(out: FloatArray, frames: Int) = renderInto(out, frames, applyScale = true)

    private fun renderInto(out: FloatArray, frames: Int, applyScale: Boolean) {
        val w = widthSmoother
        val c = colorSmoother
        var i = 0
        val n = frames * 2
        while (i < n) {
            val color = c.next()
            val width = w.next()

            // Left channel
            val whiteL = rng.nextBipolar()
            val pinkL = pinkFilter(true, whiteL)
            val brownL = brownIntegrate(true, whiteL)
            var l = mixColors(color, whiteL, pinkL, brownL, applyScale)

            // Right channel (independent noise draw => decorrelated)
            val whiteR = rng.nextBipolar()
            val pinkR = pinkFilter(false, whiteR)
            val brownR = brownIntegrate(false, whiteR)
            var r = mixColors(color, whiteR, pinkR, brownR, applyScale)

            // Stereo width via mid/side
            if (width < 0.999f) {
                val mid = (l + r) * 0.5f
                val side = (l - r) * 0.5f * width
                l = mid + side
                r = mid - side
            }

            out[i] = l
            out[i + 1] = r
            i += 2
        }
    }

    private fun mixColors(color: Float, white: Float, pink: Float, brown: Float, applyScale: Boolean): Float {
        val raw = if (color <= COLOR_PINK) {
            val t = color / COLOR_PINK
            white * (1f - t) + pink * t
        } else {
            val t = (color - COLOR_PINK) / (1f - COLOR_PINK)
            pink * (1f - t) + brown * t
        }
        return if (applyScale) raw * scaleFor(color) else raw
    }

    /** Piecewise-linear interpolation of the calibration scale for [color] in 0..1. */
    private fun scaleFor(color: Float): Float {
        val pos = color * 4f
        val i = pos.toInt().coerceIn(0, 3)
        val frac = pos - i
        return scales[i] * (1f - frac) + scales[i + 1] * frac
    }

    private fun pinkFilter(left: Boolean, white: Float): Float {
        if (left) {
            pinkB0L = 0.99886f * pinkB0L + white * 0.0555179f
            pinkB1L = 0.99332f * pinkB1L + white * 0.0750759f
            pinkB2L = 0.96900f * pinkB2L + white * 0.1538520f
            pinkB3L = 0.86650f * pinkB3L + white * 0.3104856f
            pinkB4L = 0.55000f * pinkB4L + white * 0.5329522f
            pinkB5L = -0.7616f * pinkB5L - white * 0.0168980f
            return pinkB0L + pinkB1L + pinkB2L + pinkB3L + pinkB4L + pinkB5L + pinkB5L + white * 0.5362f
        }
        pinkB0R = 0.99886f * pinkB0R + white * 0.0555179f
        pinkB1R = 0.99332f * pinkB1R + white * 0.0750759f
        pinkB2R = 0.96900f * pinkB2R + white * 0.1538520f
        pinkB3R = 0.86650f * pinkB3R + white * 0.3104856f
        pinkB4R = 0.55000f * pinkB4R + white * 0.5329522f
        pinkB5R = -0.7616f * pinkB5R - white * 0.0168980f
        return pinkB0R + pinkB1R + pinkB2R + pinkB3R + pinkB4R + pinkB5R + pinkB5R + white * 0.5362f
    }

    private fun brownIntegrate(left: Boolean, white: Float): Float {
        if (left) {
            brownL = (brownL + BROWN_INPUT * white) * BROWN_LEAK
            return brownL
        }
        brownR = (brownR + BROWN_INPUT * white) * BROWN_LEAK
        return brownR
    }

    override fun setParam(id: String, value: Float) {
        when (id) {
            PARAM_COLOR -> colorSmoother.setTarget(value.coerceIn(0f, 1f))
            PARAM_WIDTH -> widthSmoother.setTarget(value.coerceIn(0f, 1f))
        }
    }

    override fun reset() {
        colorSmoother.setTargetImmediately(colorSmoother.current())
        widthSmoother.setTargetImmediately(widthSmoother.current())
        brownL = 0f
        brownR = 0f
    }

    /** Renders a short private buffer at a fixed color and returns the RMS-calibrating scale. */
    private fun calibrate(color: Float): Float {
        val frames = CALIBRATION_FRAMES
        val buf = FloatArray(frames * 2)
        val savedColor = colorSmoother.current()
        colorSmoother.setTargetImmediately(color)
        widthSmoother.setTargetImmediately(1f)
        // Measure the RAW (unscaled) mix: the scale table is not valid yet.
        renderInto(buf, frames, applyScale = false)
        colorSmoother.setTargetImmediately(savedColor)
        widthSmoother.setTargetImmediately(DEFAULT_WIDTH)
        var sum = 0.0
        for (v in buf) sum += (v.toDouble() * v)
        val rms = sqrt(sum / buf.size).toFloat()
        if (rms < 1e-9f) return 1f
        return (TARGET_RMS / rms).coerceAtMost(MAX_SCALE)
    }

    companion object {
        const val PARAM_COLOR = "color"
        const val PARAM_WIDTH = "width"
        const val COLOR_WHITE = 0f
        const val COLOR_PINK = 0.5f
        const val COLOR_BROWN = 1f
        val COLOR_STEPS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        const val DEFAULT_WIDTH = 1f
        const val TARGET_RMS = 0.1f // -20 dBFS
        private const val SMOOTH_MS = 30f
        private const val BROWN_INPUT = 0.02f
        private const val BROWN_LEAK = 1f / 1.02f
        private const val CALIBRATION_FRAMES = 16384
        private const val MAX_SCALE = 200f
    }
}
