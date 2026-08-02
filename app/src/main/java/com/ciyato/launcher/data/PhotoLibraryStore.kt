package com.ciyato.launcher.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
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
        return runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { }
                ?: error("Selected media is no longer readable")
            var displayName: String? = null
            var lastModified = 0L
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, "last_modified"),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it >= 0 }
                        ?.let { displayName = cursor.getString(it) }
                    cursor.getColumnIndex("last_modified")
                        .takeIf { it >= 0 }
                        ?.let { lastModified = cursor.getLong(it) }
                }
            }
            MediaMetadata(displayName, resolver.getType(uri), lastModified, true)
        }.getOrElse {
            MediaMetadata(null, null, 0L, false)
        }
    }

    private data class MediaMetadata(
        val displayName: String?,
        val mimeType: String?,
        val lastModified: Long,
        val isAvailable: Boolean,
    )
}
