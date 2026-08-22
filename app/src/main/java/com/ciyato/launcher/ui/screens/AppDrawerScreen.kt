package com.ciyato.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.components.AppContextMenu
import com.ciyato.launcher.ui.components.AppIconTile
import com.ciyato.launcher.ui.components.CiyatoEmptyState
import com.ciyato.launcher.ui.components.CiyatoSearchBar
import com.ciyato.launcher.ui.components.RealAppIcon
import com.ciyato.launcher.ui.components.SmartCategoryCard
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoBgEl2
import com.ciyato.launcher.ui.theme.CiyatoBorder
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoShapes
import com.ciyato.launcher.ui.theme.CiyatoWhite
import com.ciyato.launcher.viewmodel.LauncherViewModel

private data class AppLibraryGroup(
    val category: AppCategory,
    val label: String,
    val apps: List<InstalledApp>,
)

private val APP_LIBRARY_ORDER = listOf(
    AppCategory.WORK,
    AppCategory.SOCIAL,
    AppCategory.FINANCE,
    AppCategory.CREATIVITY,
    AppCategory.UTILITIES,
    AppCategory.DAILY,
    AppCategory.COMMUNICATION,
    AppCategory.PRODUCTIVITY,
    AppCategory.ENTERTAINMENT,
    AppCategory.GAMES,
    AppCategory.TRAVEL,
    AppCategory.SHOPPING,
    AppCategory.AI,
    AppCategory.VIDEO_EDITING,
    AppCategory.CONTACTS,
    AppCategory.REVIEW,
)

/**
 * Ciyato's complete app browser. Home is curated; this is intentionally the
 * place to browse every installed launchable app. Preview icons are descriptive
 * only, so a group always has one clear tap target and one expanded state.
 */
@Composable
fun AppDrawerScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var expandedGroup by remember { mutableStateOf<AppLibraryGroup?>(null) }
    var contextMenuApp by remember { mutableStateOf<InstalledApp?>(null) }

    val groups = remember(apps) {
        APP_LIBRARY_ORDER.mapNotNull { category ->
            val categoryApps = apps.filter { it.category == category }
                .sortedBy { it.label.lowercase() }
            categoryApps.takeIf { it.isNotEmpty() }?.let {
                AppLibraryGroup(category, category.displayName, it)
            }
        }
    }
    val groupedPackages = remember(groups) { groups.flatMap { it.apps }.mapTo(mutableSetOf()) { it.packageName } }
    val standaloneApps = remember(apps, groupedPackages) {
        apps.filter { it.packageName !in groupedPackages && it.category != AppCategory.HIDDEN }
            .sortedBy { it.label.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CiyatoBg)
            .padding(top = 18.dp),
    ) {
        Text(
            text = "Apps",
            color = CiyatoWhite,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        CiyatoSearchBar(
            query = query,
            onQueryChange = viewModel::setSearch,
            placeholder = "Search apps",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = CiyatoGold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    Text("Loading apps…", color = CiyatoMuted, fontSize = 13.sp)
                }
            }
            query.isNotBlank() -> SearchResultsGrid(
                query = query,
                // Already relevance-ranked by the repository; re-sorting A→Z here
                // threw that ranking away and put "A…" apps ahead of better matches.
                results = searchResults,
                onAppTap = viewModel::launchApp,
                onAppLongTap = { contextMenuApp = it },
            )
            groups.isEmpty() && standaloneApps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CiyatoEmptyState(
                    icon = Icons.Default.Apps,
                    title = "No apps to show",
                    subtitle = "Apps you've hidden or removed won't appear here. Restore them from Settings to see them again.",
                    modifier = Modifier.padding(32.dp),
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.category.name }) { group ->
                    SmartCategoryCard(
                        category = group.category,
                        displayName = group.label,
                        apps = group.apps,
                        onTap = { expandedGroup = group },
                        tileSize = "medium",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (standaloneApps.isNotEmpty()) {
                    item(key = "standalone") {
                        SmartCategoryCard(
                            category = AppCategory.OTHER,
                            displayName = "Apps",
                            apps = standaloneApps,
                            onTap = { expandedGroup = AppLibraryGroup(AppCategory.OTHER, "Apps", standaloneApps) },
                            tileSize = "medium",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    expandedGroup?.let { group ->
        AppLibraryGroupSheet(
            group = group,
            onDismiss = { expandedGroup = null },
            onLaunch = { app ->
                expandedGroup = null
                viewModel.launchApp(app)
            },
            onLongPress = { contextMenuApp = it },
        )
    }
    contextMenuApp?.let { app ->
        AppContextMenu(app = app, viewModel = viewModel, onDismiss = { contextMenuApp = null })
    }
}

@Composable
private fun AppLibraryGroupTile(group: AppLibraryGroup, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(CiyatoShapes.medium)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoBorder, CiyatoShapes.medium)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            group.apps.take(4).forEach { app ->
                RealAppIcon(
                    drawable = app.icon,
                    size = 32.dp,
                    cornerRadius = 9.dp,
                    scale = app.iconScale,
                    rotation = app.iconRotation,
                    accentHex = app.iconAccent,
                )
            }
        }
        Column {
            Text(group.label, color = CiyatoWhite, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1)
            Text("${group.apps.size} apps", color = CiyatoMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StandaloneAppsTile(apps: List<InstalledApp>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(CiyatoShapes.medium)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoBorder, CiyatoShapes.medium)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.take(4).forEach { app ->
                RealAppIcon(app.icon, size = 32.dp, cornerRadius = 9.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
            }
        }
        Column {
            Text("Apps", color = CiyatoWhite, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text("${apps.size} ungrouped", color = CiyatoMuted, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLibraryGroupSheet(
    group: AppLibraryGroup,
    onDismiss: () -> Unit,
    onLaunch: (InstalledApp) -> Unit,
    onLongPress: (InstalledApp) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CiyatoBgEl,
        contentColor = CiyatoWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
        ) {
            Text(group.label, color = CiyatoWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("${group.apps.size} apps", color = CiyatoMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            HorizontalDivider(color = CiyatoBorder, modifier = Modifier.padding(vertical = 16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(group.apps, key = { app -> app.packageName }) { app ->
                    AppIconTile(
                        app = app,
                        iconSize = 52.dp,
                        labelColor = CiyatoSec,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPress(app) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsGrid(
    query: String,
    results: List<InstalledApp>,
    onAppTap: (InstalledApp) -> Unit,
    onAppLongTap: (InstalledApp) -> Unit,
) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CiyatoEmptyState(
                icon = Icons.Default.SearchOff,
                title = "No apps found",
                subtitle = "Nothing installed matches \"$query\".",
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(results, key = { app -> app.packageName }) { app ->
            AppIconTile(
                app = app,
                iconSize = 52.dp,
                labelColor = CiyatoSec,
                onClick = { onAppTap(app) },
                onLongClick = { onAppLongTap(app) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
