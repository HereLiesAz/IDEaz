package com.hereliesaz.ideaz.ai.local

import com.hereliesaz.ideaz.ai.local.LocalModelAvailability.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class LocalModelAvailabilityTest {

    private val arm64 = setOf("arm64-v8a", "armeabi-v7a")

    private fun gguf(
        minRam: Long = 2_000_000_000,
        abi: String? = "arm64-v8a",
        auth: Boolean = false,
    ) = LocalModel(
        id = "m", name = "M", runtimeId = "llamacpp", url = "u",
        approxSizeBytes = 1, fileName = "m.gguf",
        requiresAuth = auth, minRamBytes = minRam, requiredAbi = abi,
    )

    private fun aicore() = LocalModel(
        id = "aicore", name = "Nano", runtimeId = "aicore", url = "",
        approxSizeBytes = 0, fileName = "", systemManaged = true,
    )

    @Test
    fun `usable when backend present and device meets needs`() {
        val s = LocalModelAvailability.evaluate(gguf(), true, "llama.cpp", 8_000_000_000, arm64, false)
        assertEquals(Status.Usable, s)
    }

    @Test
    fun `backend missing is unsupported with build reason`() {
        val s = LocalModelAvailability.evaluate(gguf(), false, "llama.cpp", 8_000_000_000, arm64, false)
        assertTrue(s is Status.Unsupported && "build" in s.reason)
    }

    @Test
    fun `system-managed backend missing says not supported on device`() {
        val s = LocalModelAvailability.evaluate(aicore(), false, "AICore", 8_000_000_000, arm64, false)
        assertTrue(s is Status.Unsupported && "device" in s.reason)
    }

    @Test
    fun `wrong abi is unsupported`() {
        val s = LocalModelAvailability.evaluate(gguf(abi = "arm64-v8a"), true, "llama.cpp", 8_000_000_000, setOf("x86_64"), false)
        assertTrue(s is Status.Unsupported && "arm64-v8a" in s.reason)
    }

    @Test
    fun `insufficient ram is unsupported`() {
        val s = LocalModelAvailability.evaluate(gguf(minRam = 6_000_000_000), true, "llama.cpp", 3_000_000_000, arm64, false)
        assertTrue(s is Status.Unsupported && "RAM" in s.reason)
    }

    @Test
    fun `gated model without token is unsupported`() {
        val s = LocalModelAvailability.evaluate(gguf(auth = true), true, "llama.cpp", 8_000_000_000, arm64, false)
        assertTrue(s is Status.Unsupported && "token" in s.reason)
    }

    @Test
    fun `gated model with token is usable`() {
        val s = LocalModelAvailability.evaluate(gguf(auth = true), true, "llama.cpp", 8_000_000_000, arm64, true)
        assertEquals(Status.Usable, s)
    }

    @Test
    fun `unknown ram (zero) skips the ram check`() {
        val s = LocalModelAvailability.evaluate(gguf(minRam = 6_000_000_000), true, "llama.cpp", 0, arm64, false)
        assertEquals(Status.Usable, s)
    }

    @Test
    fun `unknown abis (empty) skips the abi check`() {
        val s = LocalModelAvailability.evaluate(gguf(abi = "arm64-v8a"), true, "llama.cpp", 8_000_000_000, emptySet(), false)
        assertEquals(Status.Usable, s)
    }

    @Test
    fun `download verifier accepts independently hashed payload`() {
        val payload = "verified model bytes".toByteArray()
        val file = Files.createTempFile("ideaz-model", ".bin").toFile().apply {
            writeBytes(payload)
            deleteOnExit()
        }
        // Calculated independently with: printf 'verified model bytes' | sha256sum
        val expectedHash = "03cfa25d83f5eaa1faac98ed6ceaaf0e7afe3c273a1e1502c2714ebe10b8263e"

        verifyDownloadedFile(
            file,
            LocalModelFile(
                url = "https://example.invalid/model",
                fileName = file.name,
                expectedSizeBytes = payload.size.toLong(),
                sha256 = expectedHash,
            ),
        )
    }

    @Test
    fun `download verifier rejects wrong digest and removes no evidence`() {
        val file = Files.createTempFile("ideaz-model", ".bin").toFile().apply {
            writeText("payload supplied by test")
            deleteOnExit()
        }
        val wrongHash = "00".repeat(32)

        val error = assertThrows(IOException::class.java) {
            verifyDownloadedFile(
                file,
                LocalModelFile(
                    url = "https://example.invalid/model",
                    fileName = file.name,
                    expectedSizeBytes = file.length(),
                    sha256 = wrongHash,
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("SHA-256 mismatch"))
        assertTrue(file.exists())
    }

    @Test
    fun `download storage preflight accepts payload plus reserve`() {
        requireDownloadStorage(
            availableBytes = 1_024L,
            remainingDownloadBytes = 700L,
            reserveBytes = 300L,
        )
    }

    @Test
    fun `download storage preflight rejects one byte short`() {
        val error = assertThrows(IOException::class.java) {
            requireDownloadStorage(
                availableBytes = 999L,
                remainingDownloadBytes = 700L,
                reserveBytes = 300L,
            )
        }

        assertTrue(error.message.orEmpty().contains("need 1000 bytes"))
        assertTrue(error.message.orEmpty().contains("999 bytes available"))
    }

    @Test
    fun `download worker retry policy separates transient and permanent failures`() {
        assertTrue(isRetryableModelDownloadFailure("timeout while reading response"))
        assertTrue(isRetryableModelDownloadFailure("Download failed for model: HTTP 503"))
        assertFalse(isRetryableModelDownloadFailure("Download failed for model: HTTP 404"))
        assertFalse(isRetryableModelDownloadFailure("SHA-256 mismatch for model.task"))
        assertFalse(isRetryableModelDownloadFailure("Not enough storage for model download"))
    }

    @Test
    fun `download worker uses stable unique name per model`() {
        assertEquals(
            "local-model-download:gemma-test",
            LocalModelDownloadWorker.uniqueWorkName("gemma-test"),
        )
    }

    @Test
    fun `partial download survives only with matching catalog identity`() {
        val directory = Files.createTempDirectory("ideaz-partial").toFile().apply { deleteOnExit() }
        val part = directory.resolve("model.gguf.part").apply { writeText("partial bytes") }
        val metadata = directory.resolve("model.gguf.part.meta")
        val spec = LocalModelFile(
            url = "https://models.example/v1/model.gguf",
            fileName = "model.gguf",
            expectedSizeBytes = 1_000L,
            sha256 = "ab".repeat(32),
        )
        // Independently calculated with Python hashlib over the NUL-delimited manifest fields.
        metadata.writeText("8fd402aac9c33d82d0b3ca18cb35987cacf5bb4c46113da98f809e1feefd20eb")

        assertEquals(part.length(), reconcilePartialDownload(part, metadata, spec))
        assertTrue(part.exists())
    }

    @Test
    fun `catalog change deletes stale partial download`() {
        val directory = Files.createTempDirectory("ideaz-stale-partial").toFile().apply { deleteOnExit() }
        val part = directory.resolve("model.gguf.part").apply { writeText("old model bytes") }
        val metadata = directory.resolve("model.gguf.part.meta")
        val oldSpec = LocalModelFile("https://models.example/v1/model.gguf", "model.gguf")
        val newSpec = oldSpec.copy(url = "https://models.example/v2/model.gguf")
        // Independently calculated with Python hashlib over the NUL-delimited old manifest fields.
        metadata.writeText("731ff952ebb5253b3f0f8e92ee89840845ad3e183e3b379b9c739379e93bd2aa")

        assertEquals(0L, reconcilePartialDownload(part, metadata, newSpec))
        assertFalse(part.exists())
        assertFalse(metadata.exists())
    }

    @Test
    fun `oversized partial download is discarded`() {
        val directory = Files.createTempDirectory("ideaz-oversized-partial").toFile().apply { deleteOnExit() }
        val part = directory.resolve("model.gguf.part").apply { writeText("too many bytes") }
        val metadata = directory.resolve("model.gguf.part.meta")
        val spec = LocalModelFile(
            url = "https://models.example/v1/model.gguf",
            fileName = "model.gguf",
            expectedSizeBytes = 4L,
        )
        metadata.writeText("00".repeat(32))

        assertEquals(0L, reconcilePartialDownload(part, metadata, spec))
        assertFalse(part.exists())
        assertFalse(metadata.exists())
    }

    @Test
    fun `content range validation rejects wrong offsets and totals`() {
        assertTrue(contentRangeMatches("bytes 400-999/1000", 400L, 1_000L))
        assertFalse(contentRangeMatches("bytes 0-999/1000", 400L, 1_000L))
        assertFalse(contentRangeMatches("bytes 400-999/1200", 400L, 1_000L))
        assertFalse(contentRangeMatches("bytes */1000", 400L, 1_000L))
    }
}
