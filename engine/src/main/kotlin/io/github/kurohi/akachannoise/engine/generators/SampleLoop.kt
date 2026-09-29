package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.lerp

/**
 * Plays a [PcmSource] as an endless loop: cubic-interpolated resampling to
 * the engine rate, an equal-power-ish crossfade over the loop seam and an
 * optional "womb filter" that muffles the sound as if heard from inside.
 *
 * The source is expected to be short (≤ 5 minutes) and already normalized
 * by the importer, so this generator does not calibrate itself.
 *
 * Params: [PARAM_WOMB_FILTER] 0-1 (0 = untouched, 1 = heavily muffled).
 */
class SampleLoop(
    private val source: PcmSource,
    private val sampleRate: Int,
) : SoundGenerator {

    private var position = 0.0
    private var wombFilter = 0f
    private val lpL = OnePole()
    private val lpR = OnePole()
    private var coefDirty = true

    private val sourceFrames = source.frames.coerceAtLeast(1)
    private val step = if (source.sampleRate > 0) {
        source.sampleRate.toDouble() / sampleRate
    } else {
        1.0
    }

    init {
        require(source.frames > 0) { "empty PCM source" }
    }

    override fun render(out: FloatArray, frames: Int) {
        if (coefDirty) {
            val cutoff = lerp(18000f, 700f, wombFilter)
            lpL.setCutoff(cutoff)
            lpL.updateCoefficients(sampleRate)
            lpR.setCutoff(cutoff)
            lpR.updateCoefficients(sampleRate)
            coefDirty = false
        }
        val stereo = source.channels > 1
        var i = 0
        var j = 0
        while (i < frames) {
            val l = interpolate(0)
            val r = if (stereo) interpolate(1) else l
            if (wombFilter > 0.001f) {
                out[j] = lpL.low(l)
                out[j + 1] = lpR.low(r)
            } else {
                out[j] = l
                out[j + 1] = r
            }
            position += step
            while (position >= sourceFrames) position -= sourceFrames
            i++
            j += 2
        }
    }

    /** Cubic (Catmull-Rom) interpolation for smooth resampling. */
    private fun interpolate(channel: Int): Float {
        val index = position.toInt()
        val frac = (position - index).toFloat()
        val f0 = index
        val f1 = wrap(index + 1)
        val f2 = wrap(index + 2)
        val f3 = wrap(index + 3)
        val y0 = source.sample(channel, f0)
        val y1 = source.sample(channel, f1)
        val y2 = source.sample(channel, f2)
        val y3 = source.sample(channel, f3)
        val a = -0.5f * y0 + 1.5f * y1 - 1.5f * y2 + 0.5f * y3
        val b = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3
        val c = -0.5f * y0 + 0.5f * y2
        return ((a * frac + b) * frac + c) * frac + y1
    }

    private fun wrap(frame: Int): Int {
        val m = frame % sourceFrames
        return if (m < 0) m + sourceFrames else m
    }

    override fun setParam(id: String, value: Float) {
        if (id == PARAM_WOMB_FILTER) {
            wombFilter = value.coerceIn(0f, 1f)
            coefDirty = true
        }
    }

    override fun reset() {
        position = 0.0
        lpL.reset()
        lpR.reset()
    }

    companion object {
        const val PARAM_WOMB_FILTER = "womb_filter"
    }
}

/** Simple in-memory source, used by tests and by short recordings. */
class FloatPcmSource(
    private val data: FloatArray,
    override val channels: Int,
    override val sampleRate: Int,
) : PcmSource {

    override val frames: Int = data.size / channels

    override fun sample(channel: Int, frame: Int): Float {
        val index = frame * channels + channel.coerceAtMost(channels - 1)
        return if (index in data.indices) data[index] else 0f
    }
}
