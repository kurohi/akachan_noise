package io.github.kurohi.akachannoise.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kurohi.akachannoise.data.codec.MixCodec
import io.github.kurohi.akachannoise.data.model.AppSettings
import io.github.kurohi.akachannoise.data.model.ThemeMode
import io.github.kurohi.akachannoise.data.repo.MixRepository
import io.github.kurohi.akachannoise.data.repo.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val mixes: MixRepository,
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settings

    fun setTimerMinutes(minutes: Int) = update { settings.setTimerMinutes(minutes) }

    fun setTimerFadeSeconds(seconds: Int) = update { settings.setTimerFadeSeconds(seconds) }

    fun setStepDown(enabled: Boolean) = update { settings.setStepDownEnabled(enabled) }

    fun setVolumeCap(cap: Float) = update { settings.setVolumeCap(cap) }

    fun setTheme(theme: ThemeMode) = update { settings.setTheme(theme) }

    fun setDynamicColor(enabled: Boolean) = update { settings.setDynamicColor(enabled) }

    fun setMono(mono: Boolean) = update { settings.setMono(mono) }

    /** Serializes everything for a manual backup file. */
    fun exportBackup(): String = MixCodec.encodeBackup(mixes.data.value)

    fun importBackup(text: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val parsed = MixCodec.decodeBackup(text)
            if (parsed == null) {
                onResult(false)
            } else {
                mixes.replaceAll(parsed)
                onResult(true)
            }
        }
    }

    fun deleteEverything() {
        viewModelScope.launch { mixes.clearAll() }
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
