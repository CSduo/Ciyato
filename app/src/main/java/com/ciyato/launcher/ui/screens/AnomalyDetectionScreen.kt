package com.ciyato.launcher.ui.screens

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.ErrorOutline
import com.ciyato.launcher.data.UsageAnomalies

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
    var outcome by remember { mutableStateOf<AnomalyOutcome?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = hasUsageStatsPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission) {
        outcome = if (hasPermission) {
            withContext(Dispatchers.IO) { detectAnomalies(context) }
        } else {
            null
        }
        isLoading = false
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Usage Anomalies",
                subtitle = "7-day z-score analysis",
                onBack = onBack,
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
            !hasPermission -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("📊", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Usage Access Required", color = CiyatoWhite, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ciyato needs Usage Access permission to detect unusual app usage.",
                    color = CiyatoMuted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                ) {
                    Text("Grant Permission", color = Color.Black)
                }
            }
            // A failure and a clean result are different answers, and now look
            // different. The reassuring green tick is reserved for an analysis
            // that actually ran (F-129).
            outcome is AnomalyOutcome.Unavailable -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = CiyatoMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Could not analyse usage", color = CiyatoWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        (outcome as AnomalyOutcome.Unavailable).reason,
                        color = CiyatoMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            outcome is AnomalyOutcome.NotEnoughHistory -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Icon(Icons.Default.HourglassEmpty, null, tint = CiyatoMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Not enough history yet", color = CiyatoWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Judging a day needs several finished days before it. Check back " +
                            "after a few more days of normal use.",
                        color = CiyatoMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            (outcome as? AnomalyOutcome.Analysed)?.anomalies.isNullOrEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = CiyatoGreen, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Nothing unusual", color = CiyatoWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Compared the last finished day against the days before it for " +
                            ((outcome as AnomalyOutcome.Analysed).appsJudged).toString() + " apps.",
                        color = CiyatoMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
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
                items((outcome as AnomalyOutcome.Analysed).anomalies) { anomaly ->
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
                "z=${String.format(java.util.Locale.getDefault(), "%.1f", anomaly.zScore)}",
                color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Analyses the last week of daily usage.
 *
 * Two things changed here. The comparison uses complete days only - the day in
 * progress was being measured against finished ones, so every morning produced a
 * wave of "unusual drops" that were nothing but the clock (F-128). And a failure
 * is no longer indistinguishable from a clean result: a thrown query used to
 * return an empty list, which the screen rendered as "Your usage patterns look
 * consistent" (F-129).
 */
private fun detectAnomalies(context: Context): AnomalyOutcome {
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // Eight days: seven complete, plus the one in progress that gets dropped.
        val windowMs = 8L * 24 * 60 * 60 * 1000

        val weekStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - windowMs, now)
        if (weekStats.isEmpty()) {
            return AnomalyOutcome.Unavailable(
                "Android returned no usage history. It can take a day of normal use " +
                    "before there is anything to compare.",
            )
        }

        val byPackage = weekStats.sortedBy { it.firstTimeStamp }.groupBy { it.packageName }
        val pm = context.packageManager
        val results = mutableListOf<UsageAnomaly>()
        var judged = 0

        byPackage.forEach { (pkg, stats) ->
            val verdict = UsageAnomalies.analyse(stats.map { it.totalTimeInForeground })
                ?: return@forEach
            judged++
            if (!verdict.isUnusual) return@forEach
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg
            }
            results.add(
                UsageAnomaly(
                    packageName = pkg,
                    label = label,
                    todayMs = verdict.dayMs,
                    avgMs = verdict.meanMs,
                    zScore = verdict.zScore,
                    isSpike = verdict.isIncrease,
                ),
            )
        }

        if (judged == 0) {
            AnomalyOutcome.NotEnoughHistory(byPackage.size)
        } else {
            AnomalyOutcome.Analysed(results.sortedByDescending { abs(it.zScore) }.take(10), judged)
        }
    } catch (e: Exception) {
        // Swallowing this into an empty list is how a failed analysis became a
        // clean bill of health.
        AnomalyOutcome.Unavailable(
            "Usage history could not be read (" + e.javaClass.simpleName + "). Nothing was analysed.",
        )
    }
}

/** What [detectAnomalies] found, or why it could not look. */
private sealed interface AnomalyOutcome {
    data class Analysed(val anomalies: List<UsageAnomaly>, val appsJudged: Int) : AnomalyOutcome
    data class NotEnoughHistory(val appsSeen: Int) : AnomalyOutcome
    data class Unavailable(val reason: String) : AnomalyOutcome
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
