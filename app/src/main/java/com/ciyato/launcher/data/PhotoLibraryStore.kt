package com.ciyato.launcher.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Photos destination stores only URI references deliberately selected by
 * the person. It never enumerates the device gallery outside that access model.
 */
data class PhotoCollection(
    val id: String,
    val name: String,
    val uris: List<String>,
)

data class AuthorizedMedia(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val lastModified: Long,
    val isAvailable: Boolean,
)

object PhotoLibraryStore {
    fun parseUris(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct()
    }.getOrDefault(emptyList())

    fun serializeUris(uris: Collection<String>): String = JSONArray(uris.distinct()).toString()

    fun parseCollections(raw: String): List<PhotoCollection> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(PhotoCollection(id, name, parseUris(item.optString("uris"))))
            }
        }
    }.getOrDefault(emptyList())

    fun serializeCollections(collections: Collection<PhotoCollection>): String = JSONArray().apply {
        collections.forEach { collection ->
            put(
                JSONObject().apply {
                    put("id", collection.id)
                    put("name", collection.name)
                    put("uris", JSONArray(collection.uris.distinct()))
                },
            )
        }
    }.toString()
}

class PhotoMediaRepository(private val context: Context) {
    suspend fun resolve(uriStrings: List<String>): List<AuthorizedMedia> = withContext(Dispatchers.IO) {
        uriStrings.distinct().map { rawUri ->
            val uri = Uri.parse(rawUri)
            val metadata = queryMetadata(context.contentResolver, uri)
            AuthorizedMedia(
                uri = uri,
                displayName = metadata.displayName ?: uri.lastPathSegment ?: "Selected media",
                mimeType = metadata.mimeType,
                lastModified = metadata.lastModified,
                isAvailable = metadata.isAvailable,
            )
        }
    }

    private fun queryMetadata(resolver: ContentResolver, uri: Uri): MediaMetadata {
        // Availability is decided purely by whether the URI still opens. Metadata
        // is a bonus: providers reject projections they don't know ("last_modified"
        // is a DocumentsProvider column, not a MediaStore one), and a rejected
        // column must not make a perfectly readable photo look revoked.
        val readable = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { } ?: error("no descriptor")
        }.isSuccess
        if (!readable) return MediaMetadata(null, null, 0L, false)

        var displayName: String? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) displayName = cursor.getString(0)
            }
        }
        var lastModified = 0L
        runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) lastModified = cursor.getLong(0) * 1000L
            }
        }
        return MediaMetadata(displayName, resolver.getType(uri), lastModified, true)
    }

    private data class MediaMetadata(
        val displayName: String?,
        val mimeType: String?,
        val lastModified: Long,
        val isAvailable: Boolean,
    )
}
