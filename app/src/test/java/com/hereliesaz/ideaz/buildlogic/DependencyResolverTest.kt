package com.hereliesaz.ideaz.buildlogic

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DependencyResolverTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    // @Ignore("Known issue: jcabi-aether 0.10.1 uses legacy transport that fails with TLS 1.2+ on modern JDKs/Environments.")
    fun testResolverInstantiationAndExecution() {
        val projectDir = tempFolder.newFolder("project")
        val dependenciesFile = File(projectDir, "dependencies.txt")
        // Use a small dependency. gson is what failed for the user.
        dependenciesFile.writeText("com.google.code.gson:gson:2.8.8")
        val cacheDir = tempFolder.newFolder("cache")

        val resolver = DependencyResolver(projectDir, dependenciesFile, cacheDir)

        val result = resolver.execute(null)

        println("Resolver result: ${result.output}")
        assertTrue("Resolution failed: ${result.output}", result.success)
    }
}
