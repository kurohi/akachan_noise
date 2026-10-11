package io.github.kurohi.akachannoise.ui.mix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
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
import io.github.kurohi.akachannoise.engine.generators.ShushGenerator
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
    // The user's own defaults always get a chip, even when they are not one
    // of the presets (otherwise nothing would look selected).
    val minuteOptions = remember(settings.defaultTimerMinutes) {
        (DURATION_OPTIONS + settings.defaultTimerMinutes).filter { it > 0 }.distinct().sorted()
    }
    val fadeOptions = remember(settings.timerFadeSeconds) {
        (FADE_OPTIONS + settings.timerFadeSeconds).distinct().sorted()
    }

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

            // Wraps instead of running off the edge on narrow phones.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                minuteOptions.forEach { option ->
                    FilterChip(
                        selected = minutes == option,
                        onClick = { minutes = option },
                        label = { Text(stringResource(R.string.timer_minutes, option)) },
                    )
                }
                FilterChip(
                    selected = minutes == 0,
                    onClick = { minutes = 0 },
                    label = { Text(stringResource(R.string.timer_off)) },
                )
            }

            SectionTitle(stringResource(R.string.timer_fade_out))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fadeOptions.forEach { option ->
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
            // One primary action that says exactly what will happen.
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
                    when {
                        minutes > 0 -> stringResource(R.string.timer_start, minutes)
                        playback.timerRemainingMs != null -> stringResource(R.string.timer_cancel)
                        else -> stringResource(R.string.timer_keep_playing)
                    },
                )
            }
        }
    }
}

private val DURATION_OPTIONS = listOf(15, 30, 45, 60, 90, 120)
private val FADE_OPTIONS = listOf(0, 5, 10, 30)

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

            if (mix.layers.isEmpty()) {
                Text(
                    stringResource(R.string.mixer_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
                var filter by remember(soundId) {
                    mutableFloatStateOf(layer?.params?.get(SampleLoop.PARAM_WOMB_FILTER) ?: custom.wombFilter)
                }
                Slider(
                    value = filter,
                    onValueChange = { value ->
                        filter = value
                        editor.setLayerParam(soundId, SampleLoop.PARAM_WOMB_FILTER, value)
                    },
                    // Persist once per gesture, not on every drag frame.
                    onValueChangeFinished = {
                        scope.launch { container.customSounds.setWombFilter(soundId, filter) }
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

                // Destructive, so it looks destructive and asks first.
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.custom_sound_delete))
                }
                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text(stringResource(R.string.custom_sound_delete)) },
                        text = { Text(stringResource(R.string.custom_sound_delete_confirm, custom.name)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    confirmDelete = false
                                    // Out of the playing mix first, so the
                                    // layer fades instead of lingering.
                                    editor.removeSound(soundId)
                                    scope.launch {
                                        container.customSounds.delete(soundId)
                                        onDismiss()
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(R.string.custom_sound_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDelete = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        },
                    )
                }
            } else if (layer == null || spec == null) {
                Text(stringResource(R.string.sound_not_in_mix))
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
                            // Shush rhythm has three settings (continuous,
                            // slow, fast): snap instead of a fake continuum.
                            steps = if (param.id == ShushGenerator.PARAM_RHYTHM) 1 else 0,
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
