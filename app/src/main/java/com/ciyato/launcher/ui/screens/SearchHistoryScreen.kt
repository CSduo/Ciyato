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
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import com.ciyato.launcher.viewmodel.searchHistory
import com.ciyato.launcher.viewmodel.clearSearchHistory
import com.ciyato.launcher.viewmodel.removeSearchQuery

/**
 * SearchHistoryScreen — Suggestion #108
 * Shows search history with the ability to tap a query, clear individual entries, or clear all.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHistoryScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    onQuerySelected: (String) -> Unit,
) {
    val history by viewModel.searchHistory.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear search history?", color = CiyatoWhite) },
            text = { Text("This will remove all ${history.size} saved searches.", color = CiyatoSec) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSearchHistory()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = Color(0xFFEF4444))
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Clear all", tint = Color(0xFFEF4444))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, tint = CiyatoMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No search history", color = CiyatoWhite, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("Your searches will appear here", color = CiyatoMuted, fontSize = 14.sp)
                }
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
                        IconButton(onClick = { viewModel.removeSearchQuery(query) }) {
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
