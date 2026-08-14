package com.hereliesaz.ideaz.ai.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val DOWNLOAD_STORAGE_RESERVE_BYTES = 256L * 1024L * 1024L
private const val PART_METADATA_SUFFIX = ".part.meta"

/** Stable catalog identity persisted beside an interrupted partial download. */
internal fun partialDownloadFingerprint(spec: LocalModelFile): String {
    val manifest = listOf(
        spec.url,
        spec.fileName,
        spec.expectedSizeBytes?.toString().orEmpty(),
        spec.sha256?.lowercase().orEmpty(),
    ).joinToString("\u0000")
    return MessageDigest.getInstance("SHA-256")
        .digest(manifest.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/**
 * Keeps only a non-empty, manifest-matching partial whose size is plausible.
 * Old or catalog-stale bytes are deleted instead of being grafted onto a new model.
 */
internal fun reconcilePartialDownload(part: File, metadata: File, spec: LocalModelFile): Long {
    if (!part.isFile) {
        metadata.delete()
        return 0L
    }
    val valid = part.length() > 0L &&
        spec.expectedSizeBytes?.let { part.length() <= it } != false &&
        metadata.isFile &&
        runCatching { metadata.readText() }.getOrNull() == partialDownloadFingerprint(spec)
    if (!valid) {
        part.delete()
        metadata.delete()
        return 0L
    }
    return part.length()
}

/** Validates that a partial response continues the requested and catalogued byte range. */
internal fun contentRangeMatches(value: String?, expectedStart: Long, expectedTotal: Long?): Boolean {
    val match = value?.let {
        Regex("^bytes (\\d+)-(\\d+)/(\\d+|\\*)$").matchEntire(it.trim())
    } ?: return false
    val start = match.groupValues[1].toLongOrNull() ?: return false
    val end = match.groupValues[2].toLongOrNull() ?: return false
    val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
    return start == expectedStart &&
        end >= start &&
        (total == null || total > end) &&
        (expectedTotal == null || total == expectedTotal)
}

/**
 * Rejects a model download unless [availableBytes] can hold the remaining payload
 * plus a fixed safety reserve for app data, filesystem bookkeeping, and the user's
 * continued ability to use their phone. Saturating arithmetic prevents a corrupt
 * catalog size from wrapping the requirement negative.
 */
internal fun requireDownloadStorage(
    availableBytes: Long,
    remainingDownloadBytes: Long,
    reserveBytes: Long = DOWNLOAD_STORAGE_RESERVE_BYTES,
) {
    if (availableBytes < 0L || remainingDownloadBytes < 0L || reserveBytes < 0L) {
        throw IOException("Invalid storage calculation for model download")
    }
    val required = if (remainingDownloadBytes > Long.MAX_VALUE - reserveBytes) {
        Long.MAX_VALUE
    } else {
        remainingDownloadBytes + reserveBytes
    }
    if (availableBytes < required) {
        throw IOException(
            "Not enough storage for model download: need $required bytes including reserve, " +
                "only $availableBytes bytes available"
        )
    }
}

/**
 * Verifies [file] against the trusted fields carried by [spec].
 *
 * A missing manifest field is skipped so legacy catalog entries remain usable while
 * they are audited. Production catalog policy requires both fields; that rollout is
 * tracked separately in `docs/TODO.md` because inventing a checksum would be security
 * theatre with better typography.
 */
internal fun verifyDownloadedFile(file: File, spec: LocalModelFile) {
    if (!file.isFile || file.length() <= 0L) {
        throw IOException("Downloaded file is empty or missing: ${spec.fileName}")
    }
    spec.expectedSizeBytes?.let { expected ->
        if (expected <= 0L) throw IOException("Invalid expected size for ${spec.fileName}: $expected")
        if (file.length() != expected) {
            throw IOException(
                "Size mismatch for ${spec.fileName}: expected $expected bytes, got ${file.length()}"
            )
        }
    }
    spec.sha256?.let { expected ->
        if (!expected.matches(Regex("[0-9a-fA-F]{64}"))) {
            throw IOException("Invalid SHA-256 manifest value for ${spec.fileName}")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, ignoreCase = true)) {
            throw IOException("SHA-256 mismatch for ${spec.fileName}")
        }
    }
}

/**
 * Downloads on-device model files into a per-model directory
 * `<filesDir>/local-models/<id>/`, with progress, HTTP-Range resume, and an
 * optional bearer token for gated models. Handles both single-file models
 * (MediaPipe `.task`, llama.cpp `.gguf`) and multi-file models (ONNX GenAI's
 * model + config + tokenizer directory).
 */
class ModelDownloadManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val root = File(context.filesDir, "local-models").apply { mkdirs() }

    /** The directory all of [model]'s files live in. */
    fun modelDir(model: LocalModel): File = File(root, model.id)

    /** The model's primary file (runtimes that need a directory use its parent). */
    fun fileFor(model: LocalModel): File = File(modelDir(model), model.fileName)

    private fun filesOf(model: LocalModel): List<LocalModelFile> =
        listOf(
            LocalModelFile(
                url = model.url,
                fileName = model.fileName,
                expectedSizeBytes = model.expectedSizeBytes,
                sha256 = model.sha256,
            )
        ) + model.additionalFiles

    fun isDownloaded(model: LocalModel): Boolean = model.systemManaged ||
        filesOf(model).all { spec ->
            val file = File(modelDir(model), spec.fileName)
            if (!file.isFile || file.length() <= 0L) return@all false
            if (spec.expectedSizeBytes == null && spec.sha256 == null) return@all true
            val marker = File(modelDir(model), spec.fileName + ".verified")
            marker.isFile && runCatching { marker.readText() }.getOrNull() ==
                verificationMarker(spec, file.length())
        }

    fun delete(model: LocalModel): Boolean = !model.systemManaged && modelDir(model).deleteRecursively()

    /**
     * Download every file of [model] into its directory, resuming partial files.
     * [onProgress] reports cumulative bytes and the catalog's size estimate
     * (`approxSizeBytes`, or -1 if unknown) for an overall progress bar.
     * Returns the model's primary file.
     */
    suspend fun download(
        model: LocalModel,
        authToken: String? = null,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val mdir = modelDir(model).apply { mkdirs() }
        preflightStorage(model, mdir)
        val approxTotal = if (model.approxSizeBytes > 0) model.approxSizeBytes else -1L
        var cumulative = 0L

        for (f in filesOf(model)) {
            val target = File(mdir, f.fileName)
            if (target.isFile && target.length() > 0) {
                val valid = runCatching { verifyDownloadedFile(target, f) }.isSuccess
                if (valid) {
                    writeVerificationMarker(f, target)
                    cumulative += target.length()
                    onProgress(cumulative, approxTotal)
                    File(mdir, f.fileName + ".part").delete()
                    File(mdir, f.fileName + PART_METADATA_SUFFIX).delete()
                    continue
                }
                target.delete()
                File(mdir, f.fileName + ".verified").delete()
            }
            val base = cumulative
            downloadOne(f, mdir, authToken) { soFar -> onProgress(base + soFar, approxTotal) }
            cumulative += target.length()
        }
        fileFor(model)
    }

    /**
     * Calculates bytes still needed on this filesystem. Exact per-file manifest
     * sizes win when every file has one; otherwise the catalog's conservative total
     * estimate is used. Existing final and partial bytes reduce the requirement
     * because activation is a same-filesystem rename, not a second full copy.
     */
    private fun preflightStorage(model: LocalModel, mdir: File) {
        val specs = filesOf(model)
        val manifestTotal = specs
            .takeIf { entries -> entries.all { it.expectedSizeBytes != null } }
            ?.fold(0L) { total, spec ->
                val size = spec.expectedSizeBytes!!
                if (size < 0L || total > Long.MAX_VALUE - size) Long.MAX_VALUE else total + size
            }
        val total = manifestTotal ?: model.approxSizeBytes
        if (total <= 0L) {
            throw IOException("Model catalog has no usable size estimate for ${model.name}")
        }
        val existing = specs.fold(0L) { accumulated, spec ->
            val target = File(mdir, spec.fileName)
            val part = File(mdir, spec.fileName + ".part")
            val size = when {
                target.isFile -> target.length()
                part.isFile -> reconcilePartialDownload(
                    part,
                    File(mdir, spec.fileName + PART_METADATA_SUFFIX),
                    spec,
                )
                else -> 0L
            }
            if (accumulated > Long.MAX_VALUE - size) Long.MAX_VALUE else accumulated + size
        }.coerceAtMost(total)
        requireDownloadStorage(
            availableBytes = root.usableSpace,
            remainingDownloadBytes = (total - existing).coerceAtLeast(0L),
        )
    }

    private suspend fun downloadOne(
        f: LocalModelFile,
        mdir: File,
        authToken: String?,
        onBytes: (Long) -> Unit,
    ) {
        val target = File(mdir, f.fileName)
        val part = File(mdir, f.fileName + ".part")
        val metadata = File(mdir, f.fileName + PART_METADATA_SUFFIX)
        val existing = reconcilePartialDownload(part, metadata, f)

        val request = Request.Builder().url(f.url).apply {
            if (existing > 0) header("Range", "bytes=$existing-")
            if (!authToken.isNullOrBlank()) header("Authorization", "Bearer $authToken")
        }.build()

        client.newCall(request).execute().use { resp ->
            if (resp.code == 416 && existing > 0L) {
                val hasCompleteManifest = f.expectedSizeBytes != null && f.sha256 != null
                val complete = hasCompleteManifest && runCatching { verifyDownloadedFile(part, f) }.isSuccess
                if (complete) {
                    if (target.exists()) target.delete()
                    if (!part.renameTo(target)) throw IOException("Could not finalize ${f.fileName}")
                    metadata.delete()
                    writeVerificationMarker(f, target)
                    return
                }
                part.delete()
                metadata.delete()
                throw IOException("Server rejected stale resume data for ${f.fileName}")
            }
            if (!resp.isSuccessful) throw IOException("Download failed for ${f.fileName}: HTTP ${resp.code}")
            val body = resp.body
            val resuming = resp.code == 206
            if (resuming && !contentRangeMatches(
                    resp.header("Content-Range"),
                    existing,
                    f.expectedSizeBytes,
                )
            ) {
                part.delete()
                metadata.delete()
                throw IOException("Server returned an inconsistent Content-Range for ${f.fileName}")
            }
            val append = resuming && existing > 0
            if (!append) {
                part.delete()
                metadata.delete()
            }
            metadata.writeText(partialDownloadFingerprint(f))
            var downloaded = if (append) existing else 0L

            body.byteStream().use { input ->
                FileOutputStream(part, append).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        downloaded += n
                        if (f.expectedSizeBytes?.let { downloaded > it } == true) {
                            throw IOException("Downloaded file exceeds expected size for ${f.fileName}")
                        }
                        onBytes(downloaded)
                    }
                    output.fd.sync()
                }
            }
        }

        try {
            verifyDownloadedFile(part, f)
        } catch (e: IOException) {
            part.delete()
            metadata.delete()
            throw e
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("Could not finalize ${f.fileName}")
        metadata.delete()
        writeVerificationMarker(f, target)
    }

    private fun writeVerificationMarker(spec: LocalModelFile, file: File) {
        if (spec.expectedSizeBytes == null && spec.sha256 == null) return
        File(file.parentFile, file.name + ".verified")
            .writeText(verificationMarker(spec, file.length()))
    }

    private fun verificationMarker(spec: LocalModelFile, actualSize: Long): String =
        "size=$actualSize;expected=${spec.expectedSizeBytes ?: ""};sha256=${spec.sha256?.lowercase() ?: ""}"
}
