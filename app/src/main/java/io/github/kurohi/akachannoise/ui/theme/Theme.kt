package io.github.kurohi.akachannoise.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.kurohi.akachannoise.data.model.ThemeMode

private val DayScheme = lightColorScheme(
    primary = DayPrimary,
    onPrimary = DayOnPrimary,
    primaryContainer = DayPrimaryContainer,
    onPrimaryContainer = DayOnPrimaryContainer,
    secondary = DaySecondary,
    onSecondary = DayOnSecondary,
    secondaryContainer = DaySecondaryContainer,
    onSecondaryContainer = DayOnSecondaryContainer,
    tertiary = DayTertiary,
    onTertiary = DayOnTertiary,
    background = DayBackground,
    onBackground = DayOnBackground,
    surface = DaySurface,
    onSurface = DayOnSurface,
    surfaceVariant = DaySurfaceVariant,
    onSurfaceVariant = DayOnSurfaceVariant,
    outline = DayOutline,
)

private val NightScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    primaryContainer = NightPrimaryContainer,
    onPrimaryContainer = NightOnPrimaryContainer,
    secondary = NightSecondary,
    onSecondary = NightOnSecondary,
    secondaryContainer = NightSecondaryContainer,
    onSecondaryContainer = NightOnSecondaryContainer,
    tertiary = NightTertiary,
    onTertiary = NightOnTertiary,
    background = NightBackground,
    onBackground = NightOnBackground,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightOnSurfaceVariant,
    outline = NightOutline,
)

private val NightRedScheme = darkColorScheme(
    primary = NightRedPrimary,
    onPrimary = NightRedOnPrimary,
    primaryContainer = NightRedPrimaryContainer,
    onPrimaryContainer = NightRedOnPrimaryContainer,
    secondary = NightRedSecondary,
    onSecondary = NightRedOnSecondary,
    secondaryContainer = NightRedSecondaryContainer,
    onSecondaryContainer = NightRedOnSecondaryContainer,
    tertiary = NightRedTertiary,
    onTertiary = NightRedOnTertiary,
    background = NightRedBackground,
    onBackground = NightRedOnBackground,
    surface = NightRedSurface,
    onSurface = NightRedOnSurface,
    surfaceVariant = NightRedSurfaceVariant,
    onSurfaceVariant = NightRedOnSurfaceVariant,
    outline = NightRedOutline,
)

/** Soft, rounded shapes — calm and friendly without being toy-like. */
val AkachanShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
)

/**
 * App theme. [themeMode] follows the user's setting; dynamic color is
 * available on Android 12+ but always optional (and off for Night-red,
 * which must stay red to protect night vision).
 */
@Composable
fun AkachanNoiseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when (themeMode) {
        ThemeMode.NIGHT_RED -> NightRedScheme

        ThemeMode.LIGHT -> if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
            dynamicLightColorScheme(context)
        } else {
            DayScheme
        }

        ThemeMode.DARK -> if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
            dynamicDarkColorScheme(context)
        } else {
            NightScheme
        }

        ThemeMode.SYSTEM -> when {
            dynamicColor && Build.VERSION.SDK_INT >= 31 ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            dark -> NightScheme

            else -> DayScheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AkachanShapes,
        content = content,
    )
}
