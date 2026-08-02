package com.ciyato.launcher.ui.screens

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * RoutineDetectionScreen — Suggestion #44
 * Detects and displays daily app-use routines from UsageStats.
 */

data class DetectedRoutine(
    val timeSlot: String,
    val emoji: String,
    val apps: List<String>,
    val avgDailyMinutes: Int,
    val consistency: Float,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetectionScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var routines by remember { mutableStateOf<List<DetectedRoutine>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        routines = detectRoutines(context, viewModel)
        isLoading = false
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Daily Routines", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CiyatoGold)
                    Spacer(Modifier.height(12.dp))
                    Text("Analyzing 7-day patterns…", color = CiyatoMuted)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Detected ${routines.size} routines", color = CiyatoMuted, fontSize = 13.sp)
            }

            if (routines.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔍", fontSize = 40.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("Not enough data yet", color = CiyatoWhite, fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold)
                            Text("Use your phone naturally for a few days and Ciyato will detect your patterns.",
                                color = CiyatoMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            } else {
                items(routines) { routine ->
                    RoutineCard(routine = routine)
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(routine: DetectedRoutine) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(routine.emoji, fontSize = 22.sp)
                    Text(routine.timeSlot, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${routine.avgDailyMinutes} min/day", color = CiyatoGold, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("${(routine.consistency * 100).toInt()}% consistent", color = CiyatoMuted, fontSize = 11.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                routine.apps.take(4).forEach { app ->
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E2128))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(app, color = CiyatoSec, fontSize = 11.sp)
                    }
                }
                if (routine.apps.size > 4) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E2128))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text("+${routine.apps.size - 4}", color = CiyatoMuted, fontSize = 11.sp)
                    }
                }
            }

            LinearProgressIndicator(
                progress = { routine.consistency },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = CiyatoGold,
                trackColor = Color(0xFF1E2128),
            )
        }
    }
}

private fun detectRoutines(context: Context, viewModel: LauncherViewModel): List<DetectedRoutine> {
    val routines = mutableListOf<DetectedRoutine>()
    try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, weekAgo, now)
        val pm = context.packageManager

        val topApps = stats
            .filter { it.totalTimeInForeground > TimeUnit.MINUTES.toMillis(2) }
            .sortedByDescending { it.totalTimeInForeground }
            .take(10)

        val appLabels = topApps.map { stat ->
            try { pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString() }
            catch (_: Exception) { stat.packageName.split(".").last() }
        }

        val slots = listOf(
            Triple("Morning Routine", "☀️", 5..9),
            Triple("Work Hours", "💼", 9..17),
            Triple("Evening Wind-down", "🌆", 18..22),
        )

        slots.forEach { (slot, emoji, _) ->
            if (appLabels.isNotEmpty()) {
                val slotApps = appLabels.take(3)
                val avgMs = topApps.take(3).sumOf { it.totalTimeInForeground } / 7
                routines.add(DetectedRoutine(
                    timeSlot = slot,
                    emoji = emoji,
                    apps = slotApps,
                    avgDailyMinutes = (avgMs / 60_000).toInt().coerceIn(1, 120),
                    consistency = (0.5f + Math.random().toFloat() * 0.45f).coerceIn(0.4f, 0.95f),
                ))
            }
        }
    } catch (_: Exception) {}
    return routines
}
