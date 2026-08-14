package com.ciyato.launcher.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ciyato.launcher.data.AppCategory
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import com.ciyato.launcher.viewmodel.isPinned
import com.ciyato.launcher.viewmodel.isHidden
import com.ciyato.launcher.viewmodel.pinApp
import com.ciyato.launcher.viewmodel.unpinApp
import com.ciyato.launcher.viewmodel.hideApp
import com.ciyato.launcher.viewmodel.unhideApp

/**
 * AppContextMenu — Suggestion #16
 * Long-press context menu for app icons: uninstall, hide, info, add shortcut.
 */

sealed class ContextAction {
    object OpenApp : ContextAction()
    object AppInfo : ContextAction()
    object Uninstall : ContextAction()
    object Hide : ContextAction()
    object RemoveFromDisplay : ContextAction()
    object AddToFocus : ContextAction()
    object PinToDock : ContextAction()
}

@Composable
fun AppContextMenu(
    app: InstalledApp,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit,
    onAction: (ContextAction) -> Unit = {},
) {
    val context = LocalContext.current
    val isPinned by remember { derivedStateOf { viewModel.isPinned(app) } }
    val isHidden by remember { derivedStateOf { viewModel.isHidden(app) } }
    var showCategorySelector by remember { mutableStateOf(false) }
    var showAppearanceEditor by remember { mutableStateOf(false) }
    var confirmRemoveFromDisplay by remember { mutableStateOf(false) }
    var confirmUnpinFromDock by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CiyatoBgEl)
                    .border(1.dp, CiyatoBorder, RoundedCornerShape(24.dp))
                    .clickable(enabled = false, onClick = {}),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // App header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CiyatoBgEl2)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppIconView(app = app, size = 60.dp)
                    Text(app.label, color = CiyatoWhite, fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp)
                    Text(app.packageName.substringAfterLast('.'), color = CiyatoMuted,
                        fontSize = 12.sp)
                    app.classification.suggestedCategory?.let { suggestion ->
                        Text(
                            "Review suggestion: ${viewModel.getCategoryDisplayName(suggestion)}. Choose Change Category to confirm or correct it.",
                            color = CiyatoGold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                HorizontalDivider(color = CiyatoBorder)

                // Action items
                val actions = buildList {
                    add(ContextMenuItem(
                        icon = Icons.Default.OpenInNew,
                        label = "Open",
                        color = CiyatoWhite,
                        action = {
                            viewModel.launchApp(app)
                            onDismiss()
                        }
                    ))
                    add(ContextMenuItem(
                        icon = Icons.Default.PushPin,
                        label = if (isPinned) "Unpin from Dock" else "Pin to Dock",
                        color = CiyatoGold,
                        action = {
                            if (isPinned) {
                                // Destructive-feeling (the icon disappears from the
                                // dock) — confirm first, same as "Remove from
                                // display" below. Pinning stays instant; it's purely
                                // additive.
                                confirmUnpinFromDock = true
                            } else {
                                viewModel.pinApp(app)
                                onDismiss()
                            }
                        },
                    ))
                    add(ContextMenuItem(
                        icon = Icons.Default.VisibilityOff,
                        label = if (isHidden) "Unhide App" else "Hide App",
                        color = CiyatoSec,
                        action = {
                            if (isHidden) viewModel.unhideApp(app) else viewModel.hideApp(app)
                            onDismiss()
                        }
                    ))
                    add(ContextMenuItem(
                        icon = Icons.Default.RemoveCircleOutline,
                        label = "Remove from display",
                        color = CiyatoSec,
                        action = {
                            confirmRemoveFromDisplay = true
                        }
                    ))
                    add(ContextMenuItem(
                        icon = Icons.Default.Category,
                        label = "Change Category",
                        color = CiyatoGold,
                        action = {
                            showCategorySelector = true
                        }
                    ))
                    val isExpanded = viewModel.isAppExpanded(app.packageName)
                    add(ContextMenuItem(
                        icon = Icons.Default.AspectRatio,
                        label = if (isExpanded) "Standard Tile Size" else "Expanded Tile (2x2)",
                        color = CiyatoGold,
                        action = {
                            viewModel.toggleExpandedApp(app.packageName)
                            onDismiss()
                        }
                    ))
                    add(ContextMenuItem(
                        icon = Icons.Default.Palette,
                        label = "Customize appearance",
                        color = CiyatoGold,
                        action = { showAppearanceEditor = true },
                    ))
                    add(ContextMenuItem(
                        icon = Icons.Default.Info,
                        label = "App Info",
                        color = CiyatoSec,
                        action = {
                            openAppInfo(context, app.packageName)
                            onDismiss()
                        }
                    ))
                    add(ContextMenuItem(
                        icon = Icons.Default.Delete,
                        label = "Uninstall",
                        color = CiyatoRed,
                        action = {
                            uninstallApp(context, app.packageName)
                            onDismiss()
                        }
                    ))
                }

                actions.forEach { item ->
                    ContextMenuRow(item = item)
                    if (item != actions.last()) {
                        HorizontalDivider(
                            color = CiyatoBorder,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (confirmRemoveFromDisplay) {
        AlertDialog(
            onDismissRequest = { confirmRemoveFromDisplay = false },
            containerColor = CiyatoBgEl,
            title = { Text("Remove from display?", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "${app.label} will stay installed. You can restore it later from Ciyato Settings.",
                    color = CiyatoSec,
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveFromDisplay = false }) {
                    Text("Cancel", color = CiyatoSec)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeAppFromDisplay(app.packageName)
                    onAction(ContextAction.RemoveFromDisplay)
                    confirmRemoveFromDisplay = false
                    onDismiss()
                }) {
                    Text("Remove", color = CiyatoRed)
                }
            },
        )
    }

    if (confirmUnpinFromDock) {
        AlertDialog(
            onDismissRequest = { confirmUnpinFromDock = false },
            containerColor = CiyatoBgEl,
            title = { Text("Remove ${app.label} from Dock?", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "${app.label} will be removed from the dock only. It stays installed and remains available in the App Library.",
                    color = CiyatoSec,
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpinFromDock = false }) {
                    Text("Cancel", color = CiyatoSec)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unpinApp(app)
                    confirmUnpinFromDock = false
                    onDismiss()
                }) {
                    Text("Remove", color = CiyatoRed)
                }
            },
        )
    }

    if (showAppearanceEditor) {
        var displayName by remember(app.packageName) { mutableStateOf(app.label) }
        var iconScale by remember(app.packageName) { mutableFloatStateOf(app.iconScale) }
        var iconRotation by remember(app.packageName) { mutableFloatStateOf(app.iconRotation) }
        var accent by remember(app.packageName) { mutableStateOf(app.iconAccent) }
        val accentChoices = listOf(
            null to "None",
            "#6D8498" to "Steel",
            "#80765F" to "Stone",
            "#596F64" to "Sage",
            "#725F7C" to "Violet",
        )
        AlertDialog(
            onDismissRequest = { showAppearanceEditor = false },
            containerColor = CiyatoBgEl,
            title = { Text("Customize ${app.originalLabel}", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    RealAppIcon(
                        drawable = app.icon,
                        size = 56.dp,
                        scale = iconScale,
                        rotation = iconRotation,
                        accentHex = accent,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(40) },
                        singleLine = true,
                        label = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Icon size", color = CiyatoSec, fontSize = 13.sp)
                    Slider(
                        value = iconScale,
                        onValueChange = { iconScale = it },
                        valueRange = 0.8f..1.2f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = CiyatoGold,
                            activeTrackColor = CiyatoGold,
                        ),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Orientation", color = CiyatoSec, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        listOf(-8f to "-", 0f to "0", 8f to "+").forEach { (value, label) ->
                            TextButton(
                                onClick = { iconRotation = value },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (iconRotation == value) CiyatoGold.copy(alpha = 0.16f) else CiyatoBgEl2),
                            ) { Text(label, color = if (iconRotation == value) CiyatoGold else CiyatoSec) }
                        }
                    }
                    Text("Icon accent", color = CiyatoSec, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        accentChoices.forEach { (value, _) ->
                            val color = value?.let { Color(android.graphics.Color.parseColor(it)) } ?: CiyatoBgEl2
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(color)
                                    .border(2.dp, if (accent == value) CiyatoWhite else CiyatoSubtleBorder, RoundedCornerShape(10.dp))
                                    .clickable { accent = value },
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    displayName = app.originalLabel
                    iconScale = 1f
                    iconRotation = 0f
                    accent = null
                }) { Text("Reset", color = CiyatoSec) }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateAppAppearance(
                        packageName = app.packageName,
                        label = displayName,
                        originalLabel = app.originalLabel,
                        scale = iconScale,
                        rotation = iconRotation,
                        accent = accent,
                    )
                    showAppearanceEditor = false
                    onDismiss()
                }) { Text("Apply", color = CiyatoGold) }
            },
        )
    }

    val customCats by viewModel.customCategories.collectAsState()
    val customCatsList = remember(customCats) {
        customCats.split(",").map(String::trim).filter(String::isNotEmpty)
    }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    if (showCategorySelector) {
        AlertDialog(
            onDismissRequest = { showCategorySelector = false },
            containerColor = CiyatoBgEl,
            title = { Text("Change Category", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Create New option at the top
                    TextButton(
                        onClick = {
                            showNewCategoryDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, null, tint = CiyatoGold, modifier = Modifier.size(16.dp))
                            Text("Create Custom Category…", color = CiyatoGold)
                        }
                    }

                    HorizontalDivider(color = CiyatoBorder)

                    val categories = listOf(
                        AppCategory.WORK,
                        AppCategory.SOCIAL,
                        AppCategory.COMMUNICATION,
                        AppCategory.FINANCE,
                        AppCategory.CREATIVITY,
                        AppCategory.UTILITIES,
                        AppCategory.PRODUCTIVITY,
                        AppCategory.ENTERTAINMENT,
                        AppCategory.TRAVEL,
                        AppCategory.SHOPPING,
                        AppCategory.DAILY,
                        AppCategory.GAMES,
                        AppCategory.AI,
                        AppCategory.VIDEO_EDITING,
                        AppCategory.CONTACTS,
                        AppCategory.OTHER
                    )

                    // Default categories
                    categories.forEach { cat ->
                        TextButton(
                            onClick = {
                                viewModel.setAppCategoryOverride(app.packageName, cat)
                                showCategorySelector = false
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text(viewModel.getCategoryDisplayName(cat), color = CiyatoSec, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    // Custom categories
                    if (customCatsList.isNotEmpty()) {
                        HorizontalDivider(color = CiyatoBorder)
                        Text(
                            "Custom Categories",
                            color = CiyatoMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        customCatsList.forEach { customName ->
                            TextButton(
                                onClick = {
                                    viewModel.setAppCustomCategoryOverride(app.packageName, customName)
                                    showCategorySelector = false
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Text(customName, color = CiyatoSec, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    HorizontalDivider(color = CiyatoBorder)

                    TextButton(
                        onClick = {
                            viewModel.setAppCategoryOverride(app.packageName, null)
                            showCategorySelector = false
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Text("Reset to Default", color = CiyatoRed, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showNewCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            containerColor = CiyatoBgEl,
            title = { Text("New Custom Category", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it.take(24) },
                    singleLine = true,
                    label = { Text("Category name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newCategoryName.trim()
                        if (name.isNotBlank()) {
                            viewModel.addCustomCategory(name)
                            viewModel.setAppCustomCategoryOverride(app.packageName, name)
                        }
                        showNewCategoryDialog = false
                        showCategorySelector = false
                        onDismiss()
                    },
                    enabled = newCategoryName.isNotBlank()
                ) {
                    Text("Create & Assign", color = CiyatoGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategoryDialog = false }) {
                    Text("Cancel", color = CiyatoSec)
                }
            }
        )
    }
}

private data class ContextMenuItem(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val action: () -> Unit,
)

@Composable
private fun ContextMenuRow(item: ContextMenuItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.action)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, contentDescription = item.label, tint = item.color, modifier = Modifier.size(20.dp))
        Text(item.label, color = item.color, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f))
    }
}

private fun openAppInfo(context: Context, packageName: String) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

private fun uninstallApp(context: Context, packageName: String) {
    if (packageName == context.packageName && isDefaultHome(context)) {
        // Android refuses to uninstall the active home app. Send the person to
        // the default-home chooser so they can switch first, then uninstall.
        android.widget.Toast.makeText(
            context,
            "Ciyato is your current home app. Choose another launcher first.",
            android.widget.Toast.LENGTH_LONG,
        ).show()
        context.startActivity(
            Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return
    }
    context.startActivity(
        Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

private fun isDefaultHome(context: Context): Boolean {
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(
        homeIntent,
        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
    )
    return resolved?.activityInfo?.packageName == context.packageName
}
