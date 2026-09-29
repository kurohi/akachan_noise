package io.github.kurohi.akachannoise.engine.generators

import io.github.kurohi.akachannoise.engine.dsp.ExpEnvelope
import io.github.kurohi.akachannoise.engine.dsp.OnePole
import io.github.kurohi.akachannoise.engine.dsp.Rng
import io.github.kurohi.akachannoise.engine.dsp.lerp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Maternal heartbeat: a "lub-dub" pair of pitched, dropping thumps per beat,
 * locked to the shared [PulseClock] so the blood-flow whoosh stays in rhythm.
 * The generator reads the clock position for the current block; the mixer
 * advances the clock once per block before generators run.
 *
 * Params: [PARAM_BPM] (drives the shared clock), [PARAM_MUFFLE] 0-1 (how
 * deeply the heart is heard "from inside"), [PARAM_VARIABILITY] 0-1 (natural
 * beat-to-beat timing jitter, applied to the shared clock).
 */
class HeartbeatGenerator(
    private val sampleRate: Int,
    private val clock: PulseClock,
    private val rng: Rng,
) : SoundGenerator {

    private var muffle = 0.6f
    private val s1Env = ExpEnvelope(sampleRate)
    private val s2Env = ExpEnvelope(sampleRate)
    private var s1Phase = 0.0
    private var s2Phase = 0.0
    private var s1Frames = 0
    private var s2Frames = 0
    private var lastBeat = -1L
    private var s2FiredForBeat = -1L
    private val thumpLp = OnePole()
    private var thumpCoefDirty = true

    override fun render(out: FloatArray, frames: Int) {
        if (thumpCoefDirty) {
            val cutoff = lerp(500f, 70f, muffle)
            thumpLp.setCutoff(cutoff)
            thumpLp.updateCoefficients(sampleRate)
            thumpCoefDirty = false
        }
        val bps = clock.beatsPerSample
        val blockStartBeat = clock.beatPos - frames * bps
        var i = 0
        var j = 0
        while (i < frames) {
            val beat = blockStartBeat + i * bps
            val beatIndex = floor(beat).toLong()
            if (beatIndex > lastBeat) {
                lastBeat = beatIndex
                s1Env.trigger(ATTACK_MS, S1_DECAY_MS)
                s1Phase = 0.0
                s1Frames = 0
            }
            if (beatIndex >= 0 && beat >= beatIndex + S2_OFFSET_BEATS && s2FiredForBeat < beatIndex) {
                s2FiredForBeat = beatIndex
                s2Env.trigger(ATTACK_MS, S2_DECAY_MS)
                s2Phase = 0.0
                s2Frames = 0
            }
            var s = 0f
            val e1 = s1Env.next()
            if (e1 > 0f) {
                val f = S1_LOW_HZ + (S1_HIGH_HZ - S1_LOW_HZ) * exp(-s1Frames * PITCH_DROP_RATE).toFloat()
                s1Phase += f / sampleRate
                s1Phase -= s1Phase.toLong()
                s += e1 * (sin(s1Phase * TWO_PI).toFloat() + thumpLp.low(rng.nextBipolar()) * THUMP_NOISE)
                s1Frames++
            }
            val e2 = s2Env.next()
            if (e2 > 0f) {
                val f = S2_LOW_HZ + (S2_HIGH_HZ - S2_LOW_HZ) * exp(-s2Frames * PITCH_DROP_RATE).toFloat()
                s2Phase += f / sampleRate
                s2Phase -= s2Phase.toLong()
                s += e2 * S2_GAIN * (sin(s2Phase * TWO_PI).toFloat() + thumpLp.low(rng.nextBipolar()) * THUMP_NOISE)
                s2Frames++
            }
            out[j] = s
            out[j + 1] = s
            i++
            j += 2
        }
    }

    override fun setParam(id: String, value: Float) {
        when (id) {
            PARAM_BPM -> clock.bpm = value

            PARAM_MUFFLE -> {
                muffle = value.coerceIn(0f, 1f)
                thumpCoefDirty = true
            }

            PARAM_VARIABILITY -> clock.variability = value.coerceIn(0f, 1f)
        }
    }

    override fun reset() {
        s1Env.reset()
        s2Env.reset()
        s1Phase = 0.0
        s2Phase = 0.0
        s1Frames = 0
        s2Frames = 0
        lastBeat = -1L
        s2FiredForBeat = -1L
        thumpLp.reset()
    }

    companion object {
        const val PARAM_BPM = "bpm"
        const val PARAM_MUFFLE = "muffle"
        const val PARAM_VARIABILITY = "variability"
        const val DEFAULT_BPM = 72f
        private const val ATTACK_MS = 6f
        private const val S1_DECAY_MS = 140f
        private const val S2_DECAY_MS = 95f
        private const val S1_HIGH_HZ = 95f
        private const val S1_LOW_HZ = 42f
        private const val S2_HIGH_HZ = 72f
        private const val S2_LOW_HZ = 55f
        private const val S2_GAIN = 0.6f
        private const val S2_OFFSET_BEATS = 0.18f
        private const val THUMP_NOISE = 0.45f
        private const val PITCH_DROP_RATE = 1.0 / (0.045 * 48000)
        private const val TWO_PI = PI * 2.0
    }
}
