package io.github.kurohi.akachannoise.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kurohi.akachannoise.AppContainer
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.playback.CryListener
import kotlin.math.roundToInt

/**
 * Live check that the detector hears a cry in this room. The microphone is
 * open only while this screen is visible; nothing is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryTestScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(CryListener.hasPermission(context)) }
    var listener by remember { mutableStateOf<CryListener?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result }

    // (Re)start the listener whenever permission or sensitivity changes; the
    // previous one must be released or the microphone would stay open.
    LaunchedEffect(granted, settings.crySensitivity) {
        listener?.stop()
        listener = null
        if (!granted) return@LaunchedEffect
        val created = CryListener(settings.crySensitivity) { }
        if (created.start()) listener = created
    }

    DisposableEffect(Unit) {
        onDispose { listener?.stop() }
    }

    val level by (listener?.level ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0f) })
        .collectAsStateWithLifecycle()
    val pitch by (listener?.pitchHz ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0f) })
        .collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cry_test_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.cry_test_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!granted) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.cry_test_needs_permission))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                            Text(stringResource(R.string.cry_test_grant))
                        }
                    }
                }
                return@Column
            }

            Text(
                stringResource(R.string.cry_test_level),
                style = MaterialTheme.typography.titleSmall,
            )
            LinearProgressIndicator(
                progress = { level },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (pitch > 0f) {
                    stringResource(R.string.cry_test_pitch, pitch.roundToInt())
                } else {
                    stringResource(R.string.cry_test_no_pitch)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null)
                    Text(
                        stringResource(R.string.cry_test_listening),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.cry_test_detected),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
