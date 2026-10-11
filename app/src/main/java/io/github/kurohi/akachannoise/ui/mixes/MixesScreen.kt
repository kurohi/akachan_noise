package io.github.kurohi.akachannoise.ui.mixes

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kurohi.akachannoise.AppContainer
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.data.codec.MixCodec
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.ui.components.MixCard
import io.github.kurohi.akachannoise.ui.components.SectionTitle
import io.github.kurohi.akachannoise.ui.mixDisplayName
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixesScreen(
    container: AppContainer,
    onPlayMix: (MixSpec) -> Unit,
    onMessage: (String) -> Unit,
    onUndoable: (message: String, actionLabel: String, onUndo: () -> Unit) -> Unit,
    onNewMix: () -> Unit,
) {
    val vm: MixesViewModel = viewModel(
        initializer = { MixesViewModel(container.mixRepository, container.mixEditor) },
    )
    val userMixes by vm.userMixes.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val playingId by vm.playingMixId.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<MixSpec?>(null) }
    var shareTarget by remember { mutableStateOf<MixSpec?>(null) }
    var showImport by remember { mutableStateOf(false) }
    val newMixName = stringResource(R.string.mixes_new)
    val deletedMessage = stringResource(R.string.mix_delete_undo)
    val undoLabel = stringResource(R.string.undo)
    val importedMessage = stringResource(R.string.mix_imported)
    val invalidMessage = stringResource(R.string.mix_import_invalid)

    // The play button of the mix that is playing pauses it.
    fun togglePlay(mix: MixSpec) = if (playingId == mix.id) vm.pause() else vm.play(mix)

    fun delete(mix: MixSpec) {
        vm.delete(mix)
        onUndoable(deletedMessage, undoLabel) { vm.undoDelete() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mixes_title)) },
                actions = {
                    IconButton(onClick = { showImport = true }) {
                        Icon(
                            Icons.Filled.AddLink,
                            contentDescription = stringResource(R.string.mix_import_title),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    // An empty mix is only useful with sounds in it: go and
                    // pick them.
                    vm.newMix(newMixName)
                    onNewMix()
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.mixes_new)) },
            )
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
                    onPlay = { togglePlay(preset) },
                    onToggleFavorite = { vm.toggleFavorite(preset) },
                    trailing = {
                        MixOverflowMenu(
                            favorite = preset.id in favoriteIds,
                            onToggleFavorite = { vm.toggleFavorite(preset) },
                            onDuplicate = { vm.duplicate(preset) },
                            onShare = { shareTarget = preset },
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
                itemsIndexed(userMixes, key = { _, mix -> mix.id }) { index, mix ->
                    MixCard(
                        mix = mix,
                        playing = playingId == mix.id,
                        favorite = mix.id in favoriteIds,
                        onPlay = { togglePlay(mix) },
                        onToggleFavorite = { vm.toggleFavorite(mix) },
                        modifier = Modifier.animateItem(),
                        trailing = {
                            MixOverflowMenu(
                                favorite = mix.id in favoriteIds,
                                onToggleFavorite = { vm.toggleFavorite(mix) },
                                onDuplicate = { vm.duplicate(mix) },
                                onShare = { shareTarget = mix },
                                onRename = { renameTarget = mix },
                                // Only offer moves that can actually happen.
                                onMoveUp = if (index > 0) ({ vm.move(mix, -1) }) else null,
                                onMoveDown = if (index < userMixes.lastIndex) ({ vm.move(mix, 1) }) else null,
                                onDelete = { delete(mix) },
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

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                showImport = false
                vm.importShared(text) { ok -> onMessage(if (ok) importedMessage else invalidMessage) }
            },
        )
    }
}

/**
 * Secondary actions for a mix. Play and favourite already have their own
 * buttons on the card, so they are not repeated here; edit actions are
 * passed only for the user's own mixes.
 */
@Composable
private fun MixOverflowMenu(
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onRename: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mix_share)) },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = {
                    expanded = false
                    onShare()
                },
            )
            onRename?.let { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mix_rename)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            onMoveUp?.let { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mix_move_up)) },
                    leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            onMoveDown?.let { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mix_move_down)) },
                    leadingIcon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            onDelete?.let { action ->
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mix_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = {
                        expanded = false
                        action()
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
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // A link rather than a bare code: tapping it opens the app straight to
    // the import, and it can also be pasted into "Add a shared mix".
    val link = remember(mix) { MixCodec.shareUri(mix) }
    val title = mixDisplayName(mix.id, mix.name)
    val chooserTitle = stringResource(R.string.mix_share_title)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chooserTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.mix_share_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, title)
                        .putExtra(Intent.EXTRA_TEXT, link)
                    context.startActivity(Intent.createChooser(send, chooserTitle))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.mix_share_send))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(chooserTitle, link)))
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.mix_share_copy))
            }
        },
    )
}

/** Paste a share link (or the bare code inside it) to add someone's mix. */
@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mix_import_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.mix_import_hint)) },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onImport(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.mix_import_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
