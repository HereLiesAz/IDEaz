package com.hereliesaz.ideaz.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RepoSnapshotTest {

    private fun tmp(): File = Files.createTempDirectory("snap").toFile()

    @Test
    fun skipsSecretFilesAndRedactsTokens() {
        val dir = tmp()
        File(dir, ".env").writeText("SECRET=abc123")
        File(dir, "Config.kt").writeText("val token = \"ghp_1234567890abcdefABCDEFghij\"")

        val r = RepoSnapshot.build(dir)

        // .env is withheld entirely.
        assertFalse("secret file contents leaked", r.text.contains("SECRET=abc123"))
        assertTrue(r.skipped.any { it.contains(".env") })

        // A normal source file is included, but its token is redacted.
        assertTrue(r.text.contains("Config.kt"))
        assertFalse("raw token leaked", r.text.contains("ghp_1234567890abcdefABCDEFghij"))
        assertTrue(r.text.contains("REDACTED"))
    }

    @Test
    fun skipsBinaryFiles() {
        val dir = tmp()
        File(dir, "logo.bin").writeBytes(byteArrayOf(1, 0, 2, 3))
        File(dir, "readme.md").writeText("hello")

        val r = RepoSnapshot.build(dir)

        assertTrue(r.text.contains("readme.md"))
        assertTrue(r.skipped.any { it.contains("logo.bin") })
    }

    @Test
    fun includesTreeAndContents() {
        val dir = tmp()
        File(dir, "a.txt").writeText("alpha")
        val r = RepoSnapshot.build(dir)
        assertTrue(r.text.contains("PROJECT FILE TREE"))
        assertTrue(r.text.contains("a.txt"))
        assertTrue(r.text.contains("alpha"))
    }
}
