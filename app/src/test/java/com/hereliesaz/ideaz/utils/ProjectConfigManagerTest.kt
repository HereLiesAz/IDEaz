package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.models.ProjectType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectConfigManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `ensureWorkflow creates flutter workflow`() {
        // Arrange
        val projectDir = tempFolder.newFolder("flutter_project")
        val workflowsDir = File(projectDir, ".github/workflows")

        // Act
        val modified = ProjectConfigManager.ensureWorkflow(
            projectDir = projectDir,
            type = ProjectType.FLUTTER
        )

        // Assert
        assertTrue("Should return true when workflow is created", modified)
        assertTrue("Workflows directory should exist", workflowsDir.exists())

        val flutterWorkflow = File(workflowsDir, "android_ci_flutter.yml")
        assertTrue("android_ci_flutter.yml should exist", flutterWorkflow.exists())

        val content = flutterWorkflow.readText()
        assertTrue("Content should contain Flutter setup", content.contains("subosito/flutter-action"))
        assertTrue("Content should contain artifact renaming", content.contains("Rename Artifact"))
        assertTrue("Content should contain pubspec parsing", content.contains("grep '^version:' pubspec.yaml"))
    }
}
