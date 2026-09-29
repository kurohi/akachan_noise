package io.github.kurohi.akachannoise.ui.recorder

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kurohi.akachannoise.AppContainer
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.data.model.CustomSoundRef
import io.github.kurohi.akachannoise.engine.generators.SampleLoop
import io.github.kurohi.akachannoise.engine.model.LayerSpec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.ui.components.SectionTitle
import io.github.kurohi.akachannoise.ui.components.formatRemaining
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Records a sound with the microphone. The take is processed into the sound
 * library immediately so it can be previewed with the womb filter, and is
 * deleted again if the user discards it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editor = container.mixEditor
    val customSounds = container.customSounds
    val level by customSounds.recorder.level.collectAsStateWithLifecycle()

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var takeFile by remember { mutableStateOf<java.io.File?>(null) }
    var take by remember { mutableStateOf<CustomSoundRef?>(null) }
    var name by remember { mutableStateOf("") }
    var wombFilter by remember { mutableStateOf(0f) }
    var previewing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var noAudio by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    val savedMessage = stringResource(R.string.recorder_saved)
    val failedMessage = stringResource(R.string.recorder_failed)
    val noAudioMessage = stringResource(R.string.recorder_no_audio)

    // Ticking clock while recording, with the five minute cap.
    LaunchedEffect(recording) {
        while (recording) {
            delay(200)
            elapsedMs += 200
            if (elapsedMs >= 5 * 60 * 1000L) recording = false
        }
    }

    // Leaving the screen stops everything; a take that was never saved is
    // deleted so it does not silently end up in the sound library.
    DisposableEffect(Unit) {
        onDispose {
            customSounds.recorder.cancel()
            if (previewing) editor.pause()
            take?.takeIf { !saved }?.let { customSounds.deleteAsync(it.id) }
        }
    }

    fun startRecording() {
        val file = customSounds.newTakeFile()
        takeFile = file
        elapsedMs = 0
        error = false
        recording = customSounds.recorder.start(file)
        if (!recording) error = true
    }

    fun stopAndProcess() {
        recording = false
        customSounds.recorder.stop()
        val file = takeFile ?: return
        busy = true
        scope.launch {
            val ref = customSounds.saveRecording(file, name.ifBlank { "My sound" })
            busy = false
            if (ref == null) {
                // The microphone opened but the take held no signal.
                noAudio = true
            } else {
                take = ref
                takeFile = null
                name = ref.name
                // Load it for preview through the normal playback path.
                editor.load(
                    MixSpec(
                        id = ref.id,
                        name = ref.name,
                        layers = listOf(
                            LayerSpec(
                                soundId = ref.id,
                                volume = 0.9f,
                                params = mapOf(SampleLoop.PARAM_WOMB_FILTER to wombFilter),
                            ),
                        ),
                    ),
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recorder_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.recorder_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Column {
                        Text(
                            stringResource(R.string.recorder_mic_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.recorder_mic_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.recorder_duration, formatRemaining(elapsedMs)),
                style = MaterialTheme.typography.headlineSmall,
            )

            LinearProgressIndicator(
                progress = { level },
                modifier = Modifier.fillMaxWidth(),
            )

            if (take == null) {
                if (!permissionGranted) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text(stringResource(R.string.recorder_start))
                    }
                } else {
                    FilledIconButton(
                        onClick = { if (recording) stopAndProcess() else startRecording() },
                        modifier = Modifier.size(96.dp),
                        enabled = !busy,
                    ) {
                        Icon(
                            imageVector = if (recording) {
                                Icons.Filled.Stop
                            } else {
                                Icons.Filled.FiberManualRecord
                            },
                            contentDescription = stringResource(
                                if (recording) R.string.recorder_stop else R.string.recorder_start,
                            ),
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }

            if (error || noAudio) {
                Text(
                    text = if (error) failedMessage else noAudioMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            take?.let { ref ->
                SectionTitle(stringResource(R.string.custom_sound_womb_filter))
                Text(
                    stringResource(R.string.custom_sound_womb_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = wombFilter,
                    onValueChange = { value ->
                        wombFilter = value
                        editor.setLayerParam(ref.id, SampleLoop.PARAM_WOMB_FILTER, value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(
                    onClick = {
                        if (previewing) {
                            editor.pause()
                        } else {
                            editor.play()
                        }
                        previewing = !previewing
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (previewing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(
                            if (previewing) R.string.recorder_stop_preview else R.string.recorder_preview,
                        ),
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.recorder_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            editor.pause()
                            saved = true
                            scope.launch {
                                customSounds.delete(ref.id)
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.recorder_discard))
                    }
                    Button(
                        onClick = {
                            editor.pause()
                            saved = true
                            scope.launch {
                                customSounds.rename(ref.id, name)
                                customSounds.setWombFilter(ref.id, wombFilter)
                                onSaved(savedMessage)
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.recorder_save))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
