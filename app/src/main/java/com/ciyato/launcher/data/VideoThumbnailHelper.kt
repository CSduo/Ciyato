package com.ciyato.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * VideoThumbnailHelper — Suggestion #74
 * Extracts video thumbnails using MediaMetadataRetriever.
 * Returns a Bitmap frame at the specified time (default: 1 second in).
 */
object VideoThumbnailHelper {

    data class VideoEntry(
        val id: Long,
        val uri: Uri,
        val name: String,
        val durationMs: Long,
        val sizeBytes: Long,
    )

    /** Extract a single frame from a video URI as a Bitmap. */
    suspend fun extractThumbnail(context: Context, uri: Uri, timeUs: Long = 1_000_000L): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                frame
            } catch (_: Exception) { null }
        }

    /** Get video duration in milliseconds. */
    fun getDurationMs(context: Context, uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
    }

    /** Format duration as "mm:ss". */
    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    /** Load all videos from MediaStore. */
    suspend fun loadAllVideos(context: Context): List<VideoEntry> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoEntry>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                videos.add(VideoEntry(
                    id = id,
                    uri = uri,
                    name = cursor.getString(nameCol) ?: "video_$id",
                    durationMs = cursor.getLong(durCol),
                    sizeBytes = cursor.getLong(sizeCol),
                ))
            }
        }
        videos
    }
}
