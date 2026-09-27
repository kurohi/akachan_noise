package io.github.kurohi.akachannoise

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.engine.model.SoundId
import io.github.kurohi.akachannoise.playback.PlaybackController
import io.github.kurohi.akachannoise.playback.PlaybackService
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * M2 debug screen — temporary until the full UI lands (M5). Verifies:
 * background playback, media notification, sleep timer with fade.
 */
class MainActivity : ComponentActivity() {

    private lateinit var controller: PlaybackController
    private var mediaController: MediaController? = null
    private var controllerConnected by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = PlaybackController.get(this)
        maybeRequestNotificationPermission()
        lifecycleScope.launch { connectMediaController() }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DebugPlaybackScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        mediaController?.release()
        mediaController = null
        super.onDestroy()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private suspend fun connectMediaController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        mediaController = runCatching {
            MediaController.Builder(this, token).buildAsync().await()
        }.getOrNull()
        controllerConnected = mediaController != null
    }

    private fun play() {
        val mix = MixSpec(
            id = "debug",
            name = "Debug White",
            layers = listOf(LayerSpec(soundId = SoundId.NOISE_WHITE.id, volume = 0.8f)),
        )
        controller.prepare(mix)
        mediaController?.play()
    }

    private fun stop() {
        mediaController?.pause()
    }

    @Composable
    private fun DebugPlaybackScreen() {
        val state by controller.state.collectAsStateWithLifecycle()
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name))
            Text(if (state.playing) "playing: ${state.currentMix?.name}" else "idle")
            Button(onClick = { play() }, enabled = controllerConnected) { Text("Play white noise") }
            Button(onClick = { stop() }, enabled = controllerConnected) { Text("Stop") }
            Button(onClick = { controller.setTimer(8_000, fadeOutMs = 3_000, stepDown = false) }) {
                Text("Timer: 8s, 3s fade")
            }
        }
    }
}
