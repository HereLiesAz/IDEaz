package com.hereliesaz.ideaz.utils

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DependencyManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testListDependencies() {
        val projectDir = tempFolder.newFolder("project")
        val gradleDir = File(projectDir, "gradle")
        gradleDir.mkdirs()
        val tomlFile = File(gradleDir, "libs.versions.toml")

        tomlFile.writeText("""
            [versions]
            agp = "8.1.0"
            kotlin = "1.9.0"
            retrofit = "2.9.0"

            [libraries]
            android-application = { id = "com.android.application", version.ref = "agp" }
            retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
            kotlin-stdlib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version.ref = "kotlin" }
            simple-dep = "com.example:simple:1.0.0"

            [plugins]
            android-application = { id = "com.android.application", version.ref = "agp" }
        """.trimIndent())

        val deps = DependencyManager.listDependencies(projectDir)

        assertEquals(3, deps.size)

        val retrofit = deps.find { it.alias == "retrofit" }
        assertEquals("com.squareup.retrofit2", retrofit?.group)
        assertEquals("retrofit", retrofit?.name)
        assertEquals("2.9.0", retrofit?.version)

        val kotlin = deps.find { it.alias == "kotlin-stdlib" }
        assertEquals("org.jetbrains.kotlin", kotlin?.group)
        assertEquals("kotlin-stdlib", kotlin?.name)
        assertEquals("1.9.0", kotlin?.version)

        val simple = deps.find { it.alias == "simple-dep" }
        assertEquals("com.example", simple?.group)
        assertEquals("simple", simple?.name)
        assertEquals("1.0.0", simple?.version)
    }

    @Test
    fun testParsePubspec() {
        val projectDir = tempFolder.newFolder("flutter_project")
        val pubspecFile = File(projectDir, "pubspec.yaml")

        pubspecFile.writeText("""
            name: my_app
            dependencies:
              flutter:
                sdk: flutter
              cupertino_icons: ^1.0.2
        """.trimIndent())

        val deps = DependencyManager.listDependencies(projectDir)

        val cupertino = deps.find { it.alias == "cupertino_icons" }
        assertEquals("Flutter", cupertino?.group)
        assertEquals("^1.0.2", cupertino?.version)
    }
}
