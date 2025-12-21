package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.models.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectAnalyzerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun detectAndroidProject_buildGradle() {
        val projectDir = tempFolder.newFolder("android_project")
        File(projectDir, "build.gradle").createNewFile()

        val type = ProjectAnalyzer.detectProjectType(projectDir)
        assertEquals(ProjectType.ANDROID, type)
    }

    @Test
    fun detectAndroidProject_buildGradleKts() {
        val projectDir = tempFolder.newFolder("android_kts_project")
        File(projectDir, "build.gradle.kts").createNewFile()

        val type = ProjectAnalyzer.detectProjectType(projectDir)
        assertEquals(ProjectType.ANDROID, type)
    }


    @Test
    fun detectWebProject() {
        val projectDir = tempFolder.newFolder("web_project")
        File(projectDir, "index.html").createNewFile()

        val type = ProjectAnalyzer.detectProjectType(projectDir)
        assertEquals(ProjectType.WEB, type)
    }

    @Test
    fun detectWebProject_withPackageJson() {
        val projectDir = tempFolder.newFolder("web_npm_project")
        File(projectDir, "package.json").createNewFile()
        File(projectDir, "index.html").createNewFile()

        val type = ProjectAnalyzer.detectProjectType(projectDir)
        assertEquals(ProjectType.WEB, type)
    }

    @Test
    fun detectOtherProject() {
        val projectDir = tempFolder.newFolder("unknown_project")
        File(projectDir, "readme.md").createNewFile()

        val type = ProjectAnalyzer.detectProjectType(projectDir)
        assertEquals(ProjectType.OTHER, type)
    }

    @Test
    fun detectFlutterProject() {
        val projectDir = tempFolder.newFolder("flutter_project")
        File(projectDir, "pubspec.yaml").createNewFile()

        val type = ProjectAnalyzer.detectProjectType(projectDir)
        assertEquals(ProjectType.FLUTTER, type)
    }

    @Test
    fun detectReactNativeProject() {
        val projectDir = tempFolder.newFolder("react_native_project")
        File(projectDir, "package.json").createNewFile()
        File(projectDir, "app.json").createNewFile()

        val type = ProjectAnalyzer.detectProjectType(projectDir)
        assertEquals(ProjectType.REACT_NATIVE, type)
    }

    @Test
    fun detectPackageName_fromManifest() {
        val projectDir = tempFolder.newFolder("manifest_project")
        val manifestDir = File(projectDir, "app/src/main").apply { mkdirs() }
        File(manifestDir, "AndroidManifest.xml").writeText("""
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.manifest">
            </manifest>
        """.trimIndent())

        val packageName = ProjectAnalyzer.detectPackageName(projectDir)
        assertEquals("com.example.manifest", packageName)
    }

    @Test
    fun detectPackageName_fromGradleApplicationId() {
        val projectDir = tempFolder.newFolder("gradle_appid_project")
        val appDir = File(projectDir, "app").apply { mkdirs() }
        File(appDir, "build.gradle").writeText("""
            android {
                defaultConfig {
                    applicationId "com.example.gradle.appid"
                }
            }
        """.trimIndent())

        val packageName = ProjectAnalyzer.detectPackageName(projectDir)
        assertEquals("com.example.gradle.appid", packageName)
    }

    @Test
    fun detectPackageName_fromGradleNamespace() {
        val projectDir = tempFolder.newFolder("gradle_namespace_project")
        val appDir = File(projectDir, "app").apply { mkdirs() }
        File(appDir, "build.gradle.kts").writeText("""
            android {
                namespace = "com.example.gradle.namespace"
            }
        """.trimIndent())

        val packageName = ProjectAnalyzer.detectPackageName(projectDir)
        assertEquals("com.example.gradle.namespace", packageName)
    }

    @Test
    fun detectPackageName_fallbackToSourceDir() {
        val projectDir = tempFolder.newFolder("source_fallback_project")
        // Create directory structure: app/src/main/java/com/example/source
        val sourceDir = File(projectDir, "app/src/main/java/com/example/source").apply { mkdirs() }
        File(sourceDir, "Main.kt").createNewFile()

        val packageName = ProjectAnalyzer.detectPackageName(projectDir)
        assertEquals("com.example.source", packageName)
    }

    @Test
    fun detectPackageName_fallbackToFolderName() {
        val projectDir = tempFolder.newFolder("My-Project_123")
        // No manifest, no gradle, no source

        val packageName = ProjectAnalyzer.detectPackageName(projectDir)

        // Should sanitize "My-Project_123" to something valid.
        // Assuming format: "com.ideaz.generated.myproject123"
        assertEquals("com.ideaz.generated.myproject123", packageName)
    }
}
