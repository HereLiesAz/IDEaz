package com.hereliesaz.ideaz.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * These used to assert a six-value `ProjectType` taxonomy and a release gate
 * (`detected in ProjectType.selectable`) that admitted exactly one of them.
 * There is one kind of project now, so the only question left is the one the
 * gate was a proxy for: is there something IDEaz can mount and preview?
 */
class ProjectAnalyzerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun indexHtmlAloneIsPreviewable() {
        val projectDir = tempFolder.newFolder("web_project")
        File(projectDir, "index.html").createNewFile()

        // No webmanifest, no service worker. It still has an entry point IDEaz
        // can mount, which is the only thing that decides whether it is usable.
        assertTrue(ProjectAnalyzer.isPreviewable(projectDir))
    }

    @Test
    fun packageJsonAloneIsPreviewable() {
        // A Vite/Next project may keep index.html somewhere findWebEntryPoint
        // doesn't look; the loader resolves the real entry at mount time.
        val projectDir = tempFolder.newFolder("web_npm_project")
        File(projectDir, "package.json").createNewFile()

        assertTrue(ProjectAnalyzer.isPreviewable(projectDir))
        assertNull(ProjectAnalyzer.findWebEntryPoint(projectDir))
    }

    /**
     * The case that used to be impossible: IDEaz could not open the React
     * template IDEaz itself ships, because it has no webmanifest. Detection
     * resolved it to `WEB`, which was not in `selectable`, so the 4.5 MB
     * React/Babel runtime bundled to preview exactly this shape of project was
     * unreachable for it.
     */
    @Test
    fun viteReactProjectIsPreviewable() {
        val projectDir = tempFolder.newFolder("vite_react")
        File(projectDir, "index.html").createNewFile()
        File(projectDir, "package.json").createNewFile()
        File(projectDir, "vite.config.js").createNewFile()
        File(projectDir, "src").mkdirs()
        File(projectDir, "src/App.jsx").createNewFile()

        assertTrue(ProjectAnalyzer.isPreviewable(projectDir))
    }

    /** Entry points are not always at the repo root. */
    @Test
    fun entryPointUnderPublicIsFound() {
        val projectDir = tempFolder.newFolder("nested_entry")
        File(projectDir, "public").mkdirs()
        File(projectDir, "public/index.html").createNewFile()

        assertTrue(ProjectAnalyzer.isPreviewable(projectDir))
        assertEquals("public/index.html", ProjectAnalyzer.findWebEntryPoint(projectDir))
    }

    @Test
    fun rootEntryPointWinsOverNestedOne() {
        val projectDir = tempFolder.newFolder("both_entries")
        File(projectDir, "index.html").createNewFile()
        File(projectDir, "src").mkdirs()
        File(projectDir, "src/index.html").createNewFile()

        assertEquals("index.html", ProjectAnalyzer.findWebEntryPoint(projectDir))
    }

    @Test
    fun projectWithNothingToPreviewIsRejected() {
        val projectDir = tempFolder.newFolder("no_entry")
        File(projectDir, "README.md").createNewFile()

        assertFalse(ProjectAnalyzer.isPreviewable(projectDir))
        assertNull(ProjectAnalyzer.findWebEntryPoint(projectDir))
    }

    /**
     * A Gradle project used to detect as ANDROID and be refused by the release
     * gate. It is still refused, for the honest reason: there is no entry point
     * to mount.
     */
    @Test
    fun gradleProjectWithNoWebEntryPointIsRejected() {
        val projectDir = tempFolder.newFolder("gradle_project")
        File(projectDir, "build.gradle.kts").createNewFile()

        assertFalse(ProjectAnalyzer.isPreviewable(projectDir))
    }

    @Test
    fun missingDirectoryIsRejected() {
        assertFalse(ProjectAnalyzer.isPreviewable(File(tempFolder.root, "does_not_exist")))
    }
}
