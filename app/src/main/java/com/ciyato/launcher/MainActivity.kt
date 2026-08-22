package com.ciyato.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.fragment.app.FragmentActivity
import androidx.navigation.navArgument
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.CrashReporter
import com.ciyato.launcher.data.LocationHelper
import com.ciyato.launcher.ui.components.CiyatoBottomNavBar
import com.ciyato.launcher.ui.components.CiyatoNavItem
import com.ciyato.launcher.ui.screens.*
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoTheme
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch

/**
 * MainActivity — dashboard/settings entry point.
 * Launched from the app drawer (LAUNCHER intent-filter).
 *
 * Routes:
 *   onboarding              →  first-run experience
 *   dashboard               →  main control center
 *   files                   →  Ciyato Files (SAF)
 *   photos                  →  Ciyato Photos
 *   search                  →  AI Search
 *   theme                   →  Theme Studio
 *   settings                →  Settings
 *   category_detail/{name}  →  Category detail (real apps)
 *   weather_detail          →  Live weather (Open-Meteo)
 *   agenda                  →  Agenda / Today
 */
// FragmentActivity, not plain ComponentActivity — see LauncherHomeActivity for why
// (androidx BiometricPrompt requires a FragmentActivity host).
class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_START_DESTINATION = "start_destination"

        /**
         * The one place an incoming destination string becomes a route.
         *
         * onCreate and onNewIntent both resolve through this, so a deep link
         * cannot mean one thing on a cold start and another on a warm one —
         * the "share one route contract" half of F-071.
         *
         * Returns null when the caller named nothing, which is the signal to
         * fall back to onboarding state rather than to a route.
         */
        fun routeForDestination(destination: String?): String? = when (destination) {
            "overview", "files", "photos", "search", "settings", "agenda" -> destination
            // Aliases kept for intents created before the rename.
            "home", "dashboard" -> "overview"
            "shared" -> "photos"
            else -> null
        }
    }

    private val viewModel: LauncherViewModel by viewModels()

    /**
     * A destination delivered to an instance that is already running.
     *
     * MainActivity is singleTop, so the launcher's explicit intents for
     * Settings/Files/Photos/Agenda are handed to the existing instance rather
     * than creating a new one. The route was only ever read in onCreate, so
     * those taps silently did nothing whenever the organizer was already open —
     * you pressed Files and stayed where you were (F-182).
     */
    private val redirectDestination = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep getIntent() truthful for anything else that reads it later.
        setIntent(intent)
        redirectDestination.value = intent.getStringExtra(EXTRA_START_DESTINATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        setContent {
            val font by viewModel.font.collectAsState()
            // Provided once here so every decorative animation can consult
            // Reduce Motion without a parameter threaded through the composables
            // in between — the reason seven of nine ignored it (F-167).
            val reduceMotionPref by viewModel.reduceMotion.collectAsState()
            androidx.compose.runtime.CompositionLocalProvider(
                com.ciyato.launcher.ui.theme.LocalReduceMotion provides reduceMotionPref,
            ) {
            CiyatoTheme(font = font) {
                val context           = LocalContext.current
                val onboardingDone by viewModel.onboardingDone.collectAsState()
                val navController     = rememberNavController()
                val requestedDestination = intent.getStringExtra(EXTRA_START_DESTINATION)
                // An explicit destination (app shortcut, Settings deep link) never
                // depends on onboarding state, so it resolves immediately. Only the
                // plain launch has to wait for DataStore.
                // There is exactly one destination named Home in this product
                // now, and it is the launcher workspace rather than this shell
                // (F-071). "home" survives only as an alias so a pinned shortcut
                // or saved intent from before the rename still lands sensibly.
                val startDest: String? = routeForDestination(requestedDestination)
                    ?: onboardingDone?.let { done -> if (done) "overview" else "onboarding" }

                // Don't compose the NavHost until the answer is known.
                //
                // This used to render immediately with onboardingDone's `false`
                // placeholder and then try to correct itself once DataStore
                // resolved. That correction was guarded on
                // `currentDestination?.route == "onboarding"`, but on the first
                // composition currentDestination is still null — the graph hasn't
                // attached. If the stored value arrived in that window the guard
                // matched nothing, and since onboardingDone never changes again the
                // effect never re-ran: an onboarded person was stranded on
                // onboarding every single launch. Waiting one frame removes the race
                // entirely instead of racing to undo it.
                if (startDest == null) {
                    Box(Modifier.fillMaxSize().background(CiyatoBg))
                    return@CiyatoTheme
                }

                // Apply the "Block screenshots" setting on the organizer surface
                // too — previously it only took effect once Settings was opened.
                val screenshotBlocked by viewModel.screenshotBlocked.collectAsState()
                LaunchedEffect(screenshotBlocked) {
                    viewModel.applyScreenshotFlag(this@MainActivity.window)
                }

                // Auto-fetch weather if permission already granted
                LaunchedEffect(Unit) {
                    if (LocationHelper.hasPermission(context)) {
                        viewModel.fetchWeather(context)
                    }
                }

                // NavHost's own back handling auto-disables once there's nothing
                // left to pop (e.g. at a bottom-nav root like Settings), letting
                // this fallback fire. Without it, that back press falls through to
                // the system default — finish() — which destroys every bit of
                // Compose state (scroll position, in-progress edits, which screen
                // you were on). Minimizing instead keeps the task alive, so
                // reopening Ciyato (icon tap or Recents) resumes exactly where
                // you left off instead of resetting to the first tab.
                BackHandler {
                    (context as? android.app.Activity)?.moveTaskToBack(true)
                }

                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val activeRoute = currentBackStackEntry?.destination?.route
                val tabRoutes = listOf("overview", "files", "search", "photos", "settings")
                val tabItems = listOf(
                    // Was Icons.Default.Home / "Home". This tab opens the
                    // organizer's storage-and-categories dashboard, while Android
                    // HOME opens the launcher workspace — two different products
                    // wearing the same name and icon, which is what made the
                    // entry path change what "Home" meant (F-071, F-073).
                    CiyatoNavItem(Icons.Default.Dashboard, "Overview"),
                    CiyatoNavItem(Icons.Default.FolderOpen, "Files"),
                    CiyatoNavItem(Icons.Default.Search, "Search"),
                    CiyatoNavItem(Icons.Default.PhotoLibrary, "Photos"),
                    CiyatoNavItem(Icons.Default.Settings, "Settings"),
                )
                val selectedTab = tabRoutes.indexOf(activeRoute).coerceAtLeast(0)

                // Acts on a destination delivered to an already-running
                // instance. Cleared once consumed so a configuration change does
                // not re-navigate and throw away where the person has since gone.
                val redirect by redirectDestination
                LaunchedEffect(redirect) {
                    val target = routeForDestination(redirect) ?: return@LaunchedEffect
                    navController.navigate(target) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    redirectDestination.value = null
                }

                Scaffold(
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (activeRoute in tabRoutes) {
                            CiyatoBottomNavBar(
                                items = tabItems,
                                selectedIndex = selectedTab,
                                onItemSelected = { index ->
                                    val target = tabRoutes[index]
                                    if (target != activeRoute) {
                                        // Bottom-tab pattern: pop back to the start
                                        // and keep each tab single-instance so the
                                        // back stack can't grow with tab history.
                                        navController.navigate(target) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                            )
                        }
                    },
                ) { contentPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDest,
                        modifier = Modifier.padding(contentPadding),
                    ) {

                    composable("onboarding") {
                        OnboardingScreen(onDone = {
                            viewModel.setOnboardingDone()
                            navController.navigate("overview") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        })
                    }

                    composable("overview") {
                        DashboardScreen(
                            viewModel = viewModel,
                            onOpenFiles = { navController.navigate("files") { launchSingleTop = true } },
                            onOpenPhotos = { navController.navigate("photos") { launchSingleTop = true } },
                            onOpenSearch = { navController.navigate("search") { launchSingleTop = true } },
                            onOpenCleanup = { navController.navigate("storage_cleanup") { launchSingleTop = true } },
                            onOpenCategory = { key ->
                                navController.navigate("file_category/$key") { launchSingleTop = true }
                            },
                        )
                    }

                    composable(
                        route = "file_category/{categoryKey}",
                        arguments = listOf(navArgument("categoryKey") { type = NavType.StringType }),
                    ) { backStack ->
                        FileCategoryScreen(
                            categoryKey = backStack.arguments?.getString("categoryKey") ?: "",
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable("files")   { FilesScreen(viewModel = viewModel, onBack = { navController.popBackStack() }) }
                    composable("photos")  {
                        PhotosLibraryScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenDuplicates = {
                                navController.navigate("photo_duplicates") { launchSingleTop = true }
                            },
                        )
                    }

                    composable("photo_duplicates") {
                        DuplicatePhotoCleanupScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable("search") {
                        NlFileSearchScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable("theme") {
                        ThemeStudioScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenWallpaper = { navController.navigate("wallpaper") },
                        )
                    }

                    composable("wallpaper") {
                        WallpaperPickerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }

                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToFiles = { navController.navigate("files") },
                            onNavigateToPhotos = { navController.navigate("photos") },
                            onNavigateToAgenda = { navController.navigate("agenda") },
                            onNavigateToTheme = { navController.navigate("theme") },
                            onNavigateToWallpaper = { navController.navigate("wallpaper") },
                            onNavigateToHiddenApps = { navController.navigate("hidden_apps") },
                            onNavigateToRemovedApps = { navController.navigate("removed_apps") },
                            onNavigateToPermissionAudit = { navController.navigate("permission_audit") },
                            onNavigateToFocus = { navController.navigate("focus") },
                            // The fourteen below had no route and no callback, so
                            // their Settings rows were enabled and inert (F-072).
                            onNavigateToStorageCleanup = { navController.navigate("storage_cleanup") },
                            onNavigateToLockedApps = { navController.navigate("locked_apps") },
                            onNavigateToRecentFiles = { navController.navigate("recent_files") },
                            onNavigateToContextualSuggestions = { navController.navigate("contextual_suggestions") },
                            onNavigateToVoiceCommands = { navController.navigate("voice_commands") },
                            onNavigateToAnomalyDetection = { navController.navigate("anomaly_detection") },
                            onNavigateToAiChangelog = { navController.navigate("ai_changelog") },
                            onNavigateToDataBreachChecker = { navController.navigate("breach_checker") },
                            onNavigateToSafeBrowsing = { navController.navigate("safe_browsing") },
                            onNavigateToSearchHistory = { navController.navigate("search_history") },
                            onNavigateToStickyNotes = { navController.navigate("sticky_notes") },
                            onNavigateToAutoBackup = { navController.navigate("auto_backup") },
                            onNavigateToDuplicateShortcuts = { navController.navigate("duplicate_shortcuts") },
                            onNavigateToWidgetHost = { navController.navigate("widget_host") },
                            onNavigateToInsights = { navController.navigate("insights") },
                        )
                    }

                    composable("permission_audit") {
                        PermissionAuditScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable("focus") {
                        FocusSessionScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable("hidden_apps") {
                        AppVisibilityScreen(
                            mode = AppVisibilityMode.Hidden,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable("removed_apps") {
                        AppVisibilityScreen(
                            mode = AppVisibilityMode.Removed,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    // ── Functional wiring screens ──────────────────────────────

                    composable(
                        route     = "category_detail/{categoryName}",
                        arguments = listOf(navArgument("categoryName") { type = NavType.StringType }),
                    ) { backStack ->
                        val name     = backStack.arguments?.getString("categoryName") ?: ""
                        val category = runCatching { AppCategory.valueOf(name) }.getOrNull()
                        if (category != null) {
                            CategoryDetailScreen(
                                category  = category,
                                viewModel = viewModel,
                                onBack    = { navController.popBackStack() },
                            )
                        } else {
                            navController.popBackStack()
                        }
                    }

                    composable("weather_detail") {
                        // Shares viewModel.weatherState with home screen WeatherCard
                        WeatherDetailScreen(
                            viewModel = viewModel,
                            onBack    = { navController.popBackStack() },
                        )
                    }


                    // ── Destinations reachable from Settings ──────────────────
                    // Every Settings row must land somewhere. These fourteen
                    // screens were fully implemented but had no route here, so
                    // their rows were visible, enabled, and did nothing at all
                    // when tapped (F-072). The nullable-callback signature made
                    // that invisible at compile time; SettingsScreen now requires
                    // every action, so a dead row cannot be reintroduced.

                    composable("locked_apps") {
                        LockedAppsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("storage_cleanup") {
                        StorageCleanupScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("recent_files") {
                        RecentFilesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("insights") {
                        InsightsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenScreenTime = { navController.navigate("app_usage") { launchSingleTop = true } },
                            onOpenTodaySummary = { navController.navigate("ai_changelog") { launchSingleTop = true } },
                            onOpenSuggestions = { navController.navigate("contextual_suggestions") { launchSingleTop = true } },
                            onOpenAnomalies = { navController.navigate("anomaly_detection") { launchSingleTop = true } },
                        )
                    }
                    composable("app_usage") {
                        AppUsageStatsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("contextual_suggestions") {
                        ContextualSuggestionsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("voice_commands") {
                        VoiceCommandScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenCategory = { category ->
                                navController.navigate("category_detail/${category.name}") { launchSingleTop = true }
                            },
                            onOpenSearch = { navController.navigate("search") { launchSingleTop = true } },
                        )
                    }
                    composable("anomaly_detection") {
                        AnomalyDetectionScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("ai_changelog") {
                        AiChangelogScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("breach_checker") {
                        DataBreachCheckerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("safe_browsing") {
                        SafeBrowsingHelperScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("search_history") {
                        SearchHistoryScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            // Picking a past query should search it, not just close.
                            onQuerySelected = {
                                navController.navigate("search") { launchSingleTop = true }
                            },
                        )
                    }
                    composable("sticky_notes") {
                        StickyNotesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("auto_backup") {
                        AutoBackupScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("duplicate_shortcuts") {
                        DuplicateShortcutsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("widget_host") {
                        WidgetHostScreen(onBack = { navController.popBackStack() })
                    }

                    composable("agenda") {
                        CalendarAgendaScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                }
                // Above the whole nav graph, so a locked app is gated from every
                // organizer surface without any of them knowing about App Lock.
                com.ciyato.launcher.ui.screens.AppLockHost(viewModel)
            }
        }
        }
    }
}
