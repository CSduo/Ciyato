package com.ciyato.launcher.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.FocusSessionManager
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * Focus Session Screen — Suggestion #75.
 *
 * Allows the user to:
 *  - Set a focus duration (5–120 min, Pomodoro presets)
 *  - Choose which categories are blocked
 *  - Start / end the session
 *  - See a live countdown ring while active
 *
 * Blocked categories are hidden from the home screen during focus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSessionScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val activeSession   by viewModel.focusSession.collectAsState()
    val focusDuration   by viewModel.focusDurationMin.collectAsState()
    val focusBlockedCsv by viewModel.focusBlockedCats.collectAsState()

    val blockedCats: List<AppCategory> = remember(focusBlockedCsv) {
        focusBlockedCsv.split(",").mapNotNull { runCatching { AppCategory.valueOf(it.trim()) }.getOrNull() }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Focus",
                subtitle = "Minimize distractions",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (activeSession != null) {
                // ── Active session view ───────────────────────────────────────
                item { ActiveSessionCard(session = activeSession!!, onEnd = viewModel::endFocusSession) }
                item { BlockedCategoryList(cats = activeSession!!.blockedCategories) }
            } else {
                // ── Setup view ────────────────────────────────────────────────
                item { FocusInfoCard() }
                item {
                    DurationSelector(
                        selectedMin = focusDuration,
                        onSelect    = viewModel::setFocusDurationMin,
                    )
                }
                item {
                    BlockCategorySelector(
                        selectedCats = blockedCats,
                        onToggle     = { cat ->
                            val mutable = blockedCats.toMutableList()
                            if (cat in mutable) mutable.remove(cat) else mutable.add(cat)
                            viewModel.setFocusBlockedCats(mutable.joinToString(","))
                        },
                        viewModel    = viewModel,
                    )
                }
                item {
                    CiyatoButton(
                        text = "Start $focusDuration-min Focus",
                        onClick = viewModel::startFocusSession,
                        leadingIcon = Icons.Default.Timer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── Active session card with countdown ring ────────────────────────────────────

@Composable
private fun ActiveSessionCard(
    session: FocusSessionManager.FocusSession,
    onEnd: () -> Unit,
) {
    val progress  = session.progressFraction
    val ringColor = CiyatoGold
    val bgColor   = CiyatoBgEl

    // Pulse animation
    val pulse by rememberPulse(1f, 1.04f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(CiyatoBgEl2, CiyatoBgEl)))
            .border(1.dp, CiyatoGold.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Focus Session Active", color = CiyatoGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)

        // Ring countdown
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(180.dp).scale(pulse),
        ) {
            val sweep = 360f * progress
            Box(modifier = Modifier.fillMaxSize().drawBehind {
                val stroke = Stroke(width = 10.dp.toPx())
                drawCircle(color = bgColor, style = stroke)
                drawArc(
                    color     = ringColor,
                    startAngle= -90f,
                    sweepAngle= sweep,
                    useCenter = false,
                    style     = stroke,
                )
            })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%02d:%02d".format(session.remainingMin, session.remainingSec),
                    color = CiyatoWhite,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text("remaining", color = CiyatoMuted, fontSize = 12.sp)
            }
        }

        Text(
            "Distracting apps are blocked.\nStay focused! \uD83D\uDCAA",
            color = CiyatoSec,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        OutlinedButton(
            onClick = onEnd,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
        ) {
            Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("End Session", fontSize = 14.sp)
        }
    }
}

// ── Duration selector ──────────────────────────────────────────────────────────

private val PRESETS = listOf(15, 25, 45, 60, 90)

@Composable
private fun DurationSelector(selectedMin: Int, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Duration", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { min ->
                val selected = selectedMin == min
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f).height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) CiyatoGold else CiyatoBgEl2)
                        .clickable { onSelect(min) },
                ) {
                    Text("${min}m", color = if (selected) CiyatoBg else CiyatoSec,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Category selector ─────────────────────────────────────────────────────────

private val BLOCKABLE_CATS = listOf(
    AppCategory.SOCIAL, AppCategory.ENTERTAINMENT, AppCategory.GAMES,
    AppCategory.SHOPPING, AppCategory.COMMUNICATION,
)

@Composable
private fun BlockCategorySelector(
    selectedCats: List<AppCategory>,
    onToggle: (AppCategory) -> Unit,
    viewModel: LauncherViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Block categories", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text("These app categories will be hidden during focus.", color = CiyatoMuted, fontSize = 12.sp)
        BLOCKABLE_CATS.forEach { cat ->
            val isBlocked = cat in selectedCats
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isBlocked) CiyatoGold.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable { onToggle(cat) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(viewModel.getCategoryDisplayName(cat), color = CiyatoWhite, fontSize = 14.sp)
                Checkbox(
                    checked = isBlocked, onCheckedChange = { onToggle(cat) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = CiyatoGold, uncheckedColor = CiyatoMuted, checkmarkColor = CiyatoBg,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BlockedCategoryList(cats: List<AppCategory>) {
    if (cats.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Blocked during this session", color = CiyatoSec, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        cats.forEach { cat ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CiyatoGold))
                Text(cat.displayName, color = CiyatoWhite, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun FocusInfoCard() {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CiyatoGold.copy(alpha = 0.08f))
            .border(1.dp, CiyatoGold.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.Timer, null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
        Text(
            "Focus Mode hides distracting app categories from your home screen for the chosen duration. Apps are still accessible from the full drawer.",
            color = CiyatoSec, fontSize = 12.sp, lineHeight = 18.sp,
        )
    }
}


