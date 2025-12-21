package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.buildlogic.HttpDependencyResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import java.io.File

object HybridToolchainManager {
    // Versions for Hybrid Host components
    // Force Recompile
    private const val REDWOOD_VERSION = "0.16.0"
    private const val ZIPLINE_VERSION = "1.17.0"
    private const val KOTLIN_VERSION = "2.0.21"

    private fun getResolver(filesDir: File, callback: IBuildCallback?): HttpDependencyResolver {
        val toolsDir = File(filesDir, "tools/hybrid")
        toolsDir.mkdirs()
        val localRepoDir = File(filesDir, "local-repo")
        return HttpDependencyResolver(toolsDir, null, localRepoDir, callback)
    }

    private fun resolve(filesDir: File, coords: String, callback: IBuildCallback?): List<File> {
        val resolver = getResolver(filesDir, callback)
        return resolver.resolveList(listOf(Dependency(DefaultArtifact(coords), "compile")))
    }

    fun downloadToolchain(filesDir: File, callback: IBuildCallback) {
        callback.onLog("Downloading Hybrid Toolchain artifacts...")
        try {
            getCodegenClasspath(filesDir, callback)
            getZiplineCompilerPluginClasspath(filesDir, callback)
            getHostRuntimeClasspath(filesDir, callback)
            getGuestRuntimeClasspath(filesDir, callback)
            callback.onLog("Hybrid Toolchain downloaded successfully.")
        } catch (e: Exception) {
            callback.onFailure("Failed to download Hybrid Toolchain: ${e.message}")
        }
    }

    fun getCodegenClasspath(filesDir: File, callback: IBuildCallback? = null): List<File> {
        return resolve(filesDir, "app.cash.redwood:redwood-tooling-codegen:$REDWOOD_VERSION", callback)
    }

    fun getZiplineCompilerPluginClasspath(filesDir: File, callback: IBuildCallback? = null): List<File> {
        return resolve(filesDir, "app.cash.zipline:zipline-kotlin-plugin-embeddable:$ZIPLINE_VERSION", callback)
    }

    fun getHostRuntimeClasspath(filesDir: File, callback: IBuildCallback? = null): List<File> {
        val resolver = getResolver(filesDir, callback)
        val deps = listOf(
            Dependency(DefaultArtifact("app.cash.redwood:redwood-runtime:$REDWOOD_VERSION"), "compile"),
            Dependency(DefaultArtifact("app.cash.zipline:zipline:$ZIPLINE_VERSION"), "compile"),
            Dependency(DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib:$KOTLIN_VERSION"), "compile")
        )
        return resolver.resolveList(deps)
    }

    fun getGuestRuntimeClasspath(filesDir: File, callback: IBuildCallback? = null): List<File> {
        val resolver = getResolver(filesDir, callback)
        val deps = listOf(
            Dependency(DefaultArtifact("app.cash.redwood:redwood-compose:$REDWOOD_VERSION"), "compile"),
            Dependency(DefaultArtifact("app.cash.zipline:zipline:$ZIPLINE_VERSION"), "compile"),
            Dependency(DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib:$KOTLIN_VERSION"), "compile")
        )
        return resolver.resolveList(deps)
    }
}
