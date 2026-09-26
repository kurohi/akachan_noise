package io.github.kurohi.akachannoise.engine.generators

import kotlin.math.floor

/**
 * Shared beat clock for rhythmic womb layers (heartbeat, blood flow). All
 * layers in a mix read the same instance so the whoosh stays locked to the
 * heartbeat. The mixer advances it once per block; generators read [beatPos]
 * (fractional beats since start, with slight natural jitter when enabled).
 *
 * BPM and jitter are set from any thread; they take effect at the next block.
 */
class PulseClock(
    private val sampleRate: Int,
    private val rng: io.github.kurohi.akachannoise.engine.dsp.Rng,
) {
    @Volatile var bpm = 72f
        set(value) {
            field = value.coerceIn(MIN_BPM, MAX_BPM)
        }

    /** 0..1 — natural variability of the beat interval (0 = metronome). */
    @Volatile var variability = 0f

    /** Fractional beat position since start (monotonic, jitter applied per beat). */
    var beatPos = 0.0
        private set

    private var currentPeriod = 1.0 // beats per sample scaling is derived per block

    /** Called by the mixer once per render block, before generators run. */
    fun advanceBlock(frames: Int) {
        var beatsPerSample = bpm / (60.0 * sampleRate)
        val startBeat = floor(beatPos)
        beatPos += frames * beatsPerSample
        val endBeat = floor(beatPos)
        if (endBeat > startBeat && variability > 0f) {
            // Apply jitter to the upcoming beat by nudging the phase within
            // the current beat; average rate over time stays at bpm.
            val jitter = 1.0 + variability * rng.nextRange(-0.02f, 0.02f).toDouble()
            beatPos -= (jitter - 1.0) * (1.0 - (beatPos - endBeat))
        }
        currentPeriod = beatsPerSample
    }

    /** Beats per sample used for the current block. */
    val beatsPerSample: Double get() = currentPeriod

    companion object {
        const val MIN_BPM = 50f
        const val MAX_BPM = 120f
    }
}
