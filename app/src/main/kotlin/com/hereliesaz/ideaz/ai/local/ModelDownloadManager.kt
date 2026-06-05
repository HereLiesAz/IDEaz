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
 * Downloads on-device model files into `<filesDir>/local-models/`, with progress
 * reporting, HTTP-Range resume of a partial download, and an optional bearer token
 * for gated models. URL-generic, so it works for any [LocalModel].
 */
class ModelDownloadManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val dir = File(context.filesDir, "local-models").apply { mkdirs() }

    fun fileFor(model: LocalModel): File = File(dir, model.fileName)
    fun isDownloaded(model: LocalModel): Boolean =
        model.systemManaged || fileFor(model).let { it.isFile && it.length() > 0 }
    fun delete(model: LocalModel): Boolean = !model.systemManaged && fileFor(model).delete()

    /**
     * Download [model], resuming a `.part` file if one exists. [onProgress] gets
     * (bytesDownloaded, totalBytes); total is -1 when the server doesn't report it.
     * Returns the finalized model file.
     */
    suspend fun download(
        model: LocalModel,
        authToken: String? = null,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val target = fileFor(model)
        if (target.isFile && target.length() > 0) return@withContext target

        val part = File(dir, model.fileName + ".part")
        val existing = if (part.isFile) part.length() else 0L

        val request = Request.Builder().url(model.url).apply {
            if (existing > 0) header("Range", "bytes=$existing-")
            if (!authToken.isNullOrBlank()) header("Authorization", "Bearer $authToken")
        }.build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed: HTTP ${resp.code}")
            val body = resp.body
            val resuming = resp.code == 206
            val total = body.contentLength().let { len ->
                if (len < 0) -1L else len + (if (resuming) existing else 0L)
            }

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
                        onProgress(downloaded, total)
                    }
                    output.fd.sync()
                }
            }
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("Could not finalize downloaded model")
        target
    }
}
