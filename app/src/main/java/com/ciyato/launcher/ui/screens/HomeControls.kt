package com.ciyato.launcher.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.launcher.*
import com.ciyato.launcher.ui.theme.*
import java.util.*

/**
 * The launcher control sheet and the small controls it is built from.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LauncherControlSheet(
    isEditMode: Boolean,
    showGreeting: Boolean,
    showDateTime: Boolean,
    onShowDateTimeChanged: (Boolean) -> Unit,
    showSearch: Boolean,
    showWeather: Boolean,
    showAgenda: Boolean,
    showRecent: Boolean,
    showCategories: Boolean,
    showDock: Boolean,
    workspaceTransition: String,
    onDismiss: () -> Unit,
    onEditLayout: () -> Unit,
    onAddToHome: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWorkspaces: () -> Unit,
    onManageDock: () -> Unit,
    onShowGreetingChanged: (Boolean) -> Unit,
    onShowSearchChanged: (Boolean) -> Unit,
    onShowWeatherChanged: (Boolean) -> Unit,
    onShowAgendaChanged: (Boolean) -> Unit,
    onShowRecentChanged: (Boolean) -> Unit,
    onShowCategoriesChanged: (Boolean) -> Unit,
    onShowDockChanged: (Boolean) -> Unit,
    onTransitionChanged: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CiyatoBgEl,
        contentColor = CiyatoWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Home controls", color = CiyatoWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Changes are saved to this launcher. System wallpaper is managed by Android.", color = CiyatoMuted, fontSize = 12.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HomeControlAction(
                    icon = Icons.Default.Add,
                    label = "Add",
                    onClick = onAddToHome,
                    modifier = Modifier.weight(1f),
                )
                HomeControlAction(
                    icon = Icons.Default.Edit,
                    label = if (isEditMode) "Layout" else "Arrange",
                    onClick = onEditLayout,
                    modifier = Modifier.weight(1f),
                )
                HomeControlAction(
                    icon = Icons.Default.Wallpaper,
                    label = "Wallpaper",
                    onClick = onOpenWallpaper,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HomeControlAction(
                    icon = Icons.Default.ViewCarousel,
                    label = "Workspaces",
                    onClick = onOpenWorkspaces,
                    modifier = Modifier.weight(1f),
                )
                HomeControlAction(
                    icon = Icons.Default.Settings,
                    label = "Home settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.weight(1f))
            }

            Text("Home sections", color = CiyatoSec, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            HomeControlToggle("Greeting", showGreeting, onShowGreetingChanged)
            HomeControlToggle("Date and time", showDateTime, onShowDateTimeChanged)
            HomeControlToggle("Search", showSearch, onShowSearchChanged)
            HomeControlToggle("Weather", showWeather, onShowWeatherChanged)
            HomeControlToggle("Agenda", showAgenda, onShowAgendaChanged)
            HomeControlToggle("Recently used", showRecent, onShowRecentChanged)
            HomeControlToggle("Categories", showCategories, onShowCategoriesChanged)
            HomeControlToggle("Dock", showDock, onShowDockChanged)
            TextButton(onClick = onManageDock, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Dock, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Manage dock", color = CiyatoGold)
            }

            Text("Workspace transition", color = CiyatoSec, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "slide" to "Slide", "fade" to "Fade", "scale" to "Scale",
                    "depth" to "Depth", "flip" to "Flip", "none" to "None",
                ).forEach { (value, label) ->
                    val selected = workspaceTransition == value
                    TextButton(
                        onClick = { onTransitionChanged(value) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) CiyatoGold.copy(alpha = 0.16f) else CiyatoBgEl2),
                    ) {
                        Text(label, color = if (selected) CiyatoGold else CiyatoSec, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeControlAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CiyatoBgEl2)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = CiyatoSec, modifier = Modifier.size(20.dp))
        Text(label, color = CiyatoWhite, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
internal fun HomeControlToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = CiyatoWhite, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun HomeSectionRemoveButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(CiyatoBg.copy(alpha = 0.84f)),
    ) {
        Icon(Icons.Default.Close, contentDescription = "Remove from Home", tint = CiyatoRed, modifier = Modifier.size(16.dp))
    }
}
