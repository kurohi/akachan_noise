package io.github.kurohi.akachannoise.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.kurohi.akachannoise.data.model.AppSettings
import io.github.kurohi.akachannoise.data.model.ThemeMode
import io.github.kurohi.akachannoise.data.store.SettingsPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferencesTest {

    @Test
    fun `empty preferences give the defaults`() {
        assertEquals(AppSettings.DEFAULTS, SettingsPreferences.read(preferencesOf()))
    }

    @Test
    fun `every field round trips`() {
        val settings = AppSettings(
            defaultTimerMinutes = 45,
            timerFadeSeconds = 10,
            stepDownEnabled = false,
            fadeInMs = 1500,
            volumeCap = 0.6f,
            mono = true,
            warmth = 0.35f,
            theme = ThemeMode.NIGHT_RED,
            dynamicColor = false,
            cryRestartEnabled = true,
            crySensitivity = 0.8f,
            cryWindowHours = 6,
            cryRestartMinutes = 15,
            pauseOnHeadsetDisconnect = false,
            playWithOtherApps = true,
            keepScreenOn = true,
            onboardingDone = true,
            lastMixId = "preset.womb",
        )
        val prefs = mutablePreferencesOf()
        with(SettingsPreferences) { prefs.writeSettings(settings) }
        assertEquals(settings, SettingsPreferences.read(prefs))
    }

    @Test
    fun `null last mix id is removed not stored`() {
        val prefs = mutablePreferencesOf()
        with(SettingsPreferences) { prefs.writeSettings(AppSettings(lastMixId = "user.1")) }
        with(SettingsPreferences) { prefs.writeSettings(AppSettings(lastMixId = null)) }
        assertEquals(null, SettingsPreferences.read(prefs).lastMixId)
    }

    @Test
    fun `out of range values are clamped on read`() {
        val prefs = preferencesOf(
            intPreferencesKey("default_timer_minutes") to 5000,
            intPreferencesKey("cry_window_hours") to 99,
            stringPreferencesKey("theme") to "NOT_A_THEME",
        )
        val settings = SettingsPreferences.read(prefs)
        assertEquals(12 * 60, settings.defaultTimerMinutes)
        assertEquals(12, settings.cryWindowHours)
        assertEquals(ThemeMode.SYSTEM, settings.theme)
    }

    @Test
    fun `zero timer is allowed and means play forever`() {
        val prefs = preferencesOf(intPreferencesKey("default_timer_minutes") to 0)
        assertEquals(0, SettingsPreferences.read(prefs).defaultTimerMinutes)
    }
}
