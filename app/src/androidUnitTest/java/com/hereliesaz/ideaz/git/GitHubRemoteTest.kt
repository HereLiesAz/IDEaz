package com.hereliesaz.ideaz.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubRemoteTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun githubHttpsUrlsAreTrusted() {
        listOf(
            "https://github.com/HereLiesAz/IDEaz.git",
            "https://github.com/o/r",
            "HTTPS://GitHub.com/o/r.git",
            "https://github.com:443/o/r.git",
        ).forEach { assertTrue(it, GitHubRemote.isTrustedUrl(it)) }
    }

    @Test
    fun everythingElseIsRejected() {
        listOf(
            "https://evil.example/o/r.git",
            "http://github.com/o/r.git",
            "git@github.com:o/r.git",
            "ssh://git@github.com/o/r.git",
            "git://github.com/o/r.git",
            "https://gitlab.com/o/r.git",
            "https://github.com@evil.com/x",
            "https://user:pass@github.com/o/r.git",
            "https://github.com.evil.com/x",
            "https://evil.com/github.com/o/r",
            "https://notgithub.com/o/r",
            "https://github.com:8443/o/r",
            "https://github.com",
            "https://github.com/",
            "https://github.com\\@evil.com/x",
            "file:///tmp/repo",
            "/tmp/repo",
            "",
        ).forEach { assertFalse(it, GitHubRemote.isTrustedUrl(it)) }
    }

    @Test
    fun allUrlsMustBeTrustedAndThereMustBeOne() {
        assertFalse(GitHubRemote.areAllTrusted(emptyList()))
        assertTrue(GitHubRemote.areAllTrusted(listOf("https://github.com/o/r")))
        assertFalse(GitHubRemote.areAllTrusted(listOf("https://github.com/o/r", "https://evil.com/o/r")))
    }

    @Test
    fun theTokenIsNotAttachedToANonGitHubOrigin() {
        val dir = tempFolder.newFolder("repo")
        val git = GitManager(dir)
        git.init()
        git.addRemote("origin", "https://evil.example/o/r.git")

        try {
            git.requireGitHubRemote("origin")
            fail("expected UntrustedRemoteException")
        } catch (e: UntrustedRemoteException) {
            assertTrue(e.message!!.contains("evil.example"))
        }
        listOf<() -> Unit>(
            { git.push("u", "tok") },
            { git.pull("u", "tok") },
            { git.fetch("u", "tok") },
            { git.fetchPr("1", "pr-1", "u", "tok") },
        ).forEach {
            try {
                it()
                fail("expected UntrustedRemoteException")
            } catch (e: UntrustedRemoteException) {
                // expected: thrown before any network access
            }
        }
    }

    @Test
    fun aGitHubOriginPassesTheCheckAndAMissingOneDoesNot() {
        val dir = tempFolder.newFolder("repo2")
        val git = GitManager(dir)
        git.init()
        try {
            git.requireGitHubRemote("origin")
            fail("a repository with no origin has nothing to trust")
        } catch (e: UntrustedRemoteException) {
            assertTrue(e.message!!.contains("no URL"))
        }
        git.addRemote("origin", "https://github.com/HereLiesAz/example.git")
        git.requireGitHubRemote("origin")
        assertEquals("https://github.com/HereLiesAz/example.git", git.remoteUrl("origin"))
    }

    @Test
    fun insteadOfRewritesAreChecked() {
        val dir = tempFolder.newFolder("repo3")
        val git = GitManager(dir)
        git.init()
        git.addRemote("origin", "https://github.com/o/r.git")
        val gitDir = java.io.File(dir, ".git/config")
        gitDir.appendText("[url \"https://evil.example/\"]\n\tinsteadOf = https://github.com/\n")

        try {
            git.requireGitHubRemote("origin")
            fail("expected UntrustedRemoteException")
        } catch (e: UntrustedRemoteException) {
            assertTrue(e.message!!.contains("evil.example"))
        }
    }
}
