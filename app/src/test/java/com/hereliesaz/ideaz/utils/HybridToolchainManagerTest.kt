package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.IBuildCallback
import org.junit.Test
import org.junit.Ignore
import java.io.File
import java.nio.file.Files

class HybridToolchainManagerTest {
    @Test
    @Ignore("Fix NoSuchMethodError in test environment (AIDL/Stub issue)")
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
            HybridToolchainManager.downloadToolchain(tempDir, callback)
        } catch (e: Exception) {
            println("Caught expected exception in test environment: ${e.message}")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
