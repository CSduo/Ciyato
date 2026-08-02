package com.ciyato.launcher.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

@Composable
fun NewsHeadlineWidget(
    modifier: Modifier = Modifier
) {
    val headlines = listOf(
        "Scientific breakthroughs hint at quantum computing enhancements this decade." to "Tech Insider",
        "Economic indices signal positive shifts in international trade routes." to "Financial News",
        "Space exploration programs schedule new lunar orbit launches." to "Aerospace Daily",
        "Smart city developers showcase sustainable low-carbon district models." to "Green Cities"
    )
    var index by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(6000L)
            index = (index + 1) % headlines.size
        }
    }

    val (title, source) = headlines[index]

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
            .clickable {
                index = (index + 1) % headlines.size
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
                    Icons.Default.Article,
                    contentDescription = null,
                    tint = CiyatoGold,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source,
                    color = CiyatoGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(2.dp))
                AnimatedContent(
                    targetState = title,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "news"
                ) { text ->
                    Text(
                        text = text,
                        color = CiyatoWhite,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
