package com.ciyato.launcher.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.ciyato.launcher.BuildConfig

/**
 * Local crash reporter.
 * Writes crash logs to app-private storage. User can share via email or any
 * app with ACTION_SEND. Never sends data automatically.
 *
 * Installs as the Thread.UncaughtExceptionHandler wrapping the default one.
 */
object CrashReporter {

    private const val LOG_DIR     = "crash_logs"
    private const val MAX_LOGS    = 10
    @Volatile private var installed = false
    /**
     * Whether a crash may be written to disk. **Fail closed.**
     *
     * This defaulted to true, and the handler is installed at process start
     * while the opt-out lives in DataStore and arrives asynchronously. So there
     * was a window — short, but exactly when early-startup crashes happen — in
     * which a crash was persisted for someone who had explicitly turned
     * diagnostics off (F-054).
     *
     * A privacy preference whose default is "on until we find out otherwise" is
     * not a preference. The cost of starting closed is that a crash in the first
     * few hundred milliseconds goes unrecorded for people who opted IN; the cost
     * of starting open is writing a file for people who opted OUT. Those are not
     * equivalent, and only one of them is a privacy failure.
     */
    @Volatile private var loggingEnabled = false

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
    /**
     * Called once the stored preference is known, and every time it changes.
     * Until the first call, nothing is written — see [loggingEnabled].
     */
    fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabled = enabled
    }

    private fun writeCrashLog(context: Context, thread: Thread, t: Throwable) {
        val dir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
        val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val log = File(dir, "crash_$ts.txt")
        log.writeText(buildString {
            append(
                crashReportHeader(
                    timestamp = ts,
                    threadName = thread.name,
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    gitSha = BuildConfig.GIT_SHA,
                    androidRelease = Build.VERSION.RELEASE,
                    androidSdk = Build.VERSION.SDK_INT,
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                ),
            )
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

    /**
     * The identifying block at the top of a crash log.
     *
     * The old header printed `Version : ${Build.VERSION.RELEASE}` — the ANDROID
     * version, under a label that reads like the app's — and recorded no app
     * version at all (F-055). So a report could not answer the first question
     * anybody asks of one: which build was this? Between releases dozens of
     * builds share `1.1.0`, which is why the commit is here too.
     *
     * Pure and parameterised because `Build.*` are non-functional stubs on the
     * JVM: taking the values as arguments is what makes the wording testable,
     * and wording is the entire defect here.
     *
     * Nothing personal is recorded IN THE HEADER, and that is a constraint rather
     * than an omission: no file names, no search queries, no notification text, no
     * package list. A local diagnostic is still the person's data, and a log they
     * might send to someone must not carry what they never chose to send.
     *
     * The scope of that promise matters and this comment used to overstate it. The
     * header is fully under Ciyato's control; the stack trace written after it is
     * not. An exception's own message is included, and the platform's messages
     * routinely embed a path - a FileNotFoundException or SecurityException can
     * carry something like `/storage/emulated/0/DCIM/Camera/IMG_0042.jpg` straight
     * into the log. Ciyato does not put it there and cannot strip it without
     * destroying the diagnostic value of the trace, so the honest handling is to
     * say so where somebody is about to share one, which the crash-log viewer now
     * does.
     */
    fun crashReportHeader(
        timestamp: String,
        threadName: String,
        appVersionName: String,
        appVersionCode: Int,
        gitSha: String,
        androidRelease: String?,
        androidSdk: Int,
        manufacturer: String?,
        model: String?,
    ): String = buildString {
        appendLine("=== Ciyato Crash Report ===")
        appendLine("Time    : $timestamp")
        appendLine("Thread  : $threadName")
        // App first: it is the thing under our control and the thing a fix
        // changes.
        appendLine("App     : $appVersionName ($appVersionCode) $gitSha")
        appendLine("Android : ${androidRelease.orUnknown()} (API $androidSdk)")
        appendLine("Device  : ${manufacturer.orUnknown()} ${model.orUnknown()}")
    }

    /** A missing Build field reads as unknown rather than as the string "null". */
    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "unknown"
}
