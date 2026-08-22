package com.ciyato.launcher.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.FocusSessionManager
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.launcher.*
import com.ciyato.launcher.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Small persistent chrome around Home: page dots, the search affordance, the
 * focus badge and the clock string.
 */

/**
 * Bottom workspace map. One dot per page; Home is a small house so the person
 * always knows their position without swiping around. Dots are tappable.
 */
@Composable
internal fun WorkspacePageIndicator(
    pageCount: Int,
    currentPage: Int,
    homePage: Int,
    onDotTap: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        repeat(pageCount) { page ->
            val isCurrent = page == currentPage
            if (page == homePage) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = if (isCurrent) "Home, current page" else "Go to Home",
                    tint = if (isCurrent) CiyatoGold else CiyatoWhite.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onDotTap(page) },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (isCurrent) CiyatoGold else CiyatoWhite.copy(alpha = 0.4f))
                        .clickable { onDotTap(page) },
                )
            }
        }
    }
}

@Composable
internal fun HomeSearchBar(isDense: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier = modifier.height(if (isDense) 50.dp else 56.dp)
        .clip(RoundedCornerShape(999.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(999.dp))
        .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart) {
        Row(modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Search, null, tint = CiyatoMuted, modifier = Modifier.size(18.dp))
            Text("Search apps...", color = CiyatoMuted, fontSize = if (isDense) 14.sp else 15.sp)
        }
    }
}

@Composable
internal fun FocusBadge(session: FocusSessionManager.FocusSession, reduceMotion: Boolean) {
    val pulse = if (reduceMotion) {
        1f
    } else {
        val animatedPulse by rememberInfiniteTransition(label = "focus_pulse").animateFloat(
            initialValue = 1f, targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse",
        )
        animatedPulse
    }
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.scale(pulse).clip(RoundedCornerShape(10.dp))
            .background(CiyatoGold.copy(0.15f)).border(1.dp, CiyatoGold.copy(0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Timer, null, tint = CiyatoGold, modifier = Modifier.size(14.dp))
            Text("%02d:%02d".format(session.remainingMin, session.remainingSec),
                color = CiyatoGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

internal fun currentTimeString(): String =
    SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
