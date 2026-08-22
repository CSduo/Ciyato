package com.ciyato.launcher.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.BuildConfig
import com.ciyato.launcher.data.CrashReporter
import com.ciyato.launcher.data.LocationHelper
import com.ciyato.launcher.data.PhotoLibraryStore
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch

/**
 * SettingsScreen — fully expanded with all configurable options.
 *
 * Suggestions covered: 1 (haptic), 21 (temp unit),
 * 23 (hide apps), 24 (category rename), 72 (time-aware), 74 (bedtime),
 * 113 (debug), 138 (privacy mode),
 * 139 (permission audit entry), 144 (crash report), 145 (screenshot block).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    onNavigateToPermissionAudit: () -> Unit,
    onNavigateToStorageCleanup: () -> Unit,
    onNavigateToRecentFiles: () -> Unit,
    onNavigateToFocus: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToPhotos: () -> Unit,
    onNavigateToAgenda: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToWallpaper: () -> Unit,
    onNavigateToHiddenApps: () -> Unit,
    onNavigateToLockedApps: () -> Unit,
    onNavigateToRemovedApps: () -> Unit,
    onNavigateToContextualSuggestions: () -> Unit,
    onNavigateToVoiceCommands: () -> Unit,
    onNavigateToAnomalyDetection: () -> Unit,
    onNavigateToAiChangelog: () -> Unit,
    onNavigateToDataBreachChecker: () -> Unit,
    onNavigateToSafeBrowsing: () -> Unit,
    onNavigateToSearchHistory: () -> Unit,
    onNavigateToStickyNotes: () -> Unit,
    onNavigateToAutoBackup: () -> Unit,
    onNavigateToDuplicateShortcuts: () -> Unit,
    onNavigateToWidgetHost: () -> Unit,
    onNavigateToInsights: () -> Unit,
) {
    val context = LocalContext.current
    val view    = LocalView.current

    // Collect all settings
    val denseLayout        by viewModel.denseLayout.collectAsState()
    val smartCategories    by viewModel.smartCategories.collectAsState()
    val tempUnit           by viewModel.tempUnit.collectAsState()
    val timeAwareLayout    by viewModel.timeAwareLayout.collectAsState()
    val bedtimeMode        by viewModel.bedtimeMode.collectAsState()
    val bedtimeHour        by viewModel.bedtimeHour.collectAsState()
    val hapticFeedback     by viewModel.hapticFeedback.collectAsState()
    val reduceMotion       by viewModel.reduceMotion.collectAsState()
    val privacyMode        by viewModel.privacyMode.collectAsState()
    val screenshotBlocked  by viewModel.screenshotBlocked.collectAsState()
    val crashReporting     by viewModel.crashReporting.collectAsState()
    val showRecentLaunched by viewModel.showRecentlyLaunched.collectAsState()
    val filesRootUri       by viewModel.filesRootUri.collectAsState()
    val photoMediaUris     by viewModel.photoMediaUris.collectAsState()
    val hiddenAppsCsv      by viewModel.hiddenApps.collectAsState()
    val lockedAppsCsv      by viewModel.lockedApps.collectAsState()
    val removedAppsCsv     by viewModel.removedApps.collectAsState()
    val locationGranted    = LocationHelper.hasPermission(context)

    // Screenshot FLAG_SECURE (Suggestion 145)
    val activity = (context as? android.app.Activity)
    LaunchedEffect(screenshotBlocked) {
        activity?.window?.let { window ->
            if (screenshotBlocked) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    var showBedtimeDialog by remember { mutableStateOf(false) }
    var showBlurDialog    by remember { mutableStateOf(false) }
    var showCrashLogs     by remember { mutableStateOf(false) }
    var showForgetFilesDialog by remember { mutableStateOf(false) }
    var showClearPhotosDialog by remember { mutableStateOf(false) }
    var showResetLayoutDialog by remember { mutableStateOf(false) }
    var showResetGuidanceDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }

    if (showCrashLogs) {
        CrashLogsScreen(context = context, onBack = { showCrashLogs = false })
        return
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Settings",
                subtitle = "Configure Ciyato Launcher",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

            // ── Launcher ──────────────────────────────────────────────────────
            item { SectionHeader("Launcher") }
            item {
                CiyatoListCard(
                    title = "Set Ciyato as Home",
                    subtitle = "Choose Ciyato as your default launcher",
                    icon = Icons.Default.Home,
                    iconColor = CiyatoGold,
                    onClick = { try { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {} }
                )
            }

            // ── Appearance ────────────────────────────────────────────────────
            item { SectionHeader("Appearance") }
            item {
                CiyatoSettingSwitch(
                    title = "Dense Layout",
                    subtitle = "Fit more content on screen",
                    icon = Icons.Default.GridView,
                    checked = denseLayout,
                    onCheckedChange = viewModel::setDenseLayout
                )
            }
            // Theme Studio item
            item {
                CiyatoListCard(
                    title = "Theme Studio",
                    subtitle = "Typeface, Home layout, app grid and wallpaper",
                    icon = Icons.Default.Palette,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToTheme() }
                )
            }
            item {
                CiyatoListCard(
                    title = "Widgets",
                    // Was "Place Android app widgets on your home screen". Home
                    // hosts no widgets — there is no AppWidgetHostView anywhere
                    // in HomeScreen — so that sentence described a feature that
                    // does not exist (F-135). Home placement is the right end
                    // state and is tracked as such; until it lands the row says
                    // what the screen actually does.
                    subtitle = "Browse and keep app widgets in Ciyato's widget panel",
                    icon = Icons.Default.Widgets,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToWidgetHost() }
                )
            }

            // ── Smart Layout ──────────────────────────────────────────────────
            item { SectionHeader("Home behaviour") }
            item {
                CiyatoSettingSwitch(
                    title = "Time-Aware Layout",
                    subtitle = "Show relevant categories based on time of day",
                    icon = Icons.Default.Schedule,
                    checked = timeAwareLayout,
                    onCheckedChange = viewModel::setTimeAwareLayout
                )
            }
            item {
                CiyatoSettingSwitch(
                    title = "Bedtime Mode",
                    subtitle = "Hide social/entertainment apps after bedtime hour",
                    icon = Icons.Default.Bedtime,
                    checked = bedtimeMode,
                    onCheckedChange = viewModel::setBedtimeMode
                )
            }
            if (bedtimeMode) {
                item {
                    CiyatoListCard(
                        title = "Bedtime Hour",
                        subtitle = "${bedtimeHour}:00 — apps hidden after this time",
                        icon = Icons.Default.Bedtime,
                        iconColor = CiyatoBlue,
                        onClick = { showBedtimeDialog = true }
                    )
                }
            }
            item {
                CiyatoSettingSwitch(
                    title = "Show Recently Launched",
                    subtitle = "Quick access strip for recently opened apps",
                    icon = Icons.Default.History,
                    checked = showRecentLaunched,
                    onCheckedChange = viewModel::setShowRecentlyLaunched
                )
            }

            // ── Organization ──────────────────────────────────────────────────
            item { SectionHeader("Organizer") }
            item {
                CiyatoSettingSwitch(
                    title = "Smart Categories",
                    subtitle = "Automatic app grouping",
                    icon = Icons.Default.Category,
                    checked = smartCategories,
                    onCheckedChange = viewModel::setSmartCategories
                )
            }
            item {
                CiyatoListCard(
                    title = "Duplicate Smart Shortcuts",
                    subtitle = "See which apps appear in more than one category",
                    icon = Icons.Default.AutoFixHigh,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToDuplicateShortcuts() }
                )
            }
            // ── Smart Insights ───────────────────────────────────────────────
            item { SectionHeader("Labs", "Experimental. These use rough signals and can be wrong.") }
            item {
                // Was four separate rows — Smart Suggestions, Usage Anomalies,
                // Today's Summary and Voice Commands — each at the same weight as
                // "Set Ciyato as Home", and the first three each asking for Usage
                // Access independently (F-130). One entry, one grant.
                CiyatoListCard(
                    title = "Insights",
                    subtitle = "Screen time, daily summary, frequent apps and unusual usage",
                    icon = Icons.Default.Insights,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToInsights() },
                )
            }
            item {
                CiyatoListCard(
                    title = "Voice Commands",
                    subtitle = "Open apps and control Ciyato with your voice",
                    icon = Icons.Default.Mic,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToVoiceCommands() },
                )
            }
            item { SectionHeader("Weather glance") }
            item {
                SettingsOptionRow(
                    icon     = Icons.Default.Thermostat,
                    title = "Temperature Unit",
                    selected = tempUnit,
                    options  = listOf("C" to "Celsius", "F" to "Fahrenheit"),
                    onSelect = viewModel::setTempUnit,
                )
            }

            // ── Accessibility ─────────────────────────────────────────────────
            item { SectionHeader("Organizer access") }
            item {
                CiyatoListCard(
                    title = "Files Access",
                    subtitle = if (filesRootUri.isBlank()) {
                        "No folder selected. Open Files to choose one folder."
                    } else {
                        "Selected folder is remembered. Tap to manage it."
                    },
                    icon = Icons.Default.FolderOpen,
                    iconColor = CiyatoGold,
                    onClick = {
                        if (filesRootUri.isBlank()) {
                            onNavigateToFiles() ?: openAppSettings(context)
                        } else {
                            showForgetFilesDialog = true
                        }
                    }
                )
            }
            item {
                CiyatoListCard(
                    title = "Storage Cleanup",
                    subtitle = "Scan for large files, old screenshots, downloads, cache, and empty files",
                    icon = Icons.Default.Storage,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToStorageCleanup() }
                )
            }
            item {
                CiyatoListCard(
                    title = "Photo Backup",
                    subtitle = "Back up photos to a folder you choose, automatically or on demand",
                    icon = Icons.Default.Backup,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToAutoBackup() }
                )
            }
            item {
                CiyatoListCard(
                    title = "Recent Files",
                    subtitle = "Browse recently modified files across the device and tag them to stay organized",
                    icon = Icons.Default.History,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToRecentFiles() }
                )
            }
            item {
                CiyatoListCard(
                    title = "Wallpaper Studio",
                    subtitle = "System wallpaper, private images, and short Ciyato-only videos",
                    icon = Icons.Default.Wallpaper,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToWallpaper() },
                )
            }
            item {
                CiyatoListCard(
                    title = "Photos",
                    // Was "Photos Access", subtitled "N selected item(s). Android
                    // Photo Picker only" — a description of the curated picker
                    // that is now only the no-permission fallback, not the
                    // product. The elvis after onNavigateToPhotos() was also
                    // dead: the callback returns Unit, so openAppSettings could
                    // never run. It is a leftover from when nav callbacks were
                    // nullable.
                    subtitle = "Your gallery in collections — screenshots, recent, videos, large files, and month by month",
                    icon = Icons.Default.PhotoLibrary,
                    iconColor = CiyatoBlue,
                    onClick = onNavigateToPhotos
                )
            }
            if (PhotoLibraryStore.parseUris(photoMediaUris).isNotEmpty()) {
                item {
                    CiyatoListCard(
                        title = "Clear Selected Photos",
                        subtitle = "Removes Ciyato's references. Original media stays on your device.",
                        icon = Icons.Default.PhotoLibrary,
                        iconColor = CiyatoSec,
                        onClick = { showClearPhotosDialog = true }
                    )
                }
            }
            item {
                CiyatoListCard(
                    title = "Calendar Access",
                    subtitle = "Connect only when you want Ciyato to show real upcoming events.",
                    icon = Icons.Default.CalendarToday,
                    iconColor = CiyatoSec,
                    onClick = { onNavigateToAgenda() ?: openAppSettings(context) }
                )
            }
            item {
                CiyatoListCard(
                    title = "Weather Location",
                    subtitle = if (locationGranted) "Approximate location permission is enabled." else "Location is off until Weather asks for it.",
                    icon = Icons.Default.LocationOn,
                    iconColor = CiyatoGreen,
                    onClick = { openAppSettings(context) }
                )
            }

            item { SectionHeader("Accessibility") }
            // Moved here from "Permissions & Access", where it had nothing to do
            // with permissions and nobody would think to look for it.
            item {
                CiyatoSettingSwitch(
                    title = "Reduce Motion",
                    subtitle = "Use calmer workspace transitions and pause Ciyato video backgrounds",
                    icon = Icons.Default.MotionPhotosPause,
                    checked = reduceMotion,
                    onCheckedChange = viewModel::setReduceMotion
                )
            }
            item {
                CiyatoSettingSwitch(
                    title = "Haptic Feedback",
                    subtitle = "Feel taps, toggles and actions",
                    icon = Icons.Default.Vibration,
                    checked = hapticFeedback,
                    onCheckedChange = viewModel::setHapticFeedback
                )
            }

            // ── Focus ─────────────────────────────────────────────────────────
            item { SectionHeader("Focus (Labs)") }
            item {
                CiyatoListCard(
                    title = "Focus Sessions",
                    subtitle = "Hide chosen categories and stop Ciyato opening them for a while",
                    icon = Icons.Default.Timer,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToFocus() }
                )
            }

            // ── Privacy & Security ────────────────────────────────────────────
            item { SectionHeader("Privacy & Security") }
            item {
                InfoCard(
                    Icons.Default.Lock,
                    "Local Only",
                    "All app indexing, categorization, and preferences stay on your device. Nothing is uploaded."
                )
            }
            item {
                CiyatoSettingSwitch(
                    title = "Privacy Mode",
                    subtitle = "Hide personal greeting, weather details, and recently used apps on Home",
                    icon = Icons.Default.VisibilityOff,
                    checked = privacyMode,
                    onCheckedChange = viewModel::setPrivacyMode
                )
            }
            item {
                CiyatoListCard(
                    title = "Search History",
                    subtitle = "Review and clear the searches saved from the app search bar",
                    icon = Icons.Default.History,
                    iconColor = CiyatoBlue,
                    onClick = { onNavigateToSearchHistory() }
                )
            }
            item {
                CiyatoListCard(
                    title = "Sticky Notes",
                    subtitle = "Quick notes kept on this device",
                    icon = Icons.Default.StickyNote2,
                    iconColor = CiyatoAmber,
                    onClick = { onNavigateToStickyNotes() }
                )
            }
            item {
                CiyatoSettingSwitch(
                    title = "Block Screenshots",
                    subtitle = "Prevents screen capture of Ciyato (FLAG_SECURE)",
                    icon = Icons.Default.Screenshot,
                    checked = screenshotBlocked,
                    onCheckedChange = { blocked ->
                        viewModel.setScreenshotBlocked(blocked)
                        activity?.window?.let { window ->
                            if (blocked) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                )
            }
            item {
                CiyatoListCard(
                    title = "Permission Audit",
                    subtitle = "See which apps have access to sensitive permissions",
                    icon = Icons.Default.Security,
                    iconColor = CiyatoBlue,
                    onClick = { onNavigateToPermissionAudit() }
                )
            }
            item {
                CiyatoListCard(
                    title = "Breach Checker",
                    subtitle = "Check if a password appeared in a known data breach — never leaves your device",
                    icon = Icons.Default.Shield,
                    iconColor = CiyatoBlue,
                    onClick = { onNavigateToDataBreachChecker() }
                )
            }
            item {
                CiyatoListCard(
                    title = "Safe Browsing Helper",
                    subtitle = "Heuristic check for suspicious URLs before you open them",
                    icon = Icons.Default.GppGood,
                    iconColor = CiyatoBlue,
                    onClick = { onNavigateToSafeBrowsing() }
                )
            }

            item {
                CiyatoListCard(
                    title = "App Lock",
                    subtitle = "${countCsv(lockedAppsCsv)} require unlocking when opened from Ciyato",
                    icon = Icons.Default.Lock,
                    iconColor = CiyatoSec,
                    onClick = onNavigateToLockedApps,
                )
            }
            item {
                CiyatoListCard(
                    title = "Hidden Apps",
                    subtitle = "${countCsv(hiddenAppsCsv)} hidden - restore any time",
                    icon = Icons.Default.VisibilityOff,
                    iconColor = CiyatoSec,
                    onClick = { onNavigateToHiddenApps() }
                )
            }
            item {
                CiyatoListCard(
                    title = "Removed Apps",
                    subtitle = "${countCsv(removedAppsCsv)} removed from display - restore any time",
                    icon = Icons.Default.RemoveCircleOutline,
                    iconColor = CiyatoSec,
                    onClick = { onNavigateToRemovedApps() }
                )
            }

            // ── Diagnostics ───────────────────────────────────────────────────
            item { SectionHeader("About & diagnostics") }
            item {
                CiyatoSettingSwitch(
                    title = "Crash Reporting",
                    subtitle = "Save crash logs locally (never uploaded)",
                    icon = Icons.Default.BugReport,
                    checked = crashReporting,
                    onCheckedChange = viewModel::setCrashReporting
                )
            }
            if (crashReporting) {
                item {
                    CiyatoListCard(
                        title = "View Crash Logs",
                        subtitle = "See locally stored crash reports",
                        icon = Icons.Default.Description,
                        iconColor = CiyatoGold,
                        onClick = { showCrashLogs = true }
                    )
                }
            }

            // ── Danger Zone ───────────────────────────────────────────────────
            item { SectionHeader("App info") }
            item {
                InfoCard(
                    Icons.Default.Info,
                    "Ciyato ${BuildConfig.VERSION_NAME}",
                    "Build ${BuildConfig.VERSION_CODE} - ${if (BuildConfig.DEBUG) "debug" else "release"}"
                )
            }
            item {
                CiyatoListCard(
                    title = "App Info / Uninstall",
                    subtitle = "Open Android app settings for Ciyato",
                    icon = Icons.Default.Info,
                    iconColor = CiyatoSec,
                    onClick = { openAppSettings(context) }
                )
            }

            item { SectionHeader("Danger Zone") }
            item {
                CiyatoListCard(
                    title = "Reset Layout",
                    subtitle = "Restore default layout settings",
                    icon = Icons.Default.RestartAlt,
                    iconColor = CiyatoSec,
                    onClick = { showResetLayoutDialog = true }
                )
            }
            item {
                CiyatoListCard(
                    title = "Reset Tips & Onboarding",
                    subtitle = "Show setup guidance again",
                    icon = Icons.Default.TipsAndUpdates,
                    iconColor = CiyatoBlue,
                    onClick = { showResetGuidanceDialog = true }
                )
            }
            item {
                CiyatoListCard(
                    title = "Reset All Ciyato Preferences",
                    subtitle = "Clear local settings, hidden apps, removed apps, and selected folders",
                    icon = Icons.Default.DeleteForever,
                    iconColor = CiyatoRed,
                    onClick = { showResetAllDialog = true }
                )
            }
            item {
                CiyatoListCard(
                    title = "Switch back to system launcher",
                    subtitle = "Change Home app in system settings",
                    icon = Icons.AutoMirrored.Filled.Launch,
                    iconColor = CiyatoSec,
                    onClick = { try { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {} }
                )
            }
        }
    }

    // ── Bedtime hour picker dialog ────────────────────────────────────────────
    if (showBedtimeDialog) {
        // The slider used to call setBedtimeHour() on every drag event, so a
        // single adjustment wrote to DataStore dozens of times and "Done" had
        // nothing left to confirm — the value was already saved, and dismissing
        // the dialog by tapping outside kept a change the user never confirmed.
        // The drag is local now; Done commits, dismissing discards.
        var pendingBedtime by remember(bedtimeHour) { mutableIntStateOf(bedtimeHour) }
        AlertDialog(
            onDismissRequest = { showBedtimeDialog = false },
            containerColor   = CiyatoBgEl,
            title = { Text("Bedtime Hour", color = CiyatoWhite) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hide apps after:", color = CiyatoSec, fontSize = 13.sp)
                    Slider(
                        value = pendingBedtime.toFloat(),
                        onValueChange = { pendingBedtime = it.toInt() },
                        valueRange = 18f..23f, steps = 4,
                        colors = SliderDefaults.colors(thumbColor = CiyatoGold, activeTrackColor = CiyatoGold),
                    )
                    Text("${pendingBedtime}:00", color = CiyatoGold, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBedtimeHour(pendingBedtime)
                    showBedtimeDialog = false
                }) {
                    Text("Done", color = CiyatoGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBedtimeDialog = false }) {
                    Text("Cancel", color = CiyatoSec)
                }
            },
        )
    }

    if (showForgetFilesDialog) {
        AlertDialog(
            onDismissRequest = { showForgetFilesDialog = false },
            containerColor = CiyatoBgEl,
            title = { Text("Forget Files Folder", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Ciyato will stop remembering the selected folder. You can choose it again from Files.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearFilesRootUri()
                    showForgetFilesDialog = false
                }) {
                    Text("Forget", color = CiyatoGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgetFilesDialog = false }) {
                    Text("Cancel", color = CiyatoSec)
                }
            }
        )
    }

    if (showClearPhotosDialog) {
        AlertDialog(
            onDismissRequest = { showClearPhotosDialog = false },
            containerColor = CiyatoBgEl,
            title = { Text("Clear selected photos", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Ciyato will forget the media you selected and remove local collections. Nothing is deleted from your device.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearPhotoLibrary()
                    showClearPhotosDialog = false
                }) { Text("Clear", color = CiyatoGold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearPhotosDialog = false }) { Text("Cancel", color = CiyatoSec) }
            },
        )
    }

    if (showResetLayoutDialog) {
        AlertDialog(
            onDismissRequest = { showResetLayoutDialog = false },
            containerColor = CiyatoBgEl,
            title = { Text("Reset Layout", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This restores Home density and App Library style to Ciyato defaults.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetLayout()
                    showResetLayoutDialog = false
                }) {
                    Text("Reset", color = CiyatoGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetLayoutDialog = false }) {
                    Text("Cancel", color = CiyatoSec)
                }
            }
        )
    }

    if (showResetGuidanceDialog) {
        AlertDialog(
            onDismissRequest = { showResetGuidanceDialog = false },
            containerColor = CiyatoBgEl,
            title = { Text("Reset Guidance", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Onboarding and home tips will appear again the next time Ciyato opens.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetGuidance()
                    showResetGuidanceDialog = false
                }) {
                    Text("Reset", color = CiyatoGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetGuidanceDialog = false }) {
                    Text("Cancel", color = CiyatoSec)
                }
            }
        )
    }

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            containerColor = CiyatoBgEl,
            title = { Text("Reset All Preferences", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This clears Ciyato preferences stored on this device. It does not uninstall apps or delete files.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAllPreferences()
                    showResetAllDialog = false
                }) {
                    Text("Reset All", color = CiyatoRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("Cancel", color = CiyatoSec)
                }
            }
        )
    }
}

// ─── Crash Logs inline screen ──────────────────────────────────────────────────

@Composable
private fun CrashLogsScreen(context: Context, onBack: () -> Unit) {
    val logs = remember { CrashReporter.getLogs(context) }
    var selectedContent by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (selectedContent != null) {
        Scaffold(
            containerColor = CiyatoBg,
            topBar = {
                CiyatoTopBar(title = "Crash Log", onBack = { selectedContent = null })
            }
        ) { p ->
            androidx.compose.foundation.lazy.LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize().padding(p),
            ) {
                item {
                    Text(selectedContent ?: "", color = CiyatoSec, fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Crash Logs",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { CrashReporter.clearLogs(context) }) {
                        Text("Clear All", color = CiyatoRed)
                    }
                },
            )
        }
    ) { p ->
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                Text("No crash logs yet \uD83C\uDF89", color = CiyatoMuted, fontSize = 16.sp)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(p),
            ) {
                items(logs, key = { it.name }) { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CiyatoBgEl)
                            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch {
                                    selectedContent = CrashReporter.readLog(file)
                                }
                            }.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(file.name.replace("crash_", "").replace(".txt", ""),
                                color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("%.1f KB".format(file.length() / 1024f), color = CiyatoMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = CiyatoMuted)
                    }
                }
            }
        }
    }
}

// ─── Shared Settings Components ───────────────────────────────────────────────

@Composable
/**
 * A settings section heading, with an optional caption.
 *
 * The caption exists so experimental features can be labelled as experimental in
 * the place people actually read. Settings previously presented thirteen sections
 * at identical visual weight, so a rough usage heuristic sat beside "Set Ciyato
 * as Home" and looked equally load-bearing (F-066, F-076).
 */
private fun SectionHeader(title: String, caption: String? = null) {
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
        Text(
            title.uppercase(), color = CiyatoGold, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        )
        if (caption != null) {
            Text(caption, color = CiyatoMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun SettingsToggle(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(onClick = { onCheckedChange(!checked) }, color = CiyatoBgEl,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CiyatoSubtleBorder)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = CiyatoSec, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = CiyatoMuted, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = CiyatoWhite, checkedTrackColor = CiyatoGold,
                    uncheckedThumbColor = CiyatoMuted, uncheckedTrackColor = CiyatoBgEl2))
        }
    }
}

@Composable
private fun SettingsAction(icon: ImageVector, title: String, subtitle: String,
    tintColor: Color = CiyatoWhite, onClick: () -> Unit) {
    Surface(onClick = onClick, color = CiyatoBgEl, shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CiyatoSubtleBorder)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = if (tintColor == CiyatoWhite) CiyatoSec else tintColor, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = tintColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = CiyatoMuted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = CiyatoMuted)
        }
    }
}

@Composable
private fun SettingsOptionRow(
    icon: ImageVector,
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = CiyatoSec, modifier = Modifier.size(22.dp))
            Text(title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                val isSelected = selected == value
                Box(contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) CiyatoGold else CiyatoBgEl2)
                        .clickable { onSelect(value) }) {
                    Text(label, color = if (isSelected) CiyatoBg else CiyatoSec,
                        fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CiyatoBgEl2).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
            Text(title, color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(body, color = CiyatoSec, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

private fun countCsv(csv: String): Int =
    csv.split(",").count { it.trim().isNotEmpty() }

private fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
        )
    } catch (_: Exception) {}
}
