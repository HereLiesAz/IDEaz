package com.hereliesaz.ideaz.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.lib.ProgressMonitor
import java.io.File
import java.io.ByteArrayInputStream

/**
 * Manages Git operations for a specific project directory using the JGit library.
 *
 * This class encapsulates all version control logic, including cloning, pulling, pushing,
 * committing, and branch management. It is designed to be thread-safe when used in conjunction
 * with external synchronization (e.g., [com.hereliesaz.ideaz.ui.MainViewModel.gitMutex]).
 *
 * @property projectDir The local directory of the project (the repository root).
 */
class GitManager(private val projectDir: File) {

    /**
     * Clones a repository from GitHub.
     *
     * @param owner The GitHub username or organization name of the repository owner.
     * @param repo The name of the repository.
     * @param username The username for authentication (optional).
     * @param token The Personal Access Token (PAT) for authentication (optional).
     * @param onProgress A callback function to report progress (percentage 0-100, task description).
     * @throws org.eclipse.jgit.api.errors.GitAPIException If the clone operation fails.
     */
    fun clone(owner: String, repo: String, username: String? = null, token: String? = null, onProgress: ((Int, String) -> Unit)? = null) {
        val url = "https://github.com/$owner/$repo.git"
        val cmd = Git.cloneRepository()
            .setURI(url)
            .setDirectory(projectDir)

        if (onProgress != null) {
            cmd.setProgressMonitor(SimpleProgressMonitor(onProgress))
        }

        if (!token.isNullOrBlank()) {
            val user = if (!username.isNullOrBlank()) username else token
            cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(user, token))
        }

        cmd.call().close()
    }

    /**
     * Pulls the latest changes from the remote origin.
     *
     * @param username The username for authentication (optional).
     * @param token The Personal Access Token (PAT) for authentication (optional).
     * @param onProgress A callback function to report progress.
     * @throws org.eclipse.jgit.api.errors.GitAPIException If the pull operation fails.
     */
    fun pull(username: String? = null, token: String? = null, onProgress: ((Int, String) -> Unit)? = null) {
        Git.open(projectDir).use { git ->
            val cmd = git.pull()

            if (onProgress != null) {
                cmd.setProgressMonitor(SimpleProgressMonitor(onProgress))
            }

            if (!token.isNullOrBlank()) {
                val user = if (!username.isNullOrBlank()) username else token
                cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(user, token))
            }

            cmd.call()
        }
    }

    /**
     * Checks if there are any uncommitted changes in the working directory or index.
     *
     * @return `true` if there are changes (modified, added, deleted, untracked files), `false` if clean.
     */
    fun hasChanges(): Boolean {
        Git.open(projectDir).use { git ->
            return !git.status().call().isClean
        }
    }

    /**
     * stages all changes in the working directory (equivalent to `git add .`).
     */
    fun addAll() {
        Git.open(projectDir).use { git ->
            git.add().addFilepattern(".").call()
        }
    }

    /**
     * Commits staged changes.
     *
     * @param message The commit message.
     * @param allowEmpty If `true`, allows creating a commit with no changes.
     */
    fun commit(message: String, allowEmpty: Boolean = false) {
        Git.open(projectDir).use { git ->
            git.commit().setMessage(message).setAllowEmpty(allowEmpty).call()
        }
    }

    /**
     * Pushes committed changes to the remote origin.
     *
     * @param username The username for authentication (optional).
     * @param token The Personal Access Token (PAT) for authentication (optional).
     * @param onProgress A callback function to report progress.
     * @throws org.eclipse.jgit.api.errors.GitAPIException If the push operation fails.
     */
    fun push(username: String? = null, token: String? = null, onProgress: ((Int, String) -> Unit)? = null) {
        Git.open(projectDir).use { git ->
            val cmd = git.push()
            if (!token.isNullOrBlank()) {
                val user = if (!username.isNullOrBlank()) username else token
                cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(user, token))
            }
            if (onProgress != null) {
                cmd.setProgressMonitor(SimpleProgressMonitor(onProgress))
            }
            cmd.call()
        }
    }

    /**
     * Fetches the latest objects and refs from the remote origin.
     *
     * @param username The username for authentication (optional).
     * @param token The Personal Access Token (PAT) for authentication (optional).
     * @param onProgress A callback function to report progress.
     */
    fun fetch(username: String? = null, token: String? = null, onProgress: ((Int, String) -> Unit)? = null) {
        Git.open(projectDir).use { git ->
            val cmd = git.fetch().setRemote("origin")
            if (!token.isNullOrBlank()) {
                val user = if (!username.isNullOrBlank()) username else token
                cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(user, token))
            }
            if (onProgress != null) {
                cmd.setProgressMonitor(SimpleProgressMonitor(onProgress))
            }
            cmd.call()
        }
    }

    /**
     * Fetches a specific Pull Request branch from GitHub.
     *
     * This method fetches `refs/pull/{id}/head` into a local branch `pr-{id}` (or whatever is passed as `localBranch`).
     *
     * @param prId The ID of the pull request (e.g., "42").
     * @param localBranch The name of the local branch to create/update (e.g., "pr-42").
     * @param username The username for authentication (optional).
     * @param token The Personal Access Token (PAT) for authentication (optional).
     * @param onProgress A callback function to report progress.
     */
    fun fetchPr(prId: String, localBranch: String, username: String? = null, token: String? = null, onProgress: ((Int, String) -> Unit)? = null) {
        Git.open(projectDir).use { git ->
            val cmd = git.fetch()
                .setRemote("origin")
                .setRefSpecs(org.eclipse.jgit.transport.RefSpec("refs/pull/$prId/head:$localBranch"))

            if (!token.isNullOrBlank()) {
                val user = if (!username.isNullOrBlank()) username else token
                cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(user, token))
            }
            if (onProgress != null) {
                cmd.setProgressMonitor(SimpleProgressMonitor(onProgress))
            }
            cmd.call()
        }
    }

    /**
     * Checks out an existing branch.
     *
     * @param branch The name of the branch to check out.
     */
    fun checkout(branch: String) {
        Git.open(projectDir).use { git ->
            val cmd = git.checkout().setName(branch)
            // If the branch doesn't exist locally but exists on remote, JGit might handle it if we set start point.
            // But simple checkout usually requires local branch.
            // For now, let's just try to checkout.
            cmd.call()
        }
    }

    /**
     * Creates a new branch if it doesn't exist, and checks it out.
     *
     * @param branch The name of the branch to create/checkout.
     */
    fun createAndCheckoutBranch(branch: String) {
        Git.open(projectDir).use { git ->
            val branchExists = git.branchList().call().any { it.name == "refs/heads/$branch" }
            if (branchExists) {
                git.checkout().setName(branch).call()
            } else {
                git.checkout().setCreateBranch(true).setName(branch).call()
            }
        }
    }

    /**
     * Deletes a local branch forcibly.
     *
     * @param branchName The name of the branch to delete.
     */
    fun deleteBranch(branchName: String) {
        Git.open(projectDir).use { git ->
            // Delete local branch
            git.branchDelete().setBranchNames(branchName).setForce(true).call()
        }
    }

    /**
     * Merges a branch into the current HEAD.
     *
     * @param branch The name of the branch to merge in.
     */
    fun merge(branch: String) {
        Git.open(projectDir).use { git ->
            val repository = git.repository
            val ref = repository.findRef(branch)
            git.merge().include(ref).call()
        }
    }

    /**
     * Checks if a branch is ahead of a base branch (i.e., has commits not in base).
     *
     * @param branch The branch to check.
     * @param base The base branch to compare against.
     * @return `true` if `branch` has commits that are not reachable from `base`.
     */
    fun isAhead(branch: String, base: String): Boolean {
        return try {
            Git.open(projectDir).use { git ->
                val repository = git.repository
                val branchId = repository.resolve(branch)
                val baseId = repository.resolve(base)

                if (branchId == null || baseId == null) return@use false

                val walk = org.eclipse.jgit.revwalk.RevWalk(repository)
                val branchCommit = walk.parseCommit(branchId)
                val baseCommit = walk.parseCommit(baseId)

                // Check if branch has commits not in base
                // count(base..branch) > 0
                walk.setRevFilter(org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE)
                walk.markStart(branchCommit)
                walk.markStart(baseCommit)
                val mergeBase = walk.next()

                // Reset walk for counting
                walk.reset()
                walk.setRevFilter(org.eclipse.jgit.revwalk.filter.RevFilter.ALL)
                walk.markStart(branchCommit)
                if (mergeBase != null) {
                    walk.markUninteresting(mergeBase)
                }

                // Simple check: does the walk return anything?
                walk.iterator().hasNext()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Initializes a new Git repository in the project directory.
     */
    fun init() {
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        Git.init().setDirectory(projectDir).call().close()
    }

    /**
     * Applies a Git patch to the repository.
     *
     * @param patch The content of the unified diff patch.
     */
    fun applyPatch(patch: String) {
        Git.open(projectDir).use { git ->
            val patchInputStream = ByteArrayInputStream(patch.toByteArray())
            git.apply().setPatch(patchInputStream).call()
        }
    }

    /**
     * Stashes current changes (dirty working directory).
     *
     * @param message The optional message for the stash entry.
     */
    fun stash(message: String? = null) {
        Git.open(projectDir).use { git ->
            val cmd = git.stashCreate()
            if (message != null) cmd.setWorkingDirectoryMessage(message)
            cmd.call()
        }
    }

    /**
     * Applies the latest stash to the working directory.
     */
    fun unstash() {
        Git.open(projectDir).use { git ->
            git.stashApply().call()
        }
    }

    /**
     * Retrieves the SHA-1 hash of the current HEAD commit.
     *
     * @return The 40-character SHA string, or null on error.
     */
    fun getHeadSha(): String? {
        return try {
            Git.open(projectDir).use { git ->
                val head = git.repository.resolve("HEAD")
                head?.name
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Retrieves the name of the currently checked-out branch.
     *
     * @return The branch name (e.g., "main"), or null on error.
     */
    fun getCurrentBranch(): String? {
        return try {
            Git.open(projectDir).use { git ->
                git.repository.branch
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Attempts to determine the default branch of the remote repository (e.g., "main" or "master").
     *
     * It checks `refs/remotes/origin/HEAD`, which should point to the default branch if
     * the clone was successful and symbolic refs were fetched.
     *
     * @return The default branch name, or null if not found.
     */
    fun getDefaultBranch(): String? {
        return try {
            Git.open(projectDir).use { git ->
                val remoteHead = git.repository.findRef("refs/remotes/origin/HEAD")
                if (remoteHead != null && remoteHead.isSymbolic) {
                    // The target is refs/remotes/origin/main, we want the last part
                    remoteHead.target.name.substringAfterLast('/')
                } else {
                    null // Fallback if not found or not symbolic
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Retrieves the commit history of the current branch.
     *
     * @return A list of formatted commit strings ("shortSha - message (author)").
     */
    fun getCommitHistory(): List<String> {
        try {
            Git.open(projectDir).use { git ->
                return git.log().call().map { commit ->
                    val author = commit.authorIdent.name
                    val message = commit.shortMessage
                    "${commit.name.substring(0, 7)} - $message ($author)"
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Retrieves a list of all known branches (local and remote).
     *
     * @return A sorted list of unique branch names.
     */
    fun getBranches(): List<String> {
        try {
            Git.open(projectDir).use { git ->
                return git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.ALL).call().map {
                    it.name.substringAfter("refs/heads/").substringAfter("refs/remotes/origin/")
                }.distinct().sorted()
            }
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Retrieves the current status of the working directory.
     *
     * @return A list of status messages (e.g., "Added: filename", "Modified: filename").
     */
    fun getStatus(): List<String> {
        try {
            Git.open(projectDir).use { git ->
                val status = git.status().call()
                val result = mutableListOf<String>()
                result.addAll(status.added.map { "Added: $it" })
                result.addAll(status.changed.map { "Changed: $it" })
                result.addAll(status.removed.map { "Removed: $it" })
                result.addAll(status.missing.map { "Missing: $it" })
                result.addAll(status.modified.map { "Modified: $it" })
                result.addAll(status.untracked.map { "Untracked: $it" })
                return result
            }
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * A simple implementation of [ProgressMonitor] to bridge JGit progress to the UI callback.
     */
    private class SimpleProgressMonitor(private val callback: (Int, String) -> Unit) : ProgressMonitor {
        private var totalWork = 0
        private var currentWork = 0
        private var currentTask = ""

        override fun start(totalTasks: Int) {}

        override fun beginTask(title: String, totalWork: Int) {
            this.currentTask = title
            this.totalWork = totalWork
            this.currentWork = 0
            callback(0, title)
        }

        override fun update(completed: Int) {
            currentWork += completed
            val percent = if (totalWork > 0) (currentWork * 100 / totalWork) else 0
            callback(percent, currentTask)
        }

        override fun endTask() {
            callback(100, currentTask)
        }

        override fun isCancelled(): Boolean = false
        override fun showDuration(enabled: Boolean) {}
    }
}
