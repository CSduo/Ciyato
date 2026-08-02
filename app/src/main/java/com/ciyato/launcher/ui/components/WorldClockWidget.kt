package com.ciyato.launcher.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
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
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WorldClockWidget(
    modifier: Modifier = Modifier
) {
    val zones = listOf(
        "Local" to TimeZone.getDefault(),
        "New York" to TimeZone.getTimeZone("America/New_York"),
        "London" to TimeZone.getTimeZone("Europe/London"),
        "Tokyo" to TimeZone.getTimeZone("Asia/Tokyo")
    )
    var selectedIndex by remember { mutableStateOf(0) }
    var timeString by remember { mutableStateOf("") }
    var dateString by remember { mutableStateOf("") }

    val activeZone = zones[selectedIndex]

    LaunchedEffect(selectedIndex) {
        while (true) {
            val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply {
                timeZone = activeZone.second
            }
            val sdfDate = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).apply {
                timeZone = activeZone.second
            }
            timeString = sdfTime.format(Date())
            dateString = sdfDate.format(Date())
            kotlinx.coroutines.delay(1000L)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
            .clickable {
                selectedIndex = (selectedIndex + 1) % zones.size
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
                    .background(CiyatoBlue.copy(alpha = 0.15f))
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = CiyatoBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeZone.first,
                    color = CiyatoWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = dateString,
                    color = CiyatoSec,
                    fontSize = 11.sp
                )
            }
            Text(
                text = timeString,
                color = CiyatoGold,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}
