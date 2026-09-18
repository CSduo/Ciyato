package com.ciyato.launcher

import com.ciyato.launcher.data.PhotoBackupWorker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether scheduled backup should wait for a network.
 *
 * Periodic backup only required storage-not-low, but a SAF destination is
 * whatever folder the person picked - Drive, OneDrive or an SMB share as easily
 * as internal storage. A daily copy of a photo library could therefore run over
 * a metered connection, or fail repeatedly offline (F-011).
 *
 * Requiring a network unconditionally would be the opposite mistake: a backup to
 * internal storage must still run on a phone in flight mode. So the constraint
 * follows the destination, and these pin which is which.
 */
class BackupConstraintsTest {

    @Test
    fun `internal and SD storage need no network`() {
        assertFalse(PhotoBackupWorker.isLikelyNetworkBacked(
            "content://com.android.externalstorage.documents/tree/primary%3ABackup"))
        assertFalse(PhotoBackupWorker.isLikelyNetworkBacked(
            "content://com.android.externalstorage.documents/tree/1AEF-2B04%3APhotos"))
    }

    @Test
    fun `the media and downloads providers are local`() {
        assertFalse(PhotoBackupWorker.isLikelyNetworkBacked(
            "content://com.android.providers.downloads.documents/tree/downloads"))
        assertFalse(PhotoBackupWorker.isLikelyNetworkBacked(
            "content://com.android.providers.media.documents/tree/images"))
    }

    @Test
    fun `a cloud provider is treated as network-backed`() {
        assertTrue(PhotoBackupWorker.isLikelyNetworkBacked(
            "content://com.google.android.apps.docs.storage/tree/abc123"))
        assertTrue(PhotoBackupWorker.isLikelyNetworkBacked(
            "content://com.microsoft.skydrive.content.external/tree/xyz"))
    }

    /**
     * Unknown providers default to network-backed. Waiting for unmetered wifi on
     * a local folder delays a backup; running a multi-gigabyte copy over mobile
     * data costs the person money. The asymmetry decides the default.
     */
    @Test
    fun `an unrecognised provider is assumed to need a network`() {
        assertTrue(PhotoBackupWorker.isLikelyNetworkBacked("content://some.unknown.provider/tree/x"))
    }

    @Test
    fun `a malformed destination does not crash scheduling`() {
        PhotoBackupWorker.isLikelyNetworkBacked("")
        PhotoBackupWorker.isLikelyNetworkBacked("not a uri at all")
        PhotoBackupWorker.isLikelyNetworkBacked("::::")
    }

    @Test
    fun `an empty destination is not treated as a cloud mount`() {
        // No authority to inspect: there is nothing to justify blocking on wifi.
        assertFalse(PhotoBackupWorker.isLikelyNetworkBacked(""))
    }
}
