package io.github.kurohi.akachannoise

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kurohi.akachannoise.data.codec.MixCodec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.ui.theme.AkachanNoiseTheme

/**
 * Single activity. Everything else is Compose; playback lives in the
 * Media3 session service so sound keeps going with the screen off.
 */
class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer
    private var sharedMix by mutableStateOf<MixSpec?>(null)
    private var pendingPlayMixId by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = AppGraph.get(this)
        sharedMix = parseShareIntent(intent)
        pendingPlayMixId = intent?.getStringExtra(EXTRA_PLAY_MIX_ID)

        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            AkachanNoiseTheme(
                themeMode = settings.theme,
                dynamicColor = settings.dynamicColor,
            ) {
                AkachanNoiseApp(
                    container = container,
                    sharedMix = sharedMix,
                    onSharedMixHandled = { sharedMix = null },
                )
            }
            // The playback notification is the only notification we need;
            // ask once, after onboarding, and never block anything on it.
            LaunchedEffect(settings.onboardingDone) {
                if (settings.onboardingDone) requestNotificationPermissionIfNeeded()
            }

            // Widgets, tiles and shortcuts can ask us to start a mix.
            LaunchedEffect(pendingPlayMixId) {
                val id = pendingPlayMixId ?: return@LaunchedEffect
                pendingPlayMixId = null
                container.mixRepository.mixById(id)?.let { container.mixEditor.playMix(it) }
            }
            LaunchedEffect(intent?.action) {
                if (intent?.action == ACTION_PLAY) {
                    setIntent(intent.apply { action = null })
                    container.mixEditor.play()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseShareIntent(intent)?.let { sharedMix = it }
        intent.getStringExtra(EXTRA_PLAY_MIX_ID)?.let { pendingPlayMixId = it }
    }

    companion object {
        /** Tile tap on Android 14+: the app opens and starts playing. */
        const val ACTION_PLAY = "io.github.kurohi.akachannoise.action.PLAY"

        /** Shortcut extra: start this saved mix straight away. */
        const val EXTRA_PLAY_MIX_ID = "play_mix_id"
    }

    private fun parseShareIntent(intent: Intent?): MixSpec? {
        val data = intent?.data ?: return null
        if (data.scheme != MixCodec.SHARE_SCHEME) return null
        return MixCodec.mixFromShareUri(data.toString())
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
