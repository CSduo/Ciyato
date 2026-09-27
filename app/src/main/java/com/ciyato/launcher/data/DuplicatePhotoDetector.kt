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
 * DuplicatePhotoDetector
 * Detects visually similar photos using an average hash (aHash) — see
 * [averageHash] for why that is weaker than the name this once carried.
 * Groups photos with Hamming distance ≤ threshold as duplicates.
 */
object DuplicatePhotoDetector {

    data class PhotoEntry(val id: Long, val uri: Uri, val name: String, val sizeBytes: Long)
    data class DuplicateGroup(val photos: List<PhotoEntry>)

    private const val HASH_SIZE = 8          // 8×8 = 64-bit hash

    /**
     * Max Hamming distance, out of 64 bits, still counted as the same photo.
     *
     * Was 10, which is far too loose for an average hash: aHash keys on coarse
     * brightness distribution, so 10 differing bits happily groups any two dim
     * photos, or any two bright ones, as duplicates of each other. Since the
     * caller deletes everything a group contains except one, a permissive
     * threshold here is measured in lost photos. 5 keeps re-saves, resizes and
     * re-compressions together while separating genuinely different pictures.
     */
    private const val SIMILARITY_THRESHOLD = 5

    /**
     * Average hash (aHash) of a bitmap: downscale to 8×8 greyscale, then set
     * one bit per pixel that is at least as bright as the frame's mean.
     *
     * Named honestly. This was called pHash, with its pixel array named `dct`
     * and a doc claiming a perceptual hash — but there is no DCT anywhere in
     * it, and never was. aHash is markedly weaker than a real pHash: it is
     * fooled by brightness and contrast changes that a DCT would ignore. That
     * matters because the result drives deletion, so it is worth knowing the
     * grouping is coarse rather than trusting a name that overstated it.
     */
    private fun averageHash(bitmap: Bitmap): Long {
        // Exactly HASH_SIZE square.
        //
        // This scaled to HASH_SIZE + 1 in both dimensions while the loops below
        // read only HASH_SIZE rows and columns, so a 9x9 bitmap was allocated and
        // scaled for every photo and 17 of its 81 pixels were thrown away
        // (F-014). The +1 is the signature of dHash, which compares each pixel
        // with its right-hand neighbour and therefore needs the extra column -
        // but this is an average hash, which does not. The name and the
        // arithmetic disagreed, and the arithmetic was the one that cost
        // something on every image in the library.
        val small = Bitmap.createScaledBitmap(bitmap, HASH_SIZE, HASH_SIZE, true)
        val luma = Array(HASH_SIZE) { row ->
            DoubleArray(HASH_SIZE) { col ->
                small.getPixel(col, row).let { px ->
                    (((px shr 16) and 0xFF) * 0.299 +
                     ((px shr 8)  and 0xFF) * 0.587 +
                     ( px         and 0xFF) * 0.114)
                }
            }
        }
        val avg = luma.flatMap { it.toList() }.average()
        var hash = 0L
        for (row in 0 until HASH_SIZE) {
            for (col in 0 until HASH_SIZE) {
                if (luma[row][col] >= avg) hash = hash or (1L shl (row * HASH_SIZE + col))
            }
        }
        small.recycle()
        return hash
    }

    private fun hammingDistance(a: Long, b: Long) = (a xor b).countOneBits()

    /**
     * How many of the newest photos this scan will hash.
     *
     * Public because the UI has to state it. Hashing is decode-bound, so a cap is
     * necessary — but a capped scan that reports "No duplicates found!" is making
     * a claim about the whole library it never looked at (F-102, F-012).
     */
    const val SCAN_LIMIT = 500

    /** Groups plus the coverage they were derived from, so the UI can be honest. */
    data class DuplicateScan(
        val groups: List<DuplicateGroup>,
        /**
         * Photos actually hashed.
         *
         * This was `photos.size` - the number LOADED - while its own comment said
         * hashed. Decoding is per-photo and each failure was swallowed, so a
         * library with unreadable images reported comparing more than it compared.
         */
        val scanned: Int,
        /**
         * Photos in the library, or null when the count query failed.
         *
         * Null rather than 0. It was 0 on failure, and `wasBounded` read
         * `libraryTotal > scanned` - so a FAILED count made `0 > 500` false, the
         * scan claimed to be exhaustive, and the screen said "across all 500
         * photos". A failure rendered as completeness, which is the single pattern
         * this audit found most often.
         */
        val libraryTotal: Int?,
        /**
         * Photos that could not be decoded, so were never compared.
         *
         * Corrupt files, a format with no decoder on this device, a permission
         * that evaporated mid-scan. Each was silently dropped. Somebody with 200
         * unreadable photos got "No duplicates found" with no hint that a quarter
         * of their library was never looked at.
         */
        val unreadable: Int,
    ) {
        /**
         * True unless completeness can be PROVEN.
         *
         * Three things have to hold: the library size is known, the scan reached
         * all of it, and nothing was skipped. Any unknown counts as bounded,
         * because the cost of wrongly claiming completeness here is somebody
         * believing their library has no duplicates left in it.
         */
        val wasBounded: Boolean
            get() = libraryTotal == null || libraryTotal > scanned || unreadable > 0
    }

    /** Total images in the library, for coverage reporting. Cheap COUNT query. */
    private fun libraryTotal(context: Context): Int? =
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                null, null, null,
            )?.use { it.count }
        }.getOrNull()

    /** Load the newest [SCAN_LIMIT] photos from MediaStore. */
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
            while (it.moveToNext() && photos.size < SCAN_LIMIT) {
                val id = it.getLong(idCol)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                photos.add(PhotoEntry(id, uri, it.getString(nameCol) ?: "", it.getLong(sizeCol)))
            }
        }
        return photos
    }

    /** Run look-alike detection over the newest [SCAN_LIMIT] photos. */
    suspend fun findDuplicates(context: Context): DuplicateScan = withContext(Dispatchers.IO) {
        val photos = loadPhotos(context)
        val total = libraryTotal(context)
        val hashes = mutableMapOf<PhotoEntry, Long>()

        photos.forEach { photo ->
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                context.contentResolver.openInputStream(photo.uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream, null, opts)
                    if (bmp != null) {
                        hashes[photo] = averageHash(bmp)
                        bmp.recycle()
                    }
                }
            } catch (_: Exception) {
                // Counted rather than swallowed. A photo that cannot be decoded is
                // a photo that was never compared, and the difference between
                // "no duplicates" and "no duplicates among the ones I could read"
                // is the whole value of this screen.
            }
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
                similar.add(photo)
                visited.addAll(similar)
                // The caller keeps photos[0] and deletes the rest, so this
                // ordering decides which copy survives. It used to be whichever
                // one the hash map happened to reach first — insertion order —
                // while the UI promised it kept "the best quality copy". That
                // made the full-resolution original a likely deletion target
                // with a thumbnail left behind. Biggest file wins, since among
                // re-saves of one image the largest is the least re-compressed;
                // id descending breaks ties toward the newest copy.
                groups.add(
                    DuplicateGroup(
                        similar.sortedWith(
                            compareByDescending<PhotoEntry> { it.sizeBytes }.thenByDescending { it.id },
                        ),
                    ),
                )
            }
        }

        DuplicateScan(
            groups = groups,
            // hashes.size, not photos.size: the number actually compared.
            scanned = hashes.size,
            libraryTotal = total,
            unreadable = photos.size - hashes.size,
        )
    }
}
