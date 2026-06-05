package com.hereliesaz.ideaz.utils

import java.io.File

/**
 * Flattens a project directory into a single annotated text blob suitable for
 * handing to an AI that has no file-system tools (e.g. the Gemini app bridge).
 *
 * The output is `project.txt`-style: a compact file tree followed by
 * `===== path =====` headers and the (redacted) contents of each text file.
 *
 * SECURITY: this text is sent to a third-party app, so secret files are skipped
 * entirely and remaining file contents are run through [LogSanitizer] to redact
 * tokens/keys/passwords. Binary files and oversized projects are truncated with
 * an explicit marker so the AI knows the picture is incomplete.
 */
object RepoSnapshot {

    /** Directories never worth sending (build/dependency/VCS noise). */
    private val SKIP_DIRS = setOf(
        ".git", "node_modules", "build", ".gradle", ".idea",
        "dist", ".next", "out", ".cache", "__ideaz__"
    )

    /** Files that must never leave the device, matched by name (case-insensitive). */
    private val SECRET_PATTERNS = listOf(
        Regex("""(?i)^\.env(\..+)?$"""),
        Regex("""(?i).*\.(keystore|jks|p12|pfx|pem|key)$"""),
        Regex("""(?i)^(id_rsa|id_ed25519)$"""),
        Regex("""(?i)^local\.properties$"""),
        Regex("""(?i)^secrets?\..+$"""),
    )

    /** Extensions treated as binary (never inlined as text). */
    private val BINARY_EXT = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "heic", "svgz",
        "zip", "jar", "aar", "apk", "aab", "so", "a", "o", "dex", "class",
        "ttf", "otf", "woff", "woff2", "eot",
        "mp3", "mp4", "m4a", "wav", "ogg", "webm", "mov", "avi",
        "pdf", "bin", "dat", "lock", "keystore", "jks"
    )

    data class Result(
        val text: String,
        val includedFiles: Int,
        val skipped: List<String>,
        val truncated: Boolean,
    )

    fun isSecret(name: String): Boolean = SECRET_PATTERNS.any { it.matches(name) }

    private fun isBinaryExt(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in BINARY_EXT

    /** Cheap heuristic: a NUL byte in the first 8KB means "not text". */
    private fun looksBinary(file: File): Boolean = try {
        file.inputStream().use { input ->
            val buf = ByteArray(8 * 1024)
            val n = input.read(buf)
            (0 until n).any { buf[it] == 0.toByte() }
        }
    } catch (e: Exception) {
        true // unreadable → treat as non-text and skip
    }

    /**
     * Build the snapshot for [projectDir], capping total inlined content at
     * [maxBytes] (default ~768KB — comfortably under the Binder transaction
     * limit when delivered inline, and a sane upper bound for an attachment).
     */
    fun build(projectDir: File, maxBytes: Int = 768_000): Result {
        val base = projectDir.canonicalFile
        if (!base.isDirectory) {
            return Result("(no project files yet)", 0, emptyList(), false)
        }

        val files = collectFiles(base)
        val sb = StringBuilder()
        sb.append("PROJECT FILE TREE\n")
        sb.append(tree(base))
        sb.append("\n\nFILE CONTENTS\n")

        var included = 0
        var bytes = sb.length
        var truncated = false
        val skipped = mutableListOf<String>()

        for (file in files) {
            val rel = file.relativeTo(base).path.replace(File.separatorChar, '/')
            when {
                isSecret(file.name) -> { skipped.add("$rel (secret — withheld)"); continue }
                isBinaryExt(file.name) || looksBinary(file) -> { skipped.add("$rel (binary)"); continue }
            }
            val raw = try { file.readText() } catch (e: Exception) {
                skipped.add("$rel (unreadable: ${e.message})"); continue
            }
            val redacted = LogSanitizer.sanitize(raw)
            val header = "\n===== $rel =====\n"
            if (bytes + header.length + redacted.length > maxBytes) {
                truncated = true
                skipped.add("$rel (omitted — size cap reached)")
                continue
            }
            sb.append(header).append(redacted).append('\n')
            bytes += header.length + redacted.length + 1
            included++
        }

        if (skipped.isNotEmpty()) {
            sb.append("\n===== NOT INCLUDED =====\n")
            skipped.forEach { sb.append("- ").append(it).append('\n') }
        }

        return Result(sb.toString(), included, skipped, truncated)
    }

    private fun collectFiles(base: File): List<File> {
        val out = mutableListOf<File>()
        fun walk(dir: File) {
            val children = dir.listFiles()?.sortedWith(compareBy({ it.isFile }, { it.name })) ?: return
            for (c in children) {
                if (c.isDirectory) {
                    if (c.name !in SKIP_DIRS) walk(c)
                } else {
                    out.add(c)
                }
            }
        }
        walk(base)
        return out
    }

    private fun tree(base: File, maxEntries: Int = 400): String {
        val sb = StringBuilder()
        var count = 0
        fun walk(dir: File, prefix: String) {
            val children = dir.listFiles()?.sortedWith(compareBy({ it.isFile }, { it.name })) ?: return
            for (c in children) {
                if (count >= maxEntries) { sb.append(prefix).append("…(truncated)\n"); return }
                if (c.isDirectory && c.name in SKIP_DIRS) continue
                count++
                sb.append(prefix).append(c.name).append(if (c.isDirectory) "/" else "").append('\n')
                if (c.isDirectory) walk(c, "$prefix  ")
            }
        }
        walk(base, "")
        return sb.toString()
    }
}
