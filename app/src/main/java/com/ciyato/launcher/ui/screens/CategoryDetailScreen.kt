package com.ciyato.launcher.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.data.SearchRankingEngine
import com.ciyato.launcher.ui.components.RealAppIcon
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.pluralStringResource
import com.ciyato.launcher.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    category: AppCategory,
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val allApps by viewModel.apps.collectAsState()
    val categoryRenames by viewModel.categoryRenames.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var contextMenuApp by remember { mutableStateOf<InstalledApp?>(null) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appPickerQuery by remember { mutableStateOf("") }
    var renameValue by remember(category) { mutableStateOf(viewModel.getCategoryDisplayName(category)) }
    val categoryDisplayName = remember(category, categoryRenames) {
        viewModel.getCategoryDisplayName(category)
    }

    val categoryApps = remember(allApps, category) {
        viewModel.byCategory(category)
    }

    val filteredApps = remember(categoryApps, searchQuery) {
        if (searchQuery.isBlank()) categoryApps
        else categoryApps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = categoryDisplayName,
                subtitle = pluralStringResource(R.plurals.count_apps, categoryApps.size, categoryApps.size),
                onBack = onBack,
                subtitleColor = CiyatoGold,
                actions = {
                    IconButton(onClick = {
                        renameValue = categoryDisplayName
                        showManageDialog = true
                    }) {
                        Icon(Icons.Default.Tune, contentDescription = "Manage", tint = CiyatoSec)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to CiyatoBgEl2,
                            0.15f to CiyatoBg,
                            1f to CiyatoBg,
                        )
                    )
                )
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Search bar
                item {
                    CategorySearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                    )
                }

                // Category badge / hero
                item {
                    CategoryHeroBadge(
                        category = category,
                        displayName = categoryDisplayName,
                        onManageApps = { showAppPicker = true }
                    )
                }

                // Apps header
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "All Apps" else "Results (${filteredApps.size})",
                        color = CiyatoWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (filteredApps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (searchQuery.isBlank()) {
                                CiyatoEmptyState(
                                    icon = Icons.Default.Apps,
                                    title = "No apps in this category yet",
                                    subtitle = "Apps show up here once you assign them to $categoryDisplayName.",
                                    actionLabel = "Add Apps",
                                    onAction = { showAppPicker = true },
                                    modifier = Modifier.padding(16.dp),
                                )
                            } else {
                                CiyatoEmptyState(
                                    icon = Icons.Default.SearchOff,
                                    title = "No apps match \"$searchQuery\"",
                                    subtitle = "Try a different search term.",
                                    actionLabel = "Clear Search",
                                    onAction = { searchQuery = "" },
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                } else {
                    // 4-column grid rows
                    val rows = filteredApps.chunked(4)
                    items(rows) { rowApps ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowApps.forEach { app ->
                                AppTile(
                                    app = app,
                                    onTap = { viewModel.launchApp(app) },
                                    onLongTap = { contextMenuApp = app },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(4 - rowApps.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                // Manage section
                item {
                    ManageCategoryCard(displayName = categoryDisplayName)
                }
            }
        }

        if (contextMenuApp != null) {
            AppContextMenu(
                app = contextMenuApp!!,
                viewModel = viewModel,
                onDismiss = { contextMenuApp = null }
            )
        }

        if (showManageDialog) {
            AlertDialog(
                onDismissRequest = { showManageDialog = false },
                containerColor = CiyatoBgEl,
                title = { Text("Rename category", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it.take(24) },
                        singleLine = true,
                        label = { Text("Category name") },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.setCategoryRename(category, renameValue.trim())
                            showManageDialog = false
                        },
                        enabled = renameValue.isNotBlank(),
                    ) {
                        Text("Save", color = CiyatoGold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.setCategoryRename(category, "")
                            renameValue = category.displayName
                            showManageDialog = false
                        },
                    ) {
                        Text("Reset", color = CiyatoSec)
                    }
                },
            )
        }

        if (showAppPicker) {
            // Rank by the visible label, not the package id — an unranked
            // `contains` over packageName made typing "u" surface com.samsung.*
            // apps whose name has no "u" in it, ahead of real name matches.
            val matchingApps = remember(allApps, appPickerQuery) {
                SearchRankingEngine.rankAppsByLabel(allApps, appPickerQuery)
            }
            val currentCategoryPackages = categoryApps.mapTo(mutableSetOf()) { it.packageName }

            AlertDialog(
                onDismissRequest = { showAppPicker = false },
                containerColor = CiyatoBgEl,
                title = { Text("Add / Remove Apps in ${categoryDisplayName}", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = appPickerQuery,
                            onValueChange = { appPickerQuery = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { Text("Search installed apps") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        matchingApps.forEach { app ->
                            val isChecked = app.packageName in currentCategoryPackages
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) {
                                            viewModel.setAppCategoryOverride(app.packageName, null)
                                        } else {
                                            viewModel.setAppCategoryOverride(app.packageName, category)
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RealAppIcon(app.icon, size = 32.dp, cornerRadius = 8.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
                                Text(app.label, color = CiyatoWhite, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            viewModel.setAppCategoryOverride(app.packageName, category)
                                        } else {
                                            viewModel.setAppCategoryOverride(app.packageName, null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAppPicker = false }) {
                        Text("Done", color = CiyatoGold)
                    }
                }
            )
        }
    }
}

@Composable
private fun CategorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = CiyatoMuted, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = CiyatoWhite, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (query.isBlank()) {
                        Text("Search in this category…", color = CiyatoMuted, fontSize = 14.sp)
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CategoryHeroBadge(category: AppCategory, displayName: String, onManageApps: () -> Unit) {
    val categoryIcon = when (category) {
        AppCategory.FINANCE -> Icons.Default.AccountBalanceWallet
        AppCategory.WORK -> Icons.Default.Work
        AppCategory.COMMUNICATION, AppCategory.SOCIAL -> Icons.Default.Chat
        AppCategory.DAILY -> Icons.Default.WbSunny
        AppCategory.UTILITIES -> Icons.Default.Build
        AppCategory.CREATIVITY -> Icons.Default.Palette
        AppCategory.ENTERTAINMENT -> Icons.Default.Movie
        else -> Icons.Default.Folder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CiyatoGold.copy(alpha = 0.08f))
            .border(1.dp, CiyatoGold.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CiyatoGold.copy(alpha = 0.18f)),
        ) {
            Icon(
                imageVector = categoryIcon,
                contentDescription = displayName,
                tint = CiyatoGold,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(displayName, color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Button(
            onClick = onManageApps,
            colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold.copy(alpha = 0.2f), contentColor = CiyatoGold),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("+ Add Apps", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: InstalledApp,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onLongTap: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongTap
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        RealAppIcon(drawable = app.icon, size = 54.dp, cornerRadius = 14.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
        Text(
            text = app.label,
            color = CiyatoSec,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ManageCategoryCard(displayName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("About this category", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(
            "$displayName apps are identified locally from package metadata and your overrides. Long-press an app to change its category, hide it, or remove it from Ciyato.",
            color = CiyatoMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}
