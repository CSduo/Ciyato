package com.ciyato.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.components.RealAppIcon
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateShortcutsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val allApps by viewModel.apps.collectAsState()
    val multiCatApps = remember(allApps) { viewModel.multiCategoryApps() }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Duplicate Smart Shortcuts",
                        color = CiyatoWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CiyatoSec)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
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
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Explainer card
                item {
                    ExplainerCard()
                }

                // Section header
                item {
                    Text(
                        "Apps in multiple categories",
                        color = CiyatoWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (multiCatApps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("No multi-category apps detected yet.", color = CiyatoMuted)
                        }
                    }
                } else {
                    items(multiCatApps) { app ->
                        DuplicateAppRow(
                            app = app,
                            categories = viewModel.categoriesForApp(app),
                            onTap = { viewModel.launchApp(app) },
                        )
                    }
                }

                // Footer note
                item {
                    FooterNote()
                }
            }
        }
    }
}

@Composable
private fun ExplainerCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoGold.copy(alpha = 0.08f))
            .border(1.dp, CiyatoGold.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CiyatoGold.copy(alpha = 0.18f)),
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
            }
            Text(
                "One app, multiple places.",
                color = CiyatoWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        Text(
            "Ciyato can show one app in several smart category sections at once. For example, WhatsApp might appear in both Social and Communication — without creating a duplicate install.",
            color = CiyatoSec,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(14.dp))
            Text("No duplicate APKs. No cloned apps.", color = CiyatoMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DuplicateAppRow(
    app: InstalledApp,
    categories: List<AppCategory>,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RealAppIcon(drawable = app.icon, size = 44.dp, cornerRadius = 12.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.Text(app.label, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (categories.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(3).forEach { cat ->
                        CategoryChip(cat.displayName)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(name: String) {
    androidx.compose.material3.Text(
        text = name,
        color = CiyatoGold,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CiyatoGold.copy(alpha = 0.12f))
            .border(1.dp, CiyatoGold.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun FooterNote() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CiyatoBgEl)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("About multi-placement", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(
            "Category assignments are computed locally from app package names and keywords. Long-press an app in Home, App Library, or a category to change its category or restore the default.",
            color = CiyatoMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}
