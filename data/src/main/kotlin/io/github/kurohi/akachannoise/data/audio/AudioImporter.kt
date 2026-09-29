package io.github.kurohi.akachannoise.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import io.github.kurohi.akachannoise.data.model.CustomSoundRef
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Decodes an audio file the user picked into a normalized, trimmed PCM loop.
 *
 * Pipeline: MediaExtractor/MediaCodec decode → cap at two channels → trim
 * leading and trailing silence → normalize to the library's loudness →
 * bake a crossfade over the loop seam → write 16-bit PCM.
 *
 * Everything happens on the device; the file is read through the system
 * picker and never leaves the app's private storage.
 */
class AudioImporter(private val context: Context) {

    /**
     * Decodes [uri] and writes `sounds/<id>.pcm`, returning the reference to
     * store. Returns null when the file cannot be decoded.
     */
    fun import(
        uri: Uri,
        id: String,
        name: String,
        soundsDir: File,
        maxDurationMs: Long = MAX_DURATION_MS,
    ): CustomSoundRef? {
        val decoded = decode(uri, maxDurationMs) ?: return null
        val prepared = prepareForLoop(decoded.samples, decoded.channels, decoded.sampleRate)
        val file = File(soundsDir, "$id.pcm")
        PcmFileWriter.write(file, prepared.samples, decoded.channels, decoded.sampleRate)
        val frames = prepared.samples.size / decoded.channels
        return CustomSoundRef(
            id = id,
            name = name,
            fileName = file.name,
            durationMs = frames * 1000L / decoded.sampleRate,
            sampleRate = decoded.sampleRate,
            channels = decoded.channels,
            wombFilter = 0f,
            loopCrossfadeMs = CROSSFADE_MS,
            createdAt = System.currentTimeMillis(),
        )
    }

    private class Decoded(
        val samples: FloatArray,
        val channels: Int,
        val sampleRate: Int,
    )

    private fun decode(uri: Uri, maxDurationMs: Long): Decoded? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val channels = sourceChannels.coerceIn(1, 2)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val maxFrames = (maxDurationMs * sampleRate / 1000).toInt()
            val out = FloatArray(maxFrames * channels + channels * 1024)
            var written = 0
            var outputChannels = channels
            var outputRate = sampleRate

            val info = MediaCodec.BufferInfo()
            var sawInputEnd = false
            var sawOutputEnd = false
            while (!sawOutputEnd && written < maxFrames * channels) {
                if (!sawInputEnd) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sawInputEnd = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                size,
                                extractor.sampleTime,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        outputRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outputChannels = newFormat
                            .getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            .coerceIn(1, 2)
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> {
                        if (outputIndex >= 0) {
                            val buffer = codec.getOutputBuffer(outputIndex)!!
                            val encoding = codec.outputFormat
                                .getInteger(
                                    MediaFormat.KEY_PCM_ENCODING,
                                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                                )
                            written = appendSamples(
                                buffer,
                                info,
                                encoding,
                                outputChannels,
                                out,
                                written,
                            )
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                sawOutputEnd = true
                            }
                        }
                    }
                }
            }
            Decoded(out.copyOf(written), channels, outputRate)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun appendSamples(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        encoding: Int,
        channels: Int,
        out: FloatArray,
        written: Int,
    ): Int {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        var index = written
        when (encoding) {
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
                while (floats.hasRemaining() && index < out.size) {
                    out[index++] = floats.get()
                }
            }

            else -> {
                val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                while (shorts.hasRemaining() && index < out.size) {
                    out[index++] = shorts.get() / 32768f
                }
            }
        }
        return index
    }

    private class Prepared(val samples: FloatArray)

    /**
     * Processes a raw 16-bit PCM recording in place into a loopable file
     * without loading it into the heap (a five minute take is ~29 MB).
     * Returns the resulting duration in milliseconds, or 0 on failure.
     */
    fun finalizeRecording(source: File, target: File, channels: Int, sampleRate: Int): Long {
        if (!source.isFile || source.length() < 2) return 0
        return try {
            RandomAccessFile(source, "r").use { raf ->
                val channel = raf.channel
                val size = channel.size()
                val frames = (size / 2 / channels).toInt()
                if (frames <= 0) return 0
                val map = channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
                    .order(ByteOrder.LITTLE_ENDIAN)

                fun sampleAt(frame: Int, c: Int): Float {
                    val index = frame * channels + c
                    if (index < 0 || index >= frames * channels) return 0f
                    return map.getShort(index * 2) / 32768f
                }

                var first = 0
                var last = frames - 1
                while (first < frames && abs(sampleAt(first, 0)) < SILENCE_THRESHOLD) first++
                while (last > first && abs(sampleAt(last, 0)) < SILENCE_THRESHOLD) last--
                if (last <= first) return 0
                val trimmedFrames = last - first + 1

                var sumSq = 0.0
                var peak = 0f
                for (frame in first until first + trimmedFrames) {
                    for (c in 0 until channels) {
                        val v = sampleAt(frame, c)
                        sumSq += v.toDouble() * v
                        peak = maxOf(peak, abs(v))
                    }
                }
                if (peak <= 1e-6f) return 0
                val rms = sqrt(sumSq / (trimmedFrames * channels)).toFloat()
                val scale = minOf(
                    if (rms > 1e-6f) NORMALIZE_TARGET / rms else 1f,
                    MAX_PEAK / peak,
                )

                val fadeFrames = (CROSSFADE_MS * sampleRate / 1000)
                    .coerceAtMost(trimmedFrames / 4)
                val head = FloatArray(fadeFrames * channels)
                for (i in 0 until fadeFrames) {
                    for (c in 0 until channels) {
                        head[i * channels + c] = sampleAt(first + i, c) * scale
                    }
                }

                target.parentFile?.mkdirs()
                RandomAccessFile(target, "rw").use { out ->
                    out.setLength(0)
                    val buffer = ByteBuffer.allocate(WRITE_CHUNK * 2).order(ByteOrder.LITTLE_ENDIAN)
                    var index = 0
                    val total = trimmedFrames * channels
                    val fadeStart = trimmedFrames - fadeFrames
                    while (index < total) {
                        buffer.clear()
                        var written = 0
                        while (written < WRITE_CHUNK && index < total) {
                            val frame = index / channels
                            val c = index % channels
                            var value = sampleAt(first + frame, c) * scale
                            if (fadeFrames > 1 && frame >= fadeStart) {
                                val j = frame - fadeStart
                                val t = j / fadeFrames.toFloat()
                                value = value * (1f - t) + head[j * channels + c] * t
                            }
                            buffer.putShort((value.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                            index++
                            written++
                        }
                        out.write(buffer.array(), 0, written * 2)
                    }
                }
                trimmedFrames * 1000L / sampleRate
            }
        } catch (e: Exception) {
            0
        }
    }

    /** Trim, normalize and crossfade so the result loops without a click. */
    private fun prepareForLoop(
        samples: FloatArray,
        channels: Int,
        sampleRate: Int,
    ): Prepared {
        if (samples.isEmpty()) return Prepared(samples)

        val frames = samples.size / channels
        val silenceThreshold = SILENCE_THRESHOLD
        var first = 0
        var last = frames - 1
        while (first < frames && abs(samples[first * channels]) < silenceThreshold) first++
        while (last > first && abs(samples[last * channels]) < silenceThreshold) last--
        if (last <= first) return Prepared(FloatArray(0))

        val trimmedFrames = last - first + 1
        val trimmed = FloatArray(trimmedFrames * channels)
        System.arraycopy(
            samples,
            first * channels,
            trimmed,
            0,
            trimmedFrames * channels,
        )

        normalize(trimmed)
        bakeLoopCrossfade(trimmed, channels, CROSSFADE_MS, sampleRate)
        return Prepared(trimmed)
    }

    private fun normalize(samples: FloatArray) {
        var sumSq = 0.0
        var peak = 0f
        for (v in samples) {
            sumSq += v.toDouble() * v
            peak = maxOf(peak, abs(v))
        }
        if (samples.isEmpty() || peak <= 1e-6f) return
        val rms = sqrt(sumSq / samples.size).toFloat()
        val scale = minOf(
            if (rms > 1e-6f) NORMALIZE_TARGET / rms else 1f,
            MAX_PEAK / peak,
        )
        for (i in samples.indices) samples[i] *= scale
    }

    /**
     * Blends the tail into the head so the end of the file connects smoothly
     * back to the start: the final [crossfadeMs] is mixed with the opening
     * samples, and the result is shortened by that amount.
     */
    private fun bakeLoopCrossfade(
        samples: FloatArray,
        channels: Int,
        crossfadeMs: Int,
        sampleRate: Int,
    ) {
        val frames = samples.size / channels
        val fadeFrames = (crossfadeMs * sampleRate / 1000).coerceAtMost(frames / 4)
        if (fadeFrames <= 1) return
        for (i in 0 until fadeFrames) {
            val t = i / fadeFrames.toFloat()
            for (c in 0 until channels) {
                val tailIndex = (frames - fadeFrames + i) * channels + c
                val headIndex = i * channels + c
                samples[tailIndex] = samples[tailIndex] * (1f - t) + samples[headIndex] * t
            }
        }
    }

    companion object {
        const val MAX_DURATION_MS = 5 * 60 * 1000L
        const val CROSSFADE_MS = 1200
        private const val MAX_PEAK = 0.99f
        private const val NORMALIZE_TARGET = 0.1f
        private const val SILENCE_THRESHOLD = 0.004f
        private const val WRITE_CHUNK = 4096
        private const val TIMEOUT_US = 10_000L
    }
}
