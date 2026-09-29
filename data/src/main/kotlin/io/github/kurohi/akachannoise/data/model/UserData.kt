package io.github.kurohi.akachannoise.data.model

import io.github.kurohi.akachannoise.engine.model.MixSpec
import kotlinx.serialization.Serializable

/**
 * Everything the app persists about the user's own content. Stored as a
 * single versioned JSON document on the device; the same schema is used for
 * manual export/import, so backups stay readable.
 */
@Serializable
data class UserData(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val mixes: List<MixSpec> = emptyList(),
    val favoriteIds: List<String> = emptyList(),
    val lastUsedMixId: String? = null,
    val customSounds: List<CustomSoundRef> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val EMPTY = UserData()
    }
}

/**
 * A user-imported or recorded sound. The audio itself lives as a raw PCM
 * file in app-private storage; this is only the reference.
 */
@Serializable
data class CustomSoundRef(
    val id: String,
    val name: String,
    val fileName: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    /** 0 = off, 1 = fully "heard from inside" muffling. */
    val wombFilter: Float = 0f,
    val loopCrossfadeMs: Int = 0,
    val createdAt: Long = 0L,
)
