package com.hereliesaz.ideaz.buildlogic

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DependencyResolverTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testResolverInstantiationAndExecution() {
        val projectDir = tempFolder.newFolder("project")
        val dependenciesFile = File(projectDir, "dependencies.txt")
        dependenciesFile.writeText("junit:junit:4.12")
        val cacheDir = tempFolder.newFolder("cache")

        val resolver = DependencyResolver(projectDir, dependenciesFile, cacheDir)

        // Execute with null callback.
        // Should not throw NoClassDefFoundError.
        val result = resolver.execute(null)

        assertNotNull(result)
        println("Resolver result: ${result.output}")
    }
}
