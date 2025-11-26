package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader

data class Dependency(
    val group: String,
    val artifact: String,
    val version: String
) {
    fun toPath() = "${group.replace('.', '/')}/$artifact/$version/$artifact-$version"
}

class HttpDependencyResolver(
    private val projectDir: File,
    private val dependenciesFile: File,
    private val cacheDir: File,
    private val repositories: List<String> = listOf("https://repo.maven.apache.org/maven2/", "https://maven.google.com/")
) : BuildStep {

    private val client = OkHttpClient()
    private val resolvedDependencies = mutableSetOf<Dependency>()
    val resolvedArtifacts = mutableListOf<File>()
    val resolvedClasspath: String
        get() = cacheDir.walkTopDown()
            .filter { it.isFile && it.extension == "jar" }
            .joinToString(File.pathSeparator) { it.absolutePath }


    override fun execute(callback: IBuildCallback?): BuildResult {
        try {
            if (!dependenciesFile.exists()) return BuildResult(true, "No dependencies file found.")
            val initialDependencies = parseDependencies(dependenciesFile.readText())
            resolve(initialDependencies, callback)
            return BuildResult(true, "Dependencies resolved successfully.")
        } catch (e: Exception) {
            callback?.onLog("[IDE] Error resolving dependencies: ${e.message}")
            return BuildResult(false, "Failed to resolve dependencies: ${e.message}")
        }
    }

    private fun parseDependencies(content: String): List<Dependency> {
        return content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map {
                val parts = it.split(":")
                Dependency(parts[0], parts[1], parts[2])
            }
    }

    private fun resolve(dependencies: List<Dependency>, callback: IBuildCallback?) {
        for (dependency in dependencies) {
            if (dependency in resolvedDependencies) continue
            callback?.onLog("[IDE] Resolving ${dependency.group}:${dependency.artifact}:${dependency.version}")
            resolvedDependencies.add(dependency)

            val (pomUrl, pomContent) = fetchPom(dependency)
            val transitiveDependencies = parsePom(pomContent)
            resolve(transitiveDependencies, callback)

            val jarUrl = pomUrl.replace(".pom", ".jar")
            val jarContent = fetchBytes(jarUrl)
            val jarFile = File(cacheDir, "${dependency.artifact}-${dependency.version}.jar")
            jarFile.writeBytes(jarContent)
            resolvedArtifacts.add(jarFile)
        }
    }

    private fun fetchPom(dependency: Dependency): Pair<String, String> {
        for (repo in repositories) {
            val url = "${repo}${dependency.toPath()}.pom"
            try {
                val content = fetch(url)
                return Pair(url, content)
            } catch (e: Exception) {
                // Try next repository
            }
        }
        throw Exception("Could not find POM for $dependency in any repository.")
    }


    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Failed to fetch $url")
        return response.body!!.string()
    }

    private fun fetchBytes(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Failed to fetch $url")
        return response.body!!.bytes()
    }

    private fun parsePom(pomContent: String): List<Dependency> {
        val dependencies = mutableListOf<Dependency>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(pomContent))

        var eventType = parser.eventType
        var group: String? = null
        var artifact: String? = null
        var version: String? = null
        var inDependency = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "dependency" -> inDependency = true
                        "groupId" -> if (inDependency) group = parser.nextText()
                        "artifactId" -> if (inDependency) artifact = parser.nextText()
                        "version" -> if (inDependency) version = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "dependency") {
                        if (group != null && artifact != null && version != null) {
                            dependencies.add(Dependency(group, artifact, version))
                        }
                        inDependency = false
                        group = null
                        artifact = null
                        version = null
                    }
                }
            }
            eventType = parser.next()
        }
        return dependencies
    }
}