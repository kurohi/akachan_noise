package io.github.kurohi.akachannoise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.nav.HomeKey
import io.github.kurohi.akachannoise.nav.MixesKey
import io.github.kurohi.akachannoise.nav.OnboardingKey
import io.github.kurohi.akachannoise.nav.PrivacyKey
import io.github.kurohi.akachannoise.nav.RecorderKey
import io.github.kurohi.akachannoise.nav.SafetyKey
import io.github.kurohi.akachannoise.nav.SettingsKey
import io.github.kurohi.akachannoise.nav.SoundsKey
import io.github.kurohi.akachannoise.ui.home.HomeScreen
import io.github.kurohi.akachannoise.ui.mix.MixerSheet
import io.github.kurohi.akachannoise.ui.mix.SoundTuneSheet
import io.github.kurohi.akachannoise.ui.mix.TimerSheet
import io.github.kurohi.akachannoise.ui.mixes.MixesScreen
import io.github.kurohi.akachannoise.ui.onboarding.OnboardingScreen
import io.github.kurohi.akachannoise.ui.recorder.RecorderScreen
import io.github.kurohi.akachannoise.ui.settings.AboutScreen
import io.github.kurohi.akachannoise.ui.settings.PrivacyScreen
import io.github.kurohi.akachannoise.ui.settings.SafetyScreen
import io.github.kurohi.akachannoise.ui.settings.SettingsScreen
import io.github.kurohi.akachannoise.ui.sleep.SleepScreen
import io.github.kurohi.akachannoise.ui.sounds.SoundsScreen
import kotlinx.coroutines.launch

private val TOP_LEVEL = setOf<NavKey>(HomeKey, SoundsKey, MixesKey)

/**
 * The app shell: bottom bar / navigation rail for the three top-level
 * destinations, plus the Sleep screen and onboarding as full-screen
 * overlays so nothing dim is ever covered by a navigation bar.
 */
@Composable
fun AkachanNoiseApp(
    container: AppContainer,
    sharedMix: MixSpec?,
    onSharedMixHandled: () -> Unit,
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val editor = container.mixEditor
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack = rememberNavBackStack(HomeKey)

    var sleepVisible by remember { mutableStateOf(false) }
    var timerSheetVisible by remember { mutableStateOf(false) }
    var mixerSheetVisible by remember { mutableStateOf(false) }
    var tuneTarget by remember { mutableStateOf<String?>(null) }

    val importedMessage = stringResource(R.string.mix_imported)

    LaunchedEffect(sharedMix) {
        if (sharedMix != null) {
            container.mixRepository.importMix(sharedMix)
            snackbarHostState.showSnackbar(importedMessage)
            onSharedMixHandled()
        }
    }

    if (!settings.onboardingDone) {
        OnboardingScreen(
            onDone = {
                scope.launch { container.settingsRepository.setOnboardingDone(true) }
            },
        )
        return
    }

    fun selectTab(key: NavKey) {
        // removeLast() would resolve to the Java 21 List method on API 35+
        // and crash on older devices; removeAt is safe everywhere.
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (backStack.last() != key) backStack.add(key)
    }

    val currentKey = backStack.lastOrNull()
    val entryProvider = entryProvider<NavKey> {
        entry(HomeKey) {
            HomeScreen(
                container = container,
                onOpenSettings = { backStack.add(SettingsKey) },
                onOpenSounds = { selectTab(SoundsKey) },
                onOpenSleep = { sleepVisible = true },
                onOpenTimer = { timerSheetVisible = true },
                onPlayMix = { editor.playMix(it) },
            )
        }
        entry(SoundsKey) {
            SoundsScreen(
                container = container,
                onOpenMixer = { mixerSheetVisible = true },
                onOpenTune = { tuneTarget = it },
                onOpenRecorder = { backStack.add(RecorderKey) },
                onSaved = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
        }
        entry(RecorderKey) {
            RecorderScreen(
                container = container,
                onBack = { backStack.removeLastOrNull() },
                onSaved = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
        }
        entry(MixesKey) {
            MixesScreen(
                container = container,
                onPlayMix = { editor.playMix(it) },
                onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
        }
        entry(SettingsKey) {
            SettingsScreen(
                container = container,
                onBack = { backStack.removeLastOrNull() },
                onOpenSafety = { backStack.add(SafetyKey) },
                onOpenPrivacy = { backStack.add(PrivacyKey) },
                onOpenAbout = { backStack.add(OnboardingKey) },
                onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
        }
        entry(SafetyKey) { SafetyScreen(onBack = { backStack.removeLastOrNull() }) }
        entry(PrivacyKey) { PrivacyScreen(onBack = { backStack.removeLastOrNull() }) }
        entry(OnboardingKey) {
            AboutScreen(
                onBack = { backStack.removeLastOrNull() },
                onShowOnboarding = {
                    scope.launch { container.settingsRepository.setOnboardingDone(false) }
                },
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            NavigationSuiteScaffold(
                modifier = Modifier.padding(padding),
                navigationSuiteItems = {
                    item(
                        selected = currentKey == HomeKey,
                        onClick = { selectTab(HomeKey) },
                        icon = { Icon(Icons.Filled.Nightlight, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_home)) },
                    )
                    item(
                        selected = currentKey == SoundsKey,
                        onClick = { selectTab(SoundsKey) },
                        icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_sounds)) },
                    )
                    item(
                        selected = currentKey == MixesKey,
                        onClick = { selectTab(MixesKey) },
                        icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_mixes)) },
                    )
                },
            ) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    entryProvider = entryProvider,
                )
            }
        }

        if (sleepVisible) {
            SleepScreen(
                container = container,
                onExit = { sleepVisible = false },
                onOpenTimer = { timerSheetVisible = true },
            )
        }
    }

    if (timerSheetVisible) {
        TimerSheet(
            container = container,
            onDismiss = { timerSheetVisible = false },
        )
    }

    if (mixerSheetVisible) {
        MixerSheet(
            container = container,
            onDismiss = { mixerSheetVisible = false },
            onTune = { tuneTarget = it },
        )
    }

    tuneTarget?.let { soundId ->
        SoundTuneSheet(
            container = container,
            soundId = soundId,
            onDismiss = { tuneTarget = null },
        )
    }
}
