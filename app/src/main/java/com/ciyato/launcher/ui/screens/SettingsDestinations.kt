package com.ciyato.launcher.ui.screens

/**
 * Everywhere Settings can send you.
 *
 * SettingsScreen took twenty-eight parameters, twenty-six of them
 * `onNavigateToX: () -> Unit` (F-075). That is not merely ugly: a flat list of
 * same-typed lambdas is a signature the compiler cannot help with. Two of them
 * swapped at a call site type-checks perfectly and sends people to the wrong
 * screen, and every new destination edited three files and grew a parameter list
 * that nobody could read.
 *
 * Grouping them here does not reduce the number of destinations - that is a
 * product fact, and the audit's "feature sprawl" point stands - but it does make
 * the call sites named, greppable, and wrong in a way the compiler notices.
 *
 * Every field is required. A defaulted no-op is how a Settings row comes to be
 * enabled and inert, which is the defect F-072 and F-080 were about.
 */
data class SettingsDestinations(
    val openPermissionAudit: () -> Unit,
    val openStorageCleanup: () -> Unit,
    val openRecentFiles: () -> Unit,
    val openFocus: () -> Unit,
    val openFiles: () -> Unit,
    val openPhotos: () -> Unit,
    val openAgenda: () -> Unit,
    val openTheme: () -> Unit,
    val openWallpaper: () -> Unit,
    val openHiddenApps: () -> Unit,
    val openLockedApps: () -> Unit,
    val openSecureVault: () -> Unit,
    val openPhotosToPdf: () -> Unit,
    val openRemovedApps: () -> Unit,
    val openContextualSuggestions: () -> Unit,
    val openVoiceCommands: () -> Unit,
    val openAnomalyDetection: () -> Unit,
    val openAiChangelog: () -> Unit,
    val openDataBreachChecker: () -> Unit,
    val openSafeBrowsing: () -> Unit,
    val openSearchHistory: () -> Unit,
    val openStickyNotes: () -> Unit,
    val openAutoBackup: () -> Unit,
    val openDuplicateShortcuts: () -> Unit,
    val openWidgetHost: () -> Unit,
    val openInsights: () -> Unit,
)
