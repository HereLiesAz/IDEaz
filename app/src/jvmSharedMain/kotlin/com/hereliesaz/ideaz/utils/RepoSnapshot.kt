package com.hereliesaz.ideaz.utils

import java.io.File
import java.nio.file.Files

/**
 * Flattens a project directory into a single annotated text blob suitable for
 * handing to an AI that has no file-system tools (e.g. the Gemini app bridge).
 *
 * The output is `project.txt`-style: a compact file tree followed by
 * `===== path =====` headers and the (redacted) contents of each text file.
 *
 * SECURITY: this text is sent to a third-party app, so secret files are skipped
 * entirely and remaining file contents are run through [LogSanitizer] to redact
 * tokens/keys/passwords. Binary files, symlinks, paths that canonicalize outside
 * the project, and oversized projects are omitted.
 */
object RepoSnapshot {

    /** Directories never worth sending (build/dependency/VCS noise). */
    private val SKIP_DIRS = setOf(
        ".git", "node_modules", "build", ".gradle", ".idea",
        "dist", ".next", "out", ".cache", "__ideaz__",
        // IDEaz's own state: prompt history and screenshots.
        ".ideaz",
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

    /** Shares [SecretFiles] with Deploy so the two deny-lists cannot drift apart. */
    fun isSecret(name: String): Boolean = SecretFiles.isSecretName(name)

    /** Size of [text] as UTF-8; the budgets are in bytes, not chars. */
    internal fun utf8Size(text: String): Int = text.toByteArray(Charsets.UTF_8).size

    /** Upper bound on files collected, so a huge tree cannot be walked to the end. */
    private const val MAX_WALK_FILES = 20_000

    /** Bytes kept free for the NOT INCLUDED list. */
    internal fun reserveFor(maxBytes: Int): Int = (maxBytes / 10).coerceIn(512, 16_000)

    /**
     * The NOT INCLUDED section for [skipped], never larger than [limitBytes] (UTF-8): entries
     * that do not fit are replaced by a count.
     */
    internal fun renderNotIncluded(skipped: List<String>, limitBytes: Int): String {
        if (skipped.isEmpty() || limitBytes <= 0) return ""
        val head = "\n===== NOT INCLUDED =====\n"
        val sb = StringBuilder(head)
        var used = utf8Size(head)
        var shown = 0
        for (entry in skipped) {
            val line = "- $entry\n"
            val size = utf8Size(line)
            // Keep room for the "and N more" line.
            if (used + size + 40 > limitBytes) break
            sb.append(line)
            used += size
            shown++
        }
        if (shown < skipped.size) sb.append("- ...and ").append(skipped.size - shown).append(" more\n")
        val out = sb.toString()
        return if (utf8Size(out) <= limitBytes) out else ""
    }

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
     * [maxBytes] UTF-8 bytes, NOT INCLUDED list included (default ~768KB — comfortably under the Binder transaction
     * limit when delivered inline, and a sane upper bound for an attachment).
     */
    fun build(projectDir: File, maxBytes: Int = 768_000): Result {
        val base = projectDir.canonicalFile
        if (!base.isDirectory) {
            return Result("(no project files yet)", 0, emptyList(), false)
        }

        val skipped = mutableListOf<String>()
        val files = collectFiles(base, skipped)
        val sb = StringBuilder()
        sb.append("PROJECT FILE TREE\n")
        sb.append(tree(base))
        sb.append("\n\nFILE CONTENTS\n")

        var included = 0
        var bytes = utf8Size(sb.toString())
        // Contents stop short of maxBytes so the NOT INCLUDED list fits inside the budget too.
        val contentLimit = maxBytes - reserveFor(maxBytes)
        var truncated = false

        for ((index, file) in files.withIndex()) {
            val rel = file.relativeTo(base).path.replace(File.separatorChar, '/')
            if (bytes >= contentLimit) {
                // Budget spent: stop reading files altogether.
                truncated = true
                skipped.add("${files.size - index} more file(s) (omitted — size cap reached)")
                break
            }
            when {
                isSecret(file.name) -> { skipped.add("$rel (secret — withheld)"); continue }
                isBinaryExt(file.name) || looksBinary(file) -> { skipped.add("$rel (binary)"); continue }
            }
            // Never load a file that cannot fit: a huge text file used to be read and
            // regex-scanned in full only to be dropped.
            if (file.length() > contentLimit) {
                truncated = true
                skipped.add("$rel (omitted — larger than the size cap)")
                continue
            }
            val raw = try { file.readText() } catch (e: Exception) {
                skipped.add("$rel (unreadable: ${e.message})"); continue
            }
            val redacted = LogSanitizer.sanitize(raw)
            val header = "\n===== $rel =====\n"
            val size = utf8Size(header) + utf8Size(redacted) + 1
            if (bytes + size > contentLimit) {
                truncated = true
                skipped.add("$rel (omitted — size cap reached)")
                continue
            }
            sb.append(header).append(redacted).append('\n')
            bytes += size
            included++
        }

        val distinct = skipped.distinct()
        sb.append(renderNotIncluded(distinct, maxBytes - bytes))

        return Result(sb.toString(), included, distinct, truncated)
    }

    private fun collectFiles(base: File, skipped: MutableList<String>): List<File> {
        val out = mutableListOf<File>()
        val basePath = base.toPath()

        var limitNoted = false

        fun walk(dir: File) {
            val children = dir.listFiles()?.sortedWith(compareBy({ it.isFile }, { it.name })) ?: return
            for (child in children) {
                if (out.size >= MAX_WALK_FILES) {
                    if (!limitNoted) {
                        limitNoted = true
                        skipped.add("(file walk stopped at $MAX_WALK_FILES files)")
                    }
                    return
                }
                val rel = runCatching {
                    child.absoluteFile.relativeTo(base.absoluteFile).path.replace(File.separatorChar, '/')
                }.getOrDefault(child.name)

                if (Files.isSymbolicLink(child.toPath())) {
                    skipped.add("$rel (symlink — withheld)")
                    continue
                }

                val canonical = runCatching { child.canonicalFile }.getOrElse {
                    skipped.add("$rel (unresolvable — withheld)")
                    continue
                }
                if (!canonical.toPath().startsWith(basePath)) {
                    skipped.add("$rel (outside project — withheld)")
                    continue
                }

                if (canonical.isDirectory) {
                    if (canonical.name !in SKIP_DIRS) walk(canonical)
                } else if (canonical.isFile) {
                    out.add(canonical)
                }
            }
        }

        walk(base)
        return out
    }

    private fun tree(base: File, maxEntries: Int = 400): String {
        val sb = StringBuilder()
        val basePath = base.toPath()
        var count = 0

        fun walk(dir: File, prefix: String) {
            val children = dir.listFiles()?.sortedWith(compareBy({ it.isFile }, { it.name })) ?: return
            for (child in children) {
                if (count >= maxEntries) {
                    sb.append(prefix).append("…(truncated)\n")
                    return
                }
                if (Files.isSymbolicLink(child.toPath())) continue
                val canonical = runCatching { child.canonicalFile }.getOrNull() ?: continue
                if (!canonical.toPath().startsWith(basePath)) continue
                if (canonical.isDirectory && canonical.name in SKIP_DIRS) continue

                count++
                sb.append(prefix)
                    .append(canonical.name)
                    .append(if (canonical.isDirectory) "/" else "")
                    .append('\n')
                if (canonical.isDirectory) walk(canonical, "$prefix  ")
            }
        }

        walk(base, "")
        return sb.toString()
    }
}
