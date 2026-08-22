package com.ciyato.launcher

import android.os.Bundle
import android.content.Intent
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.CrashReporter
import com.ciyato.launcher.data.LocationHelper
import com.ciyato.launcher.ui.screens.*
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoTheme
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch

/**
 * LauncherHomeActivity — the REAL home screen.
 *
 * Uses sealed-class navigation for zero-latency screen transitions.
 * Suggestions wired here: 75 (Focus), 139 (Permission Audit), 144 (Crash Reporter), 145 (Screenshot block).
 */
// FragmentActivity (a ComponentActivity subclass), not plain ComponentActivity:
// androidx BiometricPrompt requires a FragmentActivity host. As a bare
// ComponentActivity, every `context as? FragmentActivity` in the app resolved to
// null, which silently disabled App Lock and made both vault screens fail OPEN.
class LauncherHomeActivity : FragmentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private var shortcutRequest by mutableStateOf(LauncherShortcutRequest(sequence = 0L, action = null))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shortcutRequest = LauncherShortcutRequest(sequence = 1L, action = intent?.action)

        // Crash reporter install (Suggestion 144)
        CrashReporter.install(this)
        lifecycleScope.launch {
            viewModel.crashReporting.collect { enabled ->
                CrashReporter.setLoggingEnabled(enabled)
            }
        }

        enableEdgeToEdge(
            statusBarStyle     = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_HOME)
                }
                val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                val isDefault = resolveInfo?.activityInfo?.packageName == packageName
                if (isDefault) {
                    // Standard launcher: back does nothing on home screen
                } else {
                    finish()
                }
            }
        })

        setContent {
            val font by viewModel.font.collectAsState()
            // Ciyato V2 is intentionally a consistent black launcher surface.
            // Do not expose a partial light/dynamic theme over hard-coded dark UI.
            CiyatoTheme(font = font) {
                LauncherRoot(
                    viewModel = viewModel,
                    activity = this@LauncherHomeActivity,
                    shortcutRequest = shortcutRequest,
                )
                // Above everything, so a locked app is gated from every launcher
                // surface without any of them knowing about App Lock.
                com.ciyato.launcher.ui.screens.AppLockHost(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshApps()
    }

    override fun onPause() {
        // Recents, launching another activity, and leaving Home always end a
        // temporary edit/drag/selection state before the launcher loses focus.
        viewModel.cancelLauncherEditing()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val action = intent.action
        // Pressing Android Home reuses this singleTask activity. App shortcuts
        // are explicit Ciyato routes, so they replace Home rather than being
        // discarded by the edit-cancellation event.
        if (!isCiyatoShortcutAction(action)) viewModel.cancelLauncherEditing()
        shortcutRequest = LauncherShortcutRequest(
            sequence = shortcutRequest.sequence + 1L,
            action = action,
        )
    }

}

private data class LauncherShortcutRequest(
    val sequence: Long,
    val action: String?,
)

private fun isCiyatoShortcutAction(action: String?): Boolean = action in setOf(
    "com.ciyato.launcher.ACTION_FOCUS",
    "com.ciyato.launcher.ACTION_PERMISSION_AUDIT",
    "com.ciyato.launcher.ACTION_DRAWER",
)

// ── Navigation sealed class ────────────────────────────────────────────────────

private sealed class LauncherDest {
    object Home               : LauncherDest()
    object Drawer             : LauncherDest()
    object Settings           : LauncherDest()
    object Search             : LauncherDest()
    object ThemeStudio        : LauncherDest()
    object WallpaperStudio    : LauncherDest()
    object HiddenApps         : LauncherDest()
    object LockedApps         : LauncherDest()   // App Lock management
    object RemovedApps        : LauncherDest()
    data class CategoryDetail(val category: AppCategory) : LauncherDest()
    object WeatherDetail      : LauncherDest()
    object Agenda             : LauncherDest()
    object FocusSession       : LauncherDest()   // Suggestion 75
    object PermissionAudit    : LauncherDest()   // Suggestion 139
    object StorageCleanup     : LauncherDest()   // Suggestion 26
    object RecentFiles        : LauncherDest()   // Recent files browser + file tagging
    object ContextualSuggestions : LauncherDest()  // Suggestion 30
    object VoiceCommands      : LauncherDest()   // Suggestion 39
    object AnomalyDetection   : LauncherDest()   // Suggestion 37
    object AiChangelog        : LauncherDest()   // Suggestion 45
    object DataBreachChecker  : LauncherDest()   // Suggestion 85
    object SafeBrowsing       : LauncherDest()   // Suggestion 83
    object SearchHistory      : LauncherDest()   // Suggestion 108
    object StickyNotes        : LauncherDest()   // DataStore-backed quick notes
    object AutoBackup         : LauncherDest()   // Suggestion 67 — photo backup, manual + WorkManager schedule
    object DuplicateShortcuts : LauncherDest()   // Apps placed in more than one smart category
    object WidgetHost         : LauncherDest()   // Suggestion 15 — AppWidgetHost placement
    object Insights           : LauncherDest()   // One entry for everything built on Usage Access
    object AppUsage           : LauncherDest()   // Screen Time — was an orphan (F-154)
}

/**
 * Every destination that carries no arguments, keyed by its own class name.
 *
 * Built as a list rather than a hand-written `when` so a new destination cannot
 * be added and silently left out of state restoration — the only way to miss one
 * is to forget it here, next to the declarations themselves.
 */
private val ARGLESS_DESTS: List<LauncherDest> = listOf(
    LauncherDest.Home, LauncherDest.Drawer, LauncherDest.Settings, LauncherDest.Search,
    LauncherDest.ThemeStudio, LauncherDest.WallpaperStudio, LauncherDest.HiddenApps,
    LauncherDest.LockedApps,
    LauncherDest.RemovedApps, LauncherDest.WeatherDetail, LauncherDest.Agenda,
    LauncherDest.FocusSession, LauncherDest.PermissionAudit, LauncherDest.StorageCleanup,
    LauncherDest.RecentFiles, LauncherDest.ContextualSuggestions,
    LauncherDest.VoiceCommands, LauncherDest.AnomalyDetection, LauncherDest.AiChangelog,
    LauncherDest.DataBreachChecker, LauncherDest.SafeBrowsing, LauncherDest.SearchHistory,
    LauncherDest.StickyNotes, LauncherDest.AutoBackup, LauncherDest.DuplicateShortcuts,
    LauncherDest.WidgetHost, LauncherDest.Insights, LauncherDest.AppUsage,
)

private val DEST_BY_KEY: Map<String, LauncherDest> =
    ARGLESS_DESTS.associateBy { it::class.simpleName.orEmpty() }

private const val CATEGORY_DEST_PREFIX = "category:"

private fun LauncherDest.toKey(): String = when (this) {
    is LauncherDest.CategoryDetail -> CATEGORY_DEST_PREFIX + category.name
    else -> this::class.simpleName.orEmpty()
}

/**
 * Rebuilds a destination from its saved key, falling back to Home.
 *
 * Falling back rather than throwing matters: a key written by an older build may
 * name a destination that no longer exists, and landing on Home is a far better
 * outcome than crashing on launch.
 */
private fun launcherDestFromKey(key: String): LauncherDest {
    if (key.startsWith(CATEGORY_DEST_PREFIX)) {
        val category = runCatching {
            AppCategory.valueOf(key.removePrefix(CATEGORY_DEST_PREFIX))
        }.getOrNull()
        return if (category != null) LauncherDest.CategoryDetail(category) else LauncherDest.Home
    }
    return DEST_BY_KEY[key] ?: LauncherDest.Home
}

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
private fun LauncherRoot(
    viewModel: LauncherViewModel,
    activity: LauncherHomeActivity,
    shortcutRequest: LauncherShortcutRequest,
) {
    val context = LocalContext.current
    // Survives activity recreation.
    //
    // This was a plain `remember`, so any configuration change — rotation, font
    // scale, theme, unfolding a device, or the system recreating the launcher
    // after reclaiming memory — silently threw the person back to Home from
    // wherever they actually were (F-068, F-181). Saved as a String key because
    // LauncherDest is a sealed class and not Parcelable; the key round-trips
    // through the saved-instance bundle.
    var dest by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver(
            save = { value: LauncherDest -> value.toKey() },
            restore = { key: String -> launcherDestFromKey(key) },
        ),
    ) { mutableStateOf<LauncherDest>(LauncherDest.Home) }
    val useSystemWallpaper by viewModel.useSystemWallpaper.collectAsState()

    LaunchedEffect(shortcutRequest.sequence) {
        dest = when (shortcutRequest.action) {
            "com.ciyato.launcher.ACTION_FOCUS" -> LauncherDest.FocusSession
            "com.ciyato.launcher.ACTION_PERMISSION_AUDIT" -> LauncherDest.PermissionAudit
            "com.ciyato.launcher.ACTION_DRAWER" -> LauncherDest.Drawer
            else -> dest
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.exitLauncherEditing.collect {
            dest = LauncherDest.Home
        }
    }

    androidx.activity.compose.BackHandler(enabled = dest != LauncherDest.Home) {
        dest = when (dest) {
            is LauncherDest.PermissionAudit,
            is LauncherDest.StorageCleanup,
            is LauncherDest.RecentFiles,
            is LauncherDest.HiddenApps,
            is LauncherDest.RemovedApps,
            is LauncherDest.ContextualSuggestions,
            is LauncherDest.VoiceCommands,
            is LauncherDest.AnomalyDetection,
            is LauncherDest.AiChangelog,
            is LauncherDest.DataBreachChecker,
            is LauncherDest.SafeBrowsing,
            is LauncherDest.SearchHistory -> LauncherDest.Settings
            is LauncherDest.StickyNotes -> LauncherDest.Settings
            is LauncherDest.AutoBackup -> LauncherDest.Settings
            is LauncherDest.DuplicateShortcuts -> LauncherDest.Settings
            is LauncherDest.WidgetHost -> LauncherDest.Settings
            else -> LauncherDest.Home
        }
    }

    // Auto-fetch weather on startup if already permitted
    LaunchedEffect(Unit) {
        if (LocationHelper.hasPermission(context)) {
            viewModel.fetchWeather(context)
        }
        // Apply screenshot block setting (Suggestion 145)
        viewModel.applyScreenshotFlag(activity.window)
    }

    // Re-apply screenshot flag whenever the setting changes
    val screenshotBlocked by viewModel.screenshotBlocked.collectAsState()
    LaunchedEffect(screenshotBlocked) {
        viewModel.applyScreenshotFlag(activity.window)
    }

    when (val d = dest) {

        is LauncherDest.Home -> HomeScreen(
            viewModel       = viewModel,
            onOpenDrawer    = { dest = LauncherDest.Drawer },
            onOpenSearch    = { dest = LauncherDest.Search },
            onOpenSystemWallpaper = {
                dest = LauncherDest.WallpaperStudio
            },
            onOpenOrganizerSettings = {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_START_DESTINATION, "settings")
                    }
                )
            },
            onCategoryTap   = { category -> dest = LauncherDest.CategoryDetail(category) },
            onWeatherTap    = {
                // The phone's own weather app is the primary target; Ciyato's
                // forecast screen only covers phones without one, or setup.
                val hasWeather = viewModel.weatherState.value is com.ciyato.launcher.data.WeatherRepository.WeatherState.Success
                val weatherPkg = if (hasWeather) {
                    com.ciyato.launcher.data.WeatherRepository.findSystemWeatherPackage(context)
                } else null
                // Launched through the viewmodel so Focus blocking and App Lock
                // apply here as they do everywhere else.
                val opened = weatherPkg != null && viewModel.launchPackage(weatherPkg)
                if (!opened) dest = LauncherDest.WeatherDetail
            },
            onAgendaTap     = { dest = LauncherDest.Agenda },
        )

        is LauncherDest.Drawer -> AppDrawerScreen(
            viewModel       = viewModel,
            onBack          = { dest = LauncherDest.Home },
        )

        is LauncherDest.Settings -> SettingsScreen(
            viewModel                  = viewModel,
            onBack                     = { dest = LauncherDest.Home },
            onNavigateToFiles          = {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_START_DESTINATION, "files")
                    },
                )
            },
            onNavigateToPhotos         = {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_START_DESTINATION, "photos")
                    },
                )
            },
            onNavigateToAgenda         = {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_START_DESTINATION, "agenda")
                    },
                )
            },
            onNavigateToPermissionAudit= { dest = LauncherDest.PermissionAudit },
            onNavigateToStorageCleanup = { dest = LauncherDest.StorageCleanup },
            onNavigateToRecentFiles    = { dest = LauncherDest.RecentFiles },
            onNavigateToFocus          = { dest = LauncherDest.FocusSession },
            onNavigateToTheme          = { dest = LauncherDest.ThemeStudio },
            onNavigateToWallpaper      = { dest = LauncherDest.WallpaperStudio },
            onNavigateToHiddenApps     = { dest = LauncherDest.HiddenApps },
            onNavigateToLockedApps     = { dest = LauncherDest.LockedApps },
            onNavigateToRemovedApps    = { dest = LauncherDest.RemovedApps },
            onNavigateToContextualSuggestions = { dest = LauncherDest.ContextualSuggestions },
            onNavigateToVoiceCommands  = { dest = LauncherDest.VoiceCommands },
            onNavigateToAnomalyDetection = { dest = LauncherDest.AnomalyDetection },
            onNavigateToAiChangelog    = { dest = LauncherDest.AiChangelog },
            onNavigateToDataBreachChecker = { dest = LauncherDest.DataBreachChecker },
            onNavigateToSafeBrowsing   = { dest = LauncherDest.SafeBrowsing },
            onNavigateToSearchHistory  = { dest = LauncherDest.SearchHistory },
            onNavigateToStickyNotes    = { dest = LauncherDest.StickyNotes },
            onNavigateToAutoBackup     = { dest = LauncherDest.AutoBackup },
            onNavigateToDuplicateShortcuts = { dest = LauncherDest.DuplicateShortcuts },
            onNavigateToWidgetHost     = { dest = LauncherDest.WidgetHost },
            onNavigateToInsights       = { dest = LauncherDest.Insights },
        )

        is LauncherDest.AppUsage -> AppUsageStatsScreen(
            viewModel = viewModel,
            onBack = { dest = LauncherDest.Insights },
        )

        is LauncherDest.Insights -> InsightsScreen(
            onBack = { dest = LauncherDest.Settings },
            onOpenScreenTime = { dest = LauncherDest.AppUsage },
            onOpenTodaySummary = { dest = LauncherDest.AiChangelog },
            onOpenSuggestions = { dest = LauncherDest.ContextualSuggestions },
            onOpenAnomalies = { dest = LauncherDest.AnomalyDetection },
        )

        is LauncherDest.Search -> SearchScreen(
            viewModel = viewModel,
            onBack = {
                viewModel.setSearch("")
                dest = LauncherDest.Home
            },
            onCategoryFilter = { category -> dest = LauncherDest.CategoryDetail(category) },
        )

        is LauncherDest.ThemeStudio -> ThemeStudioScreen(
            viewModel = viewModel,
            onBack = { dest = LauncherDest.Home },
            onOpenWallpaper = { dest = LauncherDest.WallpaperStudio },
        )

        is LauncherDest.WallpaperStudio -> WallpaperPickerScreen(
            viewModel = viewModel,
            onBack = { dest = LauncherDest.Home },
        )

        is LauncherDest.HiddenApps -> AppVisibilityScreen(
            mode = AppVisibilityMode.Hidden,
            viewModel = viewModel,
            onBack = { dest = LauncherDest.Settings },
        )

        is LauncherDest.RemovedApps -> AppVisibilityScreen(
            mode = AppVisibilityMode.Removed,
            viewModel = viewModel,
            onBack = { dest = LauncherDest.Settings },
        )

        is LauncherDest.CategoryDetail -> CategoryDetailScreen(
            category  = d.category,
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Home },
        )

        is LauncherDest.WeatherDetail -> WeatherDetailScreen(
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Home },
        )

        is LauncherDest.Agenda -> CalendarAgendaScreen(
            viewModel = viewModel,
            onBack = { dest = LauncherDest.Home },
        )

        is LauncherDest.FocusSession -> FocusSessionScreen(  // Suggestion 75
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Home },
        )

        is LauncherDest.PermissionAudit -> PermissionAuditScreen( // Suggestion 139
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Home },
        )

        is LauncherDest.StorageCleanup -> StorageCleanupScreen( // Suggestion 26
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
        )

        is LauncherDest.LockedApps -> LockedAppsScreen(
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
        )

        is LauncherDest.RecentFiles -> RecentFilesScreen(
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
        )

        is LauncherDest.ContextualSuggestions -> ContextualSuggestionsScreen( // Suggestion 30
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
        )

        is LauncherDest.VoiceCommands -> VoiceCommandScreen( // Suggestion 39
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
            onOpenCategory = { category -> dest = LauncherDest.CategoryDetail(category) },
            onOpenSearch   = {
                viewModel.setSearch(it)
                dest = LauncherDest.Search
            },
        )

        is LauncherDest.AnomalyDetection -> AnomalyDetectionScreen( // Suggestion 37
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
        )

        is LauncherDest.AiChangelog -> AiChangelogScreen( // Suggestion 45
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
        )

        is LauncherDest.DataBreachChecker -> DataBreachCheckerScreen( // Suggestion 85
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
        )

        is LauncherDest.SafeBrowsing -> SafeBrowsingHelperScreen( // Suggestion 83
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
        )

        is LauncherDest.StickyNotes -> StickyNotesScreen(
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
        )

        is LauncherDest.SearchHistory -> SearchHistoryScreen( // Suggestion 108
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
            onQuerySelected = {
                viewModel.setSearch(it)
                dest = LauncherDest.Search
            },
        )

        is LauncherDest.AutoBackup -> AutoBackupScreen( // Suggestion 67
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
        )

        is LauncherDest.DuplicateShortcuts -> DuplicateShortcutsScreen(
            viewModel = viewModel,
            onBack    = { dest = LauncherDest.Settings },
        )

        is LauncherDest.WidgetHost -> WidgetHostScreen( // Suggestion 15
            onBack    = { dest = LauncherDest.Settings },
        )

    }
}
