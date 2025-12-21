package com.hereliesaz.ideaz.buildlogic

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.artifact.Artifact

class HttpDependencyResolverTest {
    @Test
    fun testParseDependencies() {
        // Updated to use direct call and direct access, removing brittle reflection

        val input = """
            # TOML
            "com.google.code.gson:gson" = "2.8.8"
            "androidx.core:core-splashscreen:aar" = "1.0.1"

            # Gradle (Groovy/KTS)
            implementation "com.squareup.okhttp3:okhttp:4.9.0"
            api("io.reactivex.rxjava3:rxjava:3.0.0")
            testImplementation 'junit:junit:4.13.2'

            # Raw
            plain:dependency:1.0.0

            # Maven XML
            <dependency>
                <groupId>org.apache.commons</groupId>
                <artifactId>commons-lang3</artifactId>
                <version>3.12.0</version>
            </dependency>
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>mylib</artifactId>
                <version>1.1.0</version>
                <type>aar</type>
            </dependency>
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>excluded-lib</artifactId>
                <version>1.0.0</version>
                <exclusions>
                    <exclusion>
                         <groupId>com.bad</groupId>
                         <artifactId>bad-lib</artifactId>
                    </exclusion>
                </exclusions>
            </dependency>
        """.trimIndent()

        val dependencies = HttpDependencyResolver.parseDependencies(input, null)
        assertEquals(9, dependencies.size)

        fun checkArtifact(dep: Dependency, artifactId: String, version: String, extension: String) {
            val artifact = dep.artifact
            assertEquals("ArtifactId mismatch for $artifactId", artifactId, artifact.artifactId)
            assertEquals("Version mismatch for $artifactId", version, artifact.version)
            assertEquals("Extension mismatch for $artifactId", extension, artifact.extension)
        }

        // Maven (Parsed first)
        checkArtifact(dependencies[0], "commons-lang3", "3.12.0", "jar")
        checkArtifact(dependencies[1], "mylib", "1.1.0", "aar")

        // Exclusions (Maven)
        val excludedDep = dependencies[2]
        checkArtifact(excludedDep, "excluded-lib", "1.0.0", "jar")
        assertEquals(1, excludedDep.exclusions.size)
        val exclusion = excludedDep.exclusions.iterator().next()
        assertEquals("com.bad", exclusion.groupId)
        assertEquals("bad-lib", exclusion.artifactId)

        // TOML
        checkArtifact(dependencies[3], "gson", "2.8.8", "jar")
        checkArtifact(dependencies[4], "core-splashscreen", "1.0.1", "aar")

        // Gradle
        checkArtifact(dependencies[5], "okhttp", "4.9.0", "jar")
        checkArtifact(dependencies[6], "rxjava", "3.0.0", "jar")
        checkArtifact(dependencies[7], "junit", "4.13.2", "jar")

        // Raw
        checkArtifact(dependencies[8], "dependency", "1.0.0", "jar")
    }
}
