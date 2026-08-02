package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ciyato.launcher.ui.theme.*

/**
 * Shimmer skeleton loading components — Suggestion #132.
 * Uses an infinite animation to create a moving highlight band.
 * All composables in this file are pure UI — no state, no side-effects.
 */

// ── Shimmer brush ─────────────────────────────────────────────────────────────

@Composable
fun rememberShimmerBrush(): Brush {
    val shimmerColors = listOf(
        CiyatoBgEl,
        CiyatoBgEl2.copy(alpha = 0.9f),
        Color.White.copy(alpha = 0.06f),
        CiyatoBgEl2.copy(alpha = 0.9f),
        CiyatoBgEl,
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start  = Offset(translateAnim - 200f, 0f),
        end    = Offset(translateAnim + 200f, 0f),
    )
}

// ── Skeleton primitives ────────────────────────────────────────────────────────

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    cornerRadius: Dp = 7.dp,
    brush: Brush = rememberShimmerBrush(),
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush),
    )
}

@Composable
fun SkeletonCircle(size: Dp, brush: Brush = rememberShimmerBrush()) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(size / 2)).background(brush))
}

// ── Skeleton cards for the home screen ───────────────────────────────────────

/**
 * Skeleton placeholder for a SmartCategoryCard during app loading.
 */
@Composable
fun SkeletonCategoryCard(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            SkeletonCircle(size = 28.dp, brush = brush)
            SkeletonBox(Modifier.width(60.dp), brush = brush)
        }
        SkeletonBox(Modifier.width(40.dp), height = 10.dp, brush = brush)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { SkeletonCircle(size = 28.dp, brush = brush) }
        }
    }
}

/**
 * Skeleton placeholder for an AppIconTile in a horizontal list.
 */
@Composable
fun SkeletonAppTile(modifier: Modifier = Modifier, brush: Brush = rememberShimmerBrush()) {
    Column(
        modifier = modifier.width(60.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SkeletonCircle(size = 48.dp, brush = brush)
        SkeletonBox(Modifier.width(42.dp), height = 10.dp, brush = brush)
    }
}

/**
 * Skeleton placeholder for the WeatherCard widget.
 */
@Composable
fun SkeletonWeatherCard(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonCircle(size = 36.dp, brush = brush)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBox(Modifier.width(50.dp), height = 24.dp, brush = brush)
                SkeletonBox(Modifier.width(70.dp), height = 10.dp, brush = brush)
            }
        }
        Spacer(Modifier.weight(1f))
        SkeletonBox(Modifier.fillMaxWidth(0.7f), height = 10.dp, brush = brush)
    }
}

/**
 * Full-page skeleton grid for the home screen category section.
 */
@Composable
fun SkeletonCategoryGrid(columns: Int = 3, rows: Int = 2, cardHeight: Dp = 114.dp) {
    val brush = rememberShimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth().height(cardHeight),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(columns) {
                    SkeletonCategoryCard(modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}
