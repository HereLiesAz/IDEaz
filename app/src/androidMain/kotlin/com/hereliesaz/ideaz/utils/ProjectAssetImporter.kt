package com.hereliesaz.ideaz.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
     * Where an imported asset lands: `assets/` inside the project.
     *
     * This used to branch on project type, routing Android imports to
     * `app/src/main/res/raw` or `app/src/main/assets` depending on whether the
     * filename sanitised to `[a-z0-9_]+` (which `res/raw/` requires). With the
     * Android target gone there is one destination, and the sanitisation
     * heuristic that decided between the two went with it.
     */
    private fun destinationDir(projectDir: File): File = File(projectDir, "assets")

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
        sourceUri: Uri,
    ): ImportResult = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(context, sourceUri)
        val destDir = destinationDir(projectDir).apply { mkdirs() }
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
