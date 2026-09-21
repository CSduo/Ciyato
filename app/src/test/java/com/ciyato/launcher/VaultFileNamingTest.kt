package com.ciyato.launcher

import com.ciyato.launcher.data.VaultCrypto
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Vault filenames, and the two ways they used to destroy files.
 *
 * Both defects were found by an independent security review rather than by me,
 * and both are ordinary-use data loss — no attacker required.
 *
 * 1. Interrupted writes were siblings of real vault files, told apart by a
 *    substring in the name. Vault names come from the imported file and were
 *    unsanitised, so importing anything called `notes.vaulttmp.txt` produced a
 *    real vault file that the sweep classified as a disposable temp artifact:
 *    hidden from the list right after a "successful" import, then deleted on the
 *    next unlock without any of the confirmation the real delete path requires.
 *
 * 2. Import built its destination straight from the filename, and the atomic
 *    write ends in `renameTo`, which replaces its destination silently. A second
 *    `IMG_20240101_120000.jpg` destroyed the first one's ciphertext.
 */
class VaultFileNamingTest {

    private lateinit var vaultDir: File

    @Before fun setUp() {
        vaultDir = Files.createTempDirectory("ciyato-vault-test").toFile()
    }

    @After fun tearDown() {
        vaultDir.deleteRecursively()
    }

    // -- defect 1: misclassifying a real file as a temp artifact ---------------

    @Test
    fun `a real vault file is never mistaken for staging, whatever it is called`() {
        // The exact names that used to be deleted silently.
        val hostile = listOf(
            "notes.vaulttmp.txt.enc",
            ".vaulttmp.enc",
            "holiday.vaulttmp12345.enc",
            "vaulttmp.enc",
        )
        for (name in hostile) {
            val f = File(vaultDir, name).apply { writeText("ciphertext") }
            assertFalse(
                "$name must not be treated as staging",
                VaultCrypto.isStagingEntry(f),
            )
        }
    }

    @Test
    fun `only the staging directory itself is staging`() {
        val staging = VaultCrypto.stagingDir(vaultDir).apply { mkdirs() }
        assertTrue(VaultCrypto.isStagingEntry(staging))
    }

    @Test
    fun `clearing staging removes partial writes and keeps real files`() {
        val real = File(vaultDir, "notes.vaulttmp.txt.enc").apply { writeText("keep me") }
        val staging = VaultCrypto.stagingDir(vaultDir).apply { mkdirs() }
        val partial = File(staging, "123456789.part").apply { writeText("half a write") }

        VaultCrypto.clearStaging(vaultDir)

        assertTrue("a real file with a temp-looking name must survive", real.exists())
        assertFalse("an interrupted write must be cleared", partial.exists())
    }

    // -- defect 2: silent overwrite on name collision --------------------------

    @Test
    fun `importing a colliding name does not overwrite the existing file`() {
        val first = File(vaultDir, "IMG_20240101_120000.jpg.enc").apply { writeText("first") }
        val second = VaultCrypto.uniqueDestination(vaultDir, "IMG_20240101_120000.jpg.enc")

        assertNotEquals(first.absolutePath, second.absolutePath)
        assertFalse(second.exists())
        second.writeText("second")
        assertEquals("first", first.readText())
    }

    @Test
    fun `repeated collisions keep counting rather than colliding again`() {
        val names = (1..4).map {
            VaultCrypto.uniqueDestination(vaultDir, "photo.jpg.enc")
                .also { f -> f.writeText("x$it") }
                .name
        }
        assertEquals(names.size, names.distinct().size)
        assertEquals("photo.jpg.enc", names.first())
        assertTrue(names[1].contains("(1)"))
    }

    @Test
    fun `a free name is used as-is`() {
        assertEquals("fresh.pdf.enc", VaultCrypto.uniqueDestination(vaultDir, "fresh.pdf.enc").name)
    }

    @Test
    fun `the disambiguated name still ends in enc so it is still a vault file`() {
        File(vaultDir, "doc.pdf.enc").writeText("first")
        assertTrue(VaultCrypto.uniqueDestination(vaultDir, "doc.pdf.enc").name.endsWith(".enc"))
    }

    // -- import name sanitisation (defence in depth) ---------------------------

    @Test
    fun `path separators cannot escape the vault directory`() {
        assertFalse(VaultCrypto.sanitiseImportName("../../etc/passwd").contains("/"))
        assertFalse(VaultCrypto.sanitiseImportName("a/b/c.txt").contains("/"))
        assertEquals("c.txt", VaultCrypto.sanitiseImportName("a/b/c.txt"))
    }

    @Test
    fun `a blank or missing name still yields a usable filename`() {
        assertTrue(VaultCrypto.sanitiseImportName(null).isNotBlank())
        assertTrue(VaultCrypto.sanitiseImportName("").isNotBlank())
        assertTrue(VaultCrypto.sanitiseImportName("   ").isNotBlank())
        assertTrue(VaultCrypto.sanitiseImportName("...").isNotBlank())
    }

    @Test
    fun `an ordinary name is left recognisable`() {
        assertEquals("Holiday photo.jpg", VaultCrypto.sanitiseImportName("Holiday photo.jpg"))
    }

    @Test
    fun `an absurdly long name is bounded`() {
        assertTrue(VaultCrypto.sanitiseImportName("a".repeat(5000)).length <= 120)
    }
}
