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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Real photo backup: copies MediaStore images into a dated subfolder inside a
 * user-chosen SAF tree. Shared by the manual "Backup Now" button in
 * AutoBackupScreen and by [PhotoBackupWorker]'s periodic runs, so both paths
 * do exactly the same, honest thing — only photos, nothing else.
 */
data class PhotoBackupResult(
    val copiedCount: Int,
    val completedAtMs: Long,
    val error: String? = null,
)

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

        val df = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
        val backupFolder = root.createDirectory("Ciyato_Backup_${df.format(Date())}") ?: run {
            cursor.close()
            return@withContext PhotoBackupResult(0, System.currentTimeMillis(), error = "Could not create a backup folder.")
        }

        var done = 0
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
                try {
                    val destFile = backupFolder.createFile(mime, name)
                    if (destFile != null) {
                        context.contentResolver.openInputStream(uri)?.use { inStream ->
                            context.contentResolver.openOutputStream(destFile.uri)?.use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        done++
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // One unreadable/vanished photo shouldn't abort the whole backup.
                }
                onProgress(done, total)
            }
        }
        PhotoBackupResult(copiedCount = done, completedAtMs = System.currentTimeMillis())
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
        if (result.error != null) return@withContext Result.failure()

        settings.setPhotoBackupLastRun(result.completedAtMs, result.copiedCount)
        Result.success()
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
