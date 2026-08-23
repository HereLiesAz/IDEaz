package com.hereliesaz.ideaz.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.hereliesaz.ideaz.models.ProjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies a user-picked asset (image, font, JSON, etc.) from a SAF [Uri] into
 * the active project directory at a sensible relative path. Used by the
 * prompt-input attach button so the AI can `read_file` the asset after the
 * user references it in the prompt.
 *
 * Filename collisions resolve to `name (2).ext`, `name (3).ext`, ... — never
 * overwrite without user consent.
 */
object ProjectAssetImporter {

    /**
     * Result of a successful import.
     *
     * @property relativePath Path inside the project tree, suitable for the
     *   prompt annotation. Forward-slash-separated.
     * @property displayName Original filename as shown to the user.
     * @property sizeBytes File size after copy.
     */
    data class ImportResult(
        val relativePath: String,
        val displayName: String,
        val sizeBytes: Long,
    )

    /**
     * Pick a subdirectory based on project type. For Android binary blobs
     * (PNG/JPG/font/etc.) `res/raw/` is the right home, but only if the
     * filename sanitises to `[a-z0-9_]+` — `res/raw/` rejects hyphens, spaces,
     * and uppercase. When sanitisation would lose information, fall back to
     * `app/src/main/assets/`.
     */
    private fun destinationDir(projectDir: File, type: ProjectType, originalName: String): File {
        return when (type) {
            ProjectType.WEB, ProjectType.PWA, ProjectType.REACT -> File(projectDir, "assets")
            ProjectType.ANDROID -> {
                val nameOnly = originalName.substringBeforeLast('.', originalName)
                val resRawOk = nameOnly == nameOnly.lowercase() &&
                    nameOnly.all { it.isLetterOrDigit() || it == '_' } &&
                    nameOnly.isNotEmpty() && !nameOnly[0].isDigit()
                if (resRawOk && originalName.isBinaryAssetName()) {
                    File(projectDir, "app/src/main/res/raw")
                } else {
                    File(projectDir, "app/src/main/assets")
                }
            }
            else -> projectDir
        }
    }

    /**
     * Heuristic for "this is a raw binary asset, not text" — used only to
     * route Android imports to `res/raw/` vs `assets/`. Text-y files go to
     * `assets/` so the build tool doesn't try to validate them.
     */
    private fun String.isBinaryAssetName(): Boolean {
        val ext = substringAfterLast('.', "").lowercase()
        return ext in setOf(
            "png", "jpg", "jpeg", "webp", "gif", "bmp",
            "mp3", "ogg", "wav", "flac", "m4a",
            "mp4", "webm", "mkv",
            "ttf", "otf",
            "pdf",
        )
    }

    /**
     * Resolve a user-visible filename for [uri]. Tries the SAF
     * `OpenableColumns.DISPLAY_NAME` column first, falls back to the last
     * path segment, finally to a generic name.
     */
    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return sanitiseFilename(name)
                }
            }
        return sanitiseFilename(uri.lastPathSegment ?: "attached-${System.currentTimeMillis()}")
    }

    /** Drop path separators, collapse whitespace, strip leading dot. */
    private fun sanitiseFilename(raw: String): String {
        var name = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        name = name.replace(Regex("\\s+"), "_")
        if (name.startsWith('.')) name = name.removePrefix(".")
        return name.ifBlank { "attached" }
    }

    /**
     * Resolve a non-colliding destination file inside [dir]. If `<name>` exists,
     * try `<base> (2).<ext>`, `<base> (3).<ext>`, etc.
     */
    private fun nonCollidingFile(dir: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val extWithDot = if ('.' in name) "." + name.substringAfterLast('.') else ""
        var candidate = File(dir, name)
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n)$extWithDot")
            n++
        }
        return candidate
    }

    /**
     * Copy the file at [sourceUri] into the project. Suspending; runs file I/O
     * on `Dispatchers.IO`. Throws on read/write failure — callers surface to
     * the user.
     */
    suspend fun import(
        context: Context,
        projectDir: File,
        projectType: ProjectType,
        sourceUri: Uri,
    ): ImportResult = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(context, sourceUri)
        val destDir = destinationDir(projectDir, projectType, displayName).apply { mkdirs() }
        val destFile = nonCollidingFile(destDir, displayName)

        val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: error("Could not open $sourceUri")
        destFile.writeBytes(bytes)

        val relative = destFile.absolutePath.removePrefix(projectDir.absolutePath).trimStart('/')
        ImportResult(
            relativePath = relative,
            displayName = displayName,
            sizeBytes = destFile.length(),
        )
    }

    /**
     * Delete a previously-imported asset by relative path. Used when the user
     * removes a chip from the attachment row before submitting.
     */
    suspend fun deleteByRelativePath(projectDir: File, relativePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val target = File(projectDir, relativePath)
            target.exists() && target.delete()
        }
}
