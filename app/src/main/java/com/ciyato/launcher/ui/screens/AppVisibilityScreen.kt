package com.ciyato.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.components.RealAppIcon
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite
import com.ciyato.launcher.viewmodel.LauncherViewModel

enum class AppVisibilityMode {
    Hidden,
    Removed,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppVisibilityScreen(
    mode: AppVisibilityMode,
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val allApps by viewModel.allApps.collectAsState()
    val hiddenCsv by viewModel.hiddenApps.collectAsState()
    val removedCsv by viewModel.removedApps.collectAsState()
    val hidden = hiddenCsv.toPackageSet()
    val removed = removedCsv.toPackageSet()
    val apps = allApps.filter {
        when (mode) {
            AppVisibilityMode.Hidden -> it.packageName in hidden
            AppVisibilityMode.Removed -> it.packageName in removed
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = apps.filter {
        searchQuery.isBlank() ||
            it.label.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
    }
    val title = if (mode == AppVisibilityMode.Hidden) "Hidden Apps" else "Removed Apps"
    val description = if (mode == AppVisibilityMode.Hidden) {
        "Hidden apps stay installed but do not appear in Home, categories, the App Library, or normal search."
    } else {
        "Removed apps stay installed. Ciyato only removes them from its visible launcher surfaces."
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = title,
                subtitle = "Manage launcher presence",
                onBack = onBack,
                actions = {
                    if (apps.isNotEmpty()) {
                        TextButton(onClick = { apps.forEach { viewModel.restoreApp(it.packageName) } }) {
                            Text("Restore all", color = CiyatoGold, fontSize = 12.sp)
                        }
                    }
                }
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CiyatoGold.copy(alpha = 0.08f))
                        .border(1.dp, CiyatoGold.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        if (mode == AppVisibilityMode.Hidden) Icons.Default.VisibilityOff else Icons.Default.RemoveCircleOutline,
                        contentDescription = null,
                        tint = CiyatoGold,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$title do not get uninstalled", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                        Text(description, color = CiyatoSec, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            if (apps.isNotEmpty()) {
                item {
                    CiyatoSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search ${title.lowercase()}",
                        onClear = { searchQuery = "" }
                    )
                }
            }

            if (filteredApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (apps.isEmpty()) "No ${title.lowercase()}" else "No apps match \"$searchQuery\"",
                            color = CiyatoMuted
                        )
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    VisibilityAppRow(app = app, onRestore = { viewModel.restoreApp(app.packageName) })
                }
            }
        }
    }
}

@Composable
private fun VisibilityAppRow(app: InstalledApp, onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RealAppIcon(drawable = app.icon, size = 44.dp, cornerRadius = 12.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, color = CiyatoWhite, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(app.packageName, color = CiyatoMuted, fontSize = 10.sp, maxLines = 1)
        }
        Button(
            onClick = onRestore,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CiyatoGold.copy(alpha = 0.16f),
                contentColor = CiyatoGold,
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
            Text("Restore", fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

private fun String.toPackageSet(): Set<String> =
    split(",").map(String::trim).filter(String::isNotEmpty).toSet()
