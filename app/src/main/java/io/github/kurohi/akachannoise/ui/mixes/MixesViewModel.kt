package io.github.kurohi.akachannoise.ui.mixes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kurohi.akachannoise.data.repo.MixRepository
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.ui.mix.MixEditor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Library screen logic: play, favourite, rename, reorder, delete with undo. */
class MixesViewModel(
    private val mixes: MixRepository,
    private val editor: MixEditor,
) : ViewModel() {

    val presets: List<MixSpec> = mixes.presets

    val userMixes: StateFlow<List<MixSpec>> = mixes.userMixes

    val favoriteIds: StateFlow<List<String>> = mixes.favoriteIds

    val playingMixId: StateFlow<String?> = editor.playbackState
        .map { if (it.playing) it.currentMix?.id else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _lastDeleted = MutableStateFlow<MixSpec?>(null)
    val lastDeleted: StateFlow<MixSpec?> = _lastDeleted.asStateFlow()

    fun play(mix: MixSpec) = editor.playMix(mix)

    fun toggleFavorite(mix: MixSpec) {
        val favorite = mix.id !in mixes.favoriteIds.value
        viewModelScope.launch { mixes.setFavorite(mix.id, favorite) }
    }

    fun delete(mix: MixSpec) {
        viewModelScope.launch {
            _lastDeleted.value = mix
            mixes.deleteMix(mix.id)
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            _lastDeleted.value?.let { mixes.saveMix(it) }
            _lastDeleted.value = null
        }
    }

    fun clearUndo() {
        _lastDeleted.value = null
    }

    fun duplicate(mix: MixSpec) {
        viewModelScope.launch { mixes.duplicateMix(mix.id) }
    }

    fun rename(mix: MixSpec, name: String) {
        viewModelScope.launch { mixes.renameMix(mix.id, name) }
    }

    fun move(mix: MixSpec, delta: Int) {
        viewModelScope.launch {
            val current = mixes.userMixes.value.map { it.id }
            val index = current.indexOf(mix.id)
            val target = index + delta
            if (index < 0 || target < 0 || target >= current.size) return@launch
            val reordered = current.toMutableList().apply {
                removeAt(index)
                add(target, mix.id)
            }
            mixes.reorderMixes(reordered)
        }
    }

    /** Starts a fresh, empty mix and loads it for editing. */
    fun newMix(name: String): MixSpec {
        val mix = MixSpec(id = MixRepository.newMixId(), name = name)
        editor.load(mix)
        return mix
    }
}
