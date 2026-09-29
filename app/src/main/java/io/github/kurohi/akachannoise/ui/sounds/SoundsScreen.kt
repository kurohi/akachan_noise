package io.github.kurohi.akachannoise.ui.sounds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kurohi.akachannoise.AppContainer
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.engine.model.SoundCatalog
import io.github.kurohi.akachannoise.engine.model.SoundCategory
import io.github.kurohi.akachannoise.engine.model.SoundSpec
import io.github.kurohi.akachannoise.ui.categoryLabel
import io.github.kurohi.akachannoise.ui.components.SoundTile
import io.github.kurohi.akachannoise.ui.mixDisplayName
import io.github.kurohi.akachannoise.ui.soundLabel
import kotlinx.coroutines.launch

private val BROWSABLE_CATEGORIES = listOf(
    SoundCategory.WOMB,
    SoundCategory.NOISE,
    SoundCategory.NATURE,
    SoundCategory.HOME,
    SoundCategory.VOICE,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsScreen(
    container: AppContainer,
    onOpenMixer: () -> Unit,
    onOpenTune: (String) -> Unit,
    onSaved: (String) -> Unit,
) {
    val editor = container.mixEditor
    val mix by editor.mix.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf<SoundCategory?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val savedMessage = stringResource(R.string.mix_saved)
    val fullMessage = stringResource(R.string.mixer_full)

    val sounds = remember(selectedCategory) {
        SoundCatalog.specs.filter { selectedCategory == null || it.category == selectedCategory }
    }
    val activeIds = mix.layers.map { it.soundId }.toSet()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.sounds_title)) }) },
        bottomBar = {
            MixTray(
                mix = mix,
                onOpenMixer = onOpenMixer,
                onSave = { showSaveDialog = true },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(stringResource(R.string.category_all)) },
                    )
                }
                items(BROWSABLE_CATEGORIES) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(categoryLabel(category)) },
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sounds, key = { it.id.id }) { spec ->
                    SoundTileForSpec(
                        spec = spec,
                        active = spec.id.id in activeIds,
                        onToggle = {
                            if (spec.id.id !in activeIds && mix.layers.size >= MixSpec.MAX_LAYERS) {
                                onSaved(fullMessage)
                            } else {
                                editor.toggleSound(spec.id.id)
                            }
                        },
                        onTune = { onOpenTune(spec.id.id) },
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveMixDialog(
            initialName = mix.name,
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                showSaveDialog = false
                scope.launch {
                    editor.saveCurrent(name)
                    onSaved(savedMessage)
                }
            },
        )
    }
}

@Composable
private fun SoundTileForSpec(
    spec: SoundSpec,
    active: Boolean,
    onToggle: () -> Unit,
    onTune: () -> Unit,
) {
    SoundTile(
        soundId = spec.id.id,
        name = soundLabel(spec.id.id),
        active = active,
        onToggle = onToggle,
        onTune = onTune,
    )
}

/** The docked tray showing what is in the mix right now. */
@Composable
private fun MixTray(mix: MixSpec, onOpenMixer: () -> Unit, onSave: () -> Unit) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    mixDisplayName(mix.id, mix.name),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    text = if (mix.layers.isEmpty()) {
                        stringResource(R.string.home_empty_mix)
                    } else {
                        pluralStringResource(R.plurals.home_layers, mix.layers.size, mix.layers.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onOpenMixer) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.mixer))
            }
            Button(onClick = onSave) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun SaveMixDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_mix_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.mix_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
