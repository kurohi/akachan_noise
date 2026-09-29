package io.github.kurohi.akachannoise.data.codec

import io.github.kurohi.akachannoise.data.model.UserData
import io.github.kurohi.akachannoise.engine.model.MixSpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Import/export for mixes and backups. Everything is plain text the user can
 * copy, paste or save — there is no server and no account.
 *
 * Share codes are raw-deflated JSON in URL-safe base64, wrapped in an
 * `akachannoise://mix?d=...` link the app can open.
 */
object MixCodec {

    const val SHARE_SCHEME = "akachannoise"
    const val SHARE_HOST = "mix"
    private const val MAX_DECOMPRESSED_BYTES = 1 shl 20
    private const val RAW_STREAM = true

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    // ---- Mixes ------------------------------------------------------------

    fun encodeMixJson(mix: MixSpec): String = prettyJson.encodeToString(MixSpec.serializer(), mix)

    fun decodeMixJson(text: String): MixSpec? = try {
        json.decodeFromString<MixSpec>(text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    fun encodeShareCode(mix: MixSpec): String = base64UrlEncode(deflate(json.encodeToString(MixSpec.serializer(), mix).encodeToByteArray()))

    fun decodeShareCode(code: String): MixSpec? {
        val bytes = base64UrlDecode(code) ?: return null
        val jsonBytes = inflate(bytes) ?: return null
        return try {
            json.decodeFromString<MixSpec>(jsonBytes.decodeToString())
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun shareUri(mix: MixSpec): String = "$SHARE_SCHEME://$SHARE_HOST?d=${encodeShareCode(mix)}"

    fun mixFromShareUri(uri: String): MixSpec? {
        val marker = "d="
        val index = uri.indexOf(marker)
        if (index < 0) return null
        val code = uri.substring(index + marker.length).substringBefore('&')
        if (code.isEmpty()) return null
        return decodeShareCode(code)
    }

    // ---- Backups ----------------------------------------------------------

    fun encodeBackup(data: UserData): String = prettyJson.encodeToString(UserData.serializer(), data)

    fun decodeBackup(text: String): UserData? = try {
        json.decodeFromString<UserData>(text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    // ---- Compression helpers ---------------------------------------------

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, RAW_STREAM)
        try {
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream(input.size)
            val buffer = ByteArray(4096)
            while (!deflater.finished()) {
                val written = deflater.deflate(buffer)
                out.write(buffer, 0, written)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(input: ByteArray): ByteArray? {
        val inflater = Inflater(RAW_STREAM)
        try {
            inflater.setInput(input)
            val out = ByteArrayOutputStream(input.size * 4)
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val written = try {
                    inflater.inflate(buffer)
                } catch (e: java.util.zip.DataFormatException) {
                    return null
                }
                if (written == 0 && inflater.needsInput()) return null
                out.write(buffer, 0, written)
                if (out.size() > MAX_DECOMPRESSED_BYTES) return null
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun base64UrlDecode(text: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        null
    }
}
