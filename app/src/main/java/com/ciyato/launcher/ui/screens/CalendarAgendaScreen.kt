package com.ciyato.launcher.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * CalendarAgendaScreen — Suggestion #51
 * Shows today's and upcoming calendar events from the device ContentProvider.
 */

data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val calendarColor: Int,
    val calendarName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarAgendaScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    val addEvent: () -> Unit = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
            })
        }
        Unit
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            events = readCalendarEvents(context)
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Agenda", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                actions = {
                    IconButton(onClick = addEvent) {
                        Icon(Icons.Default.Add, contentDescription = "Add event", tint = CiyatoGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("📅", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Calendar Access Required", color = CiyatoWhite, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Allow access only if you want Ciyato to read real calendar events for your agenda. You can still add events with your calendar app without connecting it.",
                    color = CiyatoMuted, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                ) {
                    Text("Grant Access", color = Color.Black)
                }
                TextButton(onClick = addEvent) {
                    Text("Add in Calendar", color = CiyatoSec)
                }
            }
            return@Scaffold
        }

        val today = System.currentTimeMillis()
        val todayEvents = events.filter { it.startMs >= today || it.endMs >= today }
            .groupBy { formatDate(it.startMs) }

        if (todayEvents.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No upcoming events", color = CiyatoWhite, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("Your calendar has no upcoming events.", color = CiyatoMuted, fontSize = 14.sp)
                    TextButton(onClick = addEvent) {
                        Text("Add in Calendar", color = CiyatoGold)
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            todayEvents.forEach { (dateLabel, dayEvents) ->
                item {
                    Text(dateLabel, color = CiyatoGold, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(dayEvents, key = { it.id }) { event ->
                    CalendarEventCard(event = event, onClick = {
                        openEventInCalendar(context, event.id)
                    })
                }
            }
        }
    }
}

@Composable
private fun CalendarEventCard(event: CalendarEvent, onClick: () -> Unit) {
    val style = com.ciyato.launcher.data.AgendaEventStyler.styleFor(event.title)

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                    .background(style.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(style.icon, contentDescription = null, tint = style.color,
                    modifier = Modifier.size(19.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (!event.allDay) {
                    Text(
                        "${formatTime(event.startMs)} – ${formatTime(event.endMs)}",
                        color = CiyatoSec, fontSize = 13.sp,
                    )
                } else {
                    Text("All day", color = CiyatoSec, fontSize = 13.sp)
                }
                if (!event.location.isNullOrBlank()) {
                    Text("📍 ${event.location}", color = CiyatoMuted, fontSize = 12.sp)
                }
                Text(event.calendarName, color = CiyatoMuted, fontSize = 11.sp)
            }
        }
    }
}

internal fun readCalendarEvents(context: Context): List<CalendarEvent> {
    val events = mutableListOf<CalendarEvent>()
    val now = System.currentTimeMillis()
    val twoWeeks = now + 14L * 24 * 60 * 60 * 1000

    val calendarNames = mutableMapOf<Long, Pair<String, Int>>()
    context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR),
        null, null, null,
    )?.use { cur ->
        while (cur.moveToNext()) {
            calendarNames[cur.getLong(0)] = Pair(cur.getString(1) ?: "Calendar", cur.getInt(2))
        }
    }

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, now)
    ContentUris.appendId(builder, twoWeeks)

    context.contentResolver.query(
        builder.build(),
        arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
        ),
        null, null, "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { cur ->
        while (cur.moveToNext()) {
            val calId = cur.getLong(7)
            val (calName, calColor) = calendarNames[calId] ?: Pair("Calendar", 0xFF4285F4.toInt())
            events.add(CalendarEvent(
                id = cur.getLong(0),
                title = cur.getString(1) ?: "Untitled",
                description = cur.getString(2),
                location = cur.getString(3),
                startMs = cur.getLong(4),
                endMs = cur.getLong(5),
                allDay = cur.getInt(6) != 0,
                calendarColor = calColor,
                calendarName = calName,
            ))
        }
    }
    return events
}

private fun openEventInCalendar(context: Context, eventId: Long) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        data = uri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun formatDate(ms: Long): String {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    val tomorrow = today + 24 * 60 * 60 * 1000
    val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    return when {
        ms < tomorrow -> "Today"
        ms < tomorrow + 24 * 60 * 60 * 1000 -> "Tomorrow"
        else -> dateFormat.format(Date(ms))
    }
}

private fun formatTime(ms: Long): String {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return timeFormat.format(Date(ms))
}
