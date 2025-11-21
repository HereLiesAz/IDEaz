package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.jcabi.aether.Aether
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.util.artifact.DefaultArtifact
import java.io.File

// Visible for testing
internal fun cleanDependencyLine(line: String): String {
    var cleaned = line.trim()
    // Remove quotes
    cleaned = cleaned.replace("\"", "").replace("'", "")

    // Handle "group:artifact" = "version" or "group:artifact = version"
    if (cleaned.contains("=")) {
        val parts = cleaned.split("=")
        if (parts.size == 2) {
            return "${parts[0].trim()}:${parts[1].trim()}"
        }
    }
    return cleaned
}

class DependencyResolver(
    private val projectDir: File,
    private val dependenciesFile: File,
    private val cacheDir: File
) : BuildStep {

    val resolvedClasspath: String
        get() = cacheDir.listFiles { file -> file.extension == "jar" }
            ?.joinToString(File.pathSeparator) { it.absolutePath } ?: ""

    override fun execute(callback: IBuildCallback?): BuildResult {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        if (!dependenciesFile.exists()) {
            return BuildResult(true, "No dependencies file found. Skipping resolution.")
        }

        callback?.onLog("Resolving dependencies...")

        return try {
            val google = RemoteRepository("google", "default", "https://maven.google.com/")
            val central = RemoteRepository("central", "default", "https://repo.maven.apache.org/maven2/")
            val jitpack = RemoteRepository("jitpack", "default", "https://jitpack.io")
            val aether = Aether(listOf(google, central, jitpack), cacheDir)

            dependenciesFile.readLines().forEach { line ->
                if (line.isNotBlank()) {
                    val cleanedLine = cleanDependencyLine(line)
                    callback?.onLog("  - $cleanedLine")
                    val artifact = DefaultArtifact(cleanedLine)
                    aether.resolve(artifact, "runtime")
                }
            }

            callback?.onLog("Dependencies resolved successfully.")
            BuildResult(true, "Dependencies resolved successfully.")
        } catch (e: Exception) {
            callback?.onLog("Failed to resolve dependencies: ${e.message}")
            BuildResult(false, "Failed to resolve dependencies: ${e.message}")
        }
    }
}
