package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.buildlogic.HttpDependencyResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import java.io.File

object HybridToolchainManager {
    // Versions for Hybrid Host components
    private const val REDWOOD_VERSION = "0.16.0"
    private const val ZIPLINE_VERSION = "1.17.0"
    // Kotlin version must match the embedded compiler (see libs.versions.toml)
    private const val KOTLIN_VERSION = "2.2.21"

    fun downloadToolchain(filesDir: File, callback: IBuildCallback) {
        val toolsDir = File(filesDir, "tools/hybrid")
        if (!toolsDir.exists()) {
            if (!toolsDir.mkdirs()) {
                callback.onFailure("Failed to create toolchain directory: ${toolsDir.absolutePath}")
                return
            }
        }

        // Use the common local repo for caching artifacts
        val localRepoDir = File(filesDir, "local-repo")

        val dependencies = listOf(
            // Codegen Tool
            Dependency(DefaultArtifact("app.cash.redwood:redwood-tooling-codegen:$REDWOOD_VERSION"), "compile"),
            // Zipline Compiler Plugin
            Dependency(DefaultArtifact("app.cash.zipline:zipline-kotlin-plugin-embeddable:$ZIPLINE_VERSION"), "compile"),
            // Runtime Libraries (Host & Guest)
            Dependency(DefaultArtifact("app.cash.redwood:redwood-runtime:$REDWOOD_VERSION"), "compile"),
            Dependency(DefaultArtifact("app.cash.zipline:zipline:$ZIPLINE_VERSION"), "compile"),
            Dependency(DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib:$KOTLIN_VERSION"), "compile")
        )

        callback.onLog("Downloading Hybrid Toolchain artifacts...")

        try {
            // Instantiate resolver with null dependenciesFile since we use explicit list
            val resolver = HttpDependencyResolver(toolsDir, null, localRepoDir, callback)
            val files = resolver.resolveList(dependencies)

            callback.onLog("Hybrid Toolchain downloaded successfully: ${files.size} artifacts.")

        } catch (e: Exception) {
            callback.onFailure("Failed to download Hybrid Toolchain: ${e.message}")
        }
    }
}
