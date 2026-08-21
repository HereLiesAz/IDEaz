package com.hereliesaz.ideaz.ai

import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Immutable out-of-tree checkpoint created immediately before an AI mutates the project. */
data class IdeEditCheckpoint(
    val projectPath: String,
    val checkpointId: String,
    val snapshotPath: String,
)

/** Validation result presented to the user before edited files are reloaded. */
data class IdeEditReview(
    val checkpoint: IdeEditCheckpoint,
    val changedFiles: List<String>,
    val validationErrors: List<String>,
    val contentFingerprint: String,
    val rollbackAllowed: Boolean = true,
) {
    /** Refreshes validation/content without ever weakening a prior rollback denial. */
    fun refreshedFrom(current: IdeEditReview): IdeEditReview = current.copy(
        rollbackAllowed = rollbackAllowed && current.rollbackAllowed,
    )
}

/**
 * File-system tools for the AI agent. All paths are relative to [projectDir].
 *
 * Every method returns either the requested content / "OK" on success,
 * or a string beginning with "Error: " on failure. This contract lets the
 * AI model read the result and decide what to do next.
 */
class IdeTools(private val projectDir: File) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Canonical project identity used to bind consent and edit checkpoints. */
    fun projectPath(): String = projectDir.canonicalPath

    /**
     * Resolve [relativePath] against [projectDir], throwing [IllegalArgumentException]
     * if the canonical result would escape the project directory.
     */
    private fun resolvedFile(relativePath: String): File {
        val resolved = File(projectDir, relativePath).canonicalFile
        val base = projectDir.canonicalFile
        require(resolved.startsWith(base)) { "Path escapes project directory: $relativePath" }
        val normalized = resolved.relativeTo(base).invariantSeparatorsPath
        require(normalized.substringBefore('/') !in PROTECTED_ROOTS) {
            "Path is reserved by IDEaz: $relativePath"
        }
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

    /**
     * Creates an out-of-tree checkpoint without staging files or moving Git HEAD.
     * Call [captureEditTargets] before each mutation so only AI-owned paths can be
     * restored; unrelated concurrent user edits are never reset or cleaned.
     */
    fun createEditCheckpoint(message: String): IdeEditCheckpoint {
        val id = UUID.randomUUID().toString()
        val snapshot = projectDir.canonicalFile.parentFile
            .resolve(".ideaz-edit-checkpoints/$id")
            .apply { mkdirs() }
        snapshot.resolve("message.txt").writeText(message)
        snapshot.resolve("project-path.txt").writeText(projectDir.canonicalPath)
        snapshot.resolve("status.txt").writeText(CHECKPOINT_CAPTURED)
        return IdeEditCheckpoint(projectDir.canonicalPath, id, snapshot.canonicalPath)
    }

    /** Records the pre-edit bytes/existence of [relativePaths] exactly once. */
    fun captureEditTargets(checkpoint: IdeEditCheckpoint, relativePaths: Collection<String>) {
        requireCheckpointProject(checkpoint)
        val root = checkpointRoot(checkpoint)
        relativePaths.forEach { relativePath ->
            val source = resolvedFile(relativePath)
            val normalized = source.relativeTo(projectDir.canonicalFile).invariantSeparatorsPath
            val marker = root.resolve("states/$normalized.state")
            if (marker.exists()) return@forEach
            marker.parentFile?.mkdirs()
            if (source.exists()) {
                require(source.isFile) { "$relativePath is not a regular file" }
                val snapshot = root.resolve("files/$normalized")
                snapshot.parentFile?.mkdirs()
                source.copyTo(snapshot)
                marker.writeText("file")
            } else {
                marker.writeText("missing")
            }
        }
    }

    /** Captures every path a mutating tool call can touch before dispatch. */
    fun captureToolEdit(
        checkpoint: IdeEditCheckpoint,
        toolName: String,
        arguments: Map<String, String?>,
    ) {
        val paths = when (toolName) {
            "write_file" -> listOf(arguments["path"].orEmpty())
            "apply_patch" -> arguments["patch"].orEmpty().lineSequence()
                .filter { it.startsWith("--- ") || it.startsWith("+++ ") }
                .map { it.substring(4).substringBefore('\t').trim() }
                .filter { it != "/dev/null" }
                .map { it.removePrefix("a/").removePrefix("b/") }
                .distinct()
                .toList()
            else -> emptyList()
        }
        require(paths.isNotEmpty()) { "Mutating tool did not identify a project path" }
        captureEditTargets(checkpoint, paths)
    }

    /**
     * Lists and validates every working-tree change made after [checkpoint].
     * JSON syntax, project containment, regular-file type, and a conservative
     * per-file size ceiling are checked before the UI may approve a reload.
     */
    fun reviewEdits(checkpoint: IdeEditCheckpoint): IdeEditReview {
        requireCheckpointProject(checkpoint)
        val root = checkpointRoot(checkpoint)
        val changedFiles = capturedPaths(root).filter { relativePath ->
            val current = resolvedFile(relativePath)
            val state = root.resolve("states/$relativePath.state").readText()
            when (state) {
                "missing" -> current.exists()
                "file" -> !current.isFile || !filesEqual(current, root.resolve("files/$relativePath"))
                else -> true
            }
        }.sorted()
        val errors = buildList {
            changedFiles.forEach { relativePath ->
                val file = runCatching { resolvedFile(relativePath) }.getOrElse {
                    add("$relativePath escapes the project directory")
                    return@forEach
                }
                if (!file.exists()) return@forEach
                if (!file.isFile) {
                    add("$relativePath is not a regular file")
                    return@forEach
                }
                if (file.length() > MAX_REVIEW_FILE_BYTES) {
                    add("$relativePath exceeds the 2 MiB review limit")
                } else if (file.extension.equals("json", ignoreCase = true)) {
                    runCatching { json.parseToJsonElement(file.readText()) }
                        .onFailure { add("$relativePath contains invalid JSON") }
                }
            }
        }
        return IdeEditReview(
            checkpoint = checkpoint,
            changedFiles = changedFiles,
            validationErrors = errors,
            contentFingerprint = changedFilesFingerprint(changedFiles),
        )
    }

    /** Restores captured AI paths if they still match the reviewed fingerprint. */
    fun restoreEditCheckpoint(checkpoint: IdeEditCheckpoint, expectedFingerprint: String? = null) {
        requireCheckpointProject(checkpoint)
        if (expectedFingerprint != null) {
            check(reviewEdits(checkpoint).contentFingerprint == expectedFingerprint) {
                "Files changed after review"
            }
        }
        val root = checkpointRoot(checkpoint)
        capturedPaths(root).forEach { relativePath ->
            val destination = resolvedFile(relativePath)
            when (root.resolve("states/$relativePath.state").readText()) {
                "file" -> {
                    destination.parentFile?.mkdirs()
                    root.resolve("files/$relativePath").copyTo(destination, overwrite = true)
                }
                "missing" -> destination.deleteRecursively()
            }
        }
        root.deleteRecursively()
    }

    /** Deletes a checkpoint after a no-op edit or when undo is intentionally retired. */
    fun discardEditCheckpoint(checkpoint: IdeEditCheckpoint) {
        requireCheckpointProject(checkpoint)
        checkpointRoot(checkpoint).deleteRecursively()
    }

    /** Persists the last adapter-observed edit state for crash-safe reconciliation. */
    fun updateEditCheckpointFingerprint(checkpoint: IdeEditCheckpoint, fingerprint: String) {
        requireCheckpointProject(checkpoint)
        checkpointRoot(checkpoint).resolve("expected-fingerprint.txt").writeText(fingerprint)
    }

    /** Records that dispatch is entering the only process-death-ambiguous window. */
    fun markEditMutationStarted(checkpoint: IdeEditCheckpoint) {
        requireCheckpointProject(checkpoint)
        checkpointRoot(checkpoint).resolve("status.txt").writeText(CHECKPOINT_MUTATING)
    }

    /** Records that the full post-dispatch file state is ready for normal review. */
    fun markEditAwaitingReview(checkpoint: IdeEditCheckpoint) {
        requireCheckpointProject(checkpoint)
        checkpointRoot(checkpoint).resolve("status.txt").writeText(CHECKPOINT_REVIEW)
    }

    /** Marks an approved checkpoint so startup cleanup never rolls its edit back. */
    fun markEditCheckpointApproved(checkpoint: IdeEditCheckpoint) {
        requireCheckpointProject(checkpoint)
        checkpointRoot(checkpoint).apply {
            resolve("status.txt").writeText(CHECKPOINT_APPROVED)
            setLastModified(System.currentTimeMillis())
        }
    }

    /**
     * Recovers reviewable edits left by process death and expires old approved
     * undo snapshots. It never mutates project files: ambiguous mid-dispatch
     * checkpoints disable rollback rather than guessing which bytes belong to whom.
     */
    fun reconcileEditCheckpoints(nowMillis: Long = System.currentTimeMillis()): List<IdeEditReview> {
        val base = checkpointBase()
        if (!base.isDirectory) return emptyList()
        val recovered = mutableListOf<IdeEditReview>()
        base.listFiles()?.filter { it.isDirectory }?.forEach { root ->
            if (root.resolve("project-path.txt").readTextOrNull() != projectDir.canonicalPath) return@forEach
            when (root.resolve("status.txt").readTextOrNull()) {
                CHECKPOINT_CAPTURED -> root.deleteRecursively()
                CHECKPOINT_MUTATING, CHECKPOINT_REVIEW -> {
                    val checkpoint = IdeEditCheckpoint(projectDir.canonicalPath, root.name, root.canonicalPath)
                    val review = runCatching { reviewEdits(checkpoint) }.getOrNull()
                    if (review == null || review.changedFiles.isEmpty()) {
                        root.deleteRecursively()
                    } else {
                        val expected = root.resolve("expected-fingerprint.txt").readTextOrNull()
                        val rollbackAllowed = root.resolve("status.txt").readTextOrNull() == CHECKPOINT_REVIEW &&
                            expected == review.contentFingerprint
                        recovered += review.copy(rollbackAllowed = rollbackAllowed)
                    }
                }
                CHECKPOINT_APPROVED -> if (nowMillis - root.lastModified() >= APPROVED_CHECKPOINT_TTL_MS) {
                    root.deleteRecursively()
                }
                else -> root.deleteRecursively()
            }
        }
        return recovered
    }

    private fun requireCheckpointProject(checkpoint: IdeEditCheckpoint) {
        require(checkpoint.projectPath == projectDir.canonicalPath) {
            "Checkpoint belongs to a different project"
        }
    }

    private fun checkpointRoot(checkpoint: IdeEditCheckpoint): File {
        val root = File(checkpoint.snapshotPath).canonicalFile
        val expectedBase = checkpointBase()
        require(root.parentFile == expectedBase && root.name == checkpoint.checkpointId) {
            "Invalid edit checkpoint location"
        }
        require(root.isDirectory) { "Edit checkpoint no longer exists" }
        return root
    }

    private fun checkpointBase(): File = projectDir.canonicalFile.parentFile
        .resolve(".ideaz-edit-checkpoints")
        .canonicalFile

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()

    private fun capturedPaths(root: File): List<String> {
        val states = root.resolve("states")
        if (!states.isDirectory) return emptyList()
        return states.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".state") }
            .map { it.relativeTo(states).invariantSeparatorsPath.removeSuffix(".state") }
            .toList()
    }

    private fun filesEqual(first: File, second: File): Boolean {
        if (!first.isFile || !second.isFile || first.length() != second.length()) return false
        first.inputStream().buffered().use { left ->
            second.inputStream().buffered().use { right ->
                while (true) {
                    val a = left.read()
                    val b = right.read()
                    if (a != b) return false
                    if (a < 0) return true
                }
            }
        }
    }

    private fun changedFilesFingerprint(changedFiles: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        changedFiles.forEach { relativePath ->
            digest.update(relativePath.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            val file = resolvedFile(relativePath)
            if (file.isFile) file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            } else {
                digest.update("<missing>".toByteArray(Charsets.UTF_8))
            }
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_REVIEW_FILE_BYTES = 2L * 1024L * 1024L
        const val APPROVED_CHECKPOINT_TTL_MS = 7L * 24L * 60L * 60L * 1000L
        const val CHECKPOINT_CAPTURED = "captured"
        const val CHECKPOINT_MUTATING = "mutating"
        const val CHECKPOINT_REVIEW = "review"
        const val CHECKPOINT_APPROVED = "approved"
        // ".ideaz-edit-checkpoints" was listed here too, but resolvedFile()
        // already requires containment inside projectDir before this set is
        // even checked (checkpointBase() below lives in projectDir's *parent*),
        // so that entry could never trigger - dead defense-in-depth for a path
        // that's already unreachable via a different check.
        val PROTECTED_ROOTS = setOf(".git")
    }
}
