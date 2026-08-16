package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.WeatherRepository
import com.ciyato.launcher.data.WeatherRepository.WeatherState
import com.ciyato.launcher.ui.theme.*

/**
 * Weather + Agenda widget row — live weather via Open-Meteo.
 *
 * WeatherCard now reflects [weatherState] from the ViewModel:
 *   - NoPermission / null → shows "--°" with "Enable weather" hint
 *   - Loading             → spinner
 *   - Success             → shows real temperature, condition, location
 *   - Error               → shows "!" with error hint
 *
 * onWeatherTap and onAgendaTap are required callbacks.
 */

@Composable
fun WeatherAgendaRow(
    isDense: Boolean = true,
    weatherState: WeatherState? = null,
    useFahrenheit: Boolean = false,
    agendaEvents: List<com.ciyato.launcher.ui.screens.CalendarEvent> = emptyList(),
    showWeather: Boolean = true,
    showAgenda: Boolean = true,
    isEditMode: Boolean = false,
    onWeatherTap: () -> Unit = {},
    onAgendaTap: () -> Unit = {},
    onRemoveWeather: () -> Unit = {},
    onRemoveAgenda: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!showWeather && !showAgenda) return
    val height = if (isDense) 160.dp else 190.dp
    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showWeather) {
            Box(
                modifier = if (showAgenda) Modifier.weight(1f).fillMaxHeight() else Modifier.fillMaxSize(),
            ) {
                WeatherCard(
                    isDense = isDense,
                    weatherState = weatherState,
                    useFahrenheit = useFahrenheit,
                    onTap = onWeatherTap,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isEditMode) HomeWidgetRemoveButton(onClick = onRemoveWeather)
            }
        }
        if (showAgenda) {
            Box(
                modifier = if (showWeather) Modifier.weight(1.35f).fillMaxHeight() else Modifier.fillMaxSize(),
            ) {
                AgendaCard(
                    isDense = isDense,
                    events = agendaEvents,
                    onTap = onAgendaTap,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isEditMode) HomeWidgetRemoveButton(onClick = onRemoveAgenda)
            }
        }
    }
}

@Composable
private fun BoxScope.HomeWidgetRemoveButton(onClick: () -> Unit) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(CiyatoBg.copy(alpha = 0.84f)),
    ) {
        Icon(Icons.Default.Close, contentDescription = "Remove from Home", tint = CiyatoRed, modifier = Modifier.size(16.dp))
    }
}

// ─── Weather Card ─────────────────────────────────────────────────────────────

@Composable
fun WeatherCard(
    isDense: Boolean,
    weatherState: WeatherState? = null,
    useFahrenheit: Boolean = false,
    /**
     * Turns off the animated weather overlay.
     *
     * That overlay is an infinite 60 fps particle canvas, so Home never
     * reaches a quiescent state while it is on: a frame every 16 ms whether or
     * not anyone is touching the phone, with per-frame Path allocation feeding
     * GC pauses that land during drags and page swipes. It was installed
     * unconditionally and honoured no setting.
     */
    reduceMotion: Boolean = false,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val padding    = if (isDense) 14.dp else 18.dp
    val tempSize   = if (isDense) 30.sp else 36.sp
    val iconSize   = if (isDense) 28.dp else 34.dp
    val subtextSz  = if (isDense) 10.sp else 12.sp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onTap)
            .semantics { contentDescription = "Weather — tap to open your weather app" },
    ) {
        if (!reduceMotion) {
            (weatherState as? WeatherState.Success)?.let { ws ->
                val theme = remember(ws.weatherCode, ws.tempC) {
                    resolveSeasonTheme(ws.weatherCode, ws.tempC)
                }
                Weather2DEffectOverlay(theme = theme, modifier = Modifier.matchParentSize())
            }
        }
        Column(
            modifier = Modifier.matchParentSize().padding(padding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            when (val ws = weatherState) {
                is WeatherState.Success -> WeatherCardSuccess(ws, useFahrenheit, tempSize, iconSize, subtextSz, isDense)
                is WeatherState.Loading -> WeatherCardLoading(isDense)
                else                    -> WeatherCardFallback(tempSize, iconSize, subtextSz, isDense)
            }
        }
    }
}

/**
 * Live condition layer behind the card content: falling rain streaks, a soft
 * sun glow, or drifting cloud blobs. Kept subtle so text stays readable.
 */
@Composable
private fun WeatherConditionBackdrop(weatherCode: Int, isDay: Boolean) {
    val transition = rememberInfiniteTransition(label = "weather_backdrop")
    when {
        weatherCode in 51..99 -> {
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
                label = "rain_phase",
            )
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val streaks = 14
                val streakLength = size.height * 0.16f
                repeat(streaks) { index ->
                    val x = size.width * (index + 0.5f) / streaks
                    val offset = (phase + index * 0.37f) % 1f
                    val y = offset * (size.height + streakLength) - streakLength
                    drawLine(
                        color = CiyatoBlue.copy(alpha = 0.30f),
                        start = androidx.compose.ui.geometry.Offset(x, y),
                        end = androidx.compose.ui.geometry.Offset(x - size.width * 0.02f, y + streakLength),
                        strokeWidth = 2.2f,
                    )
                }
            }
        }
        weatherCode == 0 && isDay -> {
            val glow by transition.animateFloat(
                initialValue = 0.18f,
                targetValue = 0.32f,
                animationSpec = infiniteRepeatable(tween(2_600), RepeatMode.Reverse),
                label = "sun_glow",
            )
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(CiyatoGoldSoft.copy(alpha = glow), CiyatoGoldSoft.copy(alpha = 0f)),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.2f),
                        radius = size.width * 0.7f,
                    ),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.2f),
                    radius = size.width * 0.7f,
                )
            }
        }
        weatherCode in 1..48 -> {
            val drift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Reverse),
                label = "cloud_drift",
            )
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val baseAlpha = 0.10f
                drawCircle(
                    color = CiyatoWhite.copy(alpha = baseAlpha),
                    radius = size.width * 0.30f,
                    center = androidx.compose.ui.geometry.Offset(
                        size.width * (0.15f + drift * 0.2f),
                        size.height * 0.30f,
                    ),
                )
                drawCircle(
                    color = CiyatoWhite.copy(alpha = baseAlpha * 0.8f),
                    radius = size.width * 0.22f,
                    center = androidx.compose.ui.geometry.Offset(
                        size.width * (0.65f - drift * 0.15f),
                        size.height * 0.55f,
                    ),
                )
            }
        }
    }
}

@Composable
private fun WeatherCardSuccess(
    ws: WeatherState.Success,
    useFahrenheit: Boolean,
    tempSize: androidx.compose.ui.unit.TextUnit,
    iconSize: androidx.compose.ui.unit.Dp,
    subtextSz: androidx.compose.ui.unit.TextUnit,
    isDense: Boolean,
) {
    val shownTemp = if (useFahrenheit) WeatherRepository.cToF(ws.tempC) else ws.tempC
    val shownFeels = if (useFahrenheit) WeatherRepository.cToF(ws.feelsLikeC) else ws.feelsLikeC
    val icon: ImageVector = when {
        ws.weatherCode == 0 && ws.isDay  -> Icons.Outlined.WbSunny
        ws.weatherCode == 0 && !ws.isDay -> Icons.Default.DarkMode
        ws.weatherCode in 1..2           -> Icons.Default.Cloud
        ws.weatherCode in 51..99         -> Icons.Default.Umbrella
        else                             -> Icons.Default.Cloud
    }
    val iconTint = when {
        ws.weatherCode == 0  -> CiyatoGoldSoft
        ws.weatherCode in 51..99 -> CiyatoBlue
        else -> CiyatoSec
    }
    val weatherMotion = rememberInfiniteTransition(label = "weather_icon_motion")
    val iconRotation by weatherMotion.animateFloat(
        initialValue = 0f,
        targetValue = if (ws.weatherCode == 0 && ws.isDay) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
        label = "weather_sun_rotation",
    )
    val weatherBob by weatherMotion.animateFloat(
        initialValue = -2f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Reverse),
        label = "weather_rain_bob",
    )

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            null,
            tint = iconTint,
            modifier = Modifier
                .size(iconSize)
                .padding(top = 2.dp)
                .graphicsLayer {
                    rotationZ = iconRotation
                    translationY = if (ws.weatherCode in 51..99) weatherBob else 0f
                },
        )
        Column {
            Text("$shownTemp°", color = CiyatoWhite, fontSize = tempSize,
                fontWeight = FontWeight.Bold, lineHeight = tempSize * 1.05f)
            Text(ws.condition, color = CiyatoSec, fontSize = subtextSz, maxLines = 1)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Feels like $shownFeels°", color = CiyatoMuted, fontSize = subtextSz)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                if (ws.isStale) "${ws.locationName} · Saved" else ws.locationName,
                color = CiyatoMuted, fontSize = (subtextSz.value - 1).sp, maxLines = 1,
            )
            // Green = live data just fetched; amber = a saved snapshot shown
            // because the last live fetch failed. Never the same dot for both.
            Box(
                modifier = Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (ws.isStale) CiyatoAmber else CiyatoGreen)
            )
        }
    }
}

@Composable
private fun ColumnScope.WeatherCardLoading(isDense: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = CiyatoGold,
            strokeWidth = 2.dp,
            modifier = Modifier.size(if (isDense) 22.dp else 26.dp),
        )
    }
    Text("Loading…", color = CiyatoMuted, fontSize = if (isDense) 10.sp else 11.sp)
}

@Composable
private fun WeatherCardFallback(
    tempSize: androidx.compose.ui.unit.TextUnit,
    iconSize: androidx.compose.ui.unit.Dp,
    subtextSz: androidx.compose.ui.unit.TextUnit,
    isDense: Boolean,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.WbSunny, null, tint = CiyatoGoldSoft,
            modifier = Modifier.size(iconSize).padding(top = 2.dp))
        Column {
            Text("--°", color = CiyatoWhite, fontSize = tempSize,
                fontWeight = FontWeight.Bold, lineHeight = tempSize * 1.05f)
            Text("Set weather", color = CiyatoSec, fontSize = subtextSz)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Use precise or approximate location", color = CiyatoMuted, fontSize = subtextSz)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Tap to enable", color = CiyatoBlue, fontSize = (subtextSz.value - 1).sp)
        }
    }
}

// ─── Agenda Card ──────────────────────────────────────────────────────────────

@Composable
fun AgendaCard(
    isDense: Boolean,
    events: List<com.ciyato.launcher.ui.screens.CalendarEvent> = emptyList(),
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val paddingH = if (isDense) 14.dp else 18.dp
    val paddingV = if (isDense) 12.dp else 16.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onTap)
            .semantics { contentDescription = "Today's agenda — tap to view all" }
            .padding(horizontal = paddingH, vertical = paddingV),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Today", color = CiyatoWhite, fontWeight = FontWeight.SemiBold,
                fontSize = if (isDense) 14.sp else 16.sp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (isDense) 22.dp else 26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CiyatoGlassStr),
            ) {
                Icon(Icons.Default.Add, "Add event", tint = CiyatoSec,
                    modifier = Modifier.size(if (isDense) 14.dp else 16.dp))
            }
        }

        Spacer(Modifier.height(if (isDense) 8.dp else 12.dp))

        if (events.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "No schedule connected",
                    color = CiyatoWhite,
                    fontSize = if (isDense) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap to add or connect calendar",
                    color = CiyatoMuted,
                    fontSize = if (isDense) 10.sp else 11.sp,
                    lineHeight = if (isDense) 14.sp else 16.sp,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (isDense) 6.dp else 8.dp),
            ) {
                events.take(3).forEach { event ->
                    val style = com.ciyato.launcher.data.AgendaEventStyler.styleFor(event.title)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(if (isDense) 22.dp else 26.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(style.color.copy(alpha = 0.20f)),
                        ) {
                            Icon(style.icon, contentDescription = null, tint = style.color,
                                modifier = Modifier.size(if (isDense) 12.dp else 14.dp))
                        }
                        Column {
                            Text(
                                event.title,
                                color = CiyatoWhite,
                                fontSize = if (isDense) 11.sp else 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                            Text(
                                if (event.allDay) "All day" else java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(event.startMs)),
                                color = CiyatoMuted,
                                fontSize = if (isDense) 9.sp else 10.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Text(
            if (events.isEmpty()) "Open" else "View all",
            color = CiyatoBlue,
            fontSize = if (isDense) 11.sp else 12.sp,
            modifier = Modifier.align(Alignment.End),
        )
    }
}
