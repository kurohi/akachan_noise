package io.github.kurohi.akachannoise.engine.tools

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/**
 * Minimal 16-bit PCM WAV writer. Used by the renderSamples tool and by
 * tests that need to inspect audio offline. Not part of the runtime audio
 * path on Android.
 */
class WavWriter(file: File, private val sampleRate: Int, private val channels: Int = 2) : Closeable {
    private val raf = RandomAccessFile(file, "rw")
    private var framesWritten = 0L

    init {
        raf.setLength(0)
        writeHeader(0)
    }

    fun writeInterleaved(samples: FloatArray, frames: Int) {
        val n = frames * channels
        val bytes = ByteArray(n * 2)
        var i = 0
        var o = 0
        while (i < n) {
            var v = samples[i]
            if (v.isNaN()) v = 0f
            v = v.coerceIn(-1f, 1f)
            val s = (v * 32767f).toInt()
            // little-endian int16
            bytes[o] = (s and 0xFF).toByte()
            bytes[o + 1] = ((s shr 8) and 0xFF).toByte()
            i++
            o += 2
        }
        raf.write(bytes)
        framesWritten += frames
    }

    override fun close() {
        // Rewrite the header with the real size.
        raf.seek(0)
        writeHeader(framesWritten)
        raf.close()
    }

    private fun writeHeader(frames: Long) {
        val dataBytesLong = frames * channels * 2
        require(dataBytesLong <= Int.MAX_VALUE) { "WAV data too large for 32-bit header: $dataBytesLong bytes" }
        val dataBytes = dataBytesLong.toInt()
        val byteRate = sampleRate * channels * 2
        val header = ByteArray(44)
        val w = WavHeaderWriter(header)
        w.ascii("RIFF")
        w.int32(36 + dataBytes)
        w.ascii("WAVE")
        w.ascii("fmt ")
        w.int32(16)
        w.int16(1) // PCM
        w.int16(channels)
        w.int32(sampleRate)
        w.int32(byteRate)
        w.int16(channels * 2)
        w.int16(16)
        w.ascii("data")
        w.int32(dataBytes)
        raf.write(header)
    }

    private class WavHeaderWriter(private val bytes: ByteArray) {
        var pos = 0

        fun ascii(s: String) {
            for (c in s) bytes[pos++] = c.code.toByte()
        }

        fun int32(v: Int) {
            bytes[pos++] = (v and 0xFF).toByte()
            bytes[pos++] = ((v shr 8) and 0xFF).toByte()
            bytes[pos++] = ((v shr 16) and 0xFF).toByte()
            bytes[pos++] = ((v shr 24) and 0xFF).toByte()
        }

        fun int16(v: Int) {
            bytes[pos++] = (v and 0xFF).toByte()
            bytes[pos++] = ((v shr 8) and 0xFF).toByte()
        }
    }
}
