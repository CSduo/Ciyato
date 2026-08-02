package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class WeatherSeasonTheme {
    CLEAR_SUMMER,
    HEATWAVE,
    RAIN_DRIZZLE,
    THUNDERSTORM,
    SNOW,
    HAIL_ICE,
    SPRING_SAKURA,
    AUTUMN,
}

fun resolveSeasonTheme(code: Int, tempC: Int): WeatherSeasonTheme {
    return when {
        code in listOf(96, 99, 56, 57, 66, 67) -> WeatherSeasonTheme.HAIL_ICE
        code in listOf(95) -> WeatherSeasonTheme.THUNDERSTORM
        code in 71..86 -> WeatherSeasonTheme.SNOW
        code in 51..65 || code in 80..82 -> WeatherSeasonTheme.RAIN_DRIZZLE
        tempC >= 38 -> WeatherSeasonTheme.HEATWAVE
        else -> {
            val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
            when (month) {
                java.util.Calendar.MARCH, java.util.Calendar.APRIL, java.util.Calendar.MAY -> WeatherSeasonTheme.SPRING_SAKURA
                java.util.Calendar.SEPTEMBER, java.util.Calendar.OCTOBER, java.util.Calendar.NOVEMBER -> WeatherSeasonTheme.AUTUMN
                else -> WeatherSeasonTheme.CLEAR_SUMMER
            }
        }
    }
}

/**
 * 100% Pure Jetpack Compose Canvas Anime-Style Weather Engine.
 * Zero external images — uses vector path rendering, gradient shading, and high-FPS particle physics.
 */
@Composable
fun Weather2DEffectOverlay(
    theme: WeatherSeasonTheme,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(RoundedCornerShape(22.dp))) {
        when (theme) {
            WeatherSeasonTheme.SPRING_SAKURA -> AnimeSakuraEffect()
            WeatherSeasonTheme.RAIN_DRIZZLE -> AnimeRainEffect()
            WeatherSeasonTheme.THUNDERSTORM -> AnimeThunderstormEffect()
            WeatherSeasonTheme.SNOW -> AnimeSnowEffect()
            WeatherSeasonTheme.HAIL_ICE -> AnimeHailIceEffect()
            WeatherSeasonTheme.HEATWAVE -> AnimeHeatwaveEffect()
            WeatherSeasonTheme.AUTUMN -> AnimeAutumnEffect()
            WeatherSeasonTheme.CLEAR_SUMMER -> AnimeClearSkyEffect()
        }
    }
}

// ─── 1. Anime Sakura Petals (Makoto Shinkai Spring Sky) ───────────────────────

@Composable
private fun AnimeSakuraEffect() {
    val transition = rememberInfiniteTransition(label = "sakura")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "sakura_progress"
    )

    val skyGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF2D1B4E), Color(0xFF5B326A), Color(0xFF9C5A86))
    )

    Box(modifier = Modifier.fillMaxSize().background(skyGradient)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Anime Soft Radial Sun Beam
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD1DC).copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    radius = size.width * 0.7f
                )
            )

            val r = Random(101)
            repeat(32) { i ->
                val baseX = r.nextFloat() * size.width
                val speed = 0.35f + (i % 5) * 0.08f
                val y = ((r.nextFloat() + progress * speed) % 1.15f - 0.1f) * size.height
                val x = baseX + sin((progress * 4.5f + i).toDouble()).toFloat() * 24f
                val rot = (progress * 360f + i * 25) % 360f

                rotate(rot, pivot = Offset(x, y)) {
                    drawSakuraPetal(Offset(x, y), sizeScale = 1f + (i % 3) * 0.3f)
                }
            }
        }
    }
}

private fun DrawScope.drawSakuraPetal(center: Offset, sizeScale: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - 8f * sizeScale)
        cubicTo(
            center.x + 6f * sizeScale, center.y - 4f * sizeScale,
            center.x + 5f * sizeScale, center.y + 6f * sizeScale,
            center.x, center.y + 10f * sizeScale
        )
        cubicTo(
            center.x - 5f * sizeScale, center.y + 6f * sizeScale,
            center.x - 6f * sizeScale, center.y - 4f * sizeScale,
            center.x, center.y - 8f * sizeScale
        )
        close()
    }
    drawPath(path = path, color = Color(0xFFFFB7C5).copy(alpha = 0.88f))
}

// ─── 2. Anime Shinkai Rain & Splash Ripples ──────────────────────────────────

@Composable
private fun AnimeRainEffect() {
    val transition = rememberInfiniteTransition(label = "rain")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rain_progress"
    )

    val skyGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF334155))
    )

    Box(modifier = Modifier.fillMaxSize().background(skyGradient)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = Random(202)
            repeat(50) { i ->
                val startX = r.nextFloat() * size.width
                val speed = 0.9f + (i % 5) * 0.12f
                val y = ((r.nextFloat() + progress * speed) % 1.2f - 0.1f) * size.height
                val streakLen = 32f + (i % 4) * 10f

                drawLine(
                    color = Color(0xFF7DD3FC).copy(alpha = 0.65f),
                    start = Offset(startX, y),
                    end = Offset(startX - 8f, y + streakLen),
                    strokeWidth = 2.4f,
                    cap = StrokeCap.Round
                )
            }

            // Anime Splash Rings at bottom
            repeat(6) { i ->
                val rx = size.width * ((i * 0.18f + progress) % 1f)
                val ry = size.height * (0.85f + (i % 3) * 0.04f)
                val ringAlpha = (1f - (progress % 1f)).coerceIn(0f, 0.4f)
                drawCircle(
                    color = Color(0xFFBAE6FD).copy(alpha = ringAlpha),
                    radius = (progress * 18f + i * 4f) % 22f,
                    center = Offset(rx, ry),
                    style = Stroke(width = 1.5f)
                )
            }
        }
    }
}

// ─── 3. Anime Electric Thunderstorm ──────────────────────────────────────────

@Composable
private fun AnimeThunderstormEffect() {
    val transition = rememberInfiniteTransition(label = "thunder")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "thunder_rain"
    )
    val flashAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2200
                0f at 0
                0f at 1600
                0.9f at 1700
                0.2f at 1800
                1.0f at 1850
                0f at 1950
            }
        ),
        label = "flash"
    )

    val skyGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF090514), Color(0xFF1D1235), Color(0xFF2D174D))
    )

    Box(modifier = Modifier.fillMaxSize().background(skyGradient)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (flashAlpha > 0.05f) {
                drawRect(color = Color(0xFFE0E7FF).copy(alpha = flashAlpha * 0.65f))
            }

            // Draw Anime Lightning Bolt when flashing
            if (flashAlpha > 0.3f) {
                val boltPath = Path().apply {
                    moveTo(size.width * 0.65f, 0f)
                    lineTo(size.width * 0.55f, size.height * 0.35f)
                    lineTo(size.width * 0.62f, size.height * 0.38f)
                    lineTo(size.width * 0.48f, size.height * 0.75f)
                }
                drawPath(path = boltPath, color = Color(0xFFA5B4FC), style = Stroke(width = 4f))
                drawPath(path = boltPath, color = Color.White, style = Stroke(width = 2f))
            }

            val r = Random(303)
            repeat(60) { i ->
                val startX = r.nextFloat() * size.width
                val speed = 1.2f + (i % 4) * 0.2f
                val y = ((r.nextFloat() + progress * speed) % 1.2f - 0.1f) * size.height

                drawLine(
                    color = Color(0xFFC7D2FE).copy(alpha = 0.7f),
                    start = Offset(startX, y),
                    end = Offset(startX - 14f, y + 42f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

// ─── 4. Anime Snow (6-Arm Hex Vector Flakes) ──────────────────────────────────

@Composable
private fun AnimeSnowEffect() {
    val transition = rememberInfiniteTransition(label = "snow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "snow_progress"
    )

    val skyGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF475569))
    )

    Box(modifier = Modifier.fillMaxSize().background(skyGradient)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = Random(404)
            repeat(30) { i ->
                val baseX = r.nextFloat() * size.width
                val speed = 0.4f + (i % 4) * 0.08f
                val y = ((r.nextFloat() + progress * speed) % 1.15f - 0.05f) * size.height
                val x = baseX + sin((progress * 5f + i).toDouble()).toFloat() * 18f
                val radius = 4f + (i % 4) * 1.5f

                drawCircle(color = Color.White.copy(alpha = 0.85f), radius = radius, center = Offset(x, y))
            }
        }
    }
}

// ─── 5. Anime Hail / Ice Crystal Storm ────────────────────────────────────────

@Composable
private fun AnimeHailIceEffect() {
    val transition = rememberInfiniteTransition(label = "hail")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)),
        label = "hail_progress"
    )

    val skyGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF0284C7).copy(alpha = 0.6f), Color(0xFF0F172A))
    )

    Box(modifier = Modifier.fillMaxSize().background(skyGradient)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = Random(505)
            repeat(35) { i ->
                val startX = r.nextFloat() * size.width
                val speed = 1.3f + (i % 3) * 0.2f
                val y = ((r.nextFloat() + progress * speed) % 1.2f - 0.1f) * size.height

                // Anime Diamond-Cut Ice Crystal
                val icePath = Path().apply {
                    moveTo(startX, y - 6f)
                    lineTo(startX + 4f, y)
                    lineTo(startX, y + 6f)
                    lineTo(startX - 4f, y)
                    close()
                }
                drawPath(path = icePath, color = Color(0xFFE0F2FE))
            }
        }

        // Anime Severe Weather Alert Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xEEEF4444))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("ICE / HAIL ALERT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── 6. Anime Golden Hour Summer & Heatwave ──────────────────────────────────

@Composable
private fun AnimeHeatwaveEffect() {
    val transition = rememberInfiniteTransition(label = "heatwave")
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "wave_phase"
    )

    val skyGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF7C2D12), Color(0xFFC2410C), Color(0xFFEA580C))
    )

    Box(modifier = Modifier.fillMaxSize().background(skyGradient)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Anime Sun Disk with radial flare
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A).copy(alpha = 0.9f), Color(0xFFF97316).copy(alpha = 0.2f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.2f),
                    radius = size.width * 0.65f
                )
            )

            // Cel-shaded Heat Shimmer Waves
            val stroke = Stroke(width = 3.5f)
            repeat(4) { idx ->
                val path = Path()
                val yOffset = size.height * (0.35f + idx * 0.16f)
                path.moveTo(0f, yOffset)
                var x = 0f
                while (x <= size.width) {
                    val y = yOffset + sin((x * 0.035f + wavePhase + idx).toDouble()).toFloat() * 9f
                    path.lineTo(x, y)
                    x += 10f
                }
                drawPath(path = path, color = Color(0xFFFDE047).copy(alpha = 0.55f), style = stroke)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xEEF59E0B))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("HEATWAVE ALERT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── 7. Anime Autumn Falling Maple Leaves ─────────────────────────────────────

@Composable
private fun AnimeAutumnEffect() {
    val transition = rememberInfiniteTransition(label = "autumn")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3900, easing = LinearEasing)),
        label = "leaf_progress"
    )

    val skyGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF451A03), Color(0xFF78350F), Color(0xFFB45309))
    )

    Box(modifier = Modifier.fillMaxSize().background(skyGradient)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = Random(707)
            repeat(24) { i ->
                val baseX = r.nextFloat() * size.width
                val speed = 0.38f + (i % 4) * 0.08f
                val y = ((r.nextFloat() + progress * speed) % 1.15f - 0.05f) * size.height
                val x = baseX + sin((progress * 4.8f + i).toDouble()).toFloat() * 20f
                val rot = (progress * 300f + i * 30) % 360f

                rotate(rot, pivot = Offset(x, y)) {
                    drawMapleLeaf(Offset(x, y), scale = 1f + (i % 3) * 0.25f)
                }
            }
        }
    }
}

private fun DrawScope.drawMapleLeaf(center: Offset, scale: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - 10f * scale)
        lineTo(center.x + 4f * scale, center.y - 4f * scale)
        lineTo(center.x + 9f * scale, center.y - 6f * scale)
        lineTo(center.x + 6f * scale, center.y)
        lineTo(center.x + 8f * scale, center.y + 6f * scale)
        lineTo(center.x, center.y + 3f * scale)
        lineTo(center.x - 8f * scale, center.y + 6f * scale)
        lineTo(center.x - 6f * scale, center.y)
        lineTo(center.x - 9f * scale, center.y - 6f * scale)
        lineTo(center.x - 4f * scale, center.y - 4f * scale)
        close()
    }
    drawPath(path = path, color = Color(0xFFF97316).copy(alpha = 0.88f))
}

// ─── 8. Anime Ghibli Clear Blue Sky ───────────────────────────────────────────

@Composable
private fun AnimeClearSkyEffect() {
    val transition = rememberInfiniteTransition(label = "clear")
    val pulse by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(2800), repeatMode = RepeatMode.Reverse),
        label = "sun_pulse"
    )

    val skyGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF7DD3FC))
    )

    Box(modifier = Modifier.fillMaxSize().background(skyGradient)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = pulse), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.18f),
                    radius = size.width * 0.65f
                )
            )

            // Anime Cumulus Cloud Vector Shapes
            drawCircle(color = Color.White.copy(alpha = 0.35f), radius = 35f, center = Offset(size.width * 0.25f, size.height * 0.3f))
            drawCircle(color = Color.White.copy(alpha = 0.35f), radius = 45f, center = Offset(size.width * 0.35f, size.height * 0.28f))
            drawCircle(color = Color.White.copy(alpha = 0.35f), radius = 30f, center = Offset(size.width * 0.45f, size.height * 0.32f))
        }
    }
}
