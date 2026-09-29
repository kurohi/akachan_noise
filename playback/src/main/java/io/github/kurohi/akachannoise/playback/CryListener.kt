package io.github.kurohi.akachannoise.playback

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import io.github.kurohi.akachannoise.engine.cry.CryDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opens the microphone, feeds [CryDetector] and reports what it hears.
 *
 * Audio lives in a single reusable frame buffer: nothing is written to disk
 * and nothing leaves the device. Used by the cry monitor service and by the
 * "test listening" screen so both behave identically.
 */
class CryListener(
    private val sensitivity: Float,
    private val onCry: () -> Unit,
) {
    private val _level = MutableStateFlow(0f)

    /** Live microphone level, 0-1, for a meter. */
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _pitchHz = MutableStateFlow(0f)

    /** Last detected pitch in Hz (0 when unvoiced). */
    val pitchHz: StateFlow<Float> = _pitchHz.asStateFlow()

    private val _detected = MutableStateFlow(false)

    /** True briefly after a cry is confirmed. */
    val detected: StateFlow<Boolean> = _detected.asStateFlow()

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile private var running = false

    /**
     * Opens the microphone and starts analysing. Callers must have verified
     * [hasPermission] first (the service and the settings screen both do);
     * the constructor is additionally wrapped against a revoked permission.
     */
    @SuppressLint("MissingPermission")
    fun start(shouldContinue: () -> Boolean = { true }): Boolean {
        if (running) return true
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return false
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                maxOf(minBuffer, SAMPLE_RATE / 5 * 2),
            )
        } catch (e: Exception) {
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        val detector = CryDetector(SAMPLE_RATE, sensitivity)
        record = recorder
        running = true
        thread = Thread(
            { loop(recorder, detector, shouldContinue) },
            "akachan-cry-listener",
        ).apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
        return true
    }

    fun stop() {
        running = false
        thread?.join(600)
        thread = null
        record?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        record = null
        _level.value = 0f
        _pitchHz.value = 0f
    }

    private fun loop(
        recorder: AudioRecord,
        detector: CryDetector,
        shouldContinue: () -> Boolean,
    ) {
        val shorts = ShortArray(CryDetector.FRAME_SIZE)
        val floats = FloatArray(CryDetector.FRAME_SIZE)
        try {
            recorder.startRecording()
            while (running && shouldContinue()) {
                val read = recorder.read(shorts, 0, shorts.size)
                if (read <= 0) continue
                for (i in 0 until read) floats[i] = shorts[i] / 32768f
                val triggered = detector.process(floats, read)
                _level.value = detector.level
                _pitchHz.value = detector.pitchHz
                if (triggered) {
                    _detected.value = true
                    detector.reset()
                    detector.resetNoiseFloor()
                    onCry()
                }
            }
        } catch (e: Exception) {
            // Microphone revoked or busy: stop quietly.
        } finally {
            running = false
            _level.value = 0f
        }
    }

    companion object {
        const val SAMPLE_RATE = CryDetector.DEFAULT_SAMPLE_RATE
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** True when the app holds the microphone permission. */
        fun hasPermission(context: Context): Boolean = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
