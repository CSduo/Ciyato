package com.ciyato.launcher.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.AppCategorizer
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * SearchScreen — fully enhanced.
 *
 * Suggestions: 36 (recent searches history), 38 (fuzzy search),
 * 40 (NLP intent detection), 42 (grouped results), 44 (Play Store link),
 * 45 (smart suggestion chips by time of day).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    onCategoryFilter: ((AppCategory) -> Unit)? = null,
) {
    val searchQuery     by viewModel.searchQuery.collectAsState()
    val searchResults   by viewModel.searchResults.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val apps            by viewModel.apps.collectAsState()
    val recentSearches  by viewModel.recentSearches.collectAsState()
    val nlpResult       by viewModel.nlpSearchResult.collectAsState()

    val suggestionChips = remember {
        listOf(
            Triple("Work apps", Icons.Default.Work, AppCategory.WORK),
            Triple("Social apps", Icons.Default.Groups, AppCategory.SOCIAL),
            Triple("Games", Icons.Default.SportsEsports, AppCategory.GAMES),
            Triple("Finance", Icons.Default.AccountBalance, AppCategory.FINANCE),
        )
    }

    val frequentApps = remember(apps) { viewModel.byUsageFrequency().take(8) }
    val groups = remember(searchResults) {
        searchResults.groupBy { it.category.displayName }
            .filter { it.value.size >= 2 && it.key != "Other" }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = {
                    CiyatoSearchBar(
                        query = searchQuery,
                        onQueryChange = viewModel::setSearch,
                        placeholder = "Search apps…",
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setSearch(""); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiyatoSec)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Empty state: recent searches + suggestion chips ───────────────
            if (searchQuery.isBlank()) {

                // Recent searches (Suggestion 36)
                if (recentSearches.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("Recent", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                TextButton(onClick = viewModel::clearRecentSearches) {
                                    Text("Clear", color = CiyatoMuted, fontSize = 12.sp)
                                }
                            }
                            recentSearches.forEach { q ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp)).background(CiyatoBgEl)
                                        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(12.dp))
                                        .clickable { viewModel.setSearch(q) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.History, null, tint = CiyatoMuted, modifier = Modifier.size(16.dp))
                                        Text(q, color = CiyatoSec, fontSize = 14.sp)
                                    }
                                    IconButton(onClick = { viewModel.removeRecentSearch(q) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, "Remove", tint = CiyatoMuted, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Suggestion chips (Suggestion 45)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Try searching for", color = CiyatoSec, fontSize = 13.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(suggestionChips, key = { it.first }) { (label, icon, cat) ->
                                SuggestionChip(label, icon) {
                                    viewModel.recordSearch(label)
                                    viewModel.setSearch(label)
                                    onCategoryFilter?.invoke(cat)
                                }
                            }
                        }
                    }
                }

                // Most-used apps (Suggestion 37)
                if (frequentApps.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Most Used", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(frequentApps, key = { it.packageName }) { app ->
                                    AppIconTile(app = app, iconSize = 48.dp,
                                        onClick = { viewModel.launchApp(app) }, modifier = Modifier.width(60.dp))
                                }
                            }
                        }
                    }
                }
            } else {

                // ── NLP intent detected (Suggestion 40) ──────────────────────
                nlpResult?.let { (detectedCat, _) ->
                    if (detectedCat != null) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CiyatoGold.copy(0.08f))
                                .border(1.dp, CiyatoGold.copy(0.2f), RoundedCornerShape(12.dp))
                                .clickable { onCategoryFilter?.invoke(detectedCat) }
                                .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = CiyatoGold, modifier = Modifier.size(16.dp))
                                    Text("Looking for ${detectedCat.displayName} apps?", color = CiyatoGold, fontSize = 13.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // ── Loading ───────────────────────────────────────────────────
                if (isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CiyatoGold, strokeWidth = 2.dp)
                        }
                    }
                } else if (searchResults.isEmpty()) {
                    // No results — fuzzy fallback + Play Store suggestion (Suggestion 44)
                    item {
                        NoResultsCard(query = searchQuery, onPlayStore = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://search?q=${searchQuery}"))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            try { it.startActivity(intent) } catch (_: Exception) {}
                        })
                    }
                } else {
                    // ── App results ───────────────────────────────────────────
                    item {
                        Row(horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Apps", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("${searchResults.size} found", color = CiyatoMuted, fontSize = 12.sp)
                        }
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(searchResults.take(16), key = { it.packageName }) { app ->
                                AppIconTile(app = app, iconSize = 52.dp,
                                    onClick = {
                                        viewModel.recordSearch(searchQuery)
                                        viewModel.launchApp(app)
                                    },
                                    modifier = Modifier.width(64.dp))
                            }
                        }
                    }

                    // ── Category grouping (Suggestion 42) ────────────────────
                    if (groups.isNotEmpty()) {
                        item { Text("By category", color = CiyatoSec, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        groups.entries.take(4).forEach { (cat, apps) ->
                            item {
                                CategoryResultRow(categoryName = cat, apps = apps) { app ->
                                    viewModel.launchApp(app)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



// ─── Suggestion chip ──────────────────────────────────────────────────────────

@Composable
private fun SuggestionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(999.dp))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = CiyatoGold, modifier = Modifier.size(14.dp))
        Text(label, color = CiyatoSec, fontSize = 13.sp)
    }
}

// ─── Category result row ──────────────────────────────────────────────────────

@Composable
private fun CategoryResultRow(
    categoryName: String,
    apps: List<com.ciyato.launcher.data.InstalledApp>,
    onAppTap: (com.ciyato.launcher.data.InstalledApp) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(categoryName, color = CiyatoGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(apps.take(8), key = { it.packageName }) { app ->
                AppIconTile(app = app, iconSize = 44.dp, onClick = { onAppTap(app) }, modifier = Modifier.width(56.dp))
            }
        }
    }
}

// ─── No results ───────────────────────────────────────────────────────────────

@Composable
private fun NoResultsCard(query: String, onPlayStore: (android.content.Context) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(20.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.SearchOff, null, tint = CiyatoMuted, modifier = Modifier.size(36.dp))
        Text("No apps match \"$query\"", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
        Text("Nothing installed matches that search.", color = CiyatoSec, fontSize = 13.sp)
        OutlinedButton(onClick = { onPlayStore(context) }, shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CiyatoGold),
            border = androidx.compose.foundation.BorderStroke(1.dp, CiyatoGold.copy(0.4f)),
            modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Icon(Icons.Default.Shop, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Find \"$query\" on Play Store", fontSize = 13.sp)
        }
    }
}
