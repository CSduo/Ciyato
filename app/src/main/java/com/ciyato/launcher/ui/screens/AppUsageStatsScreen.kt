package com.ciyato.launcher.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.ciyato.launcher.ui.components.CiyatoEmptyState
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.util.concurrent.TimeUnit

/**
 * AppUsageStatsScreen — Suggestion #19
 * Shows per-app screen time using Android UsageStatsManager.
 */

data class AppUsageStat(
    val appLabel: String,
    val packageName: String,
    val totalTimeMs: Long,
    val lastUsed: Long,
)

@Composable
fun AppUsageStatsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var stats by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }
    var isLoading by remember { mutableStateOf(hasPermission) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            stats = getUsageStats(context)
            isLoading = false
        } else {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(title = "Screen Time", onBack = onBack)
        }
    ) { padding ->
        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("⏱", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Usage Access Required", color = CiyatoWhite, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ciyato needs Usage Access permission to show your screen time breakdown.",
                    color = CiyatoMuted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                ) {
                    Text("Grant Permission", color = Color.Black)
                }
            }
            return@Scaffold
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = CiyatoGold)
                    Text("Reading screen time…", color = CiyatoMuted, fontSize = 14.sp)
                }
            }
            return@Scaffold
        }

        if (stats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CiyatoEmptyState(
                    icon = Icons.Default.HourglassEmpty,
                    title = "No screen time recorded",
                    subtitle = "No app has been used for a full minute in the last 24 hours.",
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Scaffold
        }

        val totalMs = stats.sumOf { it.totalTimeMs }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Today's total", color = CiyatoMuted, fontSize = 13.sp)
                        Text(
                            formatDuration(totalMs),
                            color = CiyatoGold,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text("across ${stats.size} apps", color = CiyatoSec, fontSize = 13.sp)
                    }
                }
            }

            item {
                Text("By App", color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            itemsIndexed(stats.take(20), key = { _, stat -> stat.packageName }) { idx, stat ->
                UsageStatRow(stat = stat, totalMs = totalMs, rank = idx + 1)
            }
        }
    }
}

@Composable
private fun UsageStatRow(stat: AppUsageStat, totalMs: Long, rank: Int) {
    val pct = if (totalMs > 0) (stat.totalTimeMs.toFloat() / totalMs) else 0f
    val animPct = remember { Animatable(0f) }
    LaunchedEffect(pct) {
        animPct.animateTo(pct, animationSpec = tween(800, delayMillis = rank * 40))
    }

    val barColor = when {
        rank == 1 -> CiyatoGold
        rank <= 3 -> CiyatoInfo
        else -> CiyatoSec
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(barColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("#$rank", color = barColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(stat.appLabel, color = CiyatoWhite, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
                Text(formatDuration(stat.totalTimeMs), color = barColor, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color(0xFF1E2128))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animPct.value)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(barColor)
                )
            }
        }
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun getUsageStats(context: Context): List<AppUsageStat> {
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val start = now - TimeUnit.DAYS.toMillis(1)
        val statsMap = usm.queryAndAggregateUsageStats(start, now)
        val pm = context.packageManager
        statsMap.values
            .filter { it.totalTimeInForeground > 60_000L }
            .map { s ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
                } catch (_: Exception) {
                    s.packageName.substringAfterLast('.')
                }
                AppUsageStat(
                    appLabel = label,
                    packageName = s.packageName,
                    totalTimeMs = s.totalTimeInForeground,
                    lastUsed = s.lastTimeUsed,
                )
            }
            .sortedByDescending { it.totalTimeMs }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun formatDuration(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "<1m"
    }
}
