package com.hereliesaz.ideaz.buildlogic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BuildCacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun shouldSkip_returnsFalse_whenOutputDirDoesNotExist() {
        val input = tempFolder.newFile("input.txt")
        val outputDir = File(tempFolder.root, "output") // Not created

        assertFalse(BuildCacheManager.shouldSkip("task", listOf(input), outputDir))
    }

    @Test
    fun shouldSkip_returnsFalse_whenSnapshotMissing() {
        val input = tempFolder.newFile("input.txt")
        val outputDir = tempFolder.newFolder("output")

        assertFalse(BuildCacheManager.shouldSkip("task", listOf(input), outputDir))
    }

    @Test
    fun shouldSkip_returnsFalse_whenDirectoryEmptyExceptSnapshot() {
        val input = tempFolder.newFile("input.txt")
        val outputDir = tempFolder.newFolder("output")

        // Create snapshot
        BuildCacheManager.updateSnapshot("task", listOf(input), outputDir)

        // Output dir has snapshot but nothing else
        assertFalse(BuildCacheManager.shouldSkip("task", listOf(input), outputDir))
    }

    @Test
    fun shouldSkip_returnsTrue_whenDirectoryHasContentAndHashMatches() {
        val input = tempFolder.newFile("input.txt")
        val outputDir = tempFolder.newFolder("output")

        // Create random artifact
        File(outputDir, "artifact.txt").createNewFile()

        BuildCacheManager.updateSnapshot("task", listOf(input), outputDir)

        assertTrue(BuildCacheManager.shouldSkip("task", listOf(input), outputDir))
    }

    @Test
    fun shouldSkip_returnsFalse_whenRequiredArtifactMissing() {
        val input = tempFolder.newFile("input.txt")
        val outputDir = tempFolder.newFolder("output")

        // Create some other artifact
        File(outputDir, "other.txt").createNewFile()

        BuildCacheManager.updateSnapshot("task", listOf(input), outputDir)

        // Check for specific artifact
        assertFalse(BuildCacheManager.shouldSkip("task", listOf(input), outputDir, "required.dex"))
    }

    @Test
    fun shouldSkip_returnsTrue_whenRequiredArtifactExistsAndHashMatches() {
        val input = tempFolder.newFile("input.txt")
        val outputDir = tempFolder.newFolder("output")

        // Create required artifact
        File(outputDir, "required.dex").createNewFile()

        BuildCacheManager.updateSnapshot("task", listOf(input), outputDir)

        assertTrue(BuildCacheManager.shouldSkip("task", listOf(input), outputDir, "required.dex"))
    }
}
