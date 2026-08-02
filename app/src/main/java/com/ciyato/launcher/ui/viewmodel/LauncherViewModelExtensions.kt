package com.ciyato.launcher.viewmodel

import com.ciyato.launcher.data.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Extension properties and functions for LauncherViewModel.
 * Adds search history, pin, hide helpers, and custom greeting.
 * Suggestions: #100 (custom greeting), #108 (search history), #16 (pin/hide helpers).
 */

// ── Search history ─────────────────────────────────────────────────────────────

// TODO: _searchHistory is a top-level MutableStateFlow and should be migrated to inside LauncherViewModel.
private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
private val _customGreeting = MutableStateFlow<String?>(null)

val LauncherViewModel.searchHistory: StateFlow<List<String>>
    get() = _searchHistory.asStateFlow()

val LauncherViewModel.customGreeting: String?
    get() = _customGreeting.value

fun LauncherViewModel.addSearchQuery(query: String) {
    if (query.isBlank()) return
    _searchHistory.update { list -> (listOf(query) + list).distinct().take(20) }
}

fun LauncherViewModel.clearSearchHistory() {
    _searchHistory.value = emptyList()
}

fun LauncherViewModel.removeSearchQuery(query: String) {
    _searchHistory.value = _searchHistory.value.filter { it != query }
}

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
