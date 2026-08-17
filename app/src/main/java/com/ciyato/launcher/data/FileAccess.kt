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

    /** The root of internal storage — `/storage/emulated/0` on most phones. */
    fun internalRoot(): File = Environment.getExternalStorageDirectory()

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
        return runCatching {
            FileProvider.getUriForFile(context, context.packageName + PROVIDER_SUFFIX, File(path))
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

    fun mimeTypeOf(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
    }

    /** One file found by [scanDirectory]. */
    data class ScannedFile(
        val file: File,
        val name: String,
        val mimeType: String,
        val sizeBytes: Long,
        val modifiedAt: Long,
    )

    data class DirectoryScan(
        val rootName: String,
        val files: List<ScannedFile>,
        val reachedLimit: Boolean,
    )

    /**
     * Walks [root] breadth-first, stopping at [limit] entries.
     *
     * Breadth-first on purpose: a depth-first walk that hits the limit can burn
     * the whole budget inside one deep folder and report a scan that misses
     * everything at the top level. The cap is reported rather than hidden —
     * a file tool that quietly truncates is worse than one that says it did.
     */
    suspend fun scanDirectory(root: File, limit: Int): DirectoryScan =
        withContext(Dispatchers.IO) {
            val files = mutableListOf<ScannedFile>()
            val queue = ArrayDeque<File>().apply { add(root) }
            var reachedLimit = false

            while (queue.isNotEmpty() && !reachedLimit) {
                val dir = queue.removeFirst()
                val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
                for (child in children) {
                    if (files.size >= limit) {
                        reachedLimit = true
                        break
                    }
                    when {
                        // Android/data and Android/obb stay unreadable even
                        // with All files access on Android 11+, so descending
                        // only wastes the budget. Matched against the parent
                        // too — a folder merely *named* "data" elsewhere in the
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

            DirectoryScan(
                rootName = root.name.ifBlank { "Internal storage" },
                files = files,
                reachedLimit = reachedLimit || queue.isNotEmpty(),
            )
        }

    private val OPAQUE_ANDROID_DIRS = setOf("data", "obb")
}
