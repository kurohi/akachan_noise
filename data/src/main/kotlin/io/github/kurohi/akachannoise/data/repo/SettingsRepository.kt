package io.github.kurohi.akachannoise.data.repo

import io.github.kurohi.akachannoise.data.model.AppSettings
import io.github.kurohi.akachannoise.data.model.ThemeMode
import io.github.kurohi.akachannoise.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Local-only user preferences. */
class SettingsRepository(
    private val store: SettingsStore,
    scope: CoroutineScope,
) {
    val settings: StateFlow<AppSettings> = store.settings
        .stateIn(scope, SharingStarted.Eagerly, AppSettings.DEFAULTS)

    suspend fun update(transform: (AppSettings) -> AppSettings) = store.update(transform)

    suspend fun setTimerMinutes(minutes: Int) = update { it.copy(defaultTimerMinutes = minutes.coerceIn(0, 12 * 60)) }

    suspend fun setTimerFadeSeconds(seconds: Int) = update { it.copy(timerFadeSeconds = seconds.coerceIn(0, 120)) }

    suspend fun setStepDownEnabled(enabled: Boolean) = update { it.copy(stepDownEnabled = enabled) }

    suspend fun setVolumeCap(cap: Float) = update { it.copy(volumeCap = cap.coerceIn(0.1f, 1f)) }

    suspend fun setTheme(theme: ThemeMode) = update { it.copy(theme = theme) }

    suspend fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    suspend fun setMono(mono: Boolean) = update { it.copy(mono = mono) }

    suspend fun setWarmth(warmth: Float) = update { it.copy(warmth = warmth.coerceIn(0f, 1f)) }

    suspend fun setCryRestartEnabled(enabled: Boolean) = update { it.copy(cryRestartEnabled = enabled) }

    suspend fun setCrySensitivity(value: Float) = update { it.copy(crySensitivity = value.coerceIn(0f, 1f)) }

    suspend fun setCryWindowHours(hours: Int) = update { it.copy(cryWindowHours = hours.coerceIn(1, 12)) }

    suspend fun setCryRestartMinutes(minutes: Int) = update { it.copy(cryRestartMinutes = minutes.coerceIn(1, 60)) }

    suspend fun setOnboardingDone(done: Boolean) = update { it.copy(onboardingDone = done) }
}
