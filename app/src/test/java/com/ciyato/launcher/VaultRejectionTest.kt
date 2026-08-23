package com.ciyato.launcher

import com.ciyato.launcher.data.VaultCrypto
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the vault refuses to take, and why it says so.
 *
 * Vault encryption is whole-file: the plaintext, the ciphertext and Cipher's own
 * copy are all resident at once, so a large video costs several times its size
 * in heap and can fail partway through a security operation (F-017). Streaming
 * authenticated encryption is the real fix and a larger change; until then the
 * limit is enforced up front and explained, rather than discovered as an
 * OutOfMemoryError in the middle of an encrypt.
 *
 * The crypto itself needs AndroidKeyStore and cannot run here, but the admission
 * rule is ordinary arithmetic and is where the user-visible behaviour lives.
 */
class VaultRejectionTest {

    @Test
    fun `an ordinary file is accepted`() {
        assertNull(VaultCrypto.rejectionReason(1))
        assertNull(VaultCrypto.rejectionReason(5L * 1024 * 1024))
        assertNull(VaultCrypto.rejectionReason(VaultCrypto.MAX_VAULT_FILE_BYTES))
    }

    @Test
    fun `a file one byte over the limit is refused`() {
        assertNotNull(VaultCrypto.rejectionReason(VaultCrypto.MAX_VAULT_FILE_BYTES + 1))
    }

    @Test
    fun `a large video is refused rather than attempted`() {
        val twoGb = 2L * 1024 * 1024 * 1024
        val reason = VaultCrypto.rejectionReason(twoGb)
        assertNotNull(reason)
        // The message has to say the size, or "couldn't add the file" is all the
        // person learns and they will simply try again.
        assertTrue("message should state the limit: $reason", reason!!.contains("MB"))
    }

    @Test
    fun `an empty file is refused with its own reason`() {
        val reason = VaultCrypto.rejectionReason(0)
        assertNotNull(reason)
        assertTrue(reason!!.contains("empty"))
    }

    @Test
    fun `a negative size cannot slip through as acceptable`() {
        // openAssetFileDescriptor reports -1 for UNKNOWN_LENGTH; treating that
        // as "fine" would defeat the check entirely.
        assertNotNull(VaultCrypto.rejectionReason(-1))
    }

    @Test
    fun `the limit is large enough to be useful and small enough to be safe`() {
        val mb = VaultCrypto.MAX_VAULT_FILE_BYTES / (1024 * 1024)
        assertTrue("limit of $mb MB is too small to be useful", mb >= 16)
        // Whole-file encryption needs roughly 3x the file in heap; a typical
        // Android heap is 192-512 MB, so the limit must stay well under a third.
        assertTrue("limit of $mb MB risks OOM under whole-file encryption", mb <= 128)
    }

    @Test
    fun `temp artifacts are identifiable so they can be swept safely`() {
        assertTrue(VaultCrypto.isTempArtifact("notes.pdf.enc.vaulttmp12345"))
        assertTrue(!VaultCrypto.isTempArtifact("notes.pdf.enc"))
    }
}
