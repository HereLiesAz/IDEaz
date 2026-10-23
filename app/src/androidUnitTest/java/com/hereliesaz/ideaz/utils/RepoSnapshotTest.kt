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

        assertFalse("secret file contents leaked", r.text.contains("SECRET=abc123"))
        assertTrue(r.skipped.any { it.contains(".env") })
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

    @Test
    fun skipsSymlinkThatEscapesProject() {
        val dir = tmp()
        val outside = Files.createTempFile("outside-secret", ".txt").toFile().apply {
            writeText("DO_NOT_EXPORT_THIS")
        }
        val link = File(dir, "innocent.txt").toPath()
        Files.createSymbolicLink(link, outside.toPath())

        val r = RepoSnapshot.build(dir)

        assertFalse("symlink target leaked", r.text.contains("DO_NOT_EXPORT_THIS"))
        assertTrue(r.skipped.any { it.contains("innocent.txt") && it.contains("symlink") })
    }

    @Test
    fun credentialStoresAreSecretFiles() {
        listOf(
            ".npmrc", ".netrc", "_netrc", ".pypirc", ".git-credentials", ".htpasswd",
            "credentials.json", "client_secret.json", "service-account-prod.json", "AuthKey_ABC.p8", "putty.ppk",
        ).forEach { assertTrue(it, RepoSnapshot.isSecret(it)) }
        listOf("package.json", "credentials.js", "README.md", "accounts.json").forEach {
            assertFalse(it, RepoSnapshot.isSecret(it))
        }
    }
}

class RepoSnapshotBudgetTest {
    private fun tmp(): File = Files.createTempDirectory("snapb").toFile()

    @Test
    fun budgetCountsUtf8BytesNotChars() {
        val dir = tmp()
        // 3 bytes per char: 4_000 chars = 12_000 bytes.
        File(dir, "cjk.txt").writeText("日".repeat(4_000))
        val r = RepoSnapshot.build(dir, maxBytes = 10_000)
        assertFalse(r.text.contains("日日日"))
        assertTrue(r.truncated)
        assertTrue(RepoSnapshot.utf8Size(r.text) <= 10_000)
    }

    @Test
    fun totalIncludingNotIncludedListStaysWithinBudget() {
        val dir = tmp()
        repeat(500) { File(dir, "file-with-a-fairly-long-name-$it.bin").writeBytes(byteArrayOf(0, 1)) }
        File(dir, "a.txt").writeText("alpha")
        val r = RepoSnapshot.build(dir, maxBytes = 12_000)
        assertTrue(RepoSnapshot.utf8Size(r.text) <= 12_000)
        assertTrue(r.text.contains("alpha"))
        assertTrue(r.text.contains("more"))
    }

    @Test
    fun oversizedFileIsSkippedUnread() {
        val dir = tmp()
        File(dir, "huge.txt").writeText("x".repeat(50_000))
        File(dir, "small.txt").writeText("tiny")
        val r = RepoSnapshot.build(dir, maxBytes = 20_000)
        assertTrue(r.skipped.any { it.contains("huge.txt") })
        assertTrue(r.text.contains("tiny"))
    }

    @Test
    fun ideazStateAndEnvrcAreNeverSent() {
        val dir = tmp()
        File(dir, ".ideaz").mkdirs()
        File(dir, ".ideaz/prompt_history.json").writeText("PROMPT_HISTORY_MARKER")
        File(dir, ".envrc").writeText("export TOKEN=ENVRC_MARKER")
        File(dir, "google-services.json").writeText("GOOGLE_SERVICES_MARKER")
        val r = RepoSnapshot.build(dir)
        assertFalse(r.text.contains("PROMPT_HISTORY_MARKER"))
        assertFalse(r.text.contains("ENVRC_MARKER"))
        assertFalse(r.text.contains("GOOGLE_SERVICES_MARKER"))
    }
}
