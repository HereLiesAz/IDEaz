package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.impl.DefaultServiceLocator
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.transfer.AbstractTransferListener
import org.eclipse.aether.transfer.TransferEvent
import java.io.File

class HttpDependencyResolver(
    private val projectDir: File,
    private val dependenciesFile: File,
    private val localRepoDir: File,
    private val callback: IBuildCallback?
) : BuildStep {

    val resolvedArtifacts = mutableListOf<File>()

    val resolvedClasspath: String
        get() = resolvedArtifacts
            .filter { it.extension.equals("jar", ignoreCase = true) }
            .joinToString(File.pathSeparator) { it.absolutePath }

    companion object {
        private const val TAG = "HttpDependencyResolver"
    }

    override fun execute(callback: IBuildCallback?): BuildResult {
        callback?.onLog("[IDE] Starting dependency resolution...")
        try {
            if (!dependenciesFile.exists()) {
                callback?.onLog("[IDE] No dependencies file found. Skipping.")
                return BuildResult(true, "No dependencies file found.")
            }

            val system = newRepositorySystem()
            val session = newRepositorySystemSession(system, localRepoDir)
            val remoteRepositories = listOf(
                RemoteRepository.Builder("google", "default", "https://maven.google.com/").build(),
                RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build(),
                RemoteRepository.Builder("jitpack", "default", "https://jitpack.io").build()
            )
            callback?.onLog("[IDE] Repositories: ${remoteRepositories.joinToString { it.url }}")


            val initialDependencies = parseDependencies(dependenciesFile.readText())
            if (initialDependencies.isEmpty()) {
                callback?.onLog("[IDE] No dependencies to resolve.")
                return BuildResult(true, "No dependencies to resolve.")
            }

            callback?.onLog("[IDE] Resolving: ${initialDependencies.joinToString { it.artifact.toString() }}")

            val collectRequest = CollectRequest()
            collectRequest.repositories = remoteRepositories
            initialDependencies.forEach { collectRequest.addDependency(it) }

            // Using resolveDependencies with a DependencyRequest to get the full graph including .aar files
            val dependencyRequest = org.eclipse.aether.resolution.DependencyRequest(collectRequest, null)
            val dependencyResult = system.resolveDependencies(session, dependencyRequest)

            dependencyResult.artifactResults.forEach { artifactResult ->
                artifactResult.artifact?.file?.let {
                    resolvedArtifacts.add(it)
                    callback?.onLog("[IDE] Resolved artifact: ${it.absolutePath}")
                }
            }

            callback?.onLog("[IDE] Dependency resolution finished successfully.")
            return BuildResult(true, "Dependencies resolved successfully.")

        } catch (e: Exception) {
            e.printStackTrace()
            callback?.onLog("[IDE] ERROR: Dependency resolution failed: ${e.message}")
            return BuildResult(false, e.stackTraceToString())
        }
    }

    private fun newRepositorySystem(): RepositorySystem {
        val locator = DefaultServiceLocator()
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(TransporterFactory::class.java, FileTransporterFactory::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        locator.setErrorHandler(object : DefaultServiceLocator.ErrorHandler() {
            override fun serviceCreationFailed(type: Class<*>, impl: Class<*>, e: Throwable) {
                e.printStackTrace()
            }
        })
        return locator.getService(RepositorySystem::class.java)
    }

    private fun newRepositorySystemSession(system: RepositorySystem, localRepoPath: File): DefaultRepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession()
        val localRepo = LocalRepository(localRepoPath)
        session.localRepositoryManager = system.newLocalRepositoryManager(session, localRepo)
        session.transferListener = object : AbstractTransferListener() {
            override fun transferSucceeded(event: TransferEvent) {
                callback?.onLog("[IDE] Downloaded: ${event.resource.repositoryUrl}${event.resource.resourceName}")
            }
             override fun transferFailed(event: TransferEvent?) {
                callback?.onLog("[IDE] FAILED Download: ${event?.resource?.repositoryUrl}${event?.resource?.resourceName} - ${event?.exception?.message}")
             }
        }
        return session
    }

    private fun parseDependencies(content: String): List<Dependency> {
        return content.lines()
            .map { it.trim().substringBefore("//").trim() } // Allow comments
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull {
                try {
                    // Support both JAR and AAR packaging explicitly
                    val artifact = DefaultArtifact(it)
                    Dependency(artifact, "compile")
                } catch (e: IllegalArgumentException) {
                    callback?.onLog("[IDE] WARNING: Skipping invalid dependency line: '$it'")
                    null
                }
            }
    }
}
