package com.hereliesaz.ideaz.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.ui.widget.Attachment
import com.hereliesaz.ideaz.utils.ProjectAssetImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Resolves prompt-input [Attachment]s at submit time:
 *  - **Asset** mode: copy via [ProjectAssetImporter], collect the relative
 *    path for the prompt annotation.
 *  - **Reference** mode: load the bytes, classify by mime, build a
 *    [ChatPart] the active adapter can forward.
 *
 * Hard caps:
 *  - Reference images are downscaled with a JPEG re-encode pass until under
 *    [IMAGE_BYTE_CAP] (≈4 MB) so we never bombard a vision endpoint with
 *    raw 20-MP camera output.
 *  - Reference text files are refused above [TEXT_BYTE_CAP] (64 KB). Bigger
 *    files should be attached as Asset and read via the `read_file` tool.
 *  - PDFs forward as-is to [ChatPart.FileBlob] — Gemini supports them
 *    natively; other adapters drop them with a notice.
 *
 * The result combines a prompt-annotation string (lines describing each
 * attachment) with the parts list ready to splice into the next
 * [ChatMessage].
 */
object AttachmentResolver {

    /** Cap on a single Reference image, post-decode. */
    private const val IMAGE_BYTE_CAP = 4 * 1024 * 1024
    /** Cap on a single Reference text file. */
    private const val TEXT_BYTE_CAP = 64 * 1024

    data class Resolved(
        /** Lines to append to the user's prompt, one per resolved attachment. */
        val annotationLines: List<String>,
        /** Reference-mode parts to splice into the outgoing ChatMessage. */
        val referenceParts: List<ChatPart>,
        /** Non-fatal warnings to surface to the user (e.g. "PDF dropped"). */
        val warnings: List<String>,
    )

    suspend fun resolve(
        context: Context,
        projectDir: File,
        projectType: ProjectType,
        attachments: List<Attachment>,
    ): Resolved = withContext(Dispatchers.IO) {
        val annotations = mutableListOf<String>()
        val parts = mutableListOf<ChatPart>()
        val warnings = mutableListOf<String>()

        for (att in attachments) {
            when (att.mode) {
                Attachment.Mode.ASSET -> {
                    runCatching {
                        ProjectAssetImporter.import(context, projectDir, projectType, att.uri)
                    }.onSuccess { result ->
                        annotations += "- ${result.relativePath} (${humanSize(result.sizeBytes)})"
                    }.onFailure { e ->
                        warnings += "Failed to import ${att.displayName}: ${e.message ?: e::class.simpleName}"
                    }
                }
                Attachment.Mode.REFERENCE -> {
                    val mime = att.mimeType.lowercase()
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(att.uri)?.use { it.readBytes() }
                    }.getOrNull()
                    if (bytes == null) {
                        warnings += "Could not read ${att.displayName}."
                        continue
                    }
                    when {
                        mime.startsWith("image/") -> {
                            val downscaled = capImageBytes(bytes, mime)
                            parts += ChatPart.Image(downscaled, "image/jpeg".takeIf { downscaled !== bytes } ?: mime)
                            annotations += "- ${att.displayName} (reference image, ${humanSize(downscaled.size.toLong())})"
                        }
                        mime == "application/pdf" -> {
                            parts += ChatPart.FileBlob(bytes, mime, att.displayName)
                            annotations += "- ${att.displayName} (reference PDF, ${humanSize(bytes.size.toLong())})"
                        }
                        isTextLikeMime(mime) || isTextLikeExtension(att.displayName) -> {
                            if (bytes.size > TEXT_BYTE_CAP) {
                                warnings += "${att.displayName} is too large to send as reference (max ${TEXT_BYTE_CAP / 1024} KB). Attach as Asset instead."
                                continue
                            }
                            val text = bytes.toString(Charsets.UTF_8)
                            parts += ChatPart.Text("File `${att.displayName}`:\n```\n$text\n```")
                            annotations += "- ${att.displayName} (reference, ${humanSize(bytes.size.toLong())})"
                        }
                        else -> {
                            warnings += "${att.displayName} has unsupported type $mime for Reference. Attach as Asset?"
                        }
                    }
                }
            }
        }

        Resolved(annotations, parts, warnings)
    }

    /**
     * Downscale [bytes] (an image) below [IMAGE_BYTE_CAP] by re-encoding to
     * JPEG and stepping quality down. Returns the original [bytes] if
     * already under the cap, or if the decode fails.
     */
    private fun capImageBytes(bytes: ByteArray, mime: String): ByteArray {
        if (bytes.size <= IMAGE_BYTE_CAP) return bytes
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            ?: return bytes
        var quality = 85
        var out = bytes
        while (quality >= 35) {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            out = baos.toByteArray()
            if (out.size <= IMAGE_BYTE_CAP) break
            quality -= 10
        }
        return out
    }

    private fun isTextLikeMime(mime: String): Boolean =
        mime.startsWith("text/") ||
        mime in setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-yaml",
            "application/yaml",
            "application/toml",
        )

    private fun isTextLikeExtension(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("md", "txt", "csv", "tsv", "kt", "java", "py", "rs", "go", "ts", "tsx", "jsx", "html", "css", "yaml", "yml", "toml", "ini", "conf", "cfg")
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
