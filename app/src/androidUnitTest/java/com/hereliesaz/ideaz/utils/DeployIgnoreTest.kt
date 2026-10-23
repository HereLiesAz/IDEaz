package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.git.GitManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DeployIgnoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun ensureWritesEveryEntryIntoANewGitignore() {
        val dir = tempFolder.newFolder("p")
        assertTrue(DeployIgnore.ensure(dir))
        val lines = File(dir, ".gitignore").readLines()
        DeployIgnore.ENTRIES.forEach { assertTrue("missing $it", it in lines) }
    }

    @Test
    fun ensureAppendsOnlyMissingEntriesAndIsIdempotent() {
        val dir = tempFolder.newFolder("p")
        File(dir, ".gitignore").writeText("node_modules\n.env*\ncustom.txt")
        assertTrue(DeployIgnore.ensure(dir))
        val text = File(dir, ".gitignore").readText()
        assertTrue(text.startsWith("node_modules\n.env*\ncustom.txt\n\n"))
        assertEquals(1, text.lines().count { it == ".env*" })
        assertTrue("node_modules/" in text.lines())
        assertFalse(DeployIgnore.ensure(dir))
        assertEquals(text, File(dir, ".gitignore").readText())
    }

    @Test
    fun isExcludedMatchesSecretsStateAndBuildOutputOnly() {
        listOf(
            ".env", ".env.local", "web/.env.production", "release.jks", "a/b/upload.keystore",
            "cert.p12", "key.pem", "server.key", ".ideaz/prompt_history.json",
            ".ideaz/screenshots/s.png", "node_modules/react/index.js", "dist/index.html",
            "packages/app/build/main.js", "out/index.html",
        ).forEach { assertTrue(it, DeployIgnore.isExcluded(it)) }
        listOf(
            "index.html", "src/App.jsx", "src/env.js", "keys.js", "src/build.js",
            "docs/dist.md", "monkey.json", "src/output/x.js",
        ).forEach { assertFalse(it, DeployIgnore.isExcluded(it)) }
    }

    @Test
    fun deployAndSnapshotShareOneDenyList() {
        listOf(
            "secrets.json", "credentials.json", "local.properties", "google-services.json",
            "id_rsa", "cert.pfx", "release.jks", "debug.keystore", ".env", ".envrc", ".env.local",
            "app/google-services.json",
        ).forEach {
            assertTrue(it, DeployIgnore.isExcluded(it))
            assertTrue(it, RepoSnapshot.isSecret(it.substringAfterLast('/')))
        }
        SecretFiles.GLOBS.forEach { assertTrue(it, it in DeployIgnore.ENTRIES) }
    }

    @Test
    fun excludedTrackedFilesCanBeUntrackedAndStayOnDisk() {
        val dir = tempFolder.newFolder("repo")
        val git = GitManager(dir)
        git.init()
        File(dir, "index.html").writeText("<h1>hi</h1>")
        File(dir, ".env").writeText("SECRET=1")
        git.addAll()
        git.commit("leaky")

        val leaked = git.trackedFiles().filter { DeployIgnore.isExcluded(it) }
        assertEquals(listOf(".env"), leaked)
        DeployIgnore.ensure(dir)
        git.addPaths(listOf(".gitignore"))
        git.untrack(leaked)
        git.commit("untrack")

        assertEquals(listOf(".gitignore", "index.html"), git.trackedFiles().sorted())
        assertTrue(File(dir, ".env").exists())
        // Ignored now, so a later `git add .` does not bring it back.
        git.addAll()
        assertFalse(".env" in git.trackedFiles())
    }

    @Test
    fun isExcludedFoldsCaseOnFileNamesOnly() {
        listOf("Secrets.json", ".ENV", "config/.Env.Local", "MyKey.PEM", "Release.KeyStore")
            .forEach { assertTrue(it, DeployIgnore.isExcluded(it)) }
        // Directory entries match the exact name, like git does.
        listOf("Node_Modules/x.js", ".IDEAZ/prompt_history.json", "Dist/index.html")
            .forEach { assertFalse(it, DeployIgnore.isExcluded(it)) }
    }

    @Test
    fun addAllDoesNotStageSecretsThatTheCaseSensitiveGitignoreMisses() {
        val dir = tempFolder.newFolder("case")
        val git = GitManager(dir)
        git.init()
        DeployIgnore.ensure(dir)
        File(dir, "index.html").writeText("<h1>hi</h1>")
        File(dir, "Secrets.json").writeText("{}")
        File(dir, ".ENV").writeText("SECRET=1")
        File(dir, "MyKey.PEM").writeText("key")

        git.addAll()
        git.commit("first")

        assertEquals(listOf(".gitignore", "index.html"), git.trackedFiles().sorted())
        assertTrue(File(dir, "Secrets.json").exists())
        assertTrue(File(dir, ".ENV").exists())
        assertTrue(File(dir, "MyKey.PEM").exists())

        // And again after a later edit: still never re-staged.
        File(dir, "Secrets.json").writeText("{\"k\":1}")
        git.addAll()
        assertEquals(listOf(".gitignore", "index.html"), git.trackedFiles().sorted())
    }
}
