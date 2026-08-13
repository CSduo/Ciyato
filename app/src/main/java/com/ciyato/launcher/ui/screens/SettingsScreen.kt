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
    onNavigateToPermissionAudit: (() -> Unit)? = null,
    onNavigateToStorageCleanup: (() -> Unit)? = null,
    onNavigateToPhotoCollections: (() -> Unit)? = null,
    onNavigateToFocus: (() -> Unit)? = null,
    onNavigateToFiles: (() -> Unit)? = null,
    onNavigateToPhotos: (() -> Unit)? = null,
    onNavigateToAgenda: (() -> Unit)? = null,
    onNavigateToTheme: (() -> Unit)? = null,
    onNavigateToWallpaper: (() -> Unit)? = null,
    onNavigateToHiddenApps: (() -> Unit)? = null,
    onNavigateToRemovedApps: (() -> Unit)? = null,
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
                    subtitle = "Typography, Home layout, and App Library layout",
                    icon = Icons.Default.Palette,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToTheme?.invoke() }
                )
            }

            // ── Smart Layout ──────────────────────────────────────────────────
            item { SectionHeader("Smart Layout") }
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
            item { SectionHeader("Organization") }
            item {
                CiyatoSettingSwitch(
                    title = "Smart Categories",
                    subtitle = "Automatic app grouping",
                    icon = Icons.Default.Category,
                    checked = smartCategories,
                    onCheckedChange = viewModel::setSmartCategories
                )
            }
            // ── Weather ───────────────────────────────────────────────────────
            item { SectionHeader("Weather") }
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
            item { SectionHeader("Permissions & Access") }
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
                            onNavigateToFiles?.invoke() ?: openAppSettings(context)
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
                    onClick = { onNavigateToStorageCleanup?.invoke() }
                )
            }
            item {
                CiyatoListCard(
                    title = "Photo Collections",
                    subtitle = "Screenshots, recent, videos, large photos, and month-by-month memories",
                    icon = Icons.Default.PhotoLibrary,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToPhotoCollections?.invoke() }
                )
            }
            item {
                CiyatoListCard(
                    title = "Wallpaper Studio",
                    subtitle = "System wallpaper, private images, and short Ciyato-only videos",
                    icon = Icons.Default.Wallpaper,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToWallpaper?.invoke() },
                )
            }
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
                CiyatoListCard(
                    title = "Photos Access",
                    subtitle = "${PhotoLibraryStore.parseUris(photoMediaUris).size} selected item(s). Android Photo Picker only.",
                    icon = Icons.Default.PhotoLibrary,
                    iconColor = CiyatoBlue,
                    onClick = { onNavigateToPhotos?.invoke() ?: openAppSettings(context) }
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
                    onClick = { onNavigateToAgenda?.invoke() ?: openAppSettings(context) }
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
            item { SectionHeader("Focus") }
            item {
                CiyatoListCard(
                    title = "Focus Sessions",
                    subtitle = "Block distracting apps for a set duration",
                    icon = Icons.Default.Timer,
                    iconColor = CiyatoGold,
                    onClick = { onNavigateToFocus?.invoke() }
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
                    onClick = { onNavigateToPermissionAudit?.invoke() }
                )
            }

            item {
                CiyatoListCard(
                    title = "Hidden Apps",
                    subtitle = "${countCsv(hiddenAppsCsv)} hidden - restore any time",
                    icon = Icons.Default.VisibilityOff,
                    iconColor = CiyatoSec,
                    onClick = { onNavigateToHiddenApps?.invoke() }
                )
            }
            item {
                CiyatoListCard(
                    title = "Removed Apps",
                    subtitle = "${countCsv(removedAppsCsv)} removed from display - restore any time",
                    icon = Icons.Default.RemoveCircleOutline,
                    iconColor = CiyatoSec,
                    onClick = { onNavigateToRemovedApps?.invoke() }
                )
            }

            // ── Diagnostics ───────────────────────────────────────────────────
            item { SectionHeader("Diagnostics") }
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
            item { SectionHeader("App Info") }
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
                    iconColor = Color(0xFFEF4444),
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
        AlertDialog(
            onDismissRequest = { showBedtimeDialog = false },
            containerColor   = CiyatoBgEl,
            title = { Text("Bedtime Hour", color = CiyatoWhite) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hide apps after:", color = CiyatoSec, fontSize = 13.sp)
                    Slider(
                        value = bedtimeHour.toFloat(), onValueChange = { viewModel.setBedtimeHour(it.toInt()) },
                        valueRange = 18f..23f, steps = 4,
                        colors = SliderDefaults.colors(thumbColor = CiyatoGold, activeTrackColor = CiyatoGold),
                    )
                    Text("${bedtimeHour}:00", color = CiyatoGold, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            },
            confirmButton = {
                TextButton(onClick = { showBedtimeDialog = false }) {
                    Text("Done", color = CiyatoGold)
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
                    Text("Reset All", color = Color(0xFFEF4444))
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
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text("Crash Log", color = CiyatoWhite, fontSize = 16.sp) },
                    navigationIcon = { IconButton(onClick = { selectedContent = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiyatoSec)
                    }},
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
                )
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
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Crash Logs", color = CiyatoWhite) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiyatoSec)
                }},
                actions = {
                    TextButton(onClick = { CrashReporter.clearLogs(context) }) {
                        Text("Clear All", color = Color(0xFFEF4444))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
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
private fun SectionHeader(title: String) {
    Text(title.uppercase(), color = CiyatoGold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
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
private fun SettingsSlider(
    icon: ImageVector, title: String, value: Float, range: ClosedFloatingPointRange<Float>,
    label: String, onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = CiyatoSec, modifier = Modifier.size(22.dp))
            Text(title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(label, color = CiyatoGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = CiyatoGold, activeTrackColor = CiyatoGold, inactiveTrackColor = CiyatoBgEl2))
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
