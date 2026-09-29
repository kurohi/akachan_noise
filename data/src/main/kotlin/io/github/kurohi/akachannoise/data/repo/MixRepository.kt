package io.github.kurohi.akachannoise.data.repo

import io.github.kurohi.akachannoise.data.model.CustomSoundRef
import io.github.kurohi.akachannoise.data.model.UserData
import io.github.kurohi.akachannoise.data.store.UserDataStore
import io.github.kurohi.akachannoise.engine.model.BuiltInPresets
import io.github.kurohi.akachannoise.engine.model.MixSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/**
 * Mix library: the built-in presets plus the user's own saved mixes,
 * favorites and the last-used mix. All operations are local-only.
 */
class MixRepository(
    private val store: UserDataStore,
    scope: CoroutineScope,
) {
    /** The whole persisted document (used for backup export). */
    val data: StateFlow<UserData> = store.data.stateIn(scope, SharingStarted.Eagerly, UserData.EMPTY)

    val presets: List<MixSpec> = BuiltInPresets.presets

    val userMixes: StateFlow<List<MixSpec>> = data
        .map { it.mixes }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val favoriteIds: StateFlow<List<String>> = data
        .map { it.favoriteIds }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val lastUsedMixId: StateFlow<String?> = data
        .map { it.lastUsedMixId }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Favorite mixes resolved to specs, in favorite order. */
    val favoriteMixes: StateFlow<List<MixSpec>> = data
        .map { data ->
            data.favoriteIds.mapNotNull { id -> data.mixById(id) }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** All playable mixes: the user's mixes first, then the presets. */
    val allMixes: StateFlow<List<MixSpec>> = data
        .map { data -> data.mixes + presets }
        .stateIn(scope, SharingStarted.Eagerly, presets)

    fun mixById(id: String): MixSpec? = data.value.mixById(id)

    suspend fun saveMix(mix: MixSpec) {
        require(!isPresetId(mix.id)) { "Presets are read-only; duplicate them first" }
        store.update { data ->
            val existing = data.mixes.indexOfFirst { it.id == mix.id }
            val mixes = if (existing >= 0) {
                data.mixes.toMutableList().also { it[existing] = mix }
            } else {
                data.mixes + mix
            }
            data.copy(mixes = mixes)
        }
    }

    suspend fun deleteMix(id: String) {
        store.update { data ->
            data.copy(
                mixes = data.mixes.filterNot { it.id == id },
                favoriteIds = data.favoriteIds - id,
                lastUsedMixId = data.lastUsedMixId.takeIf { it != id },
            )
        }
    }

    /** Copies [id] (preset or user mix) into a new editable user mix. */
    suspend fun duplicateMix(id: String, name: String? = null): MixSpec? {
        val source = mixById(id) ?: return null
        val copy = source.copy(
            id = newMixId(),
            name = name ?: uniqueName(source.name),
        )
        saveMix(copy)
        return copy
    }

    suspend fun renameMix(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || isPresetId(id)) return
        store.update { data ->
            data.copy(
                mixes = data.mixes.map { if (it.id == id) it.copy(name = trimmed) else it },
            )
        }
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        store.update { data ->
            val ids = when {
                favorite && id !in data.favoriteIds -> data.favoriteIds + id
                !favorite -> data.favoriteIds - id
                else -> data.favoriteIds
            }
            data.copy(favoriteIds = ids)
        }
    }

    /** Moves favorites into the given order (ids not listed keep their place). */
    suspend fun reorderFavorites(orderedIds: List<String>) {
        store.update { data ->
            val known = data.favoriteIds
            val reordered = orderedIds.filter { it in known } + known.filterNot { it in orderedIds }
            data.copy(favoriteIds = reordered)
        }
    }

    suspend fun reorderMixes(orderedIds: List<String>) {
        store.update { data ->
            val byId = data.mixes.associateBy { it.id }
            val ordered = orderedIds.mapNotNull { byId[it] }
            val rest = data.mixes.filterNot { it.id in orderedIds }
            data.copy(mixes = ordered + rest)
        }
    }

    suspend fun setLastUsed(id: String) {
        store.update { it.copy(lastUsedMixId = id) }
    }

    // ---- Custom sounds ----------------------------------------------------

    suspend fun addCustomSound(sound: CustomSoundRef) {
        store.update { data ->
            data.copy(customSounds = data.customSounds + sound)
        }
    }

    suspend fun updateCustomSound(id: String, transform: (CustomSoundRef) -> CustomSoundRef) {
        store.update { data ->
            data.copy(
                customSounds = data.customSounds.map {
                    if (it.id == id) transform(it) else it
                },
            )
        }
    }

    /**
     * Removes a custom sound and any layer that used it, so saved mixes never
     * point at a file that is gone.
     */
    suspend fun removeCustomSound(id: String) {
        store.update { data ->
            data.copy(
                customSounds = data.customSounds.filterNot { it.id == id },
                mixes = data.mixes.map { mix ->
                    mix.copy(layers = mix.layers.filterNot { it.soundId == id })
                },
            )
        }
    }

    /**
     * Adds [mix] as a new user mix, always with a fresh id so imports never
     * overwrite anything the user already has.
     */
    suspend fun importMix(mix: MixSpec): MixSpec {
        val imported = mix.copy(id = newMixId(), name = uniqueName(mix.name))
        saveMix(imported)
        return imported
    }

    /** Replaces everything (backup restore). */
    suspend fun replaceAll(data: UserData) {
        store.update { data }
    }

    suspend fun clearAll() {
        store.update { UserData.EMPTY }
    }

    /** A name not used by any preset or saved mix ("Womb" → "Womb (2)"). */
    fun uniqueMixName(base: String): String = uniqueName(base)

    private fun uniqueName(base: String): String {
        val existing = data.value.mixes.map { it.name }.toSet() +
            presets.map { it.name }.toSet()
        if (base !in existing) return base
        var index = 2
        while ("$base ($index)" in existing) index++
        return "$base ($index)"
    }

    companion object {
        const val PRESET_ID_PREFIX = "preset."
        const val USER_ID_PREFIX = "user."

        fun isPresetId(id: String): Boolean = id.startsWith(PRESET_ID_PREFIX)

        fun newMixId(): String = USER_ID_PREFIX + UUID.randomUUID()
    }
}

private fun UserData.mixById(id: String): MixSpec? = mixes.firstOrNull { it.id == id } ?: BuiltInPresets.byId(id)
