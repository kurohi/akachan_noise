package io.github.kurohi.akachannoise.data.model

/** Theme selection; Night-red is a true-black dim theme for 3 AM use. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, NIGHT_RED }

/**
 * User preferences. Everything here is local; nothing is ever uploaded.
 * Defaults are the conservative, sleep-friendly choices.
 */
data class AppSettings(
    val defaultTimerMinutes: Int = 60,
    val timerFadeSeconds: Int = 5,
    val stepDownEnabled: Boolean = true,
    val fadeInMs: Int = 800,
    /** Master volume ceiling, 0.2-1.0, for quieter nights. */
    val volumeCap: Float = 1f,
    val mono: Boolean = false,
    val warmth: Float = 0f,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val cryRestartEnabled: Boolean = false,
    val crySensitivity: Float = 0.5f,
    val cryWindowHours: Int = 4,
    val cryRestartMinutes: Int = 10,
    val pauseOnHeadsetDisconnect: Boolean = true,
    val playWithOtherApps: Boolean = false,
    val keepScreenOn: Boolean = false,
    val onboardingDone: Boolean = false,
    val lastMixId: String? = null,
) {
    companion object {
        val DEFAULTS = AppSettings()
    }
}
