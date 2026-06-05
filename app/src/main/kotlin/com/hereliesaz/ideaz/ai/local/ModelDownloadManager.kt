package com.hereliesaz.ideaz.ai.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

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
        listOf(LocalModelFile(model.url, model.fileName)) + model.additionalFiles

    fun isDownloaded(model: LocalModel): Boolean = model.systemManaged ||
        filesOf(model).all { File(modelDir(model), it.fileName).let { f -> f.isFile && f.length() > 0 } }

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
        val approxTotal = if (model.approxSizeBytes > 0) model.approxSizeBytes else -1L
        var cumulative = 0L

        for (f in filesOf(model)) {
            val target = File(mdir, f.fileName)
            if (target.isFile && target.length() > 0) {
                cumulative += target.length()
                onProgress(cumulative, approxTotal)
                continue
            }
            val base = cumulative
            downloadOne(f, mdir, authToken) { soFar -> onProgress(base + soFar, approxTotal) }
            cumulative += target.length()
        }
        fileFor(model)
    }

    private fun downloadOne(
        f: LocalModelFile,
        mdir: File,
        authToken: String?,
        onBytes: (Long) -> Unit,
    ) {
        val target = File(mdir, f.fileName)
        val part = File(mdir, f.fileName + ".part")
        val existing = if (part.isFile) part.length() else 0L

        val request = Request.Builder().url(f.url).apply {
            if (existing > 0) header("Range", "bytes=$existing-")
            if (!authToken.isNullOrBlank()) header("Authorization", "Bearer $authToken")
        }.build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed for ${f.fileName}: HTTP ${resp.code}")
            val body = resp.body
            val resuming = resp.code == 206
            val append = resuming && existing > 0
            if (!append) part.delete()
            var downloaded = if (append) existing else 0L

            body.byteStream().use { input ->
                FileOutputStream(part, append).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        downloaded += n
                        onBytes(downloaded)
                    }
                    output.fd.sync()
                }
            }
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("Could not finalize ${f.fileName}")
    }
}
