package io.github.kurohi.akachannoise.engine.tools

import io.github.kurohi.akachannoise.engine.NoiseEngine
import io.github.kurohi.akachannoise.engine.model.MixSpec
import java.io.File

/**
 * Renders audio offline through the real engine — identical code path to
 * device playback, minus the AudioTrack. Used by tests and the
 * renderSamples tool.
 */
object OfflineRender {

    fun renderToFile(
        file: File,
        mix: MixSpec,
        seconds: Double,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        fadeInMs: Int = 0,
        fadeOutMs: Int = 0,
        onFrame: ((FloatArray, Int) -> Unit)? = null,
    ) {
        val engine = NoiseEngine(sampleRate)
        val block = 512
        val totalFrames = (seconds * sampleRate).toInt()
        val fadeOutFrames = (fadeOutMs * sampleRate) / 1000
        val buffer = FloatArray(block * 2)
        engine.start(mix, fadeInMs)
        WavWriter(file, sampleRate).use { wav ->
            var written = 0
            while (written < totalFrames) {
                if (fadeOutFrames > 0 && written == totalFrames - fadeOutFrames) {
                    engine.fadeMaster(0f, fadeOutMs)
                }
                val frames = minOf(block, totalFrames - written)
                engine.render(buffer, frames)
                onFrame?.invoke(buffer, frames)
                wav.writeInterleaved(buffer, frames)
                written += frames
            }
        }
    }
}

private const val DEFAULT_SAMPLE_RATE = 48000
