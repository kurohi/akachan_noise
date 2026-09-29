package io.github.kurohi.akachannoise

import android.content.Context

/**
 * Process-wide access to [AppContainer] for the components that are created
 * by the system rather than by the activity: the home-screen widget, the
 * Quick Settings tile and the shortcut publisher.
 */
object AppGraph {

    @Volatile private var container: AppContainer? = null

    fun get(context: Context): AppContainer {
        val existing = container
        if (existing != null) return existing
        return synchronized(this) {
            container ?: AppContainer(context.applicationContext).also { container = it }
        }
    }
}
