package io.github.kurohi.akachannoise.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.BlurLinear
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dry
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.kurohi.akachannoise.engine.model.SoundCategory
import io.github.kurohi.akachannoise.engine.model.SoundId

/** Icons for every built-in sound and category. */
object SoundIcons {

    fun forSound(soundId: String): ImageVector = when (SoundId.fromId(soundId)) {
        SoundId.HEARTBEAT -> Icons.Filled.MonitorHeart
        SoundId.BLOOD_FLOW -> Icons.Filled.Bloodtype
        SoundId.WOMB_RUMBLE -> Icons.Filled.BubbleChart
        SoundId.NOISE_WHITE -> Icons.Filled.BlurOn
        SoundId.NOISE_PINK -> Icons.Filled.BlurLinear
        SoundId.NOISE_BROWN -> Icons.Filled.BlurCircular
        SoundId.OCEAN -> Icons.Filled.Waves
        SoundId.RAIN -> Icons.Filled.WaterDrop
        SoundId.STREAM -> Icons.Filled.Water
        SoundId.WIND -> Icons.Filled.Air
        SoundId.FAN -> Icons.Filled.Toys
        SoundId.HAIR_DRYER -> Icons.Filled.Dry
        SoundId.VACUUM -> Icons.Filled.CleaningServices
        SoundId.CAR_RIDE -> Icons.Filled.DirectionsCar
        SoundId.PLASTIC_BAG -> Icons.Filled.ShoppingBag
        SoundId.SHUSH -> Icons.Filled.GraphicEq
        SoundId.CUSTOM, null -> Icons.Filled.LibraryMusic
    }

    fun forCategory(category: SoundCategory): ImageVector = when (category) {
        SoundCategory.WOMB -> Icons.Filled.Favorite
        SoundCategory.NOISE -> Icons.Filled.BlurOn
        SoundCategory.NATURE -> Icons.Filled.Park
        SoundCategory.HOME -> Icons.Filled.Home
        SoundCategory.VOICE -> Icons.Filled.RecordVoiceOver
        SoundCategory.CUSTOM -> Icons.Filled.LibraryMusic
    }
}
