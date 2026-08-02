package com.ciyato.launcher.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * StorageVisualizationScreen — Suggestion #65
 * Breakdown of storage usage by category (Photos, Videos, Apps, Documents, etc.)
 */

data class StorageCategory(
    val name: String,
    val usedGb: Float,
    val color: Color,
    val icon: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageVisualizationScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val totalGb = 128f
    val categories = listOf(
        StorageCategory("Photos", 12.4f, Color(0xFF7DB7FF), "🖼"),
        StorageCategory("Videos", 8.2f, Color(0xFFE1306C), "🎬"),
        StorageCategory("Apps", 6.7f, Color(0xFF5E5CE6), "📱"),
        StorageCategory("Music", 4.1f, Color(0xFF1DB954), "🎵"),
        StorageCategory("Downloads", 2.9f, Color(0xFFFF9500), "⬇"),
        StorageCategory("Documents", 1.8f, Color(0xFFC8C8C4), "📄"),
        StorageCategory("Other", 3.1f, Color(0xFF6E6E73), "📦"),
    )
    val usedGb = categories.sumOf { it.usedGb.toDouble() }.toFloat()
    val freeGb = totalGb - usedGb
    val usedPct = (usedGb / totalGb * 100).toInt()

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(1f, animationSpec = tween(1200, easing = EaseOutCubic))
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Storage", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Donut chart ────────────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            StorageDonut(
                                categories = categories,
                                totalGb = totalGb,
                                animProgress = animProgress.value,
                                modifier = Modifier.size(180.dp),
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$usedPct%", color = CiyatoWhite, fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold)
                                Text("Used", color = CiyatoMuted, fontSize = 13.sp)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            StorageStat("Used", "${String.format("%.1f", usedGb)} GB", CiyatoGold)
                            StorageStat("Free", "${String.format("%.1f", freeGb)} GB", Color(0xFF39C66A))
                            StorageStat("Total", "$totalGb GB", CiyatoSec)
                        }
                    }
                }
            }

            // ── Category breakdown ─────────────────────────────────────────────
            item {
                Text("Breakdown", color = CiyatoWhite, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold)
            }

            items(categories, key = { it.name }) { cat ->
                StorageCategoryRow(cat = cat, totalGb = totalGb, animProgress = animProgress.value)
            }

            // ── Cleanup status ────────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("•", fontSize = 24.sp, color = CiyatoSec)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cleanup is off by default",
                                color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                            Text("No files are suggested or deleted until a real folder-based cleanup flow is connected.",
                                color = CiyatoMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageDonut(
    categories: List<StorageCategory>,
    totalGb: Float,
    animProgress: Float,
    modifier: Modifier = Modifier,
) {
    val strokeWidth = 22f
    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height) - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)

        // Background ring
        drawArc(
            color = Color(0xFF1E2128),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )

        var sweepOffset = -90f
        categories.forEach { cat ->
            val sweep = (cat.usedGb / totalGb) * 360f * animProgress
            drawArc(
                color = cat.color,
                startAngle = sweepOffset,
                sweepAngle = sweep - 2f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            sweepOffset += sweep
        }
    }
}

@Composable
private fun StorageCategoryRow(
    cat: StorageCategory,
    totalGb: Float,
    animProgress: Float,
) {
    val pct = cat.usedGb / totalGb

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(cat.icon, fontSize = 22.sp)
                    Text(cat.name, color = CiyatoWhite, fontWeight = FontWeight.Medium)
                }
                Text("${String.format("%.1f", cat.usedGb)} GB",
                    color = CiyatoSec, fontWeight = FontWeight.SemiBold)
            }
            // Animated progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E2128))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct * animProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(cat.color)
                )
            }
        }
    }
}

@Composable
private fun StorageStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = CiyatoMuted, fontSize = 12.sp)
    }
}
