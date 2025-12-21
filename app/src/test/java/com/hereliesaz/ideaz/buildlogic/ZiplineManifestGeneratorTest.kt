package com.hereliesaz.ideaz.buildlogic

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ZiplineManifestGeneratorTest {
    @Test
    fun testGenerateManifest() {
        val tempDir = Files.createTempDirectory("manifest_test").toFile()
        val moduleFile = File(tempDir, "app.zipline")
        moduleFile.writeText("console.log('Hello');")

        val dummyKey = "00".repeat(32)
        val keys = mapOf("key1" to dummyKey)

        val json = ZiplineManifestGenerator.generateManifest(
            modules = mapOf("app" to moduleFile),
            signingKeys = keys,
            signer = { _, _ -> "deadbeef" }
        )

        println(json)
        assertTrue(json.contains("\"modules\":"))
        assertTrue(json.contains("\"url\":\"app\""))
        assertTrue(json.contains("\"signatures\":"))
        assertTrue(json.contains("\"key1\":\"deadbeef\""))

        tempDir.deleteRecursively()
    }
}
