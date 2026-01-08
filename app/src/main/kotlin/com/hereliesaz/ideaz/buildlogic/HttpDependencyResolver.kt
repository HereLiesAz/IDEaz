package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.collection.CollectResult
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.supplier.RepositorySystemSupplier
import org.eclipse.aether.transfer.AbstractTransferListener
import org.eclipse.aether.transfer.TransferEvent
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class HttpDependencyResolver(
    private val projectDir: File,
    private val dependenciesFile: File?,
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

        private val QUOTE_REGEX = "[\"']([^\"']+)[\"']".toRegex()

        fun parseDependencies(content: String, callback: IBuildCallback? = null): List<Dependency> {
            val dependencies = mutableListOf<Dependency>()

            // 1. Maven XML Parsing (Extract blocks, then parse with XmlPullParser)
            var startIndex = 0
            while (true) {
                val depStart = content.indexOf("<dependency>", startIndex)
                if (depStart == -1) break
                val depEnd = content.indexOf("</dependency>", depStart)
                if (depEnd == -1) break

                val block = content.substring(depStart, depEnd + 13) // Include </dependency>
                startIndex = depEnd + 13

                try {
                    val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                    factory.isNamespaceAware = false
                    val parser = factory.newPullParser()
                    parser.setInput(java.io.StringReader(block))

                    var eventType = parser.eventType
                    var currentTag = ""

                    var groupId: String? = null
                    var artifactId: String? = null
                    var version: String? = null
                    var type: String? = null

                    var exclusionGroupId: String? = null
                    var exclusionArtifactId: String? = null
                    val exclusions = mutableListOf<org.eclipse.aether.graph.Exclusion>()

                    var insideExclusion = false

                    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            org.xmlpull.v1.XmlPullParser.START_TAG -> {
                                currentTag = parser.name
                                if (currentTag == "exclusion") {
                                    insideExclusion = true
                                    exclusionGroupId = null
                                    exclusionArtifactId = null
                                }
                            }
                            org.xmlpull.v1.XmlPullParser.TEXT -> {
                                val text = parser.text?.trim()
                                if (!text.isNullOrEmpty()) {
                                    if (insideExclusion) {
                                         when (currentTag) {
                                            "groupId" -> exclusionGroupId = text
                                            "artifactId" -> exclusionArtifactId = text
                                        }
                                    } else {
                                        when (currentTag) {
                                            "groupId" -> groupId = text
                                            "artifactId" -> artifactId = text
                                            "version" -> version = text
                                            "type", "packaging" -> type = text
                                        }
                                    }
                                }
                            }
                            org.xmlpull.v1.XmlPullParser.END_TAG -> {
                                if (parser.name == "exclusion") {
                                    if (exclusionGroupId != null && exclusionArtifactId != null) {
                                        exclusions.add(org.eclipse.aether.graph.Exclusion(exclusionGroupId, exclusionArtifactId, "*", "*"))
                                    }
                                    insideExclusion = false
                                }
                                currentTag = ""
                            }
                        }
                        eventType = parser.next()
                    }

                    if (groupId != null && artifactId != null && version != null) {
                        val coords = if (type != null) "$groupId:$artifactId:$type:$version" else "$groupId:$artifactId:$version"
                        try {
                            dependencies.add(Dependency(DefaultArtifact(coords), "compile", false, exclusions))
                        } catch (e: IllegalArgumentException) {
                            callback?.onLog("[IDE] WARNING: Invalid dependency coordinates: $coords")
                        }
                    }

                } catch (e: Exception) {
                    callback?.onLog("[IDE] XML Block Parsing error: ${e.message}")
                    if (callback == null) e.printStackTrace() // Debug for tests
                }
            }

            // 2. Line-based parsing (TOML, Gradle, Raw)
            content.lineSequence().forEach { rawLine ->
                val line = rawLine.trim().substringBefore("//").trim()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("<")) return@forEach

                try {
                    var coords: String? = null

                    if (line.contains("=")) {
                        // TOML: "group:artifact" = "version"
                        val parts = line.split("=")
                        if (parts.size == 2) {
                            val key = parts[0].trim().replace("\"", "").replace("'", "")
                            val value = parts[1].trim().replace("\"", "").replace("'", "")
                            coords = "$key:$value"
                        }
                    } else {
                        // Gradle / Raw: Check for quotes first
                        val match = QUOTE_REGEX.find(line)
                        if (match != null) {
                            val candidate = match.groupValues[1]
                            if (candidate.contains(":")) {
                                 coords = candidate
                            }
                        } else if (line.contains(":")) {
                            // Raw: g:a:v
                            coords = line
                        }
                    }

                    if (coords != null) {
                        dependencies.add(Dependency(DefaultArtifact(coords), "compile"))
                    }
                } catch (e: Exception) {
                    callback?.onLog("[IDE] WARNING: Skipping invalid dependency line: '$line'")
                }
            }

            return dependencies.distinctBy { it.artifact.toString() }
        }

        fun parseVersionCatalog(content: String, callback: IBuildCallback? = null): List<Dependency> {
            val versions = mutableMapOf<String, String>()
            val dependencies = mutableListOf<Dependency>()
            val libraryLines = mutableListOf<String>()
            var currentSection = ""

            try {
                content.lineSequence().forEach { rawLine ->
                    val line = rawLine.substringBefore("#").trim()
                    if (line.isEmpty()) return@forEach

                    if (line.startsWith("[") && line.endsWith("]")) {
                        currentSection = line.removeSurrounding("[", "]").trim()
                        return@forEach
                    }

                    if (currentSection == "versions") {
                        if (line.contains("=")) {
                            val parts = line.split("=", limit = 2)
                            if (parts.size >= 2) {
                                val key = parts[0].trim()
                                val value = parts[1].trim().replace("\"", "").replace("'", "")
                                versions[key] = value
                            }
                        }
                    } else if (currentSection == "libraries") {
                        libraryLines.add(line)
                    }
                }

                // Process libraries after collecting versions (handles out-of-order sections)
                libraryLines.forEach { line ->
                    if (line.contains("=")) {
                        val parts = line.split("=", limit = 2)
                        if (parts.size >= 2) {
                            val value = parts[1].trim()
                            var coords: String? = null

                            if (value.startsWith("{")) {
                                // { module = "group:name", version.ref = "ver" }
                                val moduleMatch = Regex("""module\s*=\s*["']([^"']+)["']""").find(value)
                                val versionRefMatch = Regex("""version\.ref\s*=\s*["']([^"']+)["']""").find(value)
                                val versionMatch = Regex("""version\s*=\s*["']([^"']+)["']""").find(value)

                                if (moduleMatch != null) {
                                    val module = moduleMatch.groupValues[1]
                                    var version = "?"
                                    if (versionRefMatch != null) {
                                        val ref = versionRefMatch.groupValues[1]
                                        version = versions[ref] ?: "latest.release"
                                    } else if (versionMatch != null) {
                                        version = versionMatch.groupValues[1]
                                    }
                                    coords = "$module:$version"
                                } else {
                                    // Maybe group/name format?
                                    val groupMatch = Regex("""group\s*=\s*["']([^"']+)["']""").find(value)
                                    val nameMatch = Regex("""name\s*=\s*["']([^"']+)["']""").find(value)
                                    if (groupMatch != null && nameMatch != null) {
                                        val group = groupMatch.groupValues[1]
                                        val name = nameMatch.groupValues[1]
                                        var version = "?"
                                        if (versionRefMatch != null) {
                                            val ref = versionRefMatch.groupValues[1]
                                            version = versions[ref] ?: "latest.release"
                                        } else if (versionMatch != null) {
                                            version = versionMatch.groupValues[1]
                                        }
                                        coords = "$group:$name:$version"
                                    }
                                }

                            } else {
                                // alias = "group:name:version"
                                val cleanValue = value.replace("\"", "").replace("'", "")
                                if (cleanValue.contains(":")) {
                                    coords = cleanValue
                                }
                            }

                            if (coords != null) {
                                try {
                                    dependencies.add(Dependency(DefaultArtifact(coords), "compile"))
                                } catch (e: Exception) {
                                    callback?.onLog("[IDE] WARNING: Failed to parse catalog dependency: $coords")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return dependencies
        }

        fun checkForUpdate(dependency: Dependency, callback: IBuildCallback? = null): String? {
            try {
                val artifact = dependency.artifact
                val groupPath = artifact.groupId.replace('.', '/')
                val urlString = "https://repo.maven.apache.org/maven2/$groupPath/${artifact.artifactId}/maven-metadata.xml"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                if (connection.responseCode == 200) {
                    val inputStream = connection.inputStream
                    val parser = android.util.Xml.newPullParser()
                    parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(inputStream, null)

                    var eventType = parser.eventType
                    var latestVersion: String? = null
                    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "latest") {
                            parser.next()
                            latestVersion = parser.text
                            break
                        }
                        eventType = parser.next()
                    }
                    connection.disconnect()

                    if (latestVersion != null && latestVersion.isNotBlank() && latestVersion != artifact.version) {
                        return latestVersion
                    } else {
                        return null
                    }
                } else {
                    callback?.onLog("[IDE] Failed to check for updates: ${connection.responseCode}")
                    return null
                }
            } catch (e: Exception) {
                callback?.onLog("[IDE] Failed to check for updates for ${dependency.artifact}: ${e.message}")
                return null
            }
        }
    }

    fun resolveList(dependencies: List<Dependency>): List<File> {
        if (dependencies.isEmpty()) return emptyList()

        try {
            val system = newRepositorySystem()
            val session = newRepositorySystemSession(system, localRepoDir)
            val remoteRepositories = listOf(
                RemoteRepository.Builder("google", "default", "https://maven.google.com/").build(),
                RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build(),
                RemoteRepository.Builder("jitpack", "default", "https://jitpack.io").build()
            )
            callback?.onLog("[IDE] Repositories: ${remoteRepositories.joinToString { it.url }}")

            val collectRequest = CollectRequest()
            collectRequest.repositories = remoteRepositories
            dependencies.forEach { collectRequest.addDependency(it) }

            // Using resolveDependencies with a DependencyRequest to get the full graph including .aar files
            val dependencyRequest = org.eclipse.aether.resolution.DependencyRequest(collectRequest, null)
            val dependencyResult = system.resolveDependencies(session, dependencyRequest)

            val files = mutableListOf<File>()
            dependencyResult.artifactResults.forEach { artifactResult ->
                artifactResult.artifact?.file?.let {
                    files.add(it)
                    callback?.onLog("[IDE] Resolved artifact: ${it.absolutePath}")
                }
            }
            return files
        } catch (e: Exception) {
            e.printStackTrace()
            callback?.onLog("[IDE] ERROR: Dependency resolution failed: ${e.message}")
            throw e
        }
    }

    override fun execute(callback: IBuildCallback?): BuildResult {
        callback?.onLog("[IDE] Starting dependency resolution...")
        try {
            val initialDependencies = if (dependenciesFile != null && dependenciesFile.exists()) {
                val content = dependenciesFile.readText()
                if (dependenciesFile.name.endsWith(".toml") && (content.contains("[libraries]") || content.contains("[versions]"))) {
                    parseVersionCatalog(content, callback)
                } else {
                    parseDependencies(content, callback)
                }
            } else {
                callback?.onLog("[IDE] No dependencies file found.")
                return BuildResult(true, "No dependencies file found.")
            }

            if (initialDependencies.isEmpty()) {
                callback?.onLog("[IDE] No dependencies to resolve.")
                return BuildResult(true, "No dependencies to resolve.")
            }

            callback?.onLog("[IDE] Resolving: ${initialDependencies.joinToString { it.artifact.toString() }}")

            val files = resolveList(initialDependencies)
            resolvedArtifacts.clear()
            resolvedArtifacts.addAll(files)

            callback?.onLog("[IDE] Dependency resolution finished successfully.")
            return BuildResult(true, "Dependencies resolved successfully.")

        } catch (e: Exception) {
            e.printStackTrace()
            return BuildResult(false, e.stackTraceToString())
        }
    }

    private fun newRepositorySystem(): RepositorySystem {
        val supplier = RepositorySystemSupplier()
        return supplier.get()
    }

    private fun newRepositorySystemSession(system: RepositorySystem, localRepoPath: File): DefaultRepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession()
        @Suppress("DEPRECATION")
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
}
