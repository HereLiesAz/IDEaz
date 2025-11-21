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
    fun detectFlutterProject() {
        val projectDir = tempFolder.newFolder("flutter_project")
        File(projectDir, "pubspec.yaml").createNewFile()

        val type = ProjectAnalyzer.detectProjectType(projectDir)
        assertEquals(ProjectType.FLUTTER, type)
    }

    @Test
    fun detectReactNativeProject() {
        val projectDir = tempFolder.newFolder("rn_project")
        File(projectDir, "package.json").createNewFile()
        File(projectDir, "app.json").createNewFile()

        val type = ProjectAnalyzer.detectProjectType(projectDir)
        assertEquals(ProjectType.REACT_NATIVE, type)
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
}
