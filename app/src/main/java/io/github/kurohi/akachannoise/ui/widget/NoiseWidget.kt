package io.github.kurohi.akachannoise.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.kurohi.akachannoise.AppGraph
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.playback.PlaybackService
import io.github.kurohi.akachannoise.ui.mixDisplayNameFor

/**
 * Home-screen widget: the current mix, a play/pause button and, on the
 * taller size, the first two favourites. Taps go straight to the playback
 * controller through the visible app process; the widget itself only reads
 * local state.
 */
class NoiseWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL_SIZE, TALL_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = AppGraph.get(context)
        val state = container.playback.state.value
        val favorites = container.mixRepository.favoriteMixes.value.take(2)

        // Glance's composition is not Compose, so strings are resolved here.
        val mixName = state.currentMix?.let {
            mixDisplayNameFor(context, it.id, it.name)
        } ?: context.getString(R.string.app_name)
        val subtitle = state.timerRemainingMs?.let {
            io.github.kurohi.akachannoise.ui.components.formatRemaining(it)
        } ?: run {
            val layers = state.currentMix?.layers?.size ?: 0
            context.resources.getQuantityString(R.plurals.home_layers, layers, layers)
        }
        val favoriteLabels = favorites.map { mixDisplayNameFor(context, it.id, it.name) }
        val favoriteIds = favorites.map { it.id }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    playing = state.playing,
                    mixName = mixName,
                    subtitle = subtitle,
                    playLabel = context.getString(R.string.widget_play),
                    pauseLabel = context.getString(R.string.widget_pause),
                    favoriteLabels = favoriteLabels,
                    favoriteIds = favoriteIds,
                )
            }
        }
    }

    companion object {
        val SMALL_SIZE = DpSize(180.dp, 60.dp)
        val TALL_SIZE = DpSize(180.dp, 150.dp)

        /** Refreshes every widget instance after a state change. */
        suspend fun refresh(context: Context) {
            NoiseWidget().updateAll(context)
        }
    }
}

@Composable
private fun WidgetContent(
    playing: Boolean,
    mixName: String,
    subtitle: String,
    playLabel: String,
    pauseLabel: String,
    favoriteLabels: List<String>,
    favoriteIds: List<String>,
) {
    val size = LocalSize.current
    val showFavorites = size.height >= NoiseWidget.TALL_SIZE.height && favoriteLabels.isNotEmpty()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = mixName,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                    maxLines = 1,
                )
            }
            Image(
                provider = ImageProvider(
                    if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                ),
                contentDescription = if (playing) pauseLabel else playLabel,
                modifier = GlanceModifier
                    .size(44.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .clickable(actionRunCallback<TogglePlaybackAction>()),
            )
        }

        if (showFavorites) {
            Spacer(GlanceModifier.size(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                favoriteLabels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        modifier = GlanceModifier
                            .padding(end = 6.dp)
                            .background(GlanceTheme.colors.secondaryContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable(
                                actionRunCallback<PlayMixAction>(
                                    actionParametersOf(MixIdKey to favoriteIds[index]),
                                ),
                            ),
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.onSecondaryContainer,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val MixIdKey = ActionParameters.Key<String>("mixId")

/** Play/pause from the widget. A widget tap may start a foreground service. */
class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val container = AppGraph.get(context)
        if (container.playback.state.value.playing) {
            container.mixEditor.pause()
        } else {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java),
            )
            container.mixEditor.play()
        }
        NoiseWidget.refresh(context)
    }
}

/** Starts a favourite mix from the widget. */
class PlayMixAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val mixId = parameters[MixIdKey] ?: return
        val container = AppGraph.get(context)
        val mix = container.mixRepository.mixById(mixId) ?: return
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlaybackService::class.java),
        )
        container.mixEditor.playMix(mix)
        NoiseWidget.refresh(context)
    }
}

class NoiseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoiseWidget()
}
