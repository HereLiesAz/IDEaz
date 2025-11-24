package com.hereliesaz.ideaz.buildlogic

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
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.io.File

class DependencyResolver(
    private val projectDir: File,
    private val onLog: (String) -> Unit
) : BuildStep {

    override fun execute(): Boolean {
        onLog("Starting dependency resolution...")
        try {
            val system = newRepositorySystem()
            val session = newRepositorySystemSession(system)
            val artifact = DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")
            val remoteRepository = RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()

            val collectRequest = CollectRequest()
            collectRequest.root = Dependency(artifact, "compile")
            collectRequest.addRepository(remoteRepository)

            val dependencyRequest = DependencyRequest(collectRequest, null)

            val resolveResult = system.resolveDependencies(session, dependencyRequest)

            resolveResult.artifactResults.forEach {
                onLog("Resolved dependency: ${it.artifact.file}")
            }
            onLog("Dependency resolution successful.")
            return true
        } catch (e: Exception) {
            onLog("Error resolving dependencies: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    private fun newRepositorySystem(): RepositorySystem {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(TransporterFactory::class.java, FileTransporterFactory::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)

        locator.setErrorHandler(object : DefaultServiceLocator.ErrorHandler() {
            override fun serviceCreationFailed(type: Class<*>, impl: Class<*>, exception: Throwable) {
                onLog("Service creation failed for ${type.name} with implementation ${impl.name}")
                exception.printStackTrace()
            }
        })

        return locator.getService(RepositorySystem::class.java) ?: throw IllegalStateException("RepositorySystem could not be initialized")
    }

    private fun newRepositorySystemSession(system: RepositorySystem): DefaultRepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession()
        val localRepo = LocalRepository(File(projectDir,".m2/repository"))
        session.localRepositoryManager = system.newLocalRepositoryManager(session, localRepo)
        return session
    }
}
