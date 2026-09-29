package io.github.kurohi.akachannoise.ui.mix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kurohi.akachannoise.AppContainer
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.engine.generators.SampleLoop
import io.github.kurohi.akachannoise.ui.components.SectionTitle
import io.github.kurohi.akachannoise.ui.paramLabel
import io.github.kurohi.akachannoise.ui.paramValueLabel
import io.github.kurohi.akachannoise.ui.soundLabel
import kotlinx.coroutines.launch

/** Sleep timer: duration, fade length and the Soothe → Settle step-down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerSheet(container: AppContainer, onDismiss: () -> Unit) {
    val editor = container.mixEditor
    val playback by editor.playbackState.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    var minutes by remember { mutableIntStateOf(settings.defaultTimerMinutes) }
    var fadeSeconds by remember { mutableIntStateOf(settings.timerFadeSeconds) }
    var stepDown by remember { mutableStateOf(settings.stepDownEnabled) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.timer_title), style = MaterialTheme.typography.titleLarge)

            playback.timerRemainingMs?.let { remaining ->
                Text(
                    text = stringResource(
                        R.string.timer_remaining,
                        io.github.kurohi.akachannoise.ui.components.formatRemaining(remaining),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SectionTitle(stringResource(R.string.timer_title))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(15, 30, 45, 60, 90, 120).forEach { option ->
                    FilterChip(
                        selected = minutes == option,
                        onClick = { minutes = option },
                        label = { Text("$option") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = minutes == 0,
                    onClick = { minutes = 0 },
                    label = { Text(stringResource(R.string.timer_off)) },
                )
            }

            SectionTitle(stringResource(R.string.timer_fade_out))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10, 30).forEach { option ->
                    FilterChip(
                        selected = fadeSeconds == option,
                        onClick = { fadeSeconds = option },
                        label = {
                            Text(
                                if (option == 0) {
                                    stringResource(R.string.timer_fade_none)
                                } else {
                                    stringResource(R.string.timer_fade_seconds, option)
                                },
                            )
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.timer_step_down),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.timer_step_down_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = stepDown, onCheckedChange = { stepDown = it })
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (minutes > 0) {
                        editor.startTimer(minutes, fadeSeconds, stepDown)
                    } else {
                        editor.cancelTimer()
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (minutes > 0) {
                        stringResource(R.string.timer_minutes, minutes)
                    } else {
                        stringResource(R.string.timer_cancel)
                    },
                )
            }
            if (playback.timerRemainingMs != null) {
                TextButton(
                    onClick = {
                        editor.cancelTimer()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.timer_cancel))
                }
            }
        }
    }
}

/** All layers of the current mix, with per-sound volume and master controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
    onTune: (String) -> Unit,
) {
    val editor = container.mixEditor
    val mix by editor.mix.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.mixer), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.mixer_layers),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            mix.layers.forEach { layer ->
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = soundLabel(layer.soundId),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onTune(layer.soundId) }) {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = stringResource(R.string.sounds_tune),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = { editor.removeSound(layer.soundId) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.mixer_remove),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Slider(
                        value = layer.volume,
                        onValueChange = { editor.setLayerVolume(layer.soundId, it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionTitle(stringResource(R.string.mixer_warmth))
            Text(
                stringResource(R.string.mixer_warmth_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = mix.warmth,
                onValueChange = editor::setWarmth,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.mixer_mono), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.mixer_mono_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = mix.mono, onCheckedChange = editor::setMono)
            }

            Spacer(Modifier.height(4.dp))
            SectionTitle(stringResource(R.string.home_master_volume))
            Slider(
                value = mix.masterVolume,
                onValueChange = editor::setMasterVolume,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Parameters for one sound in the current mix. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundTuneSheet(container: AppContainer, soundId: String, onDismiss: () -> Unit) {
    val editor = container.mixEditor
    val scope = rememberCoroutineScope()
    val mix by editor.mix.collectAsStateWithLifecycle()
    val customSounds by container.customSounds.sounds.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    val layer = mix.layers.firstOrNull { it.soundId == soundId }
    val spec = io.github.kurohi.akachannoise.engine.model.SoundCatalog.specFor(soundId)
    val custom = customSounds.firstOrNull { it.id == soundId }
    var confirmDelete by remember { mutableStateOf(false) }
    var customName by remember(soundId) { mutableStateOf(custom?.name.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = custom?.name ?: soundLabel(soundId),
                style = MaterialTheme.typography.titleLarge,
            )

            if (custom != null) {
                // A user sound: womb filter, rename and delete.
                SectionTitle(stringResource(R.string.custom_sound_womb_filter))
                Text(
                    stringResource(R.string.custom_sound_womb_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val filter = layer?.params?.get(SampleLoop.PARAM_WOMB_FILTER) ?: custom.wombFilter
                Slider(
                    value = filter,
                    onValueChange = { value ->
                        editor.setLayerParam(soundId, SampleLoop.PARAM_WOMB_FILTER, value)
                        scope.launch { container.customSounds.setWombFilter(soundId, value) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionTitle(stringResource(R.string.custom_sound_rename))
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        scope.launch { container.customSounds.rename(soundId, customName) }
                    },
                    enabled = customName.isNotBlank() && customName != custom.name,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.save))
                }

                layer?.let {
                    SectionTitle(stringResource(R.string.home_master_volume))
                    Slider(
                        value = it.volume,
                        onValueChange = { volume -> editor.setLayerVolume(soundId, volume) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Button(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.custom_sound_delete))
                }
                if (confirmDelete) {
                    Text(
                        stringResource(R.string.custom_sound_delete_confirm, custom.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { confirmDelete = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    container.customSounds.delete(soundId)
                                    onDismiss()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.custom_sound_delete))
                        }
                    }
                }
            } else if (layer == null || spec == null) {
                Text(stringResource(R.string.sounds_active))
            } else {
                spec.params.forEach { param ->
                    val value = layer.params[param.id] ?: param.default
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                paramLabel(param.id),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                paramValueLabel(param, value),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = value,
                            valueRange = param.min..param.max,
                            onValueChange = { editor.setLayerParam(soundId, param.id, it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                SectionTitle(stringResource(R.string.home_master_volume))
                Slider(
                    value = layer.volume,
                    onValueChange = { editor.setLayerVolume(soundId, it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        editor.removeSound(soundId)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.mixer_remove))
                }
            }
        }
    }
}
