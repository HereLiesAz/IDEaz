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
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.filter.DependencyFilterUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

// Visible for testing
internal fun cleanDependencyLine(line: String): String {
    var cleaned = line.trim()
    cleaned = cleaned.replace("\"", "").replace("'", "")
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

    private val explodedDir = File(cacheDir, "exploded")

    // Holds the list of resource directories extracted from AARs
    val extractedResourceDirs = mutableListOf<File>()

    // Holds the list of assets directories extracted from AARs
    val extractedAssetDirs = mutableListOf<File>()

    // Holds the calculated classpath
    var resolvedClasspath: String = ""
        private set

    private fun newRepositorySystem(): RepositorySystem {
        val locator = MavenRepositorySystemUtils.newServiceLocator()

        // --- CRITICAL FIX: Register missing services manually ---
        // The default locator doesn't know about these implementations unless we tell it.
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)

        locator.setErrorHandler(object : DefaultServiceLocator.ErrorHandler() {
            override fun serviceCreationFailed(type: Class<*>?, impl: Class<*>?, exception: Throwable?) {
                System.err.println("Service creation failed for $type: ${exception?.message}")
                exception?.printStackTrace()
            }
        })
        // --------------------------------------------------------

        return locator.getService(RepositorySystem::class.java)
            ?: throw RuntimeException("Could not initialize RepositorySystem")
    }

    private fun newSession(system: RepositorySystem): org.eclipse.aether.RepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession()
        val localRepo = LocalRepository(cacheDir)
        session.localRepositoryManager = system.newLocalRepositoryManager(session, localRepo)
        return session
    }

    override fun execute(callback: IBuildCallback?): BuildResult {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        if (!explodedDir.exists()) explodedDir.mkdirs()

        val depsToResolve = mutableListOf<String>()

        // 1. Read Dependencies
        if (dependenciesFile.exists()) {
            dependenciesFile.readLines().forEach { line ->
                if (line.isNotBlank() && !line.trim().startsWith("#")) {
                    depsToResolve.add(cleanDependencyLine(line))
                }
            }
        } else {
            // If missing, create default for Android projects to prevent immediate failure
            if (File(projectDir, "src/main/AndroidManifest.xml").exists() || File(projectDir, "app/src/main/AndroidManifest.xml").exists()) {
                depsToResolve.add("com.google.android.material:material:1.11.0")
                depsToResolve.add("androidx.appcompat:appcompat:1.6.1")
                depsToResolve.add("androidx.core:core-ktx:1.12.0")
            }
        }

        if (depsToResolve.isEmpty()) {
            return BuildResult(true, "")
        }

        callback?.onLog("[IDE] Resolving dependencies...")

        return try {
            val system = newRepositorySystem()
            val session = newSession(system)

            val google = RemoteRepository.Builder("google", "default", "https://maven.google.com/").build()
            val central = RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()
            val jitpack = RemoteRepository.Builder("jitpack", "default", "https://jitpack.io").build()

            val classpathFiles = mutableListOf<File>()

            depsToResolve.forEach { depString ->
                try {
                    callback?.onLog("  - Resolving $depString")
                    val artifact = DefaultArtifact(depString)
                    val dependency = Dependency(artifact, "compile")

                    val collectRequest = CollectRequest()
                    collectRequest.root = dependency
                    collectRequest.addRepository(google)
                    collectRequest.addRepository(central)
                    collectRequest.addRepository(jitpack)

                    val dependencyRequest = DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter("compile"))
                    val result = system.resolveDependencies(session, dependencyRequest)

                    result.artifactResults.forEach { artifactResult ->
                        val file = artifactResult.artifact.file
                        if (file != null) {
                            if (file.name.endsWith(".aar")) {
                                // Extract AAR
                                val extractPath = File(explodedDir, "${artifactResult.artifact.groupId}_${artifactResult.artifact.artifactId}_${artifactResult.artifact.version}")
                                if (!extractPath.exists()) {
                                    callback?.onLog("    - Extracting AAR: ${file.name}")
                                    unzip(file, extractPath)
                                }

                                // Add classes.jar to classpath
                                val classesJar = File(extractPath, "classes.jar")
                                if (classesJar.exists()) {
                                    classpathFiles.add(classesJar)
                                }

                                // Track resources
                                val resDir = File(extractPath, "res")
                                if (resDir.exists()) {
                                    extractedResourceDirs.add(resDir)
                                }

                                // Track assets
                                val assetsDir = File(extractPath, "assets")
                                if (assetsDir.exists()) {
                                    extractedAssetDirs.add(assetsDir)
                                }

                            } else if (file.name.endsWith(".jar")) {
                                classpathFiles.add(file)
                            }
                        }
                    }
                } catch (e: Exception) {
                    callback?.onLog("    ! Failed to resolve $depString: ${e.message}")
                }
            }

            resolvedClasspath = classpathFiles.joinToString(File.pathSeparator) { it.absolutePath }

            // Return classpath as output so KotlincCompile can use it
            callback?.onLog("[IDE] Dependencies resolved. Found ${extractedResourceDirs.size} AAR resource directories.")
            BuildResult(true, resolvedClasspath)

        } catch (e: Throwable) {
            val msg = "Failed to resolve dependencies: ${e.message}"
            callback?.onLog("[IDE] $msg")
            BuildResult(false, msg)
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        targetDir.mkdirs()
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}