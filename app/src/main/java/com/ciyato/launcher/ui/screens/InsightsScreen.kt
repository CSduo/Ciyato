package com.ciyato.launcher.ui.screens

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite

/**
 * One entry point for everything built on Usage Access (F-130).
 *
 * Four separate features — Screen Time, Today's Summary, Frequent Apps and
 * Unusual Usage — all read the same permission, all sat as equal-weight rows in a
 * flat Settings list, and each asked for Usage Access on its own. Granting it in
 * one place told you nothing about the others, and the same permission could be
 * requested four times.
 *
 * They are gathered here behind a single grant, each described by what it
 * actually measures. That second part matters more than the grouping: all four
 * derive from coarse per-app foreground totals, so they can say how long an app
 * was open and nothing about why. The descriptions say so instead of implying
 * insight the data cannot support.
 *
 * Known remaining work, recorded rather than glossed: the four still render as
 * separate destinations. Folding their bodies into tabs here is the end state and
 * is tracked in CLAUDE_REMOVAL_SALVAGE_LEDGER.md.
 */
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    onOpenScreenTime: () -> Unit,
    onOpenTodaySummary: () -> Unit,
    onOpenSuggestions: () -> Unit,
    onOpenAnomalies: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasUsageAccess(context)) }

    // Usage Access is granted in system settings, so the answer changes while
    // Ciyato is in the background.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = hasUsageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Insights",
                subtitle = "Built from app usage on this phone",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(CiyatoBgEl)
                        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (granted) "What this can and cannot tell you" else "Usage Access needed",
                        color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    )
                    Text(
                        if (granted) {
                            "Android reports how long each app was in the foreground. That is all " +
                                "these use, so they can show what you spent time on — not why, and " +
                                "not what you were doing inside an app."
                        } else {
                            "All four features read the same Android permission. Granting it once " +
                                "enables every one of them, and nothing is sent off this phone."
                        },
                        color = CiyatoMuted, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                    if (!granted) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Open Usage Access settings",
                            color = CiyatoBg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(CiyatoGold)
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                                        )
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }

            item {
                InsightEntry(
                    icon = Icons.Default.BarChart,
                    title = "Screen Time",
                    description = "How long each app was open today, counted from midnight.",
                    onClick = onOpenScreenTime,
                )
            }
            item {
                InsightEntry(
                    icon = Icons.Default.Today,
                    title = "Today's Summary",
                    description = "New installs, and apps used more or less than their recent average.",
                    onClick = onOpenTodaySummary,
                )
            }
            item {
                InsightEntry(
                    icon = Icons.Default.Lightbulb,
                    title = "Frequent Apps",
                    // Named for what it measures. It ranks by how often apps are
                    // opened; it does not learn time-of-day habits (F-122).
                    description = "The apps you open most often. Ordering only, not prediction.",
                    onClick = onOpenSuggestions,
                )
            }
            item {
                InsightEntry(
                    icon = Icons.Default.TrendingUp,
                    title = "Unusual Usage",
                    description = "Apps well above or below their own recent daily average.",
                    onClick = onOpenAnomalies,
                )
            }
        }
    }
}

@Composable
private fun InsightEntry(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CiyatoBgEl)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, color = CiyatoMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/**
 * Shared Usage Access check.
 *
 * Four screens each rolled their own copy of this. One place means one answer,
 * and one thing to fix when the platform API changes again.
 */
internal fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
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
