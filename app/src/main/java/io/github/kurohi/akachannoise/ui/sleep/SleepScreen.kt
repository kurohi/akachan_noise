package io.github.kurohi.akachannoise.ui.sleep

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kurohi.akachannoise.AppContainer
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.ui.components.formatRemaining
import io.github.kurohi.akachannoise.ui.mixDisplayName
import kotlinx.coroutines.delay

private val SleepBackground = Color(0xFF000000)
private val SleepAccent = Color(0xFFB4463A)
private val SleepDim = Color(0xFF8E5446)
private val SleepTrack = Color(0xFF2A0F09)

/**
 * The 3 AM screen: true black, dim red, no haptics, optional touch lock and
 * auto-dimmed brightness. Deliberately independent of the app theme so it is
 * always night-safe.
 */
@Composable
fun SleepScreen(
    container: AppContainer,
    onExit: () -> Unit,
    onOpenTimer: () -> Unit,
) {
    val editor = container.mixEditor
    val playback by editor.playbackState.collectAsStateWithLifecycle()
    val mix by editor.mix.collectAsStateWithLifecycle()

    var locked by remember { mutableStateOf(false) }
    var interactionCount by remember { mutableStateOf(0) }
    var dimmed by remember { mutableStateOf(false) }

    val activity = LocalContext.current.findActivity()

    DisposableEffect(activity) {
        val window = activity?.window
        val previous = window?.attributes?.screenBrightness
        onDispose {
            if (window != null && previous != null) {
                window.attributes = window.attributes.apply { screenBrightness = previous }
            }
        }
    }

    // Dim the screen after a quiet minute, restore on any interaction.
    LaunchedEffect(interactionCount) {
        dimmed = false
        if (activity != null) {
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        delay(AUTO_DIM_MS)
        dimmed = true
        if (activity != null) {
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = MIN_BRIGHTNESS
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepBackground)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    interactionCount++
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        interactionCount++
                        onExit()
                    },
                    enabled = !locked,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = SleepDim,
                    )
                }
                Text(
                    text = mixDisplayName(mix.id, mix.name),
                    color = SleepDim,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
                TextButton(onClick = { onOpenTimer() }, enabled = !locked) {
                    Text(stringResource(R.string.timer_add_15), color = SleepAccent)
                }
            }

            CountdownRing(
                remainingMs = playback.timerRemainingMs,
                totalMs = playback.timerTotalMs ?: 0L,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                IconButton(
                    onClick = {
                        interactionCount++
                        editor.togglePlayPause()
                    },
                    enabled = !locked,
                    modifier = Modifier.size(88.dp),
                ) {
                    Icon(
                        imageVector = if (playback.playing) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = stringResource(
                            if (playback.playing) R.string.home_pause else R.string.home_play,
                        ),
                        tint = SleepAccent,
                        modifier = Modifier.size(52.dp),
                    )
                }

                TextButton(
                    onClick = {
                        interactionCount++
                        editor.addTimerMinutes(15)
                    },
                    enabled = !locked,
                ) {
                    Text(stringResource(R.string.timer_add_15), color = SleepAccent)
                }

                LongPressLock(
                    locked = locked,
                    onLock = {
                        locked = true
                        interactionCount++
                    },
                    onUnlock = {
                        locked = false
                        interactionCount++
                    },
                )

                Text(
                    text = when {
                        locked -> stringResource(R.string.sleep_touch_lock_hint)
                        else -> stringResource(R.string.sleep_lock_hint)
                    },
                    color = SleepDim,
                    fontSize = 12.sp,
                )
            }
        }

        // While locked, swallow every touch except the lock control itself.
        if (locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            interactionCount++
                        }
                    },
            )
        }
    }
}

@Composable
private fun CountdownRing(remainingMs: Long?, totalMs: Long) {
    val hasTimer = remainingMs != null && totalMs > 0
    val fraction = if (hasTimer) {
        (remainingMs!!.toFloat() / totalMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val stroke = 14f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = SleepTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (hasTimer) {
                drawArc(
                    color = SleepAccent,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remainingMs?.let { formatRemaining(it) }
                    ?: stringResource(R.string.timer_off),
                color = SleepAccent,
                fontSize = if (hasTimer) 40.sp else 22.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = stringResource(R.string.sleep_title),
                color = SleepDim,
                fontSize = 12.sp,
            )
        }
    }
}

/** Tap to lock; hold for two seconds to unlock, with a progress ring. */
@Composable
private fun LongPressLock(locked: Boolean, onLock: () -> Unit, onUnlock: () -> Unit) {
    var pressing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pressing, locked) {
        if (!pressing || !locked) {
            progress = 0f
            return@LaunchedEffect
        }
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < HOLD_MS) {
            progress = ((System.currentTimeMillis() - start) / HOLD_MS.toFloat()).coerceIn(0f, 1f)
            delay(40)
        }
        progress = 1f
        onUnlock()
    }

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(72.dp)) {
            if (progress > 0f) {
                drawArc(
                    color = SleepAccent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 8f, cap = StrokeCap.Round),
                )
            }
        }
        Icon(
            imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = stringResource(
                if (locked) R.string.sleep_unlock_hint else R.string.sleep_lock,
            ),
            tint = SleepDim,
            modifier = Modifier
                .size(36.dp)
                .pointerInput(locked) {
                    if (!locked) {
                        detectTapGestures(onTap = { onLock() })
                        return@pointerInput
                    }
                    awaitEachGesture {
                        awaitFirstDown()
                        pressing = true
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                        pressing = false
                    }
                },
        )
    }
}

private const val AUTO_DIM_MS = 60_000L
private const val HOLD_MS = 2_000L
private const val MIN_BRIGHTNESS = 0.01f

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
