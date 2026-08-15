package com.ciyato.launcher.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Device-wide media/file index backed by MediaStore.
 *
 * Access model:
 *  - Media categories (Photos, Videos, Screenshots, WhatsApp media) need the
 *    READ_MEDIA_* runtime permissions (READ_EXTERNAL_STORAGE before API 33).
 *  - Non-media categories (Documents, Downloads, APKs) additionally need
 *    "All files access" (MANAGE_EXTERNAL_STORAGE); without it their counts are
 *    whatever MediaStore exposes to us, which may be zero on API 33+.
 */
class MediaLibraryRepository(private val context: Context) {

    enum class CategoryKey { SCREENSHOTS, DOCUMENTS, DOWNLOADS, PHOTOS, VIDEOS, APKS, WHATSAPP, AUDIO }

    data class CategorySummary(
        val key: CategoryKey,
        val count: Int,
        val totalBytes: Long,
    )

    data class LibraryFile(
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val sizeBytes: Long,
        val modifiedAtMs: Long,
        val relativePath: String,
    )

    data class StorageSummary(val usedBytes: Long, val totalBytes: Long) {
        val usedFraction: Float
            get() = if (totalBytes <= 0L) 0f else (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    fun storageSummary(): StorageSummary {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return StorageSummary(usedBytes = total - free, totalBytes = total)
    }

    fun hasMediaPermission(): Boolean {
        fun granted(permission: String) =
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return when {
            // Android 14+: full image/video access OR the "Select photos"
            // partial grant all count as usable access.
            Build.VERSION.SDK_INT >= 34 ->
                granted(android.Manifest.permission.READ_MEDIA_IMAGES) ||
                    granted(android.Manifest.permission.READ_MEDIA_VIDEO) ||
                    granted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
            Build.VERSION.SDK_INT >= 33 ->
                granted(android.Manifest.permission.READ_MEDIA_IMAGES) ||
                    granted(android.Manifest.permission.READ_MEDIA_VIDEO)
            else ->
                granted(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Whether MANAGE_EXTERNAL_STORAGE is held.
     *
     * Narrower than [FileAccess.hasAllFiles], which also answers true on API
     * 29 and below where plain READ_EXTERNAL_STORAGE already reaches
     * everything. Kept separate on purpose — this one gates the "grant All
     * files access" prompts, and those must not appear on versions that have
     * no such toggle. The shared check still backs it so the two cannot drift.
     */
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= 30 && FileAccess.hasAllFiles(context)

    suspend fun categorySummaries(): Map<CategoryKey, CategorySummary> = withContext(Dispatchers.IO) {
        CategoryKey.entries.associateWith { key ->
            val (selection, args) = selectionFor(key)
            summarize(key, selection, args)
        }
    }

    suspend fun recentFiles(limit: Int = 12): List<LibraryFile> = withContext(Dispatchers.IO) {
        queryFiles(
            selection = "${MediaStore.Files.FileColumns.SIZE} > 0",
            selectionArgs = null,
            sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            limit = limit,
        )
    }

    suspend fun filesForCategory(key: CategoryKey, limit: Int = 500): List<LibraryFile> =
        withContext(Dispatchers.IO) {
            val (selection, args) = selectionFor(key)
            queryFiles(
                selection = selection,
                selectionArgs = args,
                sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
                limit = limit,
            )
        }

    suspend fun largeFiles(thresholdBytes: Long = 100L * 1024 * 1024, limit: Int = 100): List<LibraryFile> =
        withContext(Dispatchers.IO) {
            queryFiles(
                selection = "${MediaStore.Files.FileColumns.SIZE} >= ?",
                selectionArgs = arrayOf(thresholdBytes.toString()),
                sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC",
                limit = limit,
            )
        }

    // ── internals ─────────────────────────────────────────────────────────────

    private val filesUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /**
     * Column used for folder/path matching. RELATIVE_PATH only exists in the
     * MediaStore schema from API 29; on Android 8–9 (minSdk 26) it makes every
     * query throw, so we fall back to the legacy DATA (absolute path) column.
     */
    @Suppress("DEPRECATION")
    private val pathColumn: String =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Files.FileColumns.RELATIVE_PATH
        else MediaStore.Files.FileColumns.DATA

    private fun selectionFor(key: CategoryKey): Pair<String, Array<String>?> {
        val media = MediaStore.Files.FileColumns.MEDIA_TYPE
        val mime = MediaStore.Files.FileColumns.MIME_TYPE
        val path = pathColumn
        val name = MediaStore.Files.FileColumns.DISPLAY_NAME
        return when (key) {
            CategoryKey.PHOTOS ->
                "$media = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}" to null
            CategoryKey.VIDEOS ->
                "$media = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}" to null
            CategoryKey.AUDIO ->
                "$media = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}" to null
            CategoryKey.SCREENSHOTS ->
                "$media = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} AND $path LIKE ?" to
                    arrayOf("%Screenshots%")
            CategoryKey.WHATSAPP ->
                "$path LIKE ?" to arrayOf("%WhatsApp%")
            CategoryKey.DOWNLOADS ->
                // "%Download%" matches both the RELATIVE_PATH form ("Download/")
                // and the absolute DATA form (".../Download/...").
                "$path LIKE ?" to arrayOf("%Download%")
            CategoryKey.APKS ->
                "$name LIKE ?" to arrayOf("%.apk")
            CategoryKey.DOCUMENTS ->
                "$mime IN (${DOCUMENT_MIMES.joinToString(",") { "?" }})" to DOCUMENT_MIMES.toTypedArray()
        }
    }

    private fun summarize(key: CategoryKey, selection: String, args: Array<String>?): CategorySummary {
        var count = 0
        var bytes = 0L
        runCatching {
            context.contentResolver.query(
                filesUri,
                arrayOf(MediaStore.Files.FileColumns.SIZE),
                selection,
                args,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    count++
                    bytes += cursor.getLong(0)
                }
            }
        }
        return CategorySummary(key, count, bytes)
    }

    private fun queryFiles(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String,
        limit: Int,
    ): List<LibraryFile> = buildList {
        runCatching {
            context.contentResolver.query(
                filesUri,
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED,
                    pathColumn,
                ),
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idCol = 0
                while (cursor.moveToNext() && size < limit) {
                    val displayName = cursor.getString(1) ?: continue
                    add(
                        LibraryFile(
                            uri = ContentUris.withAppendedId(filesUri, cursor.getLong(idCol)),
                            name = displayName,
                            mimeType = cursor.getString(2) ?: "application/octet-stream",
                            sizeBytes = cursor.getLong(3),
                            modifiedAtMs = cursor.getLong(4) * 1000L,
                            relativePath = cursor.getString(5) ?: "",
                        ),
                    )
                }
            }
        }
    }

    companion object {
        private val DOCUMENT_MIMES = listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
        )

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
            bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
            bytes >= 1L shl 10 -> "%.0f KB".format(bytes.toDouble() / (1L shl 10))
            else -> "$bytes B"
        }
    }
}
