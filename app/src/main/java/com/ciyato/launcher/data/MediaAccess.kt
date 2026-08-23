package com.ciyato.launcher.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * How much of the media library Android is actually letting Ciyato see.
 *
 * The distinction that matters is [PARTIAL]. From Android 14 a person can grant
 * access to a hand-picked subset of their gallery, and a boolean
 * "do we have media permission?" answers true for it — while MediaStore then
 * reports only the selected files. Any total computed from those queries is a
 * total of the subset, not of the device.
 *
 * That is not a small discrepancy. Storage Cleanup subtracted the visible media
 * bytes from whole-device used space and labelled the remainder "Other / app
 * data": under a partial grant, every photo the person did not pick was silently
 * reclassified as app data, in a chart that looked authoritative (F-115).
 */
enum class MediaAccess {
    /** No media permission at all. */
    NONE,

    /** Android 14 "Select photos": a chosen subset only. Totals are not totals. */
    PARTIAL,

    /** The whole library is visible. */
    FULL;

    val canSeeAnything: Boolean get() = this != NONE

    /** True when a measured total describes the device rather than a subset. */
    val totalsAreComplete: Boolean get() = this == FULL

    companion object {
        /** Android 14's partial-access permission, absent from older SDKs. */
        const val VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        fun of(context: Context): MediaAccess {
            fun granted(permission: String) =
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

            val full = if (Build.VERSION.SDK_INT >= 33) {
                granted(android.Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                @Suppress("DEPRECATION")
                granted(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            return when {
                full -> FULL
                Build.VERSION.SDK_INT >= 34 && granted(VISUAL_USER_SELECTED) -> PARTIAL
                else -> NONE
            }
        }

        /** The permissions to request for this SDK level. */
        fun requestedPermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
            )
            // Pre-33 devices have no granular media permissions.
            else -> @Suppress("DEPRECATION") arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
