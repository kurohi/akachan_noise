package io.github.kurohi.akachannoise.ui.mixes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kurohi.akachannoise.AppContainer
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.data.codec.MixCodec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.ui.components.MixCard
import io.github.kurohi.akachannoise.ui.components.SectionTitle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixesScreen(
    container: AppContainer,
    onPlayMix: (MixSpec) -> Unit,
    onMessage: (String) -> Unit,
) {
    val vm: MixesViewModel = viewModel(
        initializer = { MixesViewModel(container.mixRepository, container.mixEditor) },
    )
    val userMixes by vm.userMixes.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val playingId by vm.playingMixId.collectAsStateWithLifecycle()
    val lastDeleted by vm.lastDeleted.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<MixSpec?>(null) }
    var shareTarget by remember { mutableStateOf<MixSpec?>(null) }
    val newMixName = stringResource(R.string.mixes_new)

    LaunchedEffect(lastDeleted) {
        if (lastDeleted != null) {
            delay(6_000)
            vm.clearUndo()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.mixes_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    vm.newMix(newMixName)
                    onMessage(newMixName)
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.mixes_new)) },
            )
        },
        bottomBar = {
            lastDeleted?.let { deleted ->
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.mix_delete_undo),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        TextButton(onClick = { vm.undoDelete() }) {
                            Text(stringResource(R.string.undo))
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.mixes_presets)) }
            items(vm.presets, key = { it.id }) { preset ->
                MixCard(
                    mix = preset,
                    playing = playingId == preset.id,
                    favorite = preset.id in favoriteIds,
                    onPlay = { vm.play(preset) },
                    onToggleFavorite = { vm.toggleFavorite(preset) },
                    trailing = {
                        MixOverflowMenu(
                            mix = preset,
                            favorite = preset.id in favoriteIds,
                            canEdit = false,
                            onPlay = { vm.play(preset) },
                            onToggleFavorite = { vm.toggleFavorite(preset) },
                            onDuplicate = { vm.duplicate(preset) },
                            onRename = { renameTarget = preset },
                            onShare = { shareTarget = preset },
                            onMove = { delta -> vm.move(preset, delta) },
                            onDelete = { vm.delete(preset) },
                        )
                    },
                )
            }

            item { SectionTitle(stringResource(R.string.mixes_mine)) }

            if (userMixes.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.mixes_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.mixes_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(userMixes, key = { it.id }) { mix ->
                    MixCard(
                        mix = mix,
                        playing = playingId == mix.id,
                        favorite = mix.id in favoriteIds,
                        onPlay = { vm.play(mix) },
                        onToggleFavorite = { vm.toggleFavorite(mix) },
                        trailing = {
                            MixOverflowMenu(
                                mix = mix,
                                favorite = mix.id in favoriteIds,
                                canEdit = true,
                                onPlay = { vm.play(mix) },
                                onToggleFavorite = { vm.toggleFavorite(mix) },
                                onDuplicate = { vm.duplicate(mix) },
                                onRename = { renameTarget = mix },
                                onShare = { shareTarget = mix },
                                onMove = { delta -> vm.move(mix, delta) },
                                onDelete = { vm.delete(mix) },
                            )
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initial = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                vm.rename(target, name)
                renameTarget = null
            },
        )
    }

    shareTarget?.let { target ->
        ShareDialog(mix = target, onDismiss = { shareTarget = null })
    }
}

@Composable
private fun MixOverflowMenu(
    mix: MixSpec,
    favorite: Boolean,
    canEdit: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mix_play)) },
                onClick = {
                    expanded = false
                    onPlay()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (favorite) R.string.mix_unfavorite else R.string.mix_favorite,
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        if (favorite) Icons.Filled.StarBorder else Icons.Filled.Star,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onToggleFavorite()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mix_duplicate)) },
                onClick = {
                    expanded = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mix_share)) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                onClick = {
                    expanded = false
                    onShare()
                },
            )
            if (canEdit) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mix_rename)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mix_move_up)) },
                    leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onMove(-1)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mix_move_down)) },
                    leadingIcon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onMove(1)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mix_delete)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mix_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.mix_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ShareDialog(mix: MixSpec, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var code by remember(mix.id) { mutableStateOf("") }
    LaunchedEffect(mix.id) { code = MixCodec.encodeShareCode(mix) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mix_share_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.mix_share_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.mix_share_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
