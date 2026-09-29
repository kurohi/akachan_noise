package io.github.kurohi.akachannoise.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.kurohi.akachannoise.data.model.AppSettings
import io.github.kurohi.akachannoise.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persistence for [AppSettings]; interface keeps repositories testable. */
interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/**
 * The preferences keys and the pure mapping in both directions. Kept
 * separate from DataStore so defaults and clamping can be unit tested.
 */
object SettingsPreferences {

    private val DEFAULT_TIMER_MINUTES = intPreferencesKey("default_timer_minutes")
    private val TIMER_FADE_SECONDS = intPreferencesKey("timer_fade_seconds")
    private val STEP_DOWN_ENABLED = booleanPreferencesKey("step_down_enabled")
    private val FADE_IN_MS = intPreferencesKey("fade_in_ms")
    private val VOLUME_CAP = floatPreferencesKey("volume_cap")
    private val MONO = booleanPreferencesKey("mono")
    private val WARMTH = floatPreferencesKey("warmth")
    private val THEME = stringPreferencesKey("theme")
    private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val CRY_RESTART_ENABLED = booleanPreferencesKey("cry_restart_enabled")
    private val CRY_SENSITIVITY = floatPreferencesKey("cry_sensitivity")
    private val CRY_WINDOW_HOURS = intPreferencesKey("cry_window_hours")
    private val CRY_RESTART_MINUTES = intPreferencesKey("cry_restart_minutes")
    private val PAUSE_ON_HEADSET_DISCONNECT = booleanPreferencesKey("pause_on_headset_disconnect")
    private val PLAY_WITH_OTHER_APPS = booleanPreferencesKey("play_with_other_apps")
    private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    fun read(preferences: Preferences): AppSettings {
        val d = AppSettings.DEFAULTS
        return AppSettings(
            defaultTimerMinutes = (preferences[DEFAULT_TIMER_MINUTES] ?: d.defaultTimerMinutes)
                .coerceIn(0, 12 * 60),
            timerFadeSeconds = (preferences[TIMER_FADE_SECONDS] ?: d.timerFadeSeconds)
                .coerceIn(0, 120),
            stepDownEnabled = preferences[STEP_DOWN_ENABLED] ?: d.stepDownEnabled,
            fadeInMs = (preferences[FADE_IN_MS] ?: d.fadeInMs).coerceIn(0, 10_000),
            volumeCap = (preferences[VOLUME_CAP] ?: d.volumeCap).coerceIn(0.1f, 1f),
            mono = preferences[MONO] ?: d.mono,
            warmth = (preferences[WARMTH] ?: d.warmth).coerceIn(0f, 1f),
            theme = preferences[THEME]?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
            } ?: d.theme,
            dynamicColor = preferences[DYNAMIC_COLOR] ?: d.dynamicColor,
            cryRestartEnabled = preferences[CRY_RESTART_ENABLED] ?: d.cryRestartEnabled,
            crySensitivity = (preferences[CRY_SENSITIVITY] ?: d.crySensitivity).coerceIn(0f, 1f),
            cryWindowHours = (preferences[CRY_WINDOW_HOURS] ?: d.cryWindowHours).coerceIn(1, 12),
            cryRestartMinutes = (preferences[CRY_RESTART_MINUTES] ?: d.cryRestartMinutes)
                .coerceIn(1, 60),
            pauseOnHeadsetDisconnect =
            preferences[PAUSE_ON_HEADSET_DISCONNECT] ?: d.pauseOnHeadsetDisconnect,
            playWithOtherApps = preferences[PLAY_WITH_OTHER_APPS] ?: d.playWithOtherApps,
            keepScreenOn = preferences[KEEP_SCREEN_ON] ?: d.keepScreenOn,
            onboardingDone = preferences[ONBOARDING_DONE] ?: d.onboardingDone,
        )
    }

    fun MutablePreferences.writeSettings(settings: AppSettings) {
        this[DEFAULT_TIMER_MINUTES] = settings.defaultTimerMinutes
        this[TIMER_FADE_SECONDS] = settings.timerFadeSeconds
        this[STEP_DOWN_ENABLED] = settings.stepDownEnabled
        this[FADE_IN_MS] = settings.fadeInMs
        this[VOLUME_CAP] = settings.volumeCap
        this[MONO] = settings.mono
        this[WARMTH] = settings.warmth
        this[THEME] = settings.theme.name
        this[DYNAMIC_COLOR] = settings.dynamicColor
        this[CRY_RESTART_ENABLED] = settings.cryRestartEnabled
        this[CRY_SENSITIVITY] = settings.crySensitivity
        this[CRY_WINDOW_HOURS] = settings.cryWindowHours
        this[CRY_RESTART_MINUTES] = settings.cryRestartMinutes
        this[PAUSE_ON_HEADSET_DISCONNECT] = settings.pauseOnHeadsetDisconnect
        this[PLAY_WITH_OTHER_APPS] = settings.playWithOtherApps
        this[KEEP_SCREEN_ON] = settings.keepScreenOn
        this[ONBOARDING_DONE] = settings.onboardingDone
    }
}

/** DataStore-backed implementation. */
class PreferencesSettingsStore(context: Context) : SettingsStore {

    private val appContext = context.applicationContext

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile(FILE_NAME) },
    )

    override val settings: Flow<AppSettings> = store.data.map(SettingsPreferences::read)

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { preferences ->
            with(SettingsPreferences) {
                preferences.writeSettings(transform(read(preferences)))
            }
        }
    }

    companion object {
        const val FILE_NAME = "settings"
    }
}
