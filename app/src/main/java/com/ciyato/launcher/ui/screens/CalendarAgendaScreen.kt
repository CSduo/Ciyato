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
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.text.SimpleDateFormat
import java.util.*
import com.ciyato.launcher.ui.components.openWithApp
import com.ciyato.launcher.ui.components.QueryFailureState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CalendarAgendaScreen
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

@Composable
fun CalendarAgendaScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }
    // Null means the calendar provider did not answer; empty means it did and the
    // fortnight really is clear.
    var events by remember { mutableStateOf<List<CalendarEvent>?>(emptyList()) }
    var isLoading by remember { mutableStateOf(hasPermission) }
    // Three controls call this - the top bar, the empty state and the bottom button -
    // and all three were silently dead with no calendar app installed: runCatching
    // with no else branch, so the tap simply did nothing. Ciyato cannot create an
    // event itself, and saying so is the only honest response.
    val addEvent: () -> Unit = {
        openWithApp(
            context,
            Intent(Intent.ACTION_INSERT).apply { data = CalendarContract.Events.CONTENT_URI },
            "No calendar app on this phone can add an event.",
        )
        Unit
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            // A ContentProvider query and a cursor walk, previously run inline on the
            // main thread.
            events = withContext(Dispatchers.IO) { readCalendarEvents(context) }
            isLoading = false
        } else {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Agenda",
                onBack = onBack,
                actions = {
                    IconButton(onClick = addEvent) {
                        Icon(Icons.Default.Add, contentDescription = "Add event", tint = CiyatoGold)
                    }
                },
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

        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = CiyatoGold)
                    Text("Loading your calendar…", color = CiyatoMuted, fontSize = 14.sp)
                }
            }
            return@Scaffold
        }

        val today = System.currentTimeMillis()
        val loaded = events
        if (loaded == null) {
            QueryFailureState(
                title = "Couldn't read your calendar",
                detail = "Android didn't return your events. This does not mean your calendar " +
                    "is clear \u2014 Ciyato could not tell either way.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        val todayEvents = loaded.filter { it.startMs >= today || it.endMs >= today }
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
                // Keyed by id AND start time. `id` is Instances.EVENT_ID, which
                // every occurrence of a recurring event shares — so a weekly
                // standup produced the same key in several day sections of this
                // one LazyColumn, and Compose throws on a duplicate key. Anyone
                // with a repeating event crashed on opening Agenda.
                items(dayEvents, key = { "${it.id}:${it.startMs}" }) { event ->
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

/**
 * The next fortnight of events, or null when the calendar provider would not answer.
 *
 * Had no error handling whatsoever. HomeScreen wrapped its call in runCatching; the
 * Agenda screen called it bare, on the main thread, so a provider failure did not
 * degrade the screen - it threw. A revoked permission, a disabled calendar provider
 * or a locked work profile all produce exactly that.
 *
 * Null rather than an empty list because the Agenda screen's empty state is a
 * definite claim - a party emoji and "Your calendar has no upcoming events" - and a
 * failed read telling somebody their fortnight is clear is the worst version of this
 * mistake in the app.
 */
internal fun readCalendarEvents(context: Context): List<CalendarEvent>? = runCatching {
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
    events
}.getOrNull()

private fun openEventInCalendar(context: Context, eventId: Long) {
    // Unguarded before, so tapping an event on a phone with no calendar app crashed.
    // Reading the calendar and being able to open it are separate capabilities: the
    // events can be read through the provider while nothing is registered to display
    // one, which is exactly the case on a device whose calendar app was removed.
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    openWithApp(
        context,
        Intent(Intent.ACTION_VIEW).apply { data = uri },
        "No app on this phone can open a calendar event.",
    )
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
