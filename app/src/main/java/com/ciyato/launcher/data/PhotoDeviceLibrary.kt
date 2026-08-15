package com.ciyato.launcher.data

import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    private val IMAGE_PROJECTION = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.SIZE,
    )

    private const val IMAGE_SORT = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

    /** Reads the row the cursor is parked on, using [IMAGE_PROJECTION]'s column order. */
    private fun Cursor.readImage(): DeviceImage = DeviceImage(
        uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, getLong(0)),
        name = getString(1) ?: "",
        bucket = getString(2) ?: "Other",
        // DATE_TAKEN is null for anything the camera didn't write (screenshots,
        // downloads), so fall back to DATE_MODIFIED — which is in seconds.
        takenAtMs = getLong(3).takeIf { it > 0 } ?: (getLong(4) * 1000L),
        sizeBytes = getLong(5),
    )

    private fun queryImages(context: Context, trashedOnly: Boolean): Cursor? {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (!trashedOnly) return resolver.query(collection, IMAGE_PROJECTION, null, null, IMAGE_SORT)
        // Trashed rows are hidden from ordinary queries by design; only the
        // Bundle form of query() can ask for them, and only from API 30.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, IMAGE_SORT)
        }
        return resolver.query(collection, IMAGE_PROJECTION, args, null)
    }

    suspend fun loadImages(context: Context, limit: Int = 3_000): List<DeviceImage> =
        withContext(Dispatchers.IO) {
            buildList {
                runCatching {
                    queryImages(context, trashedOnly = false)?.use { cursor ->
                        while (cursor.moveToNext() && size < limit) add(cursor.readImage())
                    }
                }
            }
        }

    /**
     * Same query as [loadImages], but emits an early slice before finishing.
     *
     * Reading a few thousand MediaStore rows takes long enough that a single
     * terminal result leaves the grid blank for seconds on a full phone. The
     * sort is stable (newest first), so the early slice is already the top of
     * the final list and later rows only ever append below it — nothing the
     * person is looking at moves when the rest arrives.
     */
    fun imageStream(
        context: Context,
        firstBatch: Int = 180,
        limit: Int = 3_000,
    ): Flow<List<DeviceImage>> = flow {
        val all = ArrayList<DeviceImage>(firstBatch)
        var emittedEarly = false
        runCatching {
            queryImages(context, trashedOnly = false)?.use { cursor ->
                while (cursor.moveToNext() && all.size < limit) {
                    all.add(cursor.readImage())
                    if (!emittedEarly && all.size >= firstBatch) {
                        emittedEarly = true
                        emit(ArrayList(all))
                    }
                }
            }
        }
        emit(all)
    }.flowOn(Dispatchers.IO)

    /** Photos sitting in the system trash, newest first. Empty below API 30. */
    suspend fun loadTrashedImages(context: Context, limit: Int = 1_000): List<DeviceImage> =
        withContext(Dispatchers.IO) {
            buildList {
                runCatching {
                    queryImages(context, trashedOnly = true)?.use { cursor ->
                        while (cursor.moveToNext() && size < limit) add(cursor.readImage())
                    }
                }
            }
        }

    /**
     * True when the OS owns a real trash for media, so deleting is reversible.
     *
     * Ciyato deliberately does not hand-roll a trash below this: the only way
     * to fake one is to copy every "deleted" file into private storage, which
     * *doubles* usage at the exact moment someone is deleting to reclaim space,
     * and leaves the copies orphaned if the app is uninstalled. Where the OS
     * can't do it properly, we say so instead of pretending.
     */
    val trashSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Consent requests for destructive media operations.
     *
     * Media Ciyato did not create belongs to whoever wrote it, so the system —
     * not this app — asks for confirmation. Each returns the [IntentSender] to
     * launch, or null when the platform is too old or the request is empty.
     */
    fun trashRequest(context: Context, uris: Collection<Uri>): IntentSender? {
        val media = mediaUris(uris) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            MediaStore.createTrashRequest(context.contentResolver, media, true).intentSender
        }.getOrNull()
    }

    fun restoreRequest(context: Context, uris: Collection<Uri>): IntentSender? {
        val media = mediaUris(uris) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            MediaStore.createTrashRequest(context.contentResolver, media, false).intentSender
        }.getOrNull()
    }

    fun deleteRequest(context: Context, uris: Collection<Uri>): IntentSender? {
        val media = mediaUris(uris) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, media).intentSender
        }.getOrNull()
    }

    /** Non-empty media-authority URIs, or null — anything else throws inside the system call. */
    private fun mediaUris(uris: Collection<Uri>): List<Uri>? =
        uris.filter { it.authority == MediaStore.AUTHORITY }.takeIf { it.isNotEmpty() }

    /**
     * Legacy delete for API < 30, where no trash and no batch consent exist.
     * Returns how many rows actually went away, so callers can report the true
     * number rather than assuming the whole selection succeeded.
     */
    suspend fun deleteDirectly(context: Context, uris: Collection<Uri>): Int =
        withContext(Dispatchers.IO) {
            uris.count { uri ->
                runCatching { context.contentResolver.delete(uri, null, null) > 0 }
                    .getOrDefault(false)
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
        forceChooser: Boolean = false,
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
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Sharing is the one case where a chooser is the point — you pick a
        // different destination every time. Viewing and editing are not:
        // createChooser explicitly bypasses the default-app setting, so routing
        // them through it re-asked "open with?" on every single tap even after
        // the person had chosen a gallery and tapped Always. Launch those
        // directly and let Android honour the default it already recorded.
        // [forceChooser] is how "Open with…" stays reachable. Honouring the
        // default is right for a plain tap, but it leaves no way to send one
        // photo to a different app — so that has to be an explicit choice
        // rather than something you can only get by clearing defaults in
        // system settings.
        if (action != Intent.ACTION_SEND && !forceChooser) {
            if (runCatching { context.startActivity(target) }.isSuccess) return true
        }
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

    /** Share sheet for a whole selection at once. */
    fun launchShareMultiple(context: Context, uris: Collection<Uri>): Boolean {
        if (uris.isEmpty()) return false
        val list = ArrayList(uris)
        if (list.size == 1) return launchPhotoAction(context, list[0], Intent.ACTION_SEND)
        val target = Intent(Intent.ACTION_SEND_MULTIPLE)
        target.setType("image/*")
        target.putParcelableArrayListExtra(Intent.EXTRA_STREAM, list)
        target.clipData = ClipData.newUri(context.contentResolver, "photos", list[0])
            .also { clip -> list.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return runCatching {
            context.startActivity(
                Intent.createChooser(target, "Share ${list.size} photos")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
