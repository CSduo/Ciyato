package com.ciyato.launcher.ui.screens

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * AnomalyDetectionScreen — Suggestion #37
 * App usage pattern anomaly detection using z-score on daily usage data.
 * Highlights apps whose usage today deviates significantly from the 7-day mean.
 */

data class UsageAnomaly(
    val packageName: String,
    val label: String,
    val todayMs: Long,
    val avgMs: Long,
    val zScore: Float,
    val isSpike: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnomalyDetectionScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var anomalies by remember { mutableStateOf<List<UsageAnomaly>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        anomalies = withContext(Dispatchers.IO) { detectAnomalies(context) }
        isLoading = false
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Usage Anomalies", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CiyatoGold)
                    Spacer(Modifier.height(8.dp))
                    Text("Analysing 7-day usage patterns…", color = CiyatoMuted)
                }
            }
            anomalies.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No anomalies detected", color = CiyatoWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Your usage patterns look consistent.", color = CiyatoMuted, fontSize = 13.sp)
                }
            }
            else -> LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("Today's Unusual Usage", color = CiyatoMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(anomalies) { anomaly ->
                    AnomalyCard(anomaly)
                }
            }
        }
    }
}

@Composable
private fun AnomalyCard(anomaly: UsageAnomaly) {
    val color = if (anomaly.isSpike) Color(0xFFFF9800) else Color(0xFF42A5F5)
    val icon  = if (anomaly.isSpike) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(anomaly.label, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "${if (anomaly.isSpike) "↑ Spike" else "↓ Drop"} — Today: ${anomaly.todayMs / 60000}min, Avg: ${anomaly.avgMs / 60000}min",
                    color = CiyatoMuted, fontSize = 12.sp,
                )
            }
            Text(
                "z=${String.format("%.1f", anomaly.zScore)}",
                color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun detectAnomalies(context: Context): List<UsageAnomaly> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return emptyList()
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val dayMs  = 24L * 60 * 60 * 1000

        val weekStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - weekMs, now)
            .groupBy { it.packageName }

        val pm = context.packageManager
        val results = mutableListOf<UsageAnomaly>()

        weekStats.forEach { (pkg, stats) ->
            if (stats.size < 3) return@forEach
            val times = stats.map { it.totalTimeInForeground }
            val todayMs = times.last()
            val history = times.dropLast(1)
            val mean = history.average()
            val std  = sqrt(history.map { (it - mean) * (it - mean) }.average()).toFloat()
            if (std < 60_000) return@forEach

            val zScore = ((todayMs - mean) / std).toFloat()
            if (abs(zScore) >= 2.0f) {
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
                results.add(UsageAnomaly(
                    packageName = pkg,
                    label = label,
                    todayMs = todayMs,
                    avgMs = mean.toLong(),
                    zScore = zScore,
                    isSpike = zScore > 0,
                ))
            }
        }
        results.sortedByDescending { abs(it.zScore) }.take(10)
    } catch (_: Exception) { emptyList() }
}
