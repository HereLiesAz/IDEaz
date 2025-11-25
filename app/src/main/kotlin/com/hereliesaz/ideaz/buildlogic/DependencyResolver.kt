package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
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

@Suppress("DEPRECATION")
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


    @Suppress("DEPRECATION")
    private fun newRepositorySystem(): RepositorySystem {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(TransporterFactory::class.java, FileTransporterFactory::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)

        locator.setErrorHandler(object : DefaultServiceLocator.ErrorHandler() {
            override fun serviceCreationFailed(type: Class<*>, impl: Class<*>, exception: Throwable) {
                logger.error("Failed to create Aether service implementation. type={}, impl={}", type.name, impl.name, exception)
            }
        })

        return locator.getService(RepositorySystem::class.java)
            ?: throw IllegalStateException("Failed to initialize RepositorySystem (getService returned null). Check logs for missing transitive dependencies.")
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

        val tomlFile = File(projectDir, "gradle/libs.versions.toml")
        val hasToml = tomlFile.exists()
        val hasDepFile = dependenciesFile.exists()

        if (!hasDepFile && !hasToml) {
            return BuildResult(true, "No dependencies file found (checked dependencies.toml and gradle/libs.versions.toml). Skipping resolution.")
        }

        callback?.onLog("[IDE] Resolving dependencies...")

        return try {
            val system = newRepositorySystem()
            val session = newSession(system)

            val google = RemoteRepository.Builder("google", "default", "https://maven.google.com/").build()
            val central = RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()
            val jitpack = RemoteRepository.Builder("jitpack", "default", "https://jitpack.io").build()

            val repositories = listOf(google, central, jitpack)
            val dependenciesToResolve = mutableListOf<String>()

            // 1. Process legacy dependencies.toml
            if (hasDepFile) {
                dependenciesFile.readLines().forEach { line ->
                    if (line.isNotBlank() && !line.startsWith("#")) {
                        dependenciesToResolve.add(cleanDependencyLine(line))
                    }
                }
            }

            // 2. Process libs.versions.toml
            if (hasToml) {
                callback?.onLog("[IDE] Found gradle/libs.versions.toml")
                try {
                    val tomlDeps = parseVersionCatalog(tomlFile)
                    dependenciesToResolve.addAll(tomlDeps)
                } catch (e: Exception) {
                    callback?.onLog("[IDE] Warning: Failed to parse libs.versions.toml: ${e.message}")
                }
            }

            dependenciesToResolve.forEach { depString ->
                callback?.onLog("  [IDE] - $depString")
                try {
                    val artifact = DefaultArtifact(depString)
                    val dependency = Dependency(artifact, "compile") // Use compile scope to get transitive deps

                    val collectRequest = CollectRequest()
                    collectRequest.root = dependency
                    repositories.forEach { collectRequest.addRepository(it) }

                    val dependencyRequest = DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter("runtime"))

                    val result = system.resolveDependencies(session, dependencyRequest)
                    result.artifactResults.forEach { artifactResult ->
                        artifactResult.artifact?.file?.let { resolvedArtifacts.add(it) }
                    }
                } catch (e: Exception) {
                    callback?.onLog("  [IDE] Failed to resolve $depString: ${e.message}")
                }
            }

            // Remove duplicates
            val distinctArtifacts = resolvedArtifacts.distinctBy { it.absolutePath }
            resolvedArtifacts.clear()
            resolvedArtifacts.addAll(distinctArtifacts)

            callback?.onLog("[IDE] Dependencies resolved successfully. Total artifacts: ${resolvedArtifacts.size}")
            BuildResult(true, "[IDE] Dependencies resolved successfully.")
        } catch (e: Throwable) {
            callback?.onLog("[IDE] Failed to resolve dependencies: ${e.message}")
            callback?.onLog("[IDE] Stack trace: ${e.stackTraceToString()}")
            BuildResult(false, "[IDE] Failed to resolve dependencies: ${e.message}")
        }
    }

    private fun parseVersionCatalog(tomlFile: File): List<String> {
        val versions = mutableMapOf<String, String>()
        val libraries = mutableListOf<String>()

        var currentSection = ""
        tomlFile.readLines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                if (currentSection == "[versions]") {
                    // key = "value"
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim().replace("\"", "").replace("'", "")
                        versions[key] = value
                    }
                } else if (currentSection == "[libraries]") {
                    // Parse library entry
                    parseLibraryEntry(line, versions)?.let { libraries.add(it) }
                }
            }
        }
        return libraries
    }

    private fun parseLibraryEntry(line: String, versions: Map<String, String>): String? {
        // Handle: alias = "group:artifact:version"
        if (line.contains("=") && !line.contains("{")) {
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                val coord = parts[1].trim().replace("\"", "").replace("'", "")
                return coord
            }
        }

        // Handle: alias = { ... }
        if (line.contains("=") && line.contains("{") && line.endsWith("}")) {
            val content = line.substringAfter("{").substringBefore("}").trim()
            val map = content.split(",").associate {
                val p = it.split("=", limit = 2)
                p[0].trim() to p.getOrElse(1) { "" }.trim().replace("\"", "").replace("'", "")
            }

            val group = map["group"]
            val name = map["name"]
            val module = map["module"]
            var version = map["version"]
            val versionRef = map["version.ref"]

            if (versionRef != null && versions.containsKey(versionRef)) {
                version = versions[versionRef]
            }

            if (module != null && version != null) {
                return "$module:$version"
            }
            if (group != null && name != null && version != null) {
                return "$group:$name:$version"
            }
        }
        return null
    }
}
