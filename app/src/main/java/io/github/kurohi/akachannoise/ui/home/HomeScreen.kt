package io.github.kurohi.akachannoise.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kurohi.akachannoise.AppContainer
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.ui.components.MasterVolumeRow
import io.github.kurohi.akachannoise.ui.components.MixCard
import io.github.kurohi.akachannoise.ui.components.MixIconStrip
import io.github.kurohi.akachannoise.ui.components.PlayPauseButton
import io.github.kurohi.akachannoise.ui.components.SectionTitle
import io.github.kurohi.akachannoise.ui.components.TimerChip
import io.github.kurohi.akachannoise.ui.mixDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenSounds: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenTimer: () -> Unit,
    onPlayMix: (MixSpec) -> Unit,
) {
    val editor = container.mixEditor
    val mix by editor.mix.collectAsStateWithLifecycle()
    val playback by editor.playbackState.collectAsStateWithLifecycle()
    val favorites by container.mixRepository.favoriteMixes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_home)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings),
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NowPlayingCard(mix, playing = playback.playing)

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PlayPauseButton(
                    playing = playback.playing,
                    onClick = { editor.togglePlayPause() },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimerChip(remainingMs = playback.timerRemainingMs, onClick = onOpenTimer)
                if (playback.timerRemainingMs != null) {
                    TextButton(onClick = { editor.addTimerMinutes(15) }) {
                        Text(stringResource(R.string.timer_add_15))
                    }
                }
            }

            if (playback.sootheToSettle) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AssistChip(
                        onClick = onOpenTimer,
                        label = { Text(stringResource(R.string.home_soothe_settle)) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            MasterVolumeRow(
                value = mix.masterVolume,
                onValueChange = editor::setMasterVolume,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenSounds,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.home_edit_sounds))
                }
                OutlinedButton(
                    onClick = onOpenSleep,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.Nightlight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.home_sleep_screen))
                }
            }

            if (favorites.isNotEmpty()) {
                SectionTitle(stringResource(R.string.nav_mixes))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(favorites, key = { it.id }) { favorite ->
                        Box(Modifier.fillParentMaxWidth(0.85f)) {
                            MixCard(
                                mix = favorite,
                                playing = playback.playing && playback.currentMix?.id == favorite.id,
                                favorite = true,
                                onPlay = { onPlayMix(favorite) },
                                onToggleFavorite = { editor.setFavorite(favorite.id, false) },
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.home_no_favorites),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NowPlayingCard(mix: MixSpec, playing: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (playing) {
                    stringResource(R.string.home_now_playing)
                } else {
                    stringResource(R.string.home_ready)
                },
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = mixDisplayName(mix.id, mix.name),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
            if (mix.layers.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_empty_mix),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MixIconStrip(mix)
                    Text(
                        text = pluralStringResource(
                            R.plurals.home_layers,
                            mix.layers.size,
                            mix.layers.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
