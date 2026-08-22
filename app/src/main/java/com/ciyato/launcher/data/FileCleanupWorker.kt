package com.ciyato.launcher.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Analyses only a previously granted SAF tree. It never deletes files: its output
 * is a verified, bounded duplicate-review list for the Files Browser.
 */
class FileCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val rootRaw = inputData.getString(INPUT_TREE_URI) ?: return@withContext Result.failure()
        val rootUri = Uri.parse(rootRaw)
        try {
            val root = DocumentFile.fromTreeUri(applicationContext, rootUri)
                ?.takeIf(DocumentFile::canRead)
                ?: return@withContext Result.failure(errorData("Selected folder is no longer readable."))

            val discovery = discoverCandidates(root)
            val checkpoint = FileCleanupResultStore.loadCheckpoint(applicationContext, rootRaw)
            val hashes = checkpoint.toMutableMap()
            var hashedBytes = 0L
            var skippedForBudget = discovery.skippedForBudget

            discovery.files.forEachIndexed { index, file ->
                currentCoroutineContext().ensureActive()
                val key = file.uri.toString()
                if (key !in hashes) {
                    if (hashedBytes + file.sizeBytes > MAX_TOTAL_HASH_BYTES) {
                        skippedForBudget = true
                    } else {
                        // One file that vanished or turned unreadable must not abort the
                        // whole analysis — it simply stops being a duplicate candidate.
                        val hash = try {
                            sha256(applicationContext, file.uri) { bytesRead -> hashedBytes += bytesRead }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        if (hash != null) {
                            hashes[key] = hash
                            // Checkpointing after every single file meant rewriting a growing
                            // JSON blob synchronously N times; batching keeps restart safety
                            // without the quadratic disk cost.
                            if (hashes.size % CHECKPOINT_EVERY == 0) {
                                FileCleanupResultStore.saveCheckpoint(applicationContext, rootRaw, hashes)
                            }
                        }
                    }
                }
                setProgress(
                    Data.Builder()
                        .putInt(PROGRESS_HASHED, index + 1)
                        .putInt(PROGRESS_TOTAL, discovery.files.size)
                        .build(),
                )
            }

            val verified = discovery.files.mapNotNull { file ->
                hashes[file.uri.toString()]?.let { hash -> VerifiedCleanupFile(file, hash) }
            }
            val groups = verified
                .groupBy { it.hash }
                .values
                .filter { group -> group.size > 1 }
                .map { group ->
                    DuplicateCleanupGroup(
                        bytesPerFile = group.first().file.sizeBytes,
                        files = group.map { candidate ->
                            CleanupFileRef(
                                uri = candidate.file.uri.toString(),
                                name = candidate.file.name,
                                sizeBytes = candidate.file.sizeBytes,
                            )
                        },
                    )
                }
                .sortedByDescending { it.reclaimableBytes }

            FileCleanupResultStore.saveResult(
                applicationContext,
                CleanupAnalysisResult(
                    rootUri = rootRaw,
                    inspectedEntries = discovery.inspectedEntries,
                    hashedFiles = verified.size,
                    wasBounded = discovery.reachedEntryLimit || skippedForBudget,
                    groups = groups,
                    completedAt = System.currentTimeMillis(),
                ),
            )
            FileCleanupResultStore.clearCheckpoint(applicationContext, rootRaw)
            Result.success(
                Data.Builder()
                    .putInt(RESULT_GROUPS, groups.size)
                    .putBoolean(RESULT_BOUNDED, discovery.reachedEntryLimit || skippedForBudget)
                    .build(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (security: SecurityException) {
            Result.failure(errorData("Android revoked access to the selected folder."))
        } catch (_: Exception) {
            // Retrying forever would leave the Files screen spinning on "Verifying…" for a
            // permanent failure, so give up after one retry and surface the reason.
            if (runAttemptCount < 1) Result.retry()
            else Result.failure(errorData("Duplicate analysis could not finish. No files were changed."))
        }
    }

    private suspend fun sha256(context: Context, uri: Uri, onBytesRead: (Long) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                onBytesRead(count.toLong())
            }
        } ?: throw SecurityException("File can no longer be opened")
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun discoverCandidates(root: DocumentFile): CandidateDiscovery {
        val folders = ArrayDeque<DocumentFile>().apply { add(root) }
        val files = mutableListOf<CleanupDocument>()
        var inspected = 0
        while (folders.isNotEmpty() && inspected < MAX_DISCOVERED_ENTRIES) {
            val folder = folders.removeFirst()
            val children = runCatching { folder.listFiles().sortedBy { it.uri.toString() } }.getOrDefault(emptyList())
            for (document in children) {
                if (inspected >= MAX_DISCOVERED_ENTRIES) break
                inspected += 1
                when {
                    document.isDirectory && document.canRead() -> folders.add(document)
                    document.isFile && document.canRead() -> {
                        val size = document.length().coerceAtLeast(0L)
                        if (size in 1..MAX_BYTES_PER_FILE) {
                            files += CleanupDocument(document.uri, document.name.orEmpty().ifBlank { "Unnamed file" }, size)
                        }
                    }
                }
            }
        }
        // Largest candidates first: the hashing budget should be spent where duplicates
        // actually reclaim space, not on hundreds of tiny same-size files.
        val sameSizeOnly = files
            .groupBy(CleanupDocument::sizeBytes)
            .values
            .filter { it.size > 1 }
            .flatten()
            .sortedWith(compareByDescending<CleanupDocument> { it.sizeBytes }.thenBy { it.uri.toString() })
        val selected = sameSizeOnly.take(MAX_HASH_CANDIDATES)
        return CandidateDiscovery(
            files = selected,
            inspectedEntries = inspected,
            reachedEntryLimit = folders.isNotEmpty() || inspected >= MAX_DISCOVERED_ENTRIES,
            skippedForBudget = sameSizeOnly.size > selected.size,
        )
    }

    private fun errorData(message: String): Data = Data.Builder().putString(RESULT_ERROR, message).build()

    private data class CleanupDocument(val uri: Uri, val name: String, val sizeBytes: Long)
    private data class CandidateDiscovery(
        val files: List<CleanupDocument>,
        val inspectedEntries: Int,
        val reachedEntryLimit: Boolean,
        val skippedForBudget: Boolean,
    )
    private data class VerifiedCleanupFile(val file: CleanupDocument, val hash: String)

    companion object {
        const val INPUT_TREE_URI = "tree_uri"
        const val PROGRESS_HASHED = "hashed"
        const val PROGRESS_TOTAL = "total"
        const val RESULT_GROUPS = "groups"
        const val RESULT_BOUNDED = "bounded"
        const val RESULT_ERROR = "error"
        private const val MAX_DISCOVERED_ENTRIES = 2_000
        private const val MAX_HASH_CANDIDATES = 200
        private const val MAX_BYTES_PER_FILE = 256L * 1024L * 1024L
        private const val MAX_TOTAL_HASH_BYTES = 512L * 1024L * 1024L
        private const val HASH_BUFFER_BYTES = 64 * 1024
        private const val CHECKPOINT_EVERY = 16

        fun enqueue(context: Context, treeUri: Uri) =
            OneTimeWorkRequestBuilder<FileCleanupWorker>()
                .setInputData(Data.Builder().putString(INPUT_TREE_URI, treeUri.toString()).build())
                .build()
                .also { request ->
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "ciyato-file-cleanup-${treeUri.hashCode()}",
                        ExistingWorkPolicy.REPLACE,
                        request,
                    )
                }
    }
}

data class CleanupFileRef(val uri: String, val name: String, val sizeBytes: Long)

data class DuplicateCleanupGroup(val bytesPerFile: Long, val files: List<CleanupFileRef>) {
    val reclaimableBytes: Long get() = bytesPerFile * (files.size - 1).coerceAtLeast(0)
}

data class CleanupAnalysisResult(
    val rootUri: String,
    val inspectedEntries: Int,
    val hashedFiles: Int,
    val wasBounded: Boolean,
    val groups: List<DuplicateCleanupGroup>,
    val completedAt: Long,
) {
    val reclaimableBytes: Long get() = groups.sumOf(DuplicateCleanupGroup::reclaimableBytes)
}

/**
 * Returns only the copies a person has explicitly selected for removal. Every group must
 * retain one valid URI, otherwise that group contributes no deletion candidates.
 */
fun plannedDuplicateDeletions(
    groups: List<DuplicateCleanupGroup>,
    keptUris: Map<Int, String>,
): List<CleanupFileRef> = groups.flatMapIndexed { groupIndex, group ->
    val keptUri = keptUris[groupIndex]
    if (keptUri !in group.files.map(CleanupFileRef::uri)) {
        emptyList()
    } else {
        group.files.filterNot { it.uri == keptUri }
    }
}.distinctBy(CleanupFileRef::uri)

/** Small private persistence store used for safe restart/cancellation recovery. */
object FileCleanupResultStore {
    private const val PREFERENCES = "ciyato_file_cleanup"
    private const val RESULT_PREFIX = "result:"
    private const val CHECKPOINT_PREFIX = "checkpoint:"

    fun loadResult(context: Context, rootUri: String): CleanupAnalysisResult? = runCatching {
        val raw = prefs(context).getString(RESULT_PREFIX + rootUri, null) ?: return null
        val json = JSONObject(raw)
        if (json.optString("rootUri") != rootUri) return null
        CleanupAnalysisResult(
            rootUri = rootUri,
            inspectedEntries = json.optInt("inspectedEntries"),
            hashedFiles = json.optInt("hashedFiles"),
            wasBounded = json.optBoolean("wasBounded"),
            groups = json.optJSONArray("groups")?.let(::decodeGroups).orEmpty(),
            completedAt = json.optLong("completedAt"),
        )
    }.getOrNull()

    fun saveResult(context: Context, result: CleanupAnalysisResult) {
        val groups = JSONArray().apply {
            result.groups.forEach { group ->
                put(JSONObject().apply {
                    put("bytesPerFile", group.bytesPerFile)
                    put("files", JSONArray().apply {
                        group.files.forEach { file -> put(JSONObject().apply {
                            put("uri", file.uri)
                            put("name", file.name)
                            put("sizeBytes", file.sizeBytes)
                        }) }
                    })
                })
            }
        }
        prefs(context).edit().putString(
            RESULT_PREFIX + result.rootUri,
            JSONObject().apply {
                put("rootUri", result.rootUri)
                put("inspectedEntries", result.inspectedEntries)
                put("hashedFiles", result.hashedFiles)
                put("wasBounded", result.wasBounded)
                put("groups", groups)
                put("completedAt", result.completedAt)
            }.toString(),
        ).apply()
    }

    fun loadCheckpoint(context: Context, rootUri: String): Map<String, String> = runCatching {
        val json = JSONObject(prefs(context).getString(CHECKPOINT_PREFIX + rootUri, "{}") ?: "{}")
        val hashes = json.optJSONObject("hashes") ?: return emptyMap()
        // A malformed entry would come back as a blank hash, and every file carrying it
        // would then group together as "duplicates" — files the user could then delete.
        // Only well-formed SHA-256 hex survives.
        buildMap {
            hashes.keys().forEach { key ->
                val hash = hashes.optString(key)
                if (hash.length == 64) put(key, hash)
            }
        }
    }.getOrDefault(emptyMap())

    // apply() is the usual advice and is wrong here. This runs on a worker
    // thread that the system may kill at any moment, and the entire point of the
    // checkpoint is to survive that kill so a cancelled scan resumes instead of
    // restarting. apply() defers the write, which is exactly the case it must
    // not lose. Suppressed rather than silenced: the behaviour is deliberate.
    @android.annotation.SuppressLint("ApplySharedPref")
    fun saveCheckpoint(context: Context, rootUri: String, hashes: Map<String, String>) {
        val values = JSONObject().apply { hashes.forEach { (uri, hash) -> put(uri, hash) } }
        prefs(context).edit().putString(CHECKPOINT_PREFIX + rootUri, JSONObject().put("hashes", values).toString()).commit()
    }

    fun clearCheckpoint(context: Context, rootUri: String) {
        prefs(context).edit().remove(CHECKPOINT_PREFIX + rootUri).apply()
    }

    fun clearResult(context: Context, rootUri: String) {
        prefs(context).edit().remove(RESULT_PREFIX + rootUri).apply()
    }

    private fun decodeGroups(array: JSONArray): List<DuplicateCleanupGroup> = buildList {
        for (index in 0 until array.length()) {
            val group = array.optJSONObject(index) ?: continue
            val files = group.optJSONArray("files") ?: continue
            val references = buildList {
                for (fileIndex in 0 until files.length()) {
                    val file = files.optJSONObject(fileIndex) ?: continue
                    val uri = file.optString("uri")
                    if (uri.isNotBlank()) add(CleanupFileRef(uri, file.optString("name", "Unnamed file"), file.optLong("sizeBytes")))
                }
            }
            if (references.size > 1) add(DuplicateCleanupGroup(group.optLong("bytesPerFile"), references))
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
