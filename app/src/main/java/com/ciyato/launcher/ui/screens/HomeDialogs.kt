package com.ciyato.launcher.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.data.WorkspaceRecord
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.launcher.*
import com.ciyato.launcher.ui.theme.*
import java.util.*
import androidx.compose.ui.res.pluralStringResource
import com.ciyato.launcher.R

/**
 * Home's modal surfaces: dock management and the workspace overview.
 */

@Composable
internal fun DockManagerDialog(
    dockApps: List<InstalledApp>,
    availableApps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shownApps = remember(availableApps, query) {
        availableApps
            .asSequence()
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .take(30)
            .toList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CiyatoBgEl,
        title = { Text("Dock", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Up to five app shortcuts. Changes are saved to this launcher.", color = CiyatoMuted, fontSize = 12.sp)
                dockApps.forEachIndexed { index, app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CiyatoBgEl2)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RealAppIcon(drawable = app.icon, size = 36.dp, cornerRadius = 10.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
                        Spacer(Modifier.width(10.dp))
                        Text(app.label, color = CiyatoWhite, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { onMove(app.packageName, -1) }, enabled = index > 0) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move ${app.label} left", tint = if (index > 0) CiyatoSec else CiyatoMuted)
                        }
                        IconButton(onClick = { onMove(app.packageName, 1) }, enabled = index < dockApps.lastIndex) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move ${app.label} right", tint = if (index < dockApps.lastIndex) CiyatoSec else CiyatoMuted)
                        }
                        IconButton(onClick = { onRemove(app.packageName) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove ${app.label}", tint = CiyatoSec)
                        }
                    }
                }
                if (dockApps.size < 5) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text("Add an installed app") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CiyatoWhite,
                            unfocusedTextColor = CiyatoWhite,
                            focusedBorderColor = CiyatoGold,
                            unfocusedBorderColor = CiyatoSubtleBorder,
                            focusedLabelColor = CiyatoGold,
                            unfocusedLabelColor = CiyatoMuted,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(shownApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAdd(app.packageName) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RealAppIcon(drawable = app.icon, size = 32.dp, cornerRadius = 9.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
                                Spacer(Modifier.width(10.dp))
                                Text(app.label, color = CiyatoWhite, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.Add, contentDescription = "Add ${app.label}", tint = CiyatoGold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = CiyatoGold) } },
    )
}

@Composable
internal fun WorkspaceOverviewDialog(
    workspaces: List<WorkspaceRecord>,
    defaultWorkspace: (Int) -> Boolean,
    onDismiss: () -> Unit,
    onOpen: (Int) -> Unit,
    onRename: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDuplicate: (Int) -> Unit,
    onInsertAdjacent: (Int) -> Unit,
    onSetDefault: (Int) -> Unit,
    onEditWallpaper: () -> Unit,
    onDelete: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CiyatoBgEl,
        title = { Text("Workspaces", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Reorder, name and manage each saved workspace. Wallpaper applies across workspaces unless you choose a different source in Wallpaper Studio.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                TextButton(onClick = onEditWallpaper, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Wallpaper, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Wallpaper Studio", color = CiyatoGold)
                }
                workspaces.forEachIndexed { visualIndex, workspace ->
                    val isDefault = defaultWorkspace(visualIndex)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CiyatoBgEl2,
                        border = BorderStroke(1.dp, if (isDefault) CiyatoGold.copy(alpha = 0.45f) else CiyatoSubtleBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        workspace.name ?: "Workspace ${workspace.creationOrder}",
                                        color = CiyatoWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "Workspace ${workspace.creationOrder} · " +
                        pluralStringResource(R.plurals.count_shortcuts, workspace.appPackages.size, workspace.appPackages.size) +
                        " · " +
                        pluralStringResource(R.plurals.count_categories, workspace.categoryKeys.size, workspace.categoryKeys.size),
                                        color = CiyatoMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isDefault) {
                                    Text(
                                        "Default",
                                        color = CiyatoGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            WorkspaceLayoutPreview(workspace = workspace)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { onOpen(visualIndex) }) { Text("Open", color = CiyatoGold) }
                                TextButton(onClick = { onRename(visualIndex) }) { Text("Rename", color = CiyatoSec) }
                                if (!isDefault) {
                                    TextButton(onClick = { onSetDefault(visualIndex) }) { Text("Default", color = CiyatoSec) }
                                }
                                IconButton(
                                    onClick = { onMove(visualIndex, -1) },
                                    enabled = visualIndex > 0,
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move workspace left", tint = CiyatoSec)
                                }
                                IconButton(
                                    onClick = { onMove(visualIndex, 1) },
                                    enabled = visualIndex < workspaces.lastIndex,
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move workspace right", tint = CiyatoSec)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (workspaces.size < 10) {
                                    TextButton(onClick = { onInsertAdjacent(visualIndex) }) { Text("+ Workspace", color = CiyatoSec) }
                                    TextButton(onClick = { onDuplicate(visualIndex) }) { Text("Duplicate", color = CiyatoSec) }
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = { onDelete(visualIndex) },
                                    enabled = workspaces.size > 1,
                                ) { Text("Delete", color = CiyatoRed) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = CiyatoGold) }
        },
    )
}

@Composable
internal fun WorkspaceLayoutPreview(workspace: WorkspaceRecord) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(CiyatoBg.copy(alpha = 0.72f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(workspace.categoryKeys.take(3).size.coerceAtLeast(1)) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (index == 0) CiyatoGold.copy(alpha = 0.34f) else CiyatoSec.copy(alpha = 0.18f)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(workspace.appPackages.take(5).size.coerceAtLeast(1)) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index % 2 == 0) CiyatoSec.copy(alpha = 0.18f) else CiyatoBlue.copy(alpha = 0.23f)),
                )
            }
        }
        Text(
            if (workspace.appPackages.isEmpty() && workspace.categoryKeys.isEmpty()) {
                "New workspace starter is ready"
            } else {
                "Layout summary based on saved shortcuts and categories"
            },
            color = CiyatoMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
internal fun WorkspaceStarterCard(
    onAddShortcut: () -> Unit,
    onAddCategory: () -> Unit,
    onChooseTemplate: () -> Unit,
    onStartClean: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CiyatoBgEl.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, CiyatoSubtleBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.SpaceDashboard, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(24.dp))
            Text("Make this workspace yours", color = CiyatoWhite, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Start with a few shortcuts, add a category, preview a light template, or keep the space clean. Ciyato never fills it with apps on your behalf.",
                color = CiyatoSec,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onAddShortcut,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(CiyatoGold.copy(alpha = 0.14f)),
                ) { Text("Add app", color = CiyatoGold) }
                TextButton(
                    onClick = onAddCategory,
                    modifier = Modifier.weight(1f),
                ) { Text("Add category", color = CiyatoSec) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onChooseTemplate, modifier = Modifier.weight(1f)) {
                    Text("Preview template", color = CiyatoSec)
                }
                TextButton(onClick = onStartClean, modifier = Modifier.weight(1f)) {
                    Text("Start clean", color = CiyatoSec)
                }
            }
        }
    }
}
