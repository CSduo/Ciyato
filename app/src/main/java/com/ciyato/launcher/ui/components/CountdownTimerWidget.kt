package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * CountdownTimerWidget — Suggestion #57
 * Shows countdown to user-defined events on the home screen.
 */

data class CountdownEvent(
    val title: String,
    val emoji: String,
    val targetDate: LocalDate,
)

private val DEFAULT_EVENTS = listOf(
    CountdownEvent("New Year 2027", "🎆", LocalDate.of(2027, 1, 1)),
    CountdownEvent("Summer Solstice", "☀️", LocalDate.of(2026, 6, 21)),
    CountdownEvent("End of Q3", "📅", LocalDate.of(2026, 9, 30)),
)

@Composable
fun CountdownTimerWidget(
    events: List<CountdownEvent> = DEFAULT_EVENTS,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }

    val upcoming = events
        .map { it to ChronoUnit.DAYS.between(today, it.targetDate) }
        .filter { (_, days) -> days >= 0 }
        .sortedBy { (_, days) -> days }

    if (upcoming.isEmpty()) return

    val (event, daysLeft) = upcoming.first()

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.semantics {
            contentDescription = "${event.title}: $daysLeft days remaining"
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape)
                    .background(CiyatoGold.copy(alpha = 0.12f))
                    .border(1.dp, CiyatoGold.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(event.emoji, fontSize = 24.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    when {
                        daysLeft == 0L -> "Today! 🎉"
                        daysLeft == 1L -> "Tomorrow"
                        daysLeft < 7 -> "This week"
                        daysLeft < 30 -> "This month"
                        else -> event.targetDate.toString()
                    },
                    color = CiyatoMuted, fontSize = 12.sp,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    daysLeft.toString(),
                    color = CiyatoGold,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                )
                Text("days", color = CiyatoMuted, fontSize = 11.sp)
            }
        }
    }
}
