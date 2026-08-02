package com.ciyato.launcher.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

/**
 * HealthConnectWidget — Suggestion #54
 * Steps and heart rate from Health Connect on the home screen.
 * Requires READ_STEPS and READ_HEART_RATE Health Connect permissions.
 * Falls back to showing a placeholder when data isn't available.
 */

data class HealthData(
    val steps: Int = 0,
    val stepGoal: Int = 10_000,
    val heartRate: Int = 0,
    val activeMinutes: Int = 0,
)

@Composable
fun HealthConnectWidget(
    healthData: HealthData = HealthData(),
    modifier: Modifier = Modifier,
) {
    val pct = (healthData.steps.toFloat() / healthData.stepGoal).coerceIn(0f, 1f)
    val animPct = remember { Animatable(0f) }
    LaunchedEffect(pct) { animPct.animateTo(pct, tween(900, easing = EaseOut)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.semantics {
            contentDescription = "${healthData.steps} steps out of ${healthData.stepGoal} goal"
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { animPct.value },
                    modifier = Modifier.size(60.dp),
                    color = Color(0xFF39C66A),
                    trackColor = Color(0xFF1E2128),
                    strokeWidth = 5.dp,
                    strokeCap = StrokeCap.Round,
                )
                Icon(Icons.Default.DirectionsWalk, null, tint = Color(0xFF39C66A),
                    modifier = Modifier.size(24.dp))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${healthData.steps.formatNumber()} steps", color = CiyatoWhite,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Goal: ${healthData.stepGoal.formatNumber()}", color = CiyatoMuted, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { animPct.value },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF39C66A),
                    trackColor = Color(0xFF1E2128),
                )
            }

            if (healthData.heartRate > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp))
                    Text("${healthData.heartRate}", color = CiyatoWhite, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp)
                    Text("bpm", color = CiyatoMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

private fun Int.formatNumber(): String = when {
    this >= 1_000 -> "${this / 1_000}.${(this % 1_000) / 100}k"
    else -> toString()
}
