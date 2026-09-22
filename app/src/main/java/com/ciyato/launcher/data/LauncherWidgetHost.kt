package com.ciyato.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The launcher's one AppWidgetHost, owned by the process rather than by a screen.
 *
 * It used to be created inside WidgetHostScreen's composition and torn down in
 * that screen's `onDispose` (F-138). That is the wrong owner in a launcher:
 * `stopListening()` severs the channel the system uses to push widget updates,
 * so a clock stopped ticking and a calendar stopped refreshing the instant the
 * person left the management screen — which is almost always. Widgets only
 * updated while you were looking at the screen that manages them, and never
 * while you were looking at Home, where they are supposed to live.
 *
 * Ownership is a reference count rather than a single screen's lifecycle
 * because both Home and the manager host views, often at the same moment (the
 * manager opens over a live Home). A count means leaving the manager does not
 * stop updates that Home is still relying on, while closing the last host view
 * still stops listening — an idle launcher should not hold that channel open.
 *
 * [HOST_ID] must never change. AppWidget IDs are allocated against a host ID,
 * so a new one would orphan every widget anyone has already placed, and the old
 * IDs would stay allocated in the system with no host willing to claim them.
 */
object LauncherWidgetHost {

    const val HOST_ID = 1001

    private var host: AppWidgetHost? = null
    private var listeners = 0

    @Synchronized
    fun host(context: Context): AppWidgetHost =
        host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }

    /**
     * Declares that something on screen is hosting widget views.
     *
     * Wrapped because `startListening()` reaches into system_server and has
     * been observed to throw there; a launcher that crashes on start because a
     * widget service is unhappy is worse than one whose widgets update late.
     */
    @Synchronized
    fun retain(context: Context) {
        val current = host(context)
        if (listeners == 0) runCatching { current.startListening() }
        listeners++
    }

    @Synchronized
    fun release() {
        if (listeners <= 0) return
        listeners--
        if (listeners == 0) runCatching { host?.stopListening() }
    }

    /** True while the host is connected — used by tests and diagnostics. */
    @Synchronized
    fun isListening(): Boolean = listeners > 0

    /**
     * Gives an allocated ID back.
     *
     * Every removal path funnels through here. An AppWidget ID that is dropped
     * from storage without being deallocated stays allocated in the system for
     * the life of the install, and nothing in the app can ever reach it again.
     */
    @Synchronized
    fun deleteAppWidgetId(context: Context, appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        runCatching { host(context).deleteAppWidgetId(appWidgetId) }
    }

    /**
     * Builds the hosted view for a widget.
     *
     * Null rather than a throw when the provider has gone away: a widget whose
     * app was uninstalled must not be able to take Home down with it.
     */
    fun createView(
        context: Context,
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
    ): AppWidgetHostView? = runCatching {
        host(context).createView(context.applicationContext, appWidgetId, info)
    }.getOrNull()
}

/**
 * Keeps the shared host listening for as long as this composable is on screen.
 *
 * The retain/release pair is the whole point: it is what lets Home and the
 * manager both host widgets without either one's disposal silencing the other.
 */
@Composable
fun rememberLauncherWidgetHost(): AppWidgetHost {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val host = remember(appContext) { LauncherWidgetHost.host(appContext) }
    DisposableEffect(appContext) {
        LauncherWidgetHost.retain(appContext)
        onDispose { LauncherWidgetHost.release() }
    }
    return host
}
