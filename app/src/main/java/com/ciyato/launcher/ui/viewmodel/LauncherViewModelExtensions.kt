package com.ciyato.launcher.viewmodel

import com.ciyato.launcher.data.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Extension properties and functions for LauncherViewModel.
 * Adds pin/hide helpers and custom greeting.
 * Suggestions: #100 (custom greeting), #16 (pin/hide helpers).
 *
 * Search history used to live here as a separate in-memory StateFlow
 * (searchHistory/addSearchQuery/clearSearchHistory/removeSearchQuery), but it
 * was never written to by real search flows and has been removed. The real,
 * persisted search history is LauncherViewModel.recentSearches, backed by
 * LauncherSettingsRepository (see SearchScreen/SearchHistoryScreen).
 */

private val _customGreeting = MutableStateFlow<String?>(null)

val LauncherViewModel.customGreeting: String?
    get() = _customGreeting.value

// ── Custom greeting (#100) ─────────────────────────────────────────────────────

fun LauncherViewModel.setCustomGreeting(text: String?) {
    _customGreeting.value = text
}

// ── Pin helpers (#16) ──────────────────────────────────────────────────────────

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
