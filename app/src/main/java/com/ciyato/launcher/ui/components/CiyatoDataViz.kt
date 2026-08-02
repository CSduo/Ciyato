package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

/**
 * Sparkline Chart — small trend line.
 * Used inside KPI cards.
 */
@Composable
fun CiyatoSparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = CiyatoGold,
) {
    if (points.size < 2) return
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val maxVal = points.maxOrNull() ?: 1f
        val minVal = points.minOrNull() ?: 0f
        val range = (maxVal - minVal).coerceAtLeast(0.01f)

        val path = Path().apply {
            val startX = 0f
            val startY = height - ((points[0] - minVal) / range * height)
            moveTo(startX, startY)
            for (i in 1 until points.size) {
                val nextX = (i.toFloat() / (points.size - 1)) * width
                val nextY = height - ((points[i] - minVal) / range * height)
                lineTo(nextX, nextY)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Animated circular donut progress chart.
 * Used for storage visualization.
 */
@Composable
fun CiyatoCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    accentColor: Color = CiyatoGold,
    strokeWidth: Float = 14f,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1000),
        label = "radial_progress"
    )
    Canvas(modifier = modifier) {
        // Track
        drawCircle(
            color = CiyatoBgEl2,
            style = Stroke(width = strokeWidth)
        )
        // Progress Arc
        drawArc(
            color = accentColor,
            startAngle = -90f,
            sweepAngle = animatedProgress * 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
