package com.ciyato.launcher.ui.screens

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.RemoteException
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.util.concurrent.TimeUnit

/**
 * NetworkUsageScreen — Suggestion #80
 * Shows per-app network (data) usage for the current billing cycle.
 */

data class AppNetworkStat(
    val appLabel: String,
    val uid: Int,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes get() = rxBytes + txBytes
}

@Composable
fun NetworkUsageScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var stats by remember { mutableStateOf<List<AppNetworkStat>>(emptyList()) }
    var isLoading by remember { mutableStateOf(hasPermission) }

    // NetworkStatsManager.querySummary requires the same "Usage access" special
    // app-op as screen-time stats. Granting happens in system Settings, so
    // re-check on resume rather than relying on a runtime permission callback.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            stats = getNetworkStats(context)
            isLoading = false
        } else {
            isLoading = false
        }
    }

    val totalRx = stats.sumOf { it.rxBytes }
    val totalTx = stats.sumOf { it.txBytes }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(title = "Data Usage", onBack = onBack)
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
                Text("📡", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Usage Access Required", color = CiyatoWhite, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ciyato needs Usage Access permission to break down mobile data by app.",
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
            return@Scaffold
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = CiyatoGold)
                    Text("Reading data usage…", color = CiyatoMuted, fontSize = 14.sp)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        NetStat("↓ Download", formatBytes(totalRx), Color(0xFF39C66A))
                        NetStat("↑ Upload", formatBytes(totalTx), Color(0xFF7DB7FF))
                        NetStat("Total", formatBytes(totalRx + totalTx), CiyatoGold)
                    }
                }
            }

            item {
                Text("By App (30 days)", color = CiyatoWhite, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold)
            }

            if (stats.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📡", fontSize = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("No mobile data usage", color = CiyatoMuted, fontWeight = FontWeight.SemiBold)
                            Text("No app has used mobile data in the last 30 days. Wi-Fi usage isn't counted here.",
                                color = CiyatoMuted, fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            } else {
                val maxBytes = stats.firstOrNull()?.totalBytes?.toFloat() ?: 1f
                itemsIndexed(stats.take(20), key = { _, stat -> stat.uid }) { idx, stat ->
                    NetworkStatRow(stat = stat, maxBytes = maxBytes, rank = idx + 1)
                }
            }
        }
    }
}

@Composable
private fun NetworkStatRow(stat: AppNetworkStat, maxBytes: Float, rank: Int) {
    val pct = stat.totalBytes.toFloat() / maxBytes
    val anim = remember { Animatable(0f) }
    LaunchedEffect(pct) { anim.animateTo(pct, tween(700, delayMillis = rank * 30)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stat.appLabel, color = CiyatoWhite, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    modifier = Modifier.weight(1f))
                Text(formatBytes(stat.totalBytes), color = CiyatoGold, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("↓ ${formatBytes(stat.rxBytes)}", color = Color(0xFF39C66A), fontSize = 11.sp)
                Text("↑ ${formatBytes(stat.txBytes)}", color = Color(0xFF7DB7FF), fontSize = 11.sp)
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFF1E2128))
            ) {
                Box(modifier = Modifier.fillMaxWidth(anim.value).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp)).background(CiyatoGold))
            }
        }
    }
}

@Composable
private fun NetStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = CiyatoMuted, fontSize = 11.sp)
    }
}

/** Same "Usage access" special app-op that gates UsageStatsManager also gates NetworkStatsManager#querySummary. */
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

private fun getNetworkStats(context: Context): List<AppNetworkStat> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
    return try {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val monthAgo = now - TimeUnit.DAYS.toMillis(30)

        val bucket = NetworkStats.Bucket()
        val statsMap = mutableMapOf<Int, Pair<Long, Long>>()

        val summary = nsm.querySummary(
            ConnectivityManager.TYPE_MOBILE, null, monthAgo, now
        )
        while (summary.hasNextBucket()) {
            summary.getNextBucket(bucket)
            val uid = bucket.uid
            val (rx, tx) = statsMap.getOrDefault(uid, 0L to 0L)
            statsMap[uid] = (rx + bucket.rxBytes) to (tx + bucket.txBytes)
        }
        summary.close()

        statsMap.entries
            .filter { it.value.first + it.value.second > 0 }
            .mapNotNull { (uid, bytes) ->
                val label = try {
                    val packages = pm.getPackagesForUid(uid)
                    val pkg = packages?.firstOrNull() ?: return@mapNotNull null
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) { "UID $uid" }
                AppNetworkStat(appLabel = label, uid = uid, rxBytes = bytes.first, txBytes = bytes.second)
            }
            .sortedByDescending { it.totalBytes }
    } catch (_: Exception) { emptyList() }
}

internal fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
