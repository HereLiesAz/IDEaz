package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.util.artifact.DefaultArtifact
import org.sonatype.aether.impl.internal.DefaultServiceLocator
import org.apache.maven.repository.internal.MavenServiceLocator
import org.sonatype.aether.RepositorySystem
import org.sonatype.aether.connector.wagon.WagonProvider
import org.sonatype.aether.connector.wagon.WagonRepositoryConnectorFactory
import org.sonatype.aether.connector.wagon.WagonConfigurator
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory
import org.sonatype.aether.spi.io.FileProcessor
import org.sonatype.aether.impl.internal.DefaultFileProcessor
import org.apache.maven.wagon.Wagon
import org.apache.maven.wagon.providers.http.LightweightHttpWagon
import org.apache.maven.repository.internal.MavenRepositorySystemSession
import org.sonatype.aether.repository.LocalRepository
import org.sonatype.aether.graph.Dependency
import org.sonatype.aether.resolution.DependencyRequest
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
        get() = cacheDir.walkTopDown()
            .filter { it.isFile && it.extension == "jar" }
            .joinToString(File.pathSeparator) { it.absolutePath }

    class ManualWagonProvider : WagonProvider {
        override fun lookup(roleHint: String): Wagon? {
            if (roleHint == "http" || roleHint == "https") {
                return LightweightHttpWagon()
            }
            return null
        }

        override fun release(wagon: Wagon) { }
    }

    class NoOpWagonConfigurator : WagonConfigurator {
        override fun configure(wagon: Wagon, configuration: Any?) { }
    }

    override fun execute(callback: IBuildCallback?): BuildResult {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        if (!dependenciesFile.exists()) {
            return BuildResult(true, "No dependencies file found. Skipping resolution.")
        }

        callback?.onLog("[IDE] Resolving dependencies...")

        return try {
            val locator = MavenServiceLocator()
            locator.addService(RepositoryConnectorFactory::class.java, WagonRepositoryConnectorFactory::class.java)
            locator.setService(WagonProvider::class.java, ManualWagonProvider::class.java)
            locator.setService(WagonConfigurator::class.java, NoOpWagonConfigurator::class.java)
            locator.addService(FileProcessor::class.java, DefaultFileProcessor::class.java)

            val system = locator.getService(RepositorySystem::class.java)

            val localRepo = LocalRepository(cacheDir)
            val session = MavenRepositorySystemSession()
            session.localRepositoryManager = system.newLocalRepositoryManager(localRepo)

            val google = RemoteRepository("google", "default", "https://maven.google.com/")
            val central = RemoteRepository("central", "default", "https://repo.maven.apache.org/maven2/")
            val jitpack = RemoteRepository("jitpack", "default", "https://jitpack.io")

            dependenciesFile.readLines().forEach { line ->
                if (line.isNotBlank()) {
                    val cleanedLine = cleanDependencyLine(line)
                    callback?.onLog("  [IDE] - $cleanedLine")

                    val artifact = DefaultArtifact(cleanedLine)
                    val dependency = Dependency(artifact, "runtime")
                    val collectRequest = org.sonatype.aether.collection.CollectRequest(dependency, listOf(google, central, jitpack))
                    val dependencyRequest = DependencyRequest(collectRequest, null)

                    system.resolveDependencies(session, dependencyRequest)
                }
            }

            callback?.onLog("[IDE] Dependencies resolved successfully.")
            BuildResult(true, "[IDE] Dependencies resolved successfully.")
        } catch (e: Throwable) {
            callback?.onLog("[IDE] Failed to resolve dependencies: ${e.message}")
            callback?.onLog("[IDE] Stack trace: ${e.stackTraceToString()}")
            BuildResult(false, "[IDE] Failed to resolve dependencies: ${e.message}")
        }
    }
}
