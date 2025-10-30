package com.hereliesaz.ideaz.buildlogic

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.util.graph.selector.AndDependencySelector
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector
import java.io.File

class DependencyResolver(
    private val projectDir: File,
    private val dependenciesFile: File,
    private val cacheDir: File
) : BuildStep {

    val resolvedClasspath: String
        get() = cacheDir.listFiles { file -> file.extension == "jar" }
            ?.joinToString(File.pathSeparator) { it.absolutePath } ?: ""

    override fun execute(): BuildResult {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        if (!dependenciesFile.exists()) {
            return BuildResult(true, "No dependencies file found. Skipping resolution.")
        }

        val system = newRepositorySystem()
        val session = newRepositorySystemSession(system, cacheDir)

        val central = RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()

        val dependencies = dependenciesFile.readLines().map {
            val artifact = DefaultArtifact(it)
            Dependency(artifact, "compile")
        }

        val collectRequest = CollectRequest()
        collectRequest.root = dependencies.first()
        dependencies.drop(1).forEach { collectRequest.addDependency(it) }
        collectRequest.addRepository(central)

        val dependencyRequest = DependencyRequest(collectRequest, null)

        return try {
            system.resolveDependencies(session, dependencyRequest)
            BuildResult(true, "Dependencies resolved successfully.")
        } catch (e: Exception) {
            BuildResult(false, "Failed to resolve dependencies: ${e.message}")
        }
    }

    private fun newRepositorySystem(): RepositorySystem {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService(
            org.eclipse.aether.spi.connector.RepositoryConnectorFactory::class.java,
            org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory::class.java
        )
        locator.addService(
            org.eclipse.aether.spi.connector.transport.TransporterFactory::class.java,
            org.eclipse.aether.transport.file.FileTransporterFactory::class.java
        )
        locator.addService(
            org.eclipse.aether.spi.connector.transport.TransporterFactory::class.java,
            org.eclipse.aether.transport.http.HttpTransporterFactory::class.java
        )
        return locator.getService(RepositorySystem::class.java)
    }

    private fun newRepositorySystemSession(system: RepositorySystem, cacheDir: File): DefaultRepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession()
        val localRepo = LocalRepository(cacheDir)
        session.localRepositoryManager = system.newLocalRepositoryManager(session, localRepo)
        session.dependencySelector = AndDependencySelector(
            ScopeDependencySelector("test", "provided"),
            OptionalDependencySelector()
        )
        return session
    }
}
