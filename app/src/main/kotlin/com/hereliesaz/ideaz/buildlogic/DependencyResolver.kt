package com.hereliesaz.ideaz.buildlogic

import com.jcabi.aether.Aether
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.util.artifact.DefaultArtifact
import java.io.File

class DependencyResolver(
    private val projectDir: String,
    private val localRepoPath: String
) : BuildStep {

    override fun execute(): BuildResult {
        try {
            val localRepo = File(localRepoPath)
            val remoteRepos = listOf(
                RemoteRepository(
                    "maven-central",
                    "default",
                    "https://repo1.maven.org/maven2/"
                )
            )
            val aether = Aether(remoteRepos, localRepo)

            val dependenciesToml = File(projectDir, "dependencies.toml")
            if (!dependenciesToml.exists()) {
                return BuildResult(true, "") // No dependencies to resolve
            }

            val allArtifacts = mutableListOf<org.sonatype.aether.artifact.Artifact>()
            dependenciesToml.readLines().forEach { line ->
                if (line.isNotBlank() && !line.startsWith("#")) {
                    val parts = line.split("=").map { it.trim().removeSurrounding("\"") }
                    val artifact = DefaultArtifact(parts[0] + ":" + parts[1])
                    val artifacts = aether.resolve(artifact, "runtime")
                    allArtifacts.addAll(artifacts)
                }
            }

            val artifactPaths = allArtifacts.map { it.file.absolutePath }
            val output = artifactPaths.joinToString(File.pathSeparator)

            return BuildResult(true, output)

        } catch (e: Exception) {
            e.printStackTrace()
            return BuildResult(false, e.message ?: "Unknown error during dependency resolution")
        }
    }
}
