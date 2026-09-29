package io.github.kurohi.akachannoise.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kurohi.akachannoise.AppContainer
import io.github.kurohi.akachannoise.BuildConfig
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.data.model.ThemeMode
import io.github.kurohi.akachannoise.playback.CryListener
import io.github.kurohi.akachannoise.ui.components.SectionTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenCryTest: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val vm: SettingsViewModel = viewModel(
        initializer = { SettingsViewModel(container.settingsRepository, container.mixRepository) },
    )
    val settings by vm.appSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(CryListener.hasPermission(context)) }

    val deniedMessage = stringResource(R.string.cry_test_denied)
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        if (granted) vm.setCryRestartEnabled(true) else onMessage(deniedMessage)
    }

    val exportedMessage = stringResource(R.string.settings_exported)
    val importedMessage = stringResource(R.string.settings_imported)
    val importFailedMessage = stringResource(R.string.settings_import_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(vm.exportBackup().encodeToByteArray())
                }
            }.onSuccess { onMessage(exportedMessage) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            if (text == null) {
                onMessage(importFailedMessage)
            } else {
                vm.importBackup(text) { ok ->
                    onMessage(if (ok) importedMessage else importFailedMessage)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
                .padding(horizontal = 20.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_playback))

            SettingSlider(
                title = stringResource(R.string.settings_default_timer),
                hint = stringResource(R.string.settings_default_timer_hint),
                value = settings.defaultTimerMinutes.toFloat(),
                valueRange = 0f..120f,
                steps = 7,
                valueLabel = if (settings.defaultTimerMinutes == 0) {
                    stringResource(R.string.timer_off)
                } else {
                    stringResource(R.string.timer_minutes, settings.defaultTimerMinutes)
                },
                onValueChange = { vm.setTimerMinutes(it.roundToInt()) },
            )

            SettingSlider(
                title = stringResource(R.string.timer_fade_out),
                hint = null,
                value = settings.timerFadeSeconds.toFloat(),
                valueRange = 0f..30f,
                steps = 5,
                valueLabel = if (settings.timerFadeSeconds == 0) {
                    stringResource(R.string.timer_fade_none)
                } else {
                    stringResource(R.string.timer_fade_seconds, settings.timerFadeSeconds)
                },
                onValueChange = { vm.setTimerFadeSeconds(it.roundToInt()) },
            )

            SettingSwitch(
                title = stringResource(R.string.timer_step_down),
                hint = stringResource(R.string.timer_step_down_hint),
                checked = settings.stepDownEnabled,
                onCheckedChange = vm::setStepDown,
            )

            SettingSlider(
                title = stringResource(R.string.settings_volume_cap),
                hint = stringResource(R.string.settings_volume_cap_hint),
                value = settings.volumeCap,
                valueRange = 0.1f..1f,
                valueLabel = "${(settings.volumeCap * 100).roundToInt()}%",
                onValueChange = vm::setVolumeCap,
            )

            SettingSwitch(
                title = stringResource(R.string.mixer_mono),
                hint = stringResource(R.string.mixer_mono_hint),
                checked = settings.mono,
                onCheckedChange = vm::setMono,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_appearance))

            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.theme == mode,
                        onClick = { vm.setTheme(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)

                                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)

                                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)

                                    ThemeMode.NIGHT_RED ->
                                        stringResource(R.string.settings_theme_night_red)
                                },
                            )
                        },
                    )
                }
            }

            SettingSwitch(
                title = stringResource(R.string.settings_dynamic_color),
                hint = stringResource(R.string.settings_dynamic_color_hint),
                checked = settings.dynamicColor,
                onCheckedChange = vm::setDynamicColor,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_cry))

            SettingSwitch(
                title = stringResource(R.string.settings_cry_enable),
                hint = stringResource(R.string.settings_cry_hint),
                checked = settings.cryRestartEnabled && micGranted,
                onCheckedChange = { enabled ->
                    if (enabled && !micGranted) {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        vm.setCryRestartEnabled(enabled)
                    }
                },
            )

            if (settings.cryRestartEnabled && micGranted) {
                SettingSlider(
                    title = stringResource(R.string.settings_cry_sensitivity),
                    hint = stringResource(R.string.settings_cry_sensitivity_hint),
                    value = settings.crySensitivity,
                    valueRange = 0f..1f,
                    valueLabel = "${(settings.crySensitivity * 100).roundToInt()}%",
                    onValueChange = vm::setCrySensitivity,
                )
                SettingSlider(
                    title = stringResource(R.string.settings_cry_window),
                    hint = null,
                    value = settings.cryWindowHours.toFloat(),
                    valueRange = 1f..12f,
                    steps = 10,
                    valueLabel = stringResource(R.string.cry_hours, settings.cryWindowHours),
                    onValueChange = { vm.setCryWindowHours(it.roundToInt()) },
                )
                SettingSlider(
                    title = stringResource(R.string.settings_cry_play_minutes),
                    hint = null,
                    value = settings.cryRestartMinutes.toFloat(),
                    valueRange = 1f..60f,
                    steps = 11,
                    valueLabel = stringResource(R.string.cry_minutes, settings.cryRestartMinutes),
                    onValueChange = { vm.setCryRestartMinutes(it.roundToInt()) },
                )
                SettingClick(
                    title = stringResource(R.string.settings_cry_test),
                    hint = stringResource(R.string.settings_cry_test_hint),
                    onClick = onOpenCryTest,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_data))

            SettingClick(
                title = stringResource(R.string.settings_export),
                hint = stringResource(R.string.settings_export_hint),
                onClick = { exportLauncher.launch("akachan-noise-backup.json") },
            )
            SettingClick(
                title = stringResource(R.string.settings_import),
                hint = stringResource(R.string.settings_import_hint),
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
            )
            SettingClick(
                title = stringResource(R.string.settings_delete_all),
                hint = stringResource(R.string.settings_delete_all_hint),
                onClick = { confirmDelete = true },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_about))

            SettingClick(
                title = stringResource(R.string.settings_safety),
                hint = null,
                onClick = onOpenSafety,
            )
            SettingClick(
                title = stringResource(R.string.settings_privacy),
                hint = null,
                onClick = onOpenPrivacy,
            )
            SettingClick(
                title = stringResource(R.string.settings_about),
                hint = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                onClick = onOpenAbout,
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_all_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteEverything()
                        confirmDelete = false
                    },
                ) {
                    Text(stringResource(R.string.settings_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    hint: String?,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            valueRange = valueRange,
            steps = steps,
            onValueChange = onValueChange,
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    hint: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingClick(title: String, hint: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
