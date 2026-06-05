package com.hereliesaz.ideaz.ai

import org.eclipse.jgit.api.Git
import java.io.File

/**
 * File-system tools for the AI agent. All paths are relative to [projectDir].
 *
 * Every method returns either the requested content / "OK" on success,
 * or a string beginning with "Error: " on failure. This contract lets the
 * AI model read the result and decide what to do next.
 */
class IdeTools(private val projectDir: File) {

    /**
     * Resolve [relativePath] against [projectDir], throwing [IllegalArgumentException]
     * if the canonical result would escape the project directory.
     */
    private fun resolvedFile(relativePath: String): File {
        val resolved = File(projectDir, relativePath).canonicalFile
        val base = projectDir.canonicalFile
        require(resolved.startsWith(base)) { "Path escapes project directory: $relativePath" }
        return resolved
    }

    /** Read a file's entire text content. */
    fun readFile(relativePath: String): String = try {
        resolvedFile(relativePath).readText()
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    /**
     * Overwrite a file with [content], creating parent directories as needed.
     * Returns "OK" on success.
     */
    fun writeFile(relativePath: String, content: String): String = try {
        val file = resolvedFile(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        "OK"
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    /**
     * List the immediate children of the directory at [relativePath].
     * Use "." to list the project root.
     */
    fun listFiles(relativePath: String): String = try {
        val dir = resolvedFile(relativePath)
        if (!dir.isDirectory) return "Error: Not a directory: $relativePath"
        dir.list()?.joinToString("\n") ?: "Error: Cannot list directory"
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    /**
     * A compact recursive map of the project's files, for orienting the AI before
     * it digs in with [readFile]/[listFiles]. Skips dependency/build/VCS noise and
     * caps the entry count so the listing stays small enough to put in a prompt.
     */
    fun repoMap(maxEntries: Int = 300): String {
        val base = projectDir.canonicalFile
        if (!base.isDirectory) return "(no project files yet)"
        val skip = setOf(
            ".git", "node_modules", "build", ".gradle", ".idea",
            "dist", ".next", "out", ".cache", "__ideaz__"
        )
        val sb = StringBuilder()
        var count = 0
        fun walk(dir: File, prefix: String) {
            val children = dir.listFiles()
                ?.sortedWith(compareBy({ it.isFile }, { it.name })) ?: return
            for (c in children) {
                if (count >= maxEntries) {
                    sb.append(prefix).append("…(truncated)\n")
                    return
                }
                if (c.isDirectory && c.name in skip) continue
                count++
                sb.append(prefix).append(c.name).append(if (c.isDirectory) "/" else "").append('\n')
                if (c.isDirectory) walk(c, "$prefix  ")
            }
        }
        walk(base, "")
        return sb.toString().ifBlank { "(no project files yet)" }
    }

    /**
     * Apply a unified diff [patchText] to the working tree using JGit's ApplyCommand.
     * Initialises a temporary git repository in [projectDir] if one does not already
     * exist, so that JGit's ApplyCommand has a valid work-tree context.
     * Returns "OK" on success, or "Error: ..." if the patch is invalid or cannot apply.
     */
    fun applyPatch(patchText: String): String = try {
        // Reject trivially invalid patches before touching the filesystem.
        if (!patchText.contains("--- ") || !patchText.contains("+++ ") || !patchText.contains("@@")) {
            return "Error: Malformed patch: missing unified diff headers"
        }

        val gitDir = File(projectDir, ".git")
        val ownedGitDir = !gitDir.exists()
        val git: Git = if (ownedGitDir) {
            // Create a minimal repo so JGit's ApplyCommand has a valid context.
            Git.init().setDirectory(projectDir).call()
        } else {
            Git.open(projectDir)
        }
        try {
            // Normalise line endings to LF so that JGit does not write CRLF on Windows.
            val normalised = patchText.replace("\r\n", "\n")
            val result = git.apply()
                .setPatch(normalised.byteInputStream(Charsets.UTF_8))
                .call()

            // Re-write patched files with LF endings so callers get consistent
            // newlines on Windows (JGit may emit CRLF via the OS line-separator).
            result.updatedFiles.forEach { patchedFile ->
                if (patchedFile.exists()) {
                    val text = patchedFile.readText(Charsets.UTF_8)
                    if (text.contains('\r')) {
                        patchedFile.writeText(text.replace("\r\n", "\n"), Charsets.UTF_8)
                    }
                }
            }
            "OK"
        } finally {
            git.close()
            if (ownedGitDir) gitDir.deleteRecursively()
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    /**
     * Commit the current project state so a subsequent AI edit can be undone with
     * a single `git reset`. Initialises a repo if none exists. Allows empty
     * commits so there's always a HEAD to reset to. Returns "OK" or "Error: ...";
     * never throws (a failed checkpoint must not block the edit).
     */
    fun checkpoint(message: String): String = try {
        val gitDir = File(projectDir, ".git")
        val git = if (gitDir.exists()) Git.open(projectDir) else Git.init().setDirectory(projectDir).call()
        git.use {
            it.add().addFilepattern(".").call()
            it.commit()
                .setMessage(message)
                .setAllowEmpty(true)
                .setAuthor("IDEaz", "ideaz@local")
                .setSign(false)
                .call()
        }
        "OK"
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
