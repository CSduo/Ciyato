package com.ciyato.launcher.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps an event title to a themed icon + accent color so the agenda reads at a
 * glance ("gym" looks like the gym, "art class" looks like art). Purely
 * heuristic — unknown titles fall back to a neutral calendar icon.
 */
data class AgendaEventStyle(val icon: ImageVector, val color: Color)

object AgendaEventStyler {

    private data class Rule(val keywords: List<String>, val style: AgendaEventStyle)

    private val RULES = listOf(
        Rule(listOf("gym", "workout", "training", "run", "yoga", "fitness"),
            AgendaEventStyle(Icons.Default.FitnessCenter, Color(0xFF34D399))),
        Rule(listOf("art", "design", "draw", "paint", "sketch", "craft"),
            AgendaEventStyle(Icons.Default.Brush, Color(0xFFA78BFA))),
        Rule(listOf("call", "phone", "sync", "standup", "1:1"),
            AgendaEventStyle(Icons.Default.Call, Color(0xFF60A5FA))),
        Rule(listOf("meeting", "review", "interview", "demo", "presentation"),
            AgendaEventStyle(Icons.Default.Groups, Color(0xFF60A5FA))),
        Rule(listOf("class", "lecture", "study", "exam", "college", "school", "tuition"),
            AgendaEventStyle(Icons.Default.School, Color(0xFFFBBF24))),
        Rule(listOf("flight", "travel", "trip", "airport"),
            AgendaEventStyle(Icons.Default.Flight, Color(0xFF38BDF8))),
        Rule(listOf("drive", "commute", "pickup", "drop"),
            AgendaEventStyle(Icons.Default.DirectionsCar, Color(0xFF94A3B8))),
        Rule(listOf("lunch", "dinner", "breakfast", "brunch", "food"),
            AgendaEventStyle(Icons.Default.Restaurant, Color(0xFFF87171))),
        Rule(listOf("birthday", "anniversary", "party", "celebration"),
            AgendaEventStyle(Icons.Default.Cake, Color(0xFFF472B6))),
        Rule(listOf("doctor", "dentist", "clinic", "hospital", "checkup"),
            AgendaEventStyle(Icons.Default.LocalHospital, Color(0xFFF87171))),
        Rule(listOf("music", "concert", "band", "practice"),
            AgendaEventStyle(Icons.Default.MusicNote, Color(0xFFC084FC))),
        Rule(listOf("shopping", "groceries", "market"),
            AgendaEventStyle(Icons.Default.ShoppingCart, Color(0xFFFB923C))),
        Rule(listOf("work", "office", "shift", "deadline", "project"),
            AgendaEventStyle(Icons.Default.Work, Color(0xFF60A5FA))),
    )

    private val DEFAULT = AgendaEventStyle(Icons.Default.Event, Color(0xFFD9C58B))

    fun styleFor(title: String, colorOverrideArgb: Int? = null): AgendaEventStyle {
        val lower = title.lowercase()
        val base = RULES.firstOrNull { rule -> rule.keywords.any { it in lower } }?.style ?: DEFAULT
        return colorOverrideArgb?.let { base.copy(color = Color(it)) } ?: base
    }
}
