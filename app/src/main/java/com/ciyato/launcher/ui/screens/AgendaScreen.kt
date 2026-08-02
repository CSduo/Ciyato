package com.ciyato.launcher.ui.screens

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dateStr = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }
    val openCalendarInsert: () -> Unit = {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "")
        }
        runCatching { context.startActivity(intent) }
        Unit
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Agenda", color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(dateStr, color = CiyatoSec, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CiyatoSec)
                    }
                },
                actions = {
                    IconButton(onClick = openCalendarInsert) {
                        Icon(Icons.Default.Add, contentDescription = "Add event", tint = CiyatoGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to CiyatoBgEl2,
                            0.22f to CiyatoBg,
                            1f to CiyatoBg,
                        )
                    )
                )
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CiyatoBgEl)
                    .border(1.dp, CiyatoBorder, RoundedCornerShape(24.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(CiyatoGold.copy(alpha = 0.12f))
                        .border(1.dp, CiyatoGold.copy(alpha = 0.22f), CircleShape),
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(28.dp))
                }
                Text("No schedule connected", color = CiyatoWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Ciyato will not show sample meetings. Add an event with your calendar app, or connect calendar access in a later build when the permission flow is ready.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = openCalendarInsert,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold, contentColor = CiyatoBg),
                ) {
                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add in Calendar", fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CiyatoBgEl2)
                        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CiyatoSec, modifier = Modifier.size(18.dp))
                    Text(
                        "Calendar data stays empty until you intentionally add or connect real schedule data.",
                        color = CiyatoMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}
