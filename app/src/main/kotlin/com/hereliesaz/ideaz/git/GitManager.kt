package com.hereliesaz.ideaz.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.lib.ProgressMonitor
import java.io.File
import java.io.ByteArrayInputStream

class GitManager(private val projectDir: File) {

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

    fun hasChanges(): Boolean {
        Git.open(projectDir).use { git ->
            return !git.status().call().isClean
        }
    }

    fun addAll() {
        Git.open(projectDir).use { git ->
            git.add().addFilepattern(".").call()
        }
    }

    fun commit(message: String) {
        Git.open(projectDir).use { git ->
            git.commit().setMessage(message).call()
        }
    }

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

    fun checkout(branch: String) {
        Git.open(projectDir).use { git ->
            // Check if branch exists locally
            val branchExists = git.branchList().call().any { it.name == "refs/heads/$branch" }
            val cmd = git.checkout().setName(branch)
            if (!branchExists) {
                // If not local, assume remote tracking or just creating it
                // For PR branches fetched via fetchPr, they are local heads.
                // But usually checkout checks out existing branch.
            }
            cmd.call()
        }
    }

    fun merge(branch: String) {
        Git.open(projectDir).use { git ->
            val repository = git.repository
            val ref = repository.findRef(branch)
            git.merge().include(ref).call()
        }
    }

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

    fun init() {
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        Git.init().setDirectory(projectDir).call().close()
    }

    fun applyPatch(patch: String) {
        Git.open(projectDir).use { git ->
            val patchInputStream = ByteArrayInputStream(patch.toByteArray())
            git.apply().setPatch(patchInputStream).call()
        }
    }

    fun stash(message: String? = null) {
        Git.open(projectDir).use { git ->
            val cmd = git.stashCreate()
            if (message != null) cmd.setWorkingDirectoryMessage(message)
            cmd.call()
        }
    }

    fun unstash() {
        Git.open(projectDir).use { git ->
            git.stashApply().call()
        }
    }

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

    fun getCurrentBranch(): String? {
        return try {
            Git.open(projectDir).use { git ->
                git.repository.branch
            }
        } catch (e: Exception) {
            null
        }
    }

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
