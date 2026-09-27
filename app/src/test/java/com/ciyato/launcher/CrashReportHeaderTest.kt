package com.ciyato.launcher

import com.ciyato.launcher.data.CrashReporter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A crash log has to say which build crashed.
 *
 * The old header printed `Version : <Build.VERSION.RELEASE>` — the **Android**
 * version, under a label that reads like the app's — and recorded no app version
 * at all (F-055). So a report could not answer the first question anyone asks of
 * one, and a maintainer reading "Version : 14" would reasonably diagnose against
 * the wrong artifact entirely.
 *
 * Tested as a pure function because `Build.*` are non-functional stubs on the
 * JVM, and because the defect *was* the wording. Wording is exactly the kind of
 * thing that passes review by being glanced at.
 */
class CrashReportHeaderTest {

    private fun header(
        appVersionName: String = "1.1.0",
        appVersionCode: Int = 2608231,
        gitSha: String = "a1b2c3d",
        androidRelease: String? = "14",
        androidSdk: Int = 34,
        manufacturer: String? = "Google",
        model: String? = "Pixel 8",
    ) = CrashReporter.crashReportHeader(
        timestamp = "20260927_120000",
        threadName = "main",
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        gitSha = gitSha,
        androidRelease = androidRelease,
        androidSdk = androidSdk,
        manufacturer = manufacturer,
        model = model,
    )

    @Test
    fun `the app version is present and identified as the app's`() {
        val text = header()
        assertTrue("no app version line: $text", "App     : 1.1.0 (2608231)" in text)
    }

    @Test
    fun `the Android version is identified as Android's`() {
        val text = header()
        assertTrue("no android line: $text", "Android : 14 (API 34)" in text)
    }

    @Test
    fun `an ambiguous Version label is gone`() {
        // The defect in one assertion. A line reading "Version : 14" invites a
        // maintainer to diagnose against app version 14, which does not exist.
        assertFalse("an unqualified Version label is back", "Version :" in header())
    }

    @Test
    fun `two builds of the same version are distinguishable`() {
        // Between releases, dozens of builds share "1.1.0". The commit is the
        // only thing that separates them, which is why it is in the header.
        val first = header(gitSha = "aaa1111")
        val second = header(gitSha = "bbb2222")
        assertFalse("two builds produced identical headers", first == second)
        assertTrue("aaa1111" in first)
        assertTrue("bbb2222" in second)
    }

    @Test
    fun `an uncommitted build says so`() {
        // A bare SHA would claim the artifact matches that commit when it does
        // not, which is worse than no SHA: it sends someone to read code that
        // was not what ran.
        assertTrue("a1b2c3d-dirty" in header(gitSha = "a1b2c3d-dirty"))
    }

    @Test
    fun `a build with no git information still produces a usable header`() {
        val text = header(gitSha = "unknown")
        assertTrue("App     : 1.1.0 (2608231) unknown" in text)
    }

    @Test
    fun `a missing Build field reads as unknown rather than null`() {
        // Build.MODEL can be absent on unusual images, and a header containing
        // the literal string "null" looks like a bug in the reporter instead of
        // a gap in the device.
        val text = header(androidRelease = null, manufacturer = null, model = "")
        assertFalse("the string null reached the header: $text", "null" in text)
        assertTrue("unknown" in text)
    }

    @Test
    fun `the header records nothing personal`() {
        // A local diagnostic is still the person's data, and a log they might
        // send to someone must not carry what they never chose to send. This is
        // a constraint on the header rather than an omission from it.
        val text = header().lowercase()
        listOf("query", "search", "filename", "/storage/", "content://", "notification")
            .forEach { leak ->
                assertFalse("\"$leak\" appears in the header", leak in text)
            }
    }

    @Test
    fun `the thread and time survive`() {
        val text = header()
        assertTrue("Thread  : main" in text)
        assertTrue("Time    : 20260927_120000" in text)
    }
}
