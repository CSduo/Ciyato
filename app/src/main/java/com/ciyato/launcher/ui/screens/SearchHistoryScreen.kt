package com.ciyato.launcher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.components.CiyatoEmptyState
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * SearchHistoryScreen — Suggestion #108
 * Shows search history with the ability to tap a query, clear individual entries, or clear all.
 *
 * Backed by [LauncherViewModel.recentSearches] — the same DataStore-persisted
 * list that SearchScreen reads from and writes to — so this screen and the
 * search bar's "Recent" section always agree.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHistoryScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    onQuerySelected: (String) -> Unit,
) {
    val history by viewModel.recentSearches.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear search history?", color = CiyatoWhite) },
            text = { Text("This will remove all ${history.size} saved searches.", color = CiyatoSec) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearRecentSearches()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = CiyatoRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = CiyatoSec)
                }
            },
            containerColor = CiyatoBgEl,
        )
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Search History", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiyatoWhite)
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Clear all", tint = CiyatoRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CiyatoEmptyState(
                    icon = Icons.Default.History,
                    title = "No search history",
                    subtitle = "Apps and terms you search for will be saved here so you can find them again.",
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
        ) {
            items(history, key = { it }) { query ->
                ListItem(
                    headlineContent = { Text(query, color = CiyatoWhite, fontSize = 15.sp) },
                    leadingContent = {
                        Icon(Icons.Default.History, null, tint = CiyatoMuted, modifier = Modifier.size(20.dp))
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeRecentSearch(query) }) {
                            Icon(Icons.Default.Clear, "Remove", tint = CiyatoMuted, modifier = Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.clickable { onQuerySelected(query) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = CiyatoBorder)
            }
        }
    }
}
