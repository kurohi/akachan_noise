package io.github.kurohi.akachannoise.data.audio

import io.github.kurohi.akachannoise.engine.generators.PcmSource
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Writes 16-bit little-endian PCM. Used by the importer and the recorder;
 * the file is then played back memory-mapped so it costs no heap.
 */
object PcmFileWriter {

    fun write(
        file: File,
        samples: FloatArray,
        channels: Int,
        sampleRate: Int,
    ) {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val buffer = ByteBuffer.allocate(BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            var index = 0
            while (index < samples.size) {
                buffer.clear()
                var written = 0
                while (written < BUFFER_BYTES / 2 && index < samples.size) {
                    val clamped = samples[index].coerceIn(-1f, 1f)
                    buffer.putShort((clamped * 32767f).toInt().toShort())
                    index++
                    written++
                }
                raf.write(buffer.array(), 0, written * 2)
            }
        }
    }

    private const val BUFFER_BYTES = 8192
}

/**
 * Memory-mapped, read-only view of a 16-bit PCM file. The OS page cache
 * keeps it out of the Java heap, which matters for multi-minute recordings.
 */
class MappedPcmSource private constructor(
    private val buffer: ByteBuffer,
    override val frames: Int,
    override val channels: Int,
    override val sampleRate: Int,
    private val raf: RandomAccessFile,
    private val channel: FileChannel,
) : PcmSource {

    override fun sample(channelIndex: Int, frame: Int): Float {
        val c = channelIndex.coerceIn(0, channels - 1)
        val index = frame * channels + c
        if (index < 0 || index >= frames * channels) return 0f
        val value = buffer.getShort(index * 2)
        return value / 32768f
    }

    fun close() {
        runCatching { channel.close() }
        runCatching { raf.close() }
    }

    companion object {
        /** Returns null when the file is missing or not a valid PCM payload. */
        fun open(file: File, channels: Int, sampleRate: Int): MappedPcmSource? {
            if (!file.isFile || file.length() < 2) return null
            return runCatching {
                val raf = RandomAccessFile(file, "r")
                val channel = raf.channel
                val size = channel.size()
                val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
                    .order(ByteOrder.LITTLE_ENDIAN)
                val frames = (size / 2 / channels).toInt()
                if (frames <= 0) {
                    channel.close()
                    raf.close()
                    return null
                }
                MappedPcmSource(buffer, frames, channels, sampleRate, raf, channel)
            }.getOrNull()
        }
    }
}
