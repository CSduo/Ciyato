package com.ciyato.launcher

import com.ciyato.launcher.data.VaultCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which key a vault file says it was written with.
 *
 * The vault's user-facing gate is a BiometricPrompt in Compose, and the Keystore
 * key itself did not care whether that prompt had ever run (F-015). Nothing
 * outside the app could extract the key, but any code path inside the app that
 * reached `decrypt` bypassed the product's own notion of a fresh unlock — and
 * "the UI always calls the gate first" is an invariant held by convention, which
 * is the kind that quietly stops being true during a refactor.
 *
 * New files are now written under a key the Keystore will only use shortly after
 * a device authentication. Old files are not touched, which is the whole point
 * of this file: the header already carried a version byte, so a v1 file keeps
 * saying 1 and keeps being read with the v1 key, forever. **The failure this
 * guards against is catastrophic and silent** — an AES key in the Keystore is
 * not extractable and not re-derivable, so a dispatch bug that sent a v1 file to
 * the v2 key would make the only copy of someone's plaintext permanently
 * unreadable while looking like an ordinary decryption failure.
 *
 * Only the dispatch is testable here. `AndroidKeyStore` does not exist on the
 * JVM, so key generation, the grace period, `UserNotAuthenticatedException` and
 * invalidation-on-lock-removal all need the instrumentation run — recorded as
 * outstanding in `CLAUDE_VALIDATION.md` rather than pretended.
 */
class VaultKeyVersionTest {

    private val magic = 0xC7.toByte()

    /** A vault file header, as `encrypt` writes it. */
    private fun header(version: Byte, ivLen: Int = 12, body: Int = 32): ByteArray =
        ByteArray(3 + ivLen + body).also {
            it[0] = magic
            it[1] = version
            it[2] = ivLen.toByte()
        }

    // ── Both eras are readable ───────────────────────────────────────────────

    @Test
    fun `a file from before the key change is still a vault file`() {
        assertTrue(VaultCrypto.isVaultFormat(header(VaultCrypto.VERSION_UNBOUND_KEY)))
    }

    @Test
    fun `a file written under the auth-bound key is a vault file too`() {
        assertTrue(VaultCrypto.isVaultFormat(header(VaultCrypto.VERSION_AUTH_BOUND_KEY)))
    }

    @Test
    fun `the two versions are distinct, or every file reads with the wrong key`() {
        assertFalse(VaultCrypto.VERSION_UNBOUND_KEY == VaultCrypto.VERSION_AUTH_BOUND_KEY)
        assertTrue(VaultCrypto.isKnownVersion(VaultCrypto.VERSION_UNBOUND_KEY))
        assertTrue(VaultCrypto.isKnownVersion(VaultCrypto.VERSION_AUTH_BOUND_KEY))
    }

    @Test
    fun `a file reports the version it carries, not the version this build writes`() {
        assertEquals(VaultCrypto.VERSION_UNBOUND_KEY, VaultCrypto.versionOf(header(VaultCrypto.VERSION_UNBOUND_KEY)))
        assertEquals(VaultCrypto.VERSION_AUTH_BOUND_KEY, VaultCrypto.versionOf(header(VaultCrypto.VERSION_AUTH_BOUND_KEY)))
    }

    // ── A version this build does not know ───────────────────────────────────

    @Test
    fun `an unknown version is not treated as readable`() {
        // The dangerous direction. A future build's v3 file must not be read
        // with a v1 or v2 key and must not be run through the legacy migration,
        // which is a lossy transform over what may be the only copy (F-016).
        listOf<Byte>(0, 3, 9, 127, -1).forEach { version ->
            assertFalse("version $version should not be readable", VaultCrypto.isKnownVersion(version))
            assertFalse("version $version should not pass the format check", VaultCrypto.isVaultFormat(header(version)))
        }
    }

    @Test
    fun `an unknown version is still recognised as ours`() {
        // Not readable, but ours - which is what stops it being mistaken for a
        // legacy XOR file and destroyed. versionOf answers "whose file is this",
        // isKnownVersion answers "can I read it". Conflating those two questions
        // is the F-016 defect.
        assertEquals(3.toByte(), VaultCrypto.versionOf(header(3)))
    }

    @Test
    fun `a file that is not ours reports no version`() {
        assertNull(VaultCrypto.versionOf(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        assertNull(VaultCrypto.versionOf(ByteArray(0)))
        assertNull(VaultCrypto.versionOf(byteArrayOf(magic)))
    }

    // ── Truncation ───────────────────────────────────────────────────────────

    @Test
    fun `a header with no body is not a vault file`() {
        // Three bytes of header and nothing after it. Accepting this would send
        // an empty ciphertext to the cipher and surface as a confusing GCM
        // failure rather than "this file is damaged".
        assertFalse(VaultCrypto.isVaultFormat(byteArrayOf(magic, VaultCrypto.VERSION_AUTH_BOUND_KEY, 12)))
        assertFalse(VaultCrypto.isVaultFormat(byteArrayOf(magic, VaultCrypto.VERSION_UNBOUND_KEY)))
    }

    // ── The policy the UI has to state ───────────────────────────────────────

    @Test
    fun `the grace period is a real binding and not an accident`() {
        // Much shorter and unlocking the vault then opening a third file
        // re-prompts mid-task; much longer and it stops binding to a fresh
        // authentication at all. Pinned so a future edit is a decision.
        assertTrue(VaultCrypto.AUTH_VALIDITY_SECONDS in 30..600)
    }
}
