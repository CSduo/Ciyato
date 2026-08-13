package com.ciyato.launcher.data

import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Device-wide photo library grouped into smart collections.
 *
 * Grouping is heuristic and instant (no network, no cost): storage buckets
 * (Camera, Screenshots, WhatsApp, …) plus a synthetic "Recent" collection.
 */
object PhotoDeviceLibrary {

    data class DeviceImage(
        val uri: Uri,
        val name: String,
        val bucket: String,
        val takenAtMs: Long,
        val sizeBytes: Long,
    )

    data class DeviceCollection(
        val key: String,
        val title: String,
        val coverUri: Uri?,
        val count: Int,
    )

    data class DeviceVideo(
        val uri: Uri,
        val id: Long,
        val name: String,
        val bucket: String,
        val takenAtMs: Long,
        val sizeBytes: Long,
        val durationMs: Long,
    )

    suspend fun loadImages(context: Context, limit: Int = 3_000): List<DeviceImage> =
        withContext(Dispatchers.IO) {
            buildList {
                runCatching {
                    context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(
                            MediaStore.Images.Media._ID,
                            MediaStore.Images.Media.DISPLAY_NAME,
                            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                            MediaStore.Images.Media.DATE_TAKEN,
                            MediaStore.Images.Media.DATE_MODIFIED,
                            MediaStore.Images.Media.SIZE,
                        ),
                        null,
                        null,
                        "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
                    )?.use { cursor ->
                        while (cursor.moveToNext() && size < limit) {
                            val taken = cursor.getLong(3).takeIf { it > 0 }
                                ?: (cursor.getLong(4) * 1000L)
                            add(
                                DeviceImage(
                                    uri = ContentUris.withAppendedId(
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        cursor.getLong(0),
                                    ),
                                    name = cursor.getString(1) ?: "",
                                    bucket = cursor.getString(2) ?: "Other",
                                    takenAtMs = taken,
                                    sizeBytes = cursor.getLong(5),
                                ),
                            )
                        }
                    }
                }
            }
        }

    /** Bucket images into user-facing collections, largest first. */
    fun collections(images: List<DeviceImage>): List<DeviceCollection> {
        if (images.isEmpty()) return emptyList()
        val byBucket = images.groupBy { normalizedBucket(it.bucket, it.name) }
        val bucketCollections = byBucket.entries
            .sortedByDescending { it.value.size }
            .map { (title, bucketImages) ->
                DeviceCollection(
                    key = "bucket:$title",
                    title = title,
                    coverUri = bucketImages.firstOrNull()?.uri,
                    count = bucketImages.size,
                )
            }
        val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val recent = images.filter { it.takenAtMs >= monthAgo }
        val recentCollection = recent.takeIf { it.isNotEmpty() }?.let {
            DeviceCollection(
                key = "recent",
                title = "Recent",
                coverUri = it.first().uri,
                count = it.size,
            )
        }
        return listOfNotNull(recentCollection) + bucketCollections
    }

    fun imagesForCollection(images: List<DeviceImage>, key: String): List<DeviceImage> = when {
        key == "recent" -> {
            val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            images.filter { it.takenAtMs >= monthAgo }
        }
        key.startsWith("bucket:") -> {
            val title = key.removePrefix("bucket:")
            images.filter { normalizedBucket(it.bucket, it.name) == title }
        }
        else -> emptyList()
    }

    /**
     * Device-wide videos, newest first. Mirrors [loadImages] but against the Video
     * MediaStore table. Unlike Images, the video table's DATE_TAKEN column was only
     * added in API 29 — querying it unconditionally throws on API 26–28 (this app's
     * minSdk), so DATE_MODIFIED (present on every API level) is used as the one date
     * source here instead.
     */
    suspend fun loadVideos(context: Context, limit: Int = 1_000): List<DeviceVideo> =
        withContext(Dispatchers.IO) {
            buildList {
                runCatching {
                    context.contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(
                            MediaStore.Video.Media._ID,
                            MediaStore.Video.Media.DISPLAY_NAME,
                            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                            MediaStore.Video.Media.DATE_MODIFIED,
                            MediaStore.Video.Media.SIZE,
                            MediaStore.Video.Media.DURATION,
                        ),
                        null,
                        null,
                        "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
                    )?.use { cursor ->
                        while (cursor.moveToNext() && size < limit) {
                            val id = cursor.getLong(0)
                            add(
                                DeviceVideo(
                                    uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                                    id = id,
                                    name = cursor.getString(1) ?: "",
                                    bucket = cursor.getString(2) ?: "Other",
                                    takenAtMs = cursor.getLong(3) * 1000L,
                                    sizeBytes = cursor.getLong(4),
                                    durationMs = cursor.getLong(5),
                                ),
                            )
                        }
                    }
                }
            }
        }

    /**
     * A real decoded frame for [uri], used as a grid thumbnail since Coil's default
     * pipeline (no coil-video artifact here) can't decode video containers. API 29+
     * uses the unified thumbnail API; older devices fall back to the deprecated but
     * still-functional MediaStore video-thumbnails table, keyed by [videoId].
     */
    suspend fun loadVideoThumbnail(context: Context, uri: Uri, videoId: Long): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(uri, Size(384, 384), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        videoId,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null,
                    )
                }
            }.getOrNull()
        }

    /** Group images by month for the timeline view, newest month first. */
    fun timeline(images: List<DeviceImage>): List<Pair<String, List<DeviceImage>>> {
        val format = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
        return images
            .sortedByDescending { it.takenAtMs }
            .groupBy { format.format(java.util.Date(it.takenAtMs)) }
            .map { it.key to it.value }
    }

    /**
     * Hands [uri] to the phone's own apps: [Intent.ACTION_VIEW] to open it,
     * [Intent.ACTION_EDIT] to edit it in the gallery/editor already installed,
     * [Intent.ACTION_SEND] to share it. Always routed through a chooser so the
     * person picks the app, and returns false when nothing on the device can
     * handle it so the caller can say so instead of failing silently.
     */
    fun launchPhotoAction(
        context: Context,
        uri: Uri,
        action: String,
        mimeType: String? = null,
    ): Boolean {
        val mime = mimeType?.takeIf { it.contains('/') } ?: "image/*"
        // Built without apply{}: inside an Intent receiver, `type`/`action` would
        // resolve to the Intent's own members instead of the locals here.
        val target = Intent(action)
        if (action == Intent.ACTION_SEND) {
            target.setType(mime)
            target.putExtra(Intent.EXTRA_STREAM, uri)
            target.clipData = ClipData.newUri(context.contentResolver, "photo", uri)
        } else {
            target.setDataAndType(uri, mime)
        }
        target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Editors write the result back through the same URI.
        if (action == Intent.ACTION_EDIT) target.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (target.resolveActivity(context.packageManager) == null) return false
        val label = when (action) {
            Intent.ACTION_EDIT -> "Edit photo with"
            Intent.ACTION_SEND -> "Share photo"
            else -> "Open photo with"
        }
        return runCatching {
            context.startActivity(
                Intent.createChooser(target, label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    private fun normalizedBucket(bucket: String, name: String): String = when {
        bucket.equals("Screenshots", true) || name.startsWith("Screenshot", true) -> "Screenshots"
        bucket.contains("WhatsApp", true) -> "WhatsApp"
        bucket.equals("Camera", true) || bucket.equals("DCIM", true) -> "Camera"
        bucket.equals("Download", true) || bucket.equals("Downloads", true) -> "Downloads"
        bucket.isBlank() -> "Other"
        else -> bucket
    }
}
