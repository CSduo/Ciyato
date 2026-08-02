package com.ciyato.launcher.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

@Composable
fun DailyAffirmationWidget(
    modifier: Modifier = Modifier
) {
    val affirmations = listOf(
        "Focus on what you can control. Let go of the rest." to "Daily Focus",
        "Consistency is more valuable than sporadic intensity." to "Productivity Tip",
        "Make room for stillness to enhance creativity." to "Mindfulness",
        "A cluttered mind makes tasks look harder than they are." to "Clarity"
    )
    var index by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(8000L)
            index = (index + 1) % affirmations.size
        }
    }

    val (text, topic) = affirmations[index]

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
            .clickable {
                index = (index + 1) % affirmations.size
            }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CiyatoGold.copy(alpha = 0.15f))
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = CiyatoGold,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic,
                    color = CiyatoGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(2.dp))
                AnimatedContent(
                    targetState = text,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "affirmation"
                ) { statement ->
                    Text(
                        text = statement,
                        color = CiyatoWhite,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
