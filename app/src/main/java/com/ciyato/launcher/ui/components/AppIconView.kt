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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.ciyato.launcher.data.InstalledApp
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
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    cornerRadius: Dp = 14.dp,
    scale: Float = 1f,
    rotation: Float = 0f,
    accentHex: String? = null,
) {
    // Rasterizing a Drawable means allocating a Bitmap and running draw() — for
    // an adaptive icon that is two layers plus a mask. This is the single leaf
    // behind every icon in the launcher, so it happens 30-40 times in the first
    // frames of Home and again for each item scrolling into the drawer. The
    // process-wide cache below turns that into one raster per (icon, size)
    // instead of one per composable instance; the repository already caches
    // Drawables, but nothing cached the far more expensive rasterized result.
    //
    // Two bugs fixed in passing:
    //   - the key was `drawable` only, so a span-resized tile kept the bitmap
    //     rasterized at its old size;
    //   - `size.value` is dp, and it was being passed as a pixel dimension, so
    //     a 56dp icon rasterized at 112px and was upscaled to 168px on a 3x
    //     screen. Icons looked soft. Density now converts properly.
    val density = LocalDensity.current
    val pxSize = with(density) { size.toPx() }.toInt().coerceIn(1, 512)
    val bmp = remember(drawable, pxSize) { AppIconRasterCache.get(drawable, pxSize) }
    val accent = remember(accentHex) {
        accentHex?.let { value ->
            runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull()
        }
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
 * labelColor defaults to CiyatoSec; override it when the icon sits on a
 * wallpaper or photo rather than a Ciyato surface.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconTile(
    app: InstalledApp,
    modifier: Modifier = Modifier,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    labelColor: Color = CiyatoSec,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
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
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    iconShape: String = "squircle",
    onClick: () -> Unit = {},
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
