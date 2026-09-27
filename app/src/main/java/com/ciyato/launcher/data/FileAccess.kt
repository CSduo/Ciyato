package com.ciyato.launcher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reading the phone's storage without the Storage Access Framework's fences.
 *
 * SAF is the default path everywhere in Ciyato, and for most people it is the
 * right one — it is scoped, revocable, and needs no special approval. What it
 * cannot do, by deliberate platform design since Android 11, is grant the root
 * of internal storage, the Download folder, or `Android/data`. No amount of UI
 * makes `ACTION_OPEN_DOCUMENT_TREE` return those; the picker simply refuses.
 *
 * The only way to see all of internal storage is MANAGE_EXTERNAL_STORAGE — the
 * "All files access" toggle in system settings. It is a restricted permission:
 * Google approves it only for apps whose core purpose needs it, file managers
 * being the named example, and it takes a declaration on the Play listing.
 * Treat it as optional everywhere — Ciyato has to stay useful without it.
 *
 * Even with it granted, `Android/data` and `Android/obb` remain unreadable on
 * Android 11+. Nothing here should ever claim to reach "everything".
 */
object FileAccess {

    /** Matches the authority declared for [FileProvider] in the manifest. */
    private const val PROVIDER_SUFFIX = ".files"

    /**
     * Search-index key for an All-files scan, which has no tree URI to key on.
     * Shared so the screen that writes the index and the screen that reads it
     * cannot drift apart — an index nothing matches is an index nothing finds.
     */
    const val INDEX_KEY_INTERNAL = "ciyato:internal-storage"

    /** True when Ciyato can read outside a SAF grant. */
    fun hasAllFiles(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    /**
     * Where to send someone to grant it. There is no runtime dialog for this
     * one — it can only be switched on in system settings.
     *
     * The per-app screen is preferred because it lands directly on Ciyato's own
     * toggle, but a few OEM builds ship without that activity, so the global
     * list is kept as a fallback rather than letting the tap do nothing.
     */
    fun allFilesSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val perApp = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        if (perApp.resolveActivity(context.packageManager) != null) return perApp
        val global = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        return global.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /**
     * The root of PRIMARY shared storage - `/storage/emulated/0` on most phones.
     *
     * Named honestly now. This was "the root of internal storage", and callers
     * reasonably read that as "the device", but it is one volume: an SD card, a
     * USB drive and every cloud provider are all somewhere else (F-008). A Files
     * screen built on this alone is device-wide only on a phone that has nothing
     * else attached.
     *
     * Android/data and Android/obb are unreadable inside it even with All files
     * access on Android 11+, so "everything on this volume" is not quite true
     * either - see [scanDirectory], which skips them rather than burning budget.
     *
     * Other volumes are reached through SAF, where the person picks them: see
     * [secondaryVolumeLabels] for what to tell them about it.
     */
    fun primarySharedStorageRoot(): File = Environment.getExternalStorageDirectory()

    /** Kept so existing callers keep compiling. Prefer the honest name. */
    @Deprecated(
        "Named as though it covered the device; it is one volume.",
        ReplaceWith("primarySharedStorageRoot()"),
    )
    fun internalRoot(): File = primarySharedStorageRoot()

    /**
     * Human-readable labels for storage volumes that are NOT primary.
     *
     * Empty on a phone with nothing attached, which is most of them - so an empty
     * list is the normal case and must not read as an error.
     *
     * This exists so the UI can say "this covers your phone storage; pick an SD
     * card or a cloud folder to include it" instead of implying a scan saw
     * everything. It deliberately does not try to SCAN those volumes: direct
     * paths into removable storage are unreliable across OEMs and versions, and
     * SAF is the supported route. Knowing a volume exists is enough to stop
     * overclaiming.
     */
    fun secondaryVolumeLabels(context: Context): List<String> = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return emptyList()
        val manager = context.getSystemService(android.os.storage.StorageManager::class.java)
            ?: return emptyList()
        manager.storageVolumes
            .filterNot { it.isPrimary }
            .mapNotNull { volume ->
                volume.getDescription(context)?.takeIf { it.isNotBlank() }
                    ?: if (volume.isRemovable) "Removable storage" else null
            }
    }.getOrDefault(emptyList())

    /** Outcome of converting a path into something another app may safely hold. */
    sealed interface Shareable {
        /** Safe to put in an Intent. */
        data class Ready(val uri: Uri) : Shareable

        /** Conversion failed. The caller must NOT fall back to the original URI. */
        data class Unavailable(val reason: String) : Shareable
    }

    /**
     * The single point where a path becomes something another app may hold.
     *
     * Handing a raw `file://` URI to another app throws FileUriExposedException
     * on API 24+, so every scanned file that leaves Ciyato — opened, shared,
     * edited — has to come through here first. Anything already a content URI
     * (the SAF path) passes straight through unchanged.
     *
     * Returns a result rather than a Uri, and that is the whole point. The first
     * version of this function ended in `.getOrDefault(uri)`: when FileProvider
     * refused a path — because it sits outside every configured <paths> root —
     * it handed back the raw `file://` URI it was written to eliminate. A helper
     * whose failure mode is "do the unsafe thing" is worse than no helper,
     * because every call site looks correct while none of them are. A sealed
     * result makes the failure branch impossible to ignore at compile time.
     */
    fun shareableUri(context: Context, uri: Uri): Shareable {
        if (uri.scheme != "file") return Shareable.Ready(uri)
        val path = uri.path ?: return Shareable.Unavailable("This file has no readable path")
        val file = File(path)

        // Canonicalise before converting, and require the real location to sit
        // inside shared storage or Ciyato's own directories.
        //
        // file_paths.xml has to declare a broad <external-path> for a file
        // manager to work at all — the person can browse to any folder, and a
        // narrow root would make "open" fail on perfectly ordinary files. What
        // should NOT be broad is the set of paths this code is willing to hand
        // over. getCanonicalFile resolves `..` segments and follows symlinks, so
        // a crafted or symlinked path cannot use the wide provider root to reach
        // somewhere outside shared storage.
        val canonical = runCatching { file.canonicalFile }.getOrNull()
            ?: return Shareable.Unavailable("This file's location can't be resolved")
        val allowedRoots = listOfNotNull(
            runCatching { internalRoot().canonicalFile }.getOrNull(),
            runCatching { context.getExternalFilesDir(null)?.canonicalFile }.getOrNull(),
            runCatching { context.cacheDir.canonicalFile }.getOrNull(),
        )
        val inAllowedRoot = allowedRoots.any { root ->
            canonical == root || canonical.path.startsWith(root.path + File.separator)
        }
        if (!inAllowedRoot) return Shareable.Unavailable("This file is outside shared storage")

        return runCatching {
            FileProvider.getUriForFile(context, context.packageName + PROVIDER_SUFFIX, canonical)
        }.fold(
            onSuccess = { Shareable.Ready(it) },
            onFailure = { Shareable.Unavailable("This file can't be shared safely") },
        )
    }

    /**
     * Hands a file to another app, or explains why it couldn't.
     *
     * Returns null on success, or a message to show the person. Every external
     * handoff funnels through here so three rules hold in one place instead of
     * being re-derived at each call site:
     *
     *  - a path that can't be converted safely is never launched (see
     *    [shareableUri]) — the operation fails instead of leaking a raw path;
     *  - viewing and editing launch directly, so Android honours whichever app
     *    the person set as default. Intent.createChooser deliberately bypasses
     *    that setting, which is why Ciyato used to re-ask "open with?" on every
     *    single tap. Sharing keeps the chooser, where picking a different target
     *    each time is the entire point;
     *  - nothing fails silently: if no installed app can handle the file, the
     *    caller gets a reason rather than a tap that appears to do nothing.
     */
    fun openExternally(
        context: Context,
        uri: Uri,
        mimeType: String? = null,
        action: String = Intent.ACTION_VIEW,
        forceChooser: Boolean = false,
    ): String? {
        val shareable = shareableUri(context, uri)
        val safeUri = when (shareable) {
            is Shareable.Ready -> shareable.uri
            is Shareable.Unavailable -> return shareable.reason
        }
        val type = mimeType?.takeIf { it.contains('/') } ?: mimeTypeOf(uri.lastPathSegment.orEmpty())
        val target = Intent(action)
        if (action == Intent.ACTION_SEND) {
            target.setType(type.ifBlank { "*/*" })
            target.putExtra(Intent.EXTRA_STREAM, safeUri)
            target.clipData = android.content.ClipData.newUri(context.contentResolver, "file", safeUri)
        } else {
            target.setDataAndType(safeUri, type.ifBlank { "*/*" })
        }
        target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (action == Intent.ACTION_EDIT) target.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (target.resolveActivity(context.packageManager) == null) {
            return "No app on this phone can open this file"
        }
        if (action != Intent.ACTION_SEND && !forceChooser) {
            if (runCatching { context.startActivity(target) }.isSuccess) return null
        }
        val label = when (action) {
            Intent.ACTION_SEND -> "Share file"
            Intent.ACTION_EDIT -> "Edit with"
            else -> "Open with"
        }
        return runCatching {
            context.startActivity(
                Intent.createChooser(target, label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.fold(onSuccess = { null }, onFailure = { "Could not open this file" })
    }

    /**
     * MIME type from a file name, or "" when nothing can say.
     *
     * The platform lookup is wrapped rather than trusted outright. On a device it
     * cannot fail; off one, `MimeTypeMap.getSingleton()` is an unmocked stub that
     * throws, and that made the whole directory scan untestable - so the one part
     * of Files most in need of a synthetic-tree test (F-093) could not have one
     * because of a lookup incidental to it.
     *
     * The fallback covers the extensions this app actually categorises by, so an
     * empty result stays meaningful rather than becoming the common case.
     */
    fun mimeTypeOf(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return ""
        return runCatching {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }.getOrNull() ?: FALLBACK_MIME_TYPES[extension] ?: ""
    }

    private val FALLBACK_MIME_TYPES = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "gif" to "image/gif", "webp" to "image/webp", "heic" to "image/heic",
        "mp4" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "ogg" to "audio/ogg",
        "wav" to "audio/wav", "flac" to "audio/flac",
        "pdf" to "application/pdf", "txt" to "text/plain",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "zip" to "application/zip", "apk" to "application/vnd.android.package-archive",
    )

    /** One file found by [scanDirectory]. */
    data class ScannedFile(
        val file: File,
        val name: String,
        val mimeType: String,
        val sizeBytes: Long,
        val modifiedAt: Long,
    )

    /**
     * Why a scan stopped, when it stopped early.
     *
     * Reported rather than collapsed into one boolean, because the three cases
     * mean different things to a person: a file cap means "there is more of the
     * same", a directory or time budget means "this tree is pathological and I
     * gave up". Telling them apart is the difference between "showing the first
     * 2,000" and "this folder is too deep to scan".
     */
    enum class ScanStop { FILE_LIMIT, ENTRY_BUDGET, TIME_BUDGET }

    data class DirectoryScan(
        val rootName: String,
        val files: List<ScannedFile>,
        val reachedLimit: Boolean,
        /**
         * Filesystem entries looked at, directories included.
         *
         * The number the old `limit` was documented as bounding and did not.
         */
        val inspectedEntries: Int = files.size,
        /** Null when the whole tree was walked. */
        val stoppedBecause: ScanStop? = null,
    )

    /**
     * Entries this walk will look at before giving up, directories included.
     *
     * The file limit alone bounded nothing on a directory-heavy tree (F-093):
     * the stop condition was `files.size >= limit`, and a directory did not
     * consume the budget. A tree of 200,000 empty folders with ten files in it
     * satisfied "stop at 2,000 entries" by traversing all 200,010 - and the UI
     * described that as scanning the first 2,000.
     */
    const val MAX_INSPECTED_ENTRIES = 60_000

    /**
     * Wall-clock ceiling for one scan.
     *
     * A belt to the entry budget's braces. Entry count is a proxy for work and
     * not a good one: a network-backed or fuse-mounted volume can make a single
     * listFiles() take seconds, so a scan can be well inside its entry budget
     * and still have hung the screen. Eight seconds is longer than any scan
     * anyone waits for.
     */
    const val MAX_SCAN_MILLIS = 8_000L

    /**
     * Walks [root] breadth-first, stopping at whichever budget runs out first:
     * [limit] files collected, [MAX_INSPECTED_ENTRIES] entries inspected, or
     * [MAX_SCAN_MILLIS] elapsed.
     *
     * Breadth-first on purpose: a depth-first walk that hits the limit can burn
     * the whole budget inside one deep folder and report a scan that misses
     * everything at the top level.
     *
     * Every cap is reported. A file tool that quietly truncates is worse than one
     * that says it did, and [stoppedBecause] says WHICH cap - "there is more of
     * the same" and "this tree defeated me" are different sentences.
     *
     * Symlink cycles are skipped by canonical path. Shared storage is not
     * supposed to contain them, and a scan that trusts that assumption loops
     * until one of the budgets saves it - which is a hang the person experiences
     * as the app being broken.
     */
    suspend fun scanDirectory(root: File, limit: Int): DirectoryScan =
        withContext(Dispatchers.IO) {
            val files = mutableListOf<ScannedFile>()
            val queue = ArrayDeque<File>().apply { add(root) }
            val visited = HashSet<String>()
            val deadline = System.currentTimeMillis() + MAX_SCAN_MILLIS
            var inspected = 0
            var stop: ScanStop? = null

            while (queue.isNotEmpty() && stop == null) {
                val dir = queue.removeFirst()
                // Checked per directory rather than per entry: currentTimeMillis
                // on every file in a 60,000-entry walk is its own cost.
                if (System.currentTimeMillis() >= deadline) {
                    stop = ScanStop.TIME_BUDGET
                    break
                }
                // A directory reached twice is a cycle or a hard link; walking it
                // again cannot find anything new.
                val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: dir.path
                if (!visited.add(canonical)) continue

                val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
                for (child in children) {
                    inspected++
                    if (files.size >= limit) {
                        stop = ScanStop.FILE_LIMIT
                        break
                    }
                    if (inspected >= MAX_INSPECTED_ENTRIES) {
                        stop = ScanStop.ENTRY_BUDGET
                        break
                    }
                    when {
                        // Android/data and Android/obb stay unreadable even
                        // with All files access on Android 11+, so descending
                        // only wastes the budget. Matched against the parent
                        // too - a folder merely *named* "data" elsewhere in the
                        // tree is an ordinary folder and must still be scanned.
                        child.isDirectory && dir.name == "Android" &&
                            child.name in OPAQUE_ANDROID_DIRS -> Unit
                        child.isDirectory -> queue.add(child)
                        child.isFile -> files += ScannedFile(
                            file = child,
                            name = child.name,
                            mimeType = mimeTypeOf(child.name),
                            sizeBytes = child.length().coerceAtLeast(0L),
                            modifiedAt = child.lastModified().coerceAtLeast(0L),
                        )
                    }
                }
            }

            // Anything left queued is unvisited tree, which is bounded whether or
            // not a budget tripped.
            val incomplete = stop != null || queue.isNotEmpty()
            DirectoryScan(
                rootName = root.name.ifBlank { "Internal storage" },
                files = files,
                reachedLimit = incomplete,
                inspectedEntries = inspected,
                stoppedBecause = stop ?: if (queue.isNotEmpty()) ScanStop.FILE_LIMIT else null,
            )
        }

    private val OPAQUE_ANDROID_DIRS = setOf("data", "obb")
}
