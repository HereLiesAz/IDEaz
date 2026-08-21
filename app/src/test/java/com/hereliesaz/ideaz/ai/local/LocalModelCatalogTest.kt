package com.hereliesaz.ideaz.ai.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression guard for the storage-preflight bug fixed this session:
 * `ModelDownloadManager.preflightStorage` only trusts the manifest-sum size
 * total once every downloadable file for a model has `expectedSizeBytes` -
 * otherwise it silently falls back to `approxSizeBytes`. If `approxSizeBytes`
 * only reflects the primary file (as it did for `phi3_5-mini-onnx`, whose real
 * download is ~53x larger once its `.onnx.data` and config/tokenizer siblings
 * are counted), a device can pass the preflight check and then fill its disk
 * mid-download. Asserting `approxSizeBytes` matches the real per-file total
 * whenever every file's `expectedSizeBytes` is known catches that class of bug
 * without needing to name any specific model entry.
 */
class LocalModelCatalogTest {

    @Test
    fun `approxSizeBytes matches the true per-file total wherever every file's size is known`() {
        for (model in LocalModelCatalog.models) {
            if (model.systemManaged) continue
            val files = listOf(model.expectedSizeBytes) + model.additionalFiles.map { it.expectedSizeBytes }
            if (files.any { it == null }) continue // fallback to approxSizeBytes is legitimate here
            val trueTotal = files.filterNotNull().sum()
            assertEquals(
                "${model.id}: approxSizeBytes must equal the sum of every file's expectedSizeBytes " +
                    "once all are known, or preflightStorage's manifest-sum path silently disagrees " +
                    "with the UI's progress-bar denominator (both read approxSizeBytes as the fallback/display total)",
                trueTotal,
                model.approxSizeBytes,
            )
        }
    }

    @Test
    fun `every downloadable model file has an expectedSizeBytes`() {
        val missing = LocalModelCatalog.models
            .filterNot { it.systemManaged }
            .flatMap { model ->
                val files = listOf(model.fileName to model.expectedSizeBytes) +
                    model.additionalFiles.map { it.fileName to it.expectedSizeBytes }
                files.filter { (_, size) -> size == null }.map { (name, _) -> "${model.id}/$name" }
            }
        assertEquals("Missing expectedSizeBytes for: $missing", emptyList<String>(), missing)
    }
}
