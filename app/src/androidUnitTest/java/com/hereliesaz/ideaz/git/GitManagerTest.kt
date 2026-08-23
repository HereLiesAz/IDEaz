package com.hereliesaz.ideaz.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Covers the two things Deploy's "has this project ever been published?" check
 * rests on: whether a directory is a repository, and whether it has an `origin`.
 *
 * Getting `remoteUrl` wrong is not a visible failure — it is Deploy either
 * skipping repository creation for a project that has none (and then failing at
 * push with a confusing error), or creating a second repository for one that is
 * already linked.
 */
class GitManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun remoteUrlIsNullForADirectoryThatIsNotARepo() {
        val dir = tempFolder.newFolder("not_a_repo")
        val git = GitManager(dir)

        assertFalse(git.isRepo())
        // Must not throw: this is the state a freshly created local project is
        // in the first time Deploy asks.
        assertNull(git.remoteUrl("origin"))
    }

    @Test
    fun remoteUrlIsNullForADirectoryThatDoesNotExist() {
        assertNull(GitManager(File(tempFolder.root, "missing")).remoteUrl("origin"))
    }

    @Test
    fun initMakesItARepoWithNoRemote() {
        val dir = tempFolder.newFolder("fresh")
        val git = GitManager(dir)
        git.init()

        assertTrue(git.isRepo())
        assertTrue(File(dir, ".git").isDirectory)
        // Created locally, never published.
        assertNull(git.remoteUrl("origin"))
    }

    @Test
    fun addRemoteIsReadBackByRemoteUrl() {
        val dir = tempFolder.newFolder("linked")
        val git = GitManager(dir)
        git.init()
        git.addRemote("origin", "https://github.com/HereLiesAz/example.git")

        assertEquals("https://github.com/HereLiesAz/example.git", git.remoteUrl("origin"))
        // Only the remote that was actually added.
        assertNull(git.remoteUrl("upstream"))
    }

    @Test
    fun initThenCommitProducesHistory() {
        // saveAndInitialize does exactly this for a new local project, so that
        // "every project is a git repository" holds before any GitHub account
        // exists. Previously nothing initialised one until Deploy.
        val dir = tempFolder.newFolder("committed")
        File(dir, "index.html").writeText("<!doctype html><title>hi</title>")

        val git = GitManager(dir)
        git.init()
        git.addAll()
        git.commit("Initial commit")

        assertTrue(git.isRepo())
        assertTrue(git.getCommitHistory().isNotEmpty())
        assertFalse(git.hasChanges())
    }

    /**
     * Regression: JGit reads the ambient git config, so on a machine with
     * `commit.gpgsign = true` every commit threw. With `gpg.format = ssh` it
     * threw `UnsupportedSigningFormatException` — "No signer for ssh
     * signatures" — which names nothing the user did.
     *
     * IDEaz has no signing key and no way to prompt for a passphrase, so it
     * opts out explicitly. `IdeTools.checkpoint` always did; `GitManager.commit`
     * did not, so the AI's checkpoints committed while the user's own commits,
     * Deploy, and a new project's initial commit all failed.
     */
    @Test
    fun commitSucceedsWithSigningTurnedOnInTheRepoConfig() {
        val dir = tempFolder.newFolder("signing_on")
        File(dir, "index.html").writeText("<!doctype html>")

        val git = GitManager(dir)
        git.init()
        // Repo-level config outranks the global config, so this reproduces the
        // failure regardless of how the machine running the test is set up.
        org.eclipse.jgit.api.Git.open(dir).use {
            it.repository.config.apply {
                setBoolean("commit", null, "gpgsign", true)
                setString("gpg", null, "format", "ssh")
                save()
            }
        }

        git.addAll()
        git.commit("Initial commit")

        assertTrue(git.getCommitHistory().isNotEmpty())
    }

    /**
     * saveAndInitialize commits unconditionally after `init`, so this has to
     * work for a directory with nothing in it. It gated on "did we just scaffold
     * the starter?" until this test showed the gate was unnecessary — and that
     * the case it excluded (an imported folder: files present, nothing
     * scaffolded) was left as a repository with no HEAD.
     */
    @Test
    fun initThenCommitWorksForAnEmptyDirectory() {
        val dir = tempFolder.newFolder("empty")
        val git = GitManager(dir)
        git.init()
        git.addAll()
        git.commit("Initial commit")

        assertEquals(1, git.getCommitHistory().size)
    }

    /**
     * A repository with no commits is a new repository, not an error. JGit's
     * `log()` throws `NoHeadException` for it, and GitDelegate.refreshGitData
     * catches everything silently *and in order* — so this throwing used to take
     * branches and status down with it, leaving the Git screen blank with no
     * indication why.
     */
    @Test
    fun commitHistoryIsEmptyRatherThanThrowingForARepoWithNoHead() {
        val dir = tempFolder.newFolder("no_head")
        File(dir, "index.html").writeText("<!doctype html>")
        val git = GitManager(dir)
        git.init()

        assertTrue(git.getCommitHistory().isEmpty())
        // The two calls refreshGitData makes *after* it, which a throw skipped.
        assertTrue(git.getStatus().isNotEmpty())
        assertTrue(git.getBranches().isEmpty())
    }
}
