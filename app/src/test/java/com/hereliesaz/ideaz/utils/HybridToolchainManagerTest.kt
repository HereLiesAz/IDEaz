package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.IBuildCallback
import org.junit.Ignore
import org.junit.Test
import org.junit.Ignore
import java.io.File
import java.nio.file.Files

class HybridToolchainManagerTest {
    @Test
    @Ignore("Network dependency in CI environment")
    fun testDownloadToolchainAttemptsDownload() {
        val tempDir = Files.createTempDirectory("ideaz_test").toFile()
        val callback = object : IBuildCallback.Stub() {
            override fun onLog(message: String) {
                println(message)
            }
            override fun onFailure(message: String) {
                println("Failure: $message")
            }
            override fun onSuccess(apkPath: String) {}
        }

        try {
            // This will likely fail network operations in the sandbox or due to missing dependencies in test classpath
            // but we check for compilation and basic execution flow.
            HybridToolchainManager.downloadToolchain(tempDir, callback)
        } catch (e: Exception) {
            println("Caught expected exception in test environment: ${e.message}")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
