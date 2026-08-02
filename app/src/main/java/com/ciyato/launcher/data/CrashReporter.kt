package com.ciyato.launcher.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Local crash reporter — Suggestion #144.
 * Writes crash logs to app-private storage. User can share via email or any
 * app with ACTION_SEND. Never sends data automatically.
 *
 * Installs as the Thread.UncaughtExceptionHandler wrapping the default one.
 */
object CrashReporter {

    private const val LOG_DIR     = "crash_logs"
    private const val MAX_LOGS    = 10
    @Volatile private var installed = false
    @Volatile private var loggingEnabled = true

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appCtx = context.applicationContext
            val default = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    if (loggingEnabled) writeCrashLog(appCtx, thread, throwable)
                } catch (_: Exception) {
                    // Never crash inside the crash handler
                }
                default?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    /** Controlled by the visible local-only Crash Reporting setting. */
    fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabled = enabled
    }

    private fun writeCrashLog(context: Context, thread: Thread, t: Throwable) {
        val dir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
        val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val log = File(dir, "crash_$ts.txt")
        log.writeText(buildString {
            appendLine("=== Ciyato Crash Report ===")
            appendLine("Time    : $ts")
            appendLine("Thread  : ${thread.name}")
            appendLine("Device  : ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            appendLine("Version : ${Build.VERSION.RELEASE}")
            appendLine()
            appendLine("=== Throwable ===")
            appendLine(t.toString())
            appendLine()
            appendLine("=== Stack Trace ===")
            appendLine(t.stackTraceToString())
            val cause = t.cause
            if (cause != null) {
                appendLine()
                appendLine("=== Caused By ===")
                appendLine(cause.stackTraceToString())
            }
        })

        // Rotate — keep only the newest MAX_LOGS files
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS)
            ?.forEach { it.delete() }
    }

    /** Returns all stored crash log files sorted newest-first. */
    fun getLogs(context: Context): List<File> {
        val dir = File(context.filesDir, LOG_DIR)
        return (dir.listFiles()?.toList() ?: emptyList())
            .sortedByDescending { it.lastModified() }
    }

    /** Reads the content of a crash log. */
    suspend fun readLog(file: File): String = withContext(Dispatchers.IO) {
        runCatching { file.readText() }.getOrElse { "Could not read log: ${it.message}" }
    }

    /** Deletes all crash logs. */
    fun clearLogs(context: Context) {
        File(context.filesDir, LOG_DIR).listFiles()?.forEach { it.delete() }
    }
}
