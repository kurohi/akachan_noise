package io.github.kurohi.akachannoise

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.kurohi.akachannoise.data.repo.CustomSoundRepository
import io.github.kurohi.akachannoise.data.repo.MixRepository
import io.github.kurohi.akachannoise.data.repo.SettingsRepository
import io.github.kurohi.akachannoise.data.store.DataStoreUserDataStore
import io.github.kurohi.akachannoise.data.store.PreferencesSettingsStore
import io.github.kurohi.akachannoise.data.store.SettingsStore
import io.github.kurohi.akachannoise.data.store.UserDataStore
import io.github.kurohi.akachannoise.engine.CustomSoundResolver
import io.github.kurohi.akachannoise.playback.PlaybackController
import io.github.kurohi.akachannoise.playback.PlaybackService
import io.github.kurohi.akachannoise.ui.mix.MixEditor
import io.github.kurohi.akachannoise.ui.widget.NoiseWidget
import io.github.kurohi.akachannoise.ui.widget.ShortcutsPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * Manual dependency container — no DI framework, no annotation processing.
 * Everything is local: two DataStore files, the repositories and the
 * in-process playback controller.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val userDataStore: UserDataStore = DataStoreUserDataStore(appContext)
    val settingsStore: SettingsStore = PreferencesSettingsStore(appContext)

    val mixRepository = MixRepository(userDataStore, scope)
    val settingsRepository = SettingsRepository(settingsStore, scope)

    val playback: PlaybackController = PlaybackController.get(appContext)

    val customSounds = CustomSoundRepository(appContext, mixRepository, scope)

    /**
     * Play/pause must go through the Media3 session so the service starts in
     * the foreground and the media notification appears. The controller is
     * built asynchronously at startup and is ready long before anyone taps
     * play; until then the editor falls back to the in-process controller.
     */
    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController: MediaController? get() = _mediaController.value

    val mixEditor = MixEditor(
        playback = playback,
        mixes = mixRepository,
        settings = settingsRepository,
        scope = scope,
        mediaController = { _mediaController.value },
    )

    init {
        // Let the engine play the user's own sounds.
        playback.setCustomSoundResolver(
            CustomSoundResolver { soundId, context, params ->
                customSounds.createGenerator(soundId, context, params)
            },
        )

        // Keep the cry monitor configured from settings; the feature needs
        // the microphone permission, so it stays off until that is granted.
        scope.launch {
            settingsRepository.settings.collect { settings ->
                val granted = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                playback.configureCryRestart(
                    enabled = settings.cryRestartEnabled && granted,
                    sensitivity = settings.crySensitivity,
                    windowMinutes = settings.cryWindowHours * 60,
                    playMinutes = settings.cryRestartMinutes,
                )
            }
        }

        // Keep the home-screen widget and the launcher shortcuts in step with
        // what the app is doing.
        scope.launch {
            mixRepository.favoriteMixes.collect { favorites ->
                ShortcutsPublisher.update(appContext, favorites)
            }
        }
        scope.launch {
            playback.state.collect { NoiseWidget.refresh(appContext) }
        }

        scope.launch {
            val token = SessionToken(
                appContext,
                ComponentName(appContext, PlaybackService::class.java),
            )
            _mediaController.value = runCatching {
                MediaController.Builder(appContext, token).buildAsync().await()
            }.getOrNull()
        }
    }
}
