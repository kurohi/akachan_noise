package io.github.kurohi.akachannoise.playback.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Float-PCM stereo [AudioTrack] in power-saving mode (deep buffer) —
 * battery-friendly for all-night playback, latency is irrelevant here.
 */
class AudioTrackSink(context: Context) {

    val sampleRate: Int = run {
        val rate = context.getSystemService(AudioManager::class.java)
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        rate?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE
    }

    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(bufferBytes())
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
        .build()

    private fun bufferBytes(): Int {
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        // ~200 ms of audio, at least the platform minimum.
        val target = (sampleRate * BYTES_PER_FRAME * TARGET_LATENCY_MS / 1000)
        return maxOf(minBytes, target)
    }

    fun play() {
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
    }

    fun pause(flush: Boolean = true) {
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
            if (flush) track.flush()
        }
    }

    /** Blocking write of [frames] interleaved stereo float frames. */
    fun write(buffer: FloatArray, frames: Int) {
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        track.write(buffer, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
    }

    fun release() {
        if (track.state == AudioTrack.STATE_INITIALIZED) {
            track.pause()
            track.flush()
        }
        track.release()
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48000
        const val BYTES_PER_FRAME = 8 // stereo float
        const val TARGET_LATENCY_MS = 200
    }
}
