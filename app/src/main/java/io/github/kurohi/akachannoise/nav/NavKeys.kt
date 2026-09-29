package io.github.kurohi.akachannoise.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Top-level destinations. */
@Serializable
data object HomeKey : NavKey

@Serializable
data object SoundsKey : NavKey

@Serializable
data object MixesKey : NavKey

/** Pushed destinations. */
@Serializable
data object SettingsKey : NavKey

@Serializable
data object RecorderKey : NavKey

@Serializable
data object SleepKey : NavKey

@Serializable
data object SafetyKey : NavKey

@Serializable
data object PrivacyKey : NavKey

@Serializable
data object OnboardingKey : NavKey
