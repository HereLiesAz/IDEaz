package com.hereliesaz.ideaz.utils

import java.io.File

/**
 * What a project must never commit, and therefore never push or publish:
 * secrets, IDEaz's own local state (prompt history, screenshots) and
 * dependency/build output.
 *
 * Deploy used to `git add .` with no `.gitignore` at all into a public
 * repository whose Pages workflow then served the repository root, so a
 * `.env`, a keystore or `.ideaz/prompt_history.json` went straight to the open
 * web. [ensure] runs before the first `git add` of a new project and again on
 * every Deploy; [isExcluded] lets Deploy stop tracking anything an earlier
 * commit already swept up.
 */
object DeployIgnore {

    /** gitignore entries, in the order they are written. */
    val ENTRIES: List<String> = listOf(
        ".env*",
        "*.jks",
        "*.keystore",
        "*.p12",
        "*.pem",
        "*.key",
        ".ideaz/",
        "node_modules/",
        "dist/",
        "build/",
        "out/",
    )

    private const val HEADER = "# Added by IDEaz: secrets, local IDE state and build output are never committed."

    private val dirNames = ENTRIES.filter { it.endsWith("/") }.map { it.removeSuffix("/") }.toSet()
    private val suffixes = ENTRIES.filter { it.startsWith("*.") }.map { it.removePrefix("*") }

    /**
     * Writes or completes `.gitignore` in [projectDir], appending only the
     * entries it does not already list. Returns true when the file changed.
     */
    fun ensure(projectDir: File): Boolean {
        val file = File(projectDir, ".gitignore")
        val existing = if (file.exists()) file.readText() else ""
        val present = existing.lineSequence().map { it.trim() }.toSet()
        val missing = ENTRIES.filterNot { it in present }
        if (missing.isEmpty()) return false
        val separator = when {
            existing.isEmpty() -> ""
            existing.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        projectDir.mkdirs()
        file.writeText(existing + separator + (listOf(HEADER) + missing).joinToString("\n") + "\n")
        return true
    }

    /**
     * True when the project-relative, `/`-separated [path] matches one of
     * [ENTRIES] the way git would: a directory entry at any depth, a name
     * pattern on the file name.
     */
    fun isExcluded(path: String): Boolean {
        val segments = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return false
        if (segments.dropLast(1).any { it in dirNames }) return true
        val name = segments.last()
        return name.startsWith(".env") || suffixes.any { name.endsWith(it) }
    }
}
