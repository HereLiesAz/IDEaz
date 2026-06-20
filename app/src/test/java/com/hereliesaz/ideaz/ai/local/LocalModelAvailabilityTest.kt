package com.hereliesaz.ideaz.ai.local

import com.hereliesaz.ideaz.ai.local.LocalModelAvailability.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
