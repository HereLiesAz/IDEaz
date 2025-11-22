package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.impl.DefaultServiceLocator
import org.eclipse.aether.internal.impl.DefaultRepositorySystem
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.filter.DependencyFilterUtils
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(DependencyResolver::class.java)

    val resolvedArtifacts = mutableListOf<File>()

    val resolvedClasspath: String
        get() = cacheDir.walkTopDown()
            .filter { it.isFile && it.extension == "jar" }
            .joinToString(File.pathSeparator) { it.absolutePath }


    private fun newRepositorySystem(): RepositorySystem {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        locator.addService(RepositorySystem::class.java, DefaultRepositorySystem::class.java)
        locator.setErrorHandler(object : DefaultServiceLocator.ErrorHandler() {
            override fun serviceCreationFailed(type: Class<*>, impl: Class<*>, exception: Throwable) {
                logger.error("Failed to create Aether service implementation. type={}, impl={}", type.name, impl.name, exception)
            }
        })
        return locator.getService(RepositorySystem::class.java)
    }

    private fun newSession(system: RepositorySystem): org.eclipse.aether.RepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession()
        val localRepo = LocalRepository(cacheDir)
        session.localRepositoryManager = system.newLocalRepositoryManager(session, localRepo)
        return session
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
            val system = newRepositorySystem()
            val session = newSession(system)

            val google = RemoteRepository.Builder("google", "default", "https://maven.google.com/").build()
            val central = RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()
            val jitpack = RemoteRepository.Builder("jitpack", "default", "https://jitpack.io").build()

            dependenciesFile.readLines().forEach { line ->
                if (line.isNotBlank()) {
                    val cleanedLine = cleanDependencyLine(line)
                    callback?.onLog("  [IDE] - $cleanedLine")

                    val artifact = DefaultArtifact(cleanedLine)
                    val dependency = Dependency(artifact, "runtime")

                    val collectRequest = CollectRequest()
                    collectRequest.root = dependency
                    collectRequest.addRepository(google)
                    collectRequest.addRepository(central)
                    collectRequest.addRepository(jitpack)

                    val dependencyRequest = DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter("runtime"))

                    val result = system.resolveDependencies(session, dependencyRequest)
                    result.artifactResults.forEach { artifactResult ->
                        artifactResult.artifact?.file?.let { resolvedArtifacts.add(it) }
                    }
                }
            }

            // Remove duplicates
            val distinctArtifacts = resolvedArtifacts.distinctBy { it.absolutePath }
            resolvedArtifacts.clear()
            resolvedArtifacts.addAll(distinctArtifacts)

            callback?.onLog("[IDE] Dependencies resolved successfully.")
            BuildResult(true, "[IDE] Dependencies resolved successfully.")
        } catch (e: Throwable) {
            callback?.onLog("[IDE] Failed to resolve dependencies: ${e.message}")
            callback?.onLog("[IDE] Stack trace: ${e.stackTraceToString()}")
            BuildResult(false, "[IDE] Failed to resolve dependencies: ${e.message}")
        }
    }
}
