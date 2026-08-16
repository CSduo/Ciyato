package com.ciyato.launcher.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

/**
 * Process-wide cache of rasterized app icons, keyed by (icon, pixel size).
 *
 * Turning a [Drawable] into a [Bitmap] runs `draw()` — for an adaptive icon
 * that is a background layer, a foreground layer and a mask. [RealAppIcon] is
 * the single leaf behind every icon in the launcher, so without a cache that
 * work happened once per composable instance: 30-40 times in the first frames
 * of Home, again for the dock, again per category card, and once more for each
 * item scrolling into the app drawer — all synchronously, in composition, on
 * the main thread.
 *
 * `LauncherRepository` already caches the Drawables themselves; nothing cached
 * the far more expensive rasterized result. Because the same Drawable instance
 * comes back from that cache, and icons are drawn at a handful of sizes, the
 * hit rate here is very high after the first frame.
 *
 * Bounded by total bitmap bytes rather than entry count — icons range from
 * ~40 px in a category cluster to ~200 px on a resized tile, so counting
 * entries would size the budget by a factor of 25 either way.
 */
object AppIconRasterCache {

    /** Roughly 40 icons at 96px ARGB_8888, or ~10 at 200px. */
    private const val MAX_BYTES = 6 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(drawable: Drawable, pxSize: Int): Bitmap {
        val size = pxSize.coerceIn(1, 512)
        // constantState is shared by every Drawable inflated from the same
        // resource, so two instances of one app's icon hit the same entry.
        // Falls back to instance identity for drawables that don't have one.
        val identity = drawable.constantState?.hashCode() ?: System.identityHashCode(drawable)
        val key = "$identity@$size"
        cache.get(key)?.takeIf { !it.isRecycled }?.let { return it }
        val bitmap = drawable.toBitmap(size, size)
        cache.put(key, bitmap)
        return bitmap
    }

    /** Drops every entry — call when icons themselves change (theme/pack switch). */
    fun clear() = cache.evictAll()
}
