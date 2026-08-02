package com.ciyato.launcher.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.theme.CiyatoLightSec
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

/**
 * Displays the REAL installed-app icon from the device.
 * - Never uses fake/placeholder icons for real apps.
 * - Corner radius: 14dp by default — matches Ciyato rounded icon style.
 * - Falls back silently if icon fails.
 */
@Composable
fun RealAppIcon(
    drawable: Drawable,
    size: Dp = 52.dp,
    cornerRadius: Dp = 14.dp,
    scale: Float = 1f,
    rotation: Float = 0f,
    accentHex: String? = null,
    modifier: Modifier = Modifier,
) {
    // Cache the bitmap — only recompute when the drawable reference changes.
    val bmp = remember(drawable) {
        drawable.toBitmap((size.value * 2).toInt().coerceAtLeast(1), (size.value * 2).toInt().coerceAtLeast(1))
    }
    val accent = accentHex?.let { value ->
        runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull()
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .then(if (accent != null) Modifier.background(accent.copy(alpha = 0.22f)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.coerceIn(0.72f, 1.28f)
                    scaleY = scale.coerceIn(0.72f, 1.28f)
                    rotationZ = rotation.coerceIn(-20f, 20f)
                }
                .clip(RoundedCornerShape(cornerRadius)),
        )
    }
}

/**
 * Full app icon + label tile — used in grids and rows.
 * labelColor defaults to CiyatoSec (dark); pass CiyatoLightSec for light-mode drawer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconTile(
    app: InstalledApp,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    labelColor: Color = CiyatoSec,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        RealAppIcon(
            drawable = app.icon,
            size = iconSize,
            scale = app.iconScale,
            rotation = app.iconRotation,
            accentHex = app.iconAccent,
        )
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = app.label,
                color = labelColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}


@Composable
fun AppIconView(
    app: InstalledApp,
    size: Dp = 52.dp,
    iconShape: String = "squircle",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cornerRadius = when (iconShape) {
        "circle" -> size / 2
        "rounded" -> 16.dp
        "raw" -> 0.dp
        else -> 14.dp
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        RealAppIcon(
            drawable = app.icon,
            size = size,
            cornerRadius = cornerRadius,
            scale = app.iconScale,
            rotation = app.iconRotation,
            accentHex = app.iconAccent,
        )
    }
}
