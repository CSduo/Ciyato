package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * ClockWidgetStylePicker — Suggestion #99
 * Provides 4 clock styles: Analog, Digital, Minimal, Binary.
 * Each style renders live time and can be selected as the home widget.
 */

enum class ClockStyle(val label: String) {
    ANALOG("Analog"),
    DIGITAL("Digital"),
    MINIMAL("Minimal"),
    BINARY("Binary"),
}

@Composable
fun ClockWidgetStylePicker(
    currentStyle: ClockStyle,
    onStyleSelected: (ClockStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Clock Style", color = CiyatoGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ClockStyle.entries) { style ->
                val isSelected = style == currentStyle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(CiyatoBgEl, RoundedCornerShape(16.dp))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) CiyatoGold else Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { onStyleSelected(style) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (style) {
                            ClockStyle.ANALOG  -> MiniAnalogClock(Modifier.size(52.dp))
                            ClockStyle.DIGITAL -> MiniDigitalClock()
                            ClockStyle.MINIMAL -> MiniMinimalClock()
                            ClockStyle.BINARY  -> MiniBinaryClock()
                        }
                        Text(style.label, color = if (isSelected) CiyatoGold else CiyatoMuted, fontSize = 10.sp)
                    }
                }
            }
        }

        // Full-size preview
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(CiyatoBgEl, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (currentStyle) {
                ClockStyle.ANALOG  -> AnalogClock(Modifier.size(90.dp))
                ClockStyle.DIGITAL -> DigitalClock()
                ClockStyle.MINIMAL -> MinimalClock()
                ClockStyle.BINARY  -> BinaryClock()
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Clock style implementations
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun currentTime(): Triple<Int, Int, Int> {
    var cal by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); cal = Calendar.getInstance() } }
    return Triple(
        cal.get(Calendar.HOUR),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND),
    )
}

@Composable
fun AnalogClock(modifier: Modifier = Modifier) {
    val (h, m, s) = currentTime()
    Canvas(modifier = modifier) {
        val cx = size.width / 2; val cy = size.height / 2; val r = minOf(cx, cy)
        drawCircle(color = CiyatoGold.copy(alpha = 0.2f), radius = r)
        drawCircle(color = CiyatoGold, radius = 6f)
        // Hour hand
        val hAngle = Math.toRadians(((h % 12) * 30 + m * 0.5 - 90).toDouble())
        drawLine(CiyatoGold, Offset(cx, cy), Offset((cx + cos(hAngle) * r * 0.55).toFloat(), (cy + sin(hAngle) * r * 0.55).toFloat()), strokeWidth = 6f, cap = StrokeCap.Round)
        // Minute hand
        val mAngle = Math.toRadians((m * 6 - 90).toDouble())
        drawLine(CiyatoWhite, Offset(cx, cy), Offset((cx + cos(mAngle) * r * 0.8).toFloat(), (cy + sin(mAngle) * r * 0.8).toFloat()), strokeWidth = 4f, cap = StrokeCap.Round)
        // Second hand
        val sAngle = Math.toRadians((s * 6 - 90).toDouble())
        drawLine(Color(0xFFFF6B6B), Offset(cx, cy), Offset((cx + cos(sAngle) * r * 0.85).toFloat(), (cy + sin(sAngle) * r * 0.85).toFloat()), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}

@Composable
fun DigitalClock(modifier: Modifier = Modifier) {
    val (h, m, s) = currentTime()
    val cal = Calendar.getInstance()
    val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    Text("%02d:%02d".format(h, m), color = CiyatoWhite, fontSize = 36.sp, fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace, modifier = modifier)
}

@Composable
fun MinimalClock(modifier: Modifier = Modifier) {
    val (h, m, _) = currentTime()
    val cal = Calendar.getInstance()
    val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%d:%02d".format(h, m), color = CiyatoWhite, fontSize = 40.sp, fontWeight = FontWeight.Thin)
        Text(amPm, color = CiyatoMuted, fontSize = 13.sp, letterSpacing = 4.sp)
    }
}

@Composable
fun BinaryClock(modifier: Modifier = Modifier) {
    val (h, m, s) = currentTime()
    fun toBits(n: Int, bits: Int) = (bits - 1 downTo 0).map { (n shr it) and 1 }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(h to 4, m to 6, s to 6).forEach { (n, bits) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                toBits(n, bits).forEach { bit ->
                    Box(Modifier.size(8.dp).background(if (bit == 1) CiyatoGold else CiyatoBg, CircleShape))
                }
            }
        }
    }
}

// Mini versions for the picker row
@Composable private fun MiniAnalogClock(modifier: Modifier) = AnalogClock(modifier)
@Composable private fun MiniDigitalClock() {
    val (h, m, _) = currentTime()
    Text("%02d:%02d".format(h, m), color = CiyatoWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace)
}
@Composable private fun MiniMinimalClock() {
    val (h, m, _) = currentTime()
    Text("%d:%02d".format(h, m), color = CiyatoWhite, fontSize = 16.sp, fontWeight = FontWeight.Thin)
}
@Composable private fun MiniBinaryClock() = BinaryClock(Modifier)
