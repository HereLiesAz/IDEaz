package com.hereliesaz.ideaz.utils

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectFileObserverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun watchesEditsToFilesInsideASubdirectory() {
        // Regression: FileObserver's single-File constructor only watches
        // the directory it's given, not recursively - virtually every real
        // project keeps its source under a subdirectory (src/, public/,
        // ...), so edits there never fired an event at all.
        val root = tempFolder.newFolder("project")
        val srcDir = File(root, "src").apply { mkdirs() }

        val latch = CountDownLatch(1)
        val observer = ProjectFileObserver(root.absolutePath) { latch.countDown() }
        observer.startWatching()
        try {
            File(srcDir, "App.jsx").writeText("new file")
            assertTrue(
                "editing src/App.jsx should trigger onChange() within the timeout",
                latch.await(5, TimeUnit.SECONDS),
            )
        } finally {
            observer.stopWatching()
        }
    }

    @Test
    fun skipsNodeModulesWithoutThrowing() {
        val root = tempFolder.newFolder("project2")
        File(root, "node_modules/some-pkg").mkdirs()
        File(root, "src").mkdirs()

        val observer = ProjectFileObserver(root.absolutePath) {}
        observer.startWatching()
        observer.stopWatching()
    }
}
