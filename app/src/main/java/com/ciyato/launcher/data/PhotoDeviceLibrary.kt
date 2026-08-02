package com.ciyato.launcher.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
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

    /** Group images by month for the timeline view, newest month first. */
    fun timeline(images: List<DeviceImage>): List<Pair<String, List<DeviceImage>>> {
        val format = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
        return images
            .sortedByDescending { it.takenAtMs }
            .groupBy { format.format(java.util.Date(it.takenAtMs)) }
            .map { it.key to it.value }
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
