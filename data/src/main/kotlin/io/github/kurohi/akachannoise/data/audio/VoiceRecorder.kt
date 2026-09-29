package io.github.kurohi.akachannoise.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Records the microphone straight to 16-bit PCM. Used only while the
 * recorder screen is open; the take is deleted immediately if discarded.
 * The microphone is never used without the user pressing record.
 */
class VoiceRecorder(private val context: Context) {

    private val _level = MutableStateFlow(0f)

    /** Live input level, 0-1, for the meter. */
    val level: StateFlow<Float> = _level.asStateFlow()

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile private var recording = false

    val isRecording: Boolean get() = recording

    /**
     * Starts recording into [target]. Returns false when the microphone is
     * unavailable or permission was not granted.
     */
    @SuppressLint("MissingPermission")
    fun start(target: File): Boolean {
        if (recording) return false
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return false
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE / 5 * 2)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize,
            )
        } catch (e: Exception) {
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        target.parentFile?.mkdirs()
        record = recorder
        recording = true
        thread = Thread({ recordLoop(recorder, target, bufferSize) }, "akachan-recorder").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
        return true
    }

    /** Stops recording and returns the length in milliseconds. */
    fun stop(): Long {
        if (!recording) return 0
        recording = false
        thread?.join(1_000)
        thread = null
        record?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        record = null
        _level.value = 0f
        return recordedMs
    }

    fun cancel() {
        stop()
    }

    private var recordedMs = 0L

    private fun recordLoop(recorder: AudioRecord, target: File, bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)
        val bytes = ByteBuffer.allocate(buffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        var totalFrames = 0L
        try {
            RandomAccessFile(target, "rw").use { raf ->
                raf.setLength(0)
                recorder.startRecording()
                while (recording && totalFrames < MAX_FRAMES) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    var sum = 0.0
                    bytes.clear()
                    for (i in 0 until read) {
                        val value = buffer[i]
                        bytes.putShort(value)
                        sum += (value / 32768.0) * (value / 32768.0)
                    }
                    raf.write(bytes.array(), 0, read * 2)
                    totalFrames += read
                    val rms = sqrt(sum / read).toFloat()
                    _level.value = (rms * 4f).coerceIn(0f, 1f)
                }
                recordedMs = totalFrames * 1000L / SAMPLE_RATE
            }
        } catch (e: Exception) {
            // Recording failed (permission revoked, device busy): leave the
            // partial file for the caller to discard.
        } finally {
            recordedMs = totalFrames * 1000L / SAMPLE_RATE
            recording = false
            _level.value = 0f
        }
    }

    companion object {
        const val SAMPLE_RATE = 48000
        const val CHANNELS = 1
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val MAX_DURATION_MS = 5 * 60 * 1000L
        private const val MAX_FRAMES = MAX_DURATION_MS * SAMPLE_RATE / 1000
    }
}
