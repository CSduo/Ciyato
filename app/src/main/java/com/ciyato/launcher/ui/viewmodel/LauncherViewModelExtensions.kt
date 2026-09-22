package com.ciyato.launcher.viewmodel

import com.ciyato.launcher.data.InstalledApp

/**
 * Pin and hide helpers for LauncherViewModel.
 *
 * The custom greeting used to live here too, and it could not have worked. It
 * was a file-level private MutableStateFlow read through a plain getter: not
 * persisted, so it vanished on process death; not collected, so Compose never
 * recomposed when it changed; and top-level rather than per-instance, so every
 * ViewModel shared one value. CustomGreetingScreen was its only writer and was
 * reachable from no route at all — a screen that could not work, wired to state
 * that could not hold it. Both are gone (F-170).
 *
 * Search history used to live here as a separate in-memory StateFlow
 * (searchHistory/addSearchQuery/clearSearchHistory/removeSearchQuery), but it
 * was never written to by real search flows and has been removed. The real,
 * persisted search history is LauncherViewModel.recentSearches, backed by
 * LauncherSettingsRepository (see SearchScreen/SearchHistoryScreen).
 */

// ── Pin helpers ────────────────────────────────────────────────────────────────

fun LauncherViewModel.isPinned(app: InstalledApp): Boolean {
    return isPinnedToDock(app.packageName)
}

fun LauncherViewModel.isHidden(app: InstalledApp): Boolean {
    return isHidden(app.packageName)
}

fun LauncherViewModel.pinApp(app: InstalledApp) {
    pinToDock(app.packageName)
}

fun LauncherViewModel.unpinApp(app: InstalledApp) {
    unpinFromDock(app.packageName)
}

fun LauncherViewModel.hideApp(app: InstalledApp) {
    hideApp(app.packageName)
}

fun LauncherViewModel.unhideApp(app: InstalledApp) {
    unhideApp(app.packageName)
}
