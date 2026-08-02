package com.ciyato.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DuplicatePhotoDetector — Suggestion #31
 * Detects visually duplicate photos using a perceptual hash (pHash).
 * Groups photos with Hamming distance ≤ threshold as duplicates.
 */
object DuplicatePhotoDetector {

    data class PhotoEntry(val id: Long, val uri: Uri, val name: String, val sizeBytes: Long)
    data class DuplicateGroup(val photos: List<PhotoEntry>)

    private const val HASH_SIZE = 8          // 8×8 = 64-bit hash
    private const val SIMILARITY_THRESHOLD = 10 // max Hamming distance

    /** Compute a perceptual hash (pHash) for a Bitmap. */
    private fun pHash(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, HASH_SIZE + 1, HASH_SIZE + 1, true)
        val dct = Array(HASH_SIZE) { row ->
            DoubleArray(HASH_SIZE) { col ->
                val gray = small.getPixel(col, row).let { px ->
                    (((px shr 16) and 0xFF) * 0.299 +
                     ((px shr 8)  and 0xFF) * 0.587 +
                     ( px         and 0xFF) * 0.114)
                }
                gray
            }
        }
        val avg = dct.flatMap { it.toList() }.average()
        var hash = 0L
        for (row in 0 until HASH_SIZE) {
            for (col in 0 until HASH_SIZE) {
                if (dct[row][col] >= avg) hash = hash or (1L shl (row * HASH_SIZE + col))
            }
        }
        small.recycle()
        return hash
    }

    private fun hammingDistance(a: Long, b: Long) = (a xor b).countOneBits()

    /** Load all photos from MediaStore (limited to first 500 for performance). */
    private fun loadPhotos(context: Context): List<PhotoEntry> {
        val photos = mutableListOf<PhotoEntry>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
        )
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        ) ?: return emptyList()

        cursor.use {
            val idCol   = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            while (it.moveToNext() && photos.size < 500) {
                val id = it.getLong(idCol)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                photos.add(PhotoEntry(id, uri, it.getString(nameCol) ?: "", it.getLong(sizeCol)))
            }
        }
        return photos
    }

    /** Run duplicate detection. Returns list of groups with ≥ 2 photos. */
    suspend fun findDuplicates(context: Context): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val photos = loadPhotos(context)
        val hashes = mutableMapOf<PhotoEntry, Long>()

        photos.forEach { photo ->
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                context.contentResolver.openInputStream(photo.uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream, null, opts)
                    if (bmp != null) {
                        hashes[photo] = pHash(bmp)
                        bmp.recycle()
                    }
                }
            } catch (_: Exception) {}
        }

        val visited = mutableSetOf<PhotoEntry>()
        val groups = mutableListOf<DuplicateGroup>()

        hashes.keys.forEach { photo ->
            if (photo in visited) return@forEach
            val photoHash = hashes[photo] ?: return@forEach
            val similar = hashes.filter { (other, hash) ->
                other != photo && other !in visited &&
                hammingDistance(photoHash, hash) <= SIMILARITY_THRESHOLD
            }.keys.toMutableList()

            if (similar.isNotEmpty()) {
                similar.add(0, photo)
                visited.addAll(similar)
                groups.add(DuplicateGroup(similar))
            }
        }

        groups
    }
}
