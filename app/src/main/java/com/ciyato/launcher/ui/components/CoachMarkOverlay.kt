package com.ciyato.launcher.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

/**
 * CoachMarkOverlay — Suggestion #105
 * Contextual tooltips / coach marks for new or changed features.
 */

data class CoachMark(
    val id: String,
    val title: String,
    val body: String,
    val emoji: String = "💡",
)

val ALL_COACH_MARKS = listOf(
    CoachMark(
        id = "smart_categories",
        emoji = "✨",
        title = "Smart Categories",
        body = "Ciyato automatically groups your apps by type. Tap any category card to see all apps inside.",
    ),
    CoachMark(
        id = "duplicate_shortcuts",
        emoji = "📌",
        title = "Duplicate Shortcuts",
        body = "The same app can appear in multiple categories — no extra installs. Tap the strip below to manage.",
    ),
    CoachMark(
        id = "long_press",
        emoji = "👆",
        title = "Long-press for Options",
        body = "Long-press any app icon to hide it, pin it to your dock, block it during Focus, or uninstall.",
    ),
    CoachMark(
        id = "focus_session",
        emoji = "🎯",
        title = "Focus Mode",
        body = "Start a Focus Session to block distracting app categories and stay in the zone.",
    ),
    CoachMark(
        id = "theme_studio",
        emoji = "🎨",
        title = "Theme Studio",
        body = "Customize your launcher's look — colors, icon shapes, glass effects, and more.",
    ),
    CoachMark(
        id = "ai_search",
        emoji = "🔍",
        title = "AI Search",
        body = "Type naturally: \"open a music app\", \"find my PDFs\", or \"payment screenshot from yesterday\".",
    ),
)

@Composable
fun CoachMarkBanner(
    mark: CoachMark,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "coach_pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulse_alpha",
    )

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(tween(400)) + fadeIn(tween(400)),
        exit = slideOutVertically(tween(300)) + fadeOut(tween(300)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, CiyatoGold.copy(alpha = pulseAlpha), RoundedCornerShape(16.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(mark.emoji, fontSize = 24.sp, modifier = Modifier.padding(top = 2.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(mark.title, color = CiyatoGold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(mark.body, color = CiyatoSec, fontSize = 13.sp, lineHeight = 18.sp)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, tint = CiyatoMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun rememberCoachMarkController(
    seenKey: String = "seen_coach_marks",
): CoachMarkController {
    return remember { CoachMarkController() }
}

class CoachMarkController {
    private val _seenIds = mutableStateListOf<String>()
    private val _currentMark = mutableStateOf<CoachMark?>(null)

    val currentMark: State<CoachMark?> get() = _currentMark

    fun showIfNew(mark: CoachMark) {
        if (mark.id !in _seenIds) {
            _currentMark.value = mark
        }
    }

    fun dismiss() {
        _currentMark.value?.let { _seenIds.add(it.id) }
        _currentMark.value = null
    }

    fun showNext(marks: List<CoachMark>) {
        val next = marks.firstOrNull { it.id !in _seenIds }
        _currentMark.value = next
    }
}
