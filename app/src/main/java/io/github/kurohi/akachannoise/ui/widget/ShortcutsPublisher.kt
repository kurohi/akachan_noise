package io.github.kurohi.akachannoise.ui.widget

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import io.github.kurohi.akachannoise.MainActivity
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.engine.model.MixSpec
import io.github.kurohi.akachannoise.ui.mixDisplayNameFor

/**
 * Publishes the user's favourite mixes as long-press app shortcuts, so a
 * favourite is one tap away from the launcher.
 */
object ShortcutsPublisher {

    fun update(context: Context, favorites: List<MixSpec>) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val max = manager.maxShortcutCountPerActivity
        val shortcuts = favorites
            .take(max.coerceAtMost(MAX_SHORTCUTS))
            .map { mix ->
                ShortcutInfo.Builder(context, "mix:${mix.id}")
                    .setShortLabel(mixDisplayNameFor(context, mix.id, mix.name))
                    .setLongLabel(mixDisplayNameFor(context, mix.id, mix.name))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut))
                    .setIntent(
                        Intent(context, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(MainActivity.EXTRA_PLAY_MIX_ID, mix.id),
                    )
                    .build()
            }
        runCatching { manager.dynamicShortcuts = shortcuts }
    }

    private const val MAX_SHORTCUTS = 3
}
