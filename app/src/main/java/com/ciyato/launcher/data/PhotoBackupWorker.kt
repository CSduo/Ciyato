package com.ciyato.launcher.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Single reused destination folder.
 *
 * Deliberately NOT timestamped. A per-run folder name is what made backup
 * non-idempotent: every run created a new directory and re-copied anything it
 * saw again, so the destination accumulated near-duplicate folders and the
 * person could not tell which held the current set.
 */
private const val BACKUP_FOLDER_NAME = "Ciyato_Backup"

/**
 * Real photo backup: copies MediaStore images into a dated subfolder inside a
 * user-chosen SAF tree. Shared by the manual "Backup Now" button in
 * AutoBackupScreen and by [PhotoBackupWorker]'s periodic runs, so both paths
 * do exactly the same, honest thing — only photos, nothing else.
 */
/**
 * The real outcome of a backup run.
 *
 * One count was not enough to be truthful. The previous version returned only
 * `copiedCount`, and incremented it whenever a destination file could be
 * *created* — the `?.use` blocks around the actual streams could both be skipped
 * (either `openInputStream` or `openOutputStream` may return null) and the
 * counter still advanced. So it could report "200 photos saved" having written
 * zero bytes, and then move the watermark past all 200.
 *
 * [failed] exists so a partial run can be told apart from a clean one, and
 * [skippedExisting] so a re-run over the same photos reads as "already safe"
 * rather than as work done twice.
 */
data class PhotoBackupResult(
    val copiedCount: Int,
    val completedAtMs: Long,
    val error: String? = null,
    val skippedExisting: Int = 0,
    val failed: Int = 0,
) {
    /** True when every photo the run examined is now in the destination. */
    val isComplete: Boolean get() = failed == 0 && error == null

    /** One line describing what actually happened, for the UI to show verbatim. */
    fun summary(): String = when {
        error != null -> error
        copiedCount == 0 && skippedExisting == 0 && failed == 0 -> "Nothing new to back up."
        else -> buildList {
            if (copiedCount > 0) add("$copiedCount copied")
            if (skippedExisting > 0) add("$skippedExisting already there")
            if (failed > 0) add("$failed failed")
        }.joinToString(" · ")
    }
}

/** READ_MEDIA_IMAGES on API 33+, READ_EXTERNAL_STORAGE below — this feature only ever touches photos. */
fun hasPhotoBackupPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * Copies every photo modified at or after [sinceEpochSeconds] into a new dated
 * subfolder under [folderUri]. Passing 0 backs up the entire photo library
 * (first-ever run); later calls pass the previous run's timestamp so repeat
 * backups only copy what's actually new — no artificial item cap, and no
 * empty subfolder is created when there's nothing new to copy.
 */
suspend fun runPhotoBackup(
    context: Context,
    folderUri: Uri,
    sinceEpochSeconds: Long,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
): PhotoBackupResult = withContext(Dispatchers.IO) {
    try {
        if (!hasPhotoBackupPermission(context)) {
            return@withContext PhotoBackupResult(0, System.currentTimeMillis(), error = "Photo access is not granted.")
        }
        val root = DocumentFile.fromTreeUri(context, folderUri)?.takeIf { it.canWrite() }
            ?: return@withContext PhotoBackupResult(0, System.currentTimeMillis(), error = "Backup folder is no longer accessible.")

        // The watermark for the NEXT run is taken before the query, not after
        // the copy loop. It used to be stamped at completion, which opened a
        // permanent hole: the query selects photos modified >= T0, the next run
        // starts from T1 (when copying finished), and anything taken between T0
        // and T1 was in neither window — never backed up, with no error and no
        // way to notice. The window is widest on the first full-library run,
        // exactly when it takes longest. Overlapping slightly instead means a
        // photo can be considered twice, which the copy loop already handles by
        // skipping names that exist; a gap could not be recovered at all.
        val startedAtMs = System.currentTimeMillis()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.DATE_MODIFIED} >= ?",
            arrayOf(sinceEpochSeconds.toString()),
            "${MediaStore.Images.Media.DATE_MODIFIED} ASC",
        ) ?: return@withContext PhotoBackupResult(0, System.currentTimeMillis(), error = "Could not query photos.")

        val total = cursor.count
        if (total == 0) {
            cursor.close()
            return@withContext PhotoBackupResult(copiedCount = 0, completedAtMs = System.currentTimeMillis())
        }

        // ONE stable destination, reused across runs.
        //
        // This used to be Ciyato_Backup_<timestamp>, created fresh on every run,
        // which made the feature the opposite of idempotent: each run produced a
        // new folder, and any photo seen by two runs was copied twice into two
        // places. Over weeks the destination fills with near-duplicate folders
        // and the person cannot tell which one is current. A single folder plus a
        // per-file existence check means running twice is genuinely a no-op.
        val backupFolder = root.findFile(BACKUP_FOLDER_NAME)?.takeIf { it.isDirectory }
            ?: root.createDirectory(BACKUP_FOLDER_NAME)
            ?: run {
                cursor.close()
                return@withContext PhotoBackupResult(
                    0, System.currentTimeMillis(), error = "Could not create a backup folder.",
                )
            }

        // Names already present, read once. Checking per file via findFile would
        // be an O(n) provider query inside an O(n) loop.
        val existing = runCatching {
            backupFolder.listFiles().mapNotNull { it.name }.toHashSet()
        }.getOrDefault(hashSetOf())

        var copied = 0
        var skipped = 0
        var failed = 0
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (it.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val id = it.getLong(idCol)
                val name = it.getString(nameCol) ?: "photo_$id.jpg"
                val mime = it.getString(mimeCol) ?: "image/jpeg"
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                if (name in existing) {
                    skipped++
                    onProgress(copied + skipped, total)
                    continue
                }

                // Counted as copied ONLY if bytes actually moved. The old code
                // incremented as soon as a destination file was created, so a
                // null input or output stream produced a phantom success.
                var wrote = false
                var destFile: DocumentFile? = null
                try {
                    destFile = backupFolder.createFile(mime, name)
                    if (destFile != null) {
                        context.contentResolver.openInputStream(uri)?.use { inStream ->
                            context.contentResolver.openOutputStream(destFile!!.uri)?.use { outStream ->
                                inStream.copyTo(outStream)
                                outStream.flush()
                                wrote = true
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // One unreadable or vanished photo shouldn't abort the run.
                }

                if (wrote) {
                    copied++
                    existing += name
                } else {
                    failed++
                    // Remove the empty placeholder, otherwise the next run sees
                    // the name, "skips" it, and the photo is lost for good.
                    destFile?.let { partial -> runCatching { partial.delete() } }
                }
                onProgress(copied + skipped, total)
            }
        }

        PhotoBackupResult(
            copiedCount = copied,
            completedAtMs = startedAtMs,
            skippedExisting = skipped,
            failed = failed,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        PhotoBackupResult(0, System.currentTimeMillis(), error = e.message ?: "Backup failed.")
    }
}

/**
 * Genuine periodic backup — this is what makes "Photo Backup" honestly
 * automatic rather than a manual one-shot wearing an "Auto" label. Reads the
 * saved SAF folder and last-run timestamp from [LauncherSettingsRepository],
 * runs [runPhotoBackup] against them, and records the new timestamp so the
 * next run (manual or scheduled) only copies what's new since this one.
 */
class PhotoBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = LauncherSettingsRepository(applicationContext)
        val folderRaw = settings.photoBackupFolderUri.first()
        if (folderRaw.isBlank()) return@withContext Result.failure()

        val sinceSeconds = settings.photoBackupLastRunAt.first() / 1000L
        val result = runPhotoBackup(applicationContext, Uri.parse(folderRaw), sinceSeconds) { done, total ->
            setProgressAsync(
                androidx.work.Data.Builder()
                    .putInt(PROGRESS_DONE, done)
                    .putInt(PROGRESS_TOTAL, total)
                    .build(),
            )
        }
        if (result.error != null) return@withContext Result.retry()

        // The watermark advances ONLY when every photo this run examined actually
        // landed. If any failed, leaving it where it was means the next run sees
        // them again — which is now harmless, because the destination is a single
        // folder and already-copied photos are skipped by name. Advancing past a
        // failure would have lost those photos permanently, with the UI still
        // reporting success.
        if (result.isComplete) {
            settings.setPhotoBackupLastRun(result.completedAtMs, result.copiedCount)
            Result.success()
        } else {
            // Partial: keep the old watermark and let WorkManager retry with
            // backoff. Nothing is reported as finished that wasn't.
            Result.retry()
        }
    }

    companion object {
        const val PROGRESS_DONE = "done"
        const val PROGRESS_TOTAL = "total"
        private const val WORK_NAME = "ciyato-photo-backup-auto"
        private val PERIOD = 24L to TimeUnit.HOURS

        /** Idempotent: safe to call every time the setting/folder is (re)confirmed. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PhotoBackupWorker>(PERIOD.first, PERIOD.second)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
