package com.hereliesaz.ideaz.utils

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun zipEntryNames(file: File): Set<String> =
        ZipFile(file).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }

    @Test
    fun exportData_includesRealProjectFilesThatMerelyContainCacheOrIdeazAsASubstring() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val filesDir = context.filesDir

        // Real project content whose path text happens to contain "cache" or
        // ".ideaz" as a substring, not as an actual top-level management
        // directory or checkpoint folder - these must survive a backup.
        File(filesDir, "myproj/src/CacheManager.js").apply { parentFile?.mkdirs(); writeText("x") }
        File(filesDir, "my-cache-app/index.html").apply { parentFile?.mkdirs(); writeText("x") }
        File(filesDir, "myproj/related.ideaz.config.js").apply { parentFile?.mkdirs(); writeText("x") }

        // The actual things that should be skipped.
        File(filesDir, "cache/http/entry1").apply { parentFile?.mkdirs(); writeText("x") }
        File(filesDir, "myproj/.ideaz-edit-checkpoints/1/message.txt").apply { parentFile?.mkdirs(); writeText("x") }
        File(filesDir, "myproj/keystores/debug.keystore").apply { parentFile?.mkdirs(); writeText("x") }

        val outZip = tempFolder.newFile("out.zip")
        val ok = runBlocking { BackupManager.exportData(context, android.net.Uri.fromFile(outZip)) }
        assertTrue("export should succeed", ok)

        val entries = zipEntryNames(outZip)
        assertTrue("src/CacheManager.js must be included", entries.contains("myproj/src/CacheManager.js"))
        assertTrue("a project literally named my-cache-app must be included", entries.contains("my-cache-app/index.html"))
        assertTrue("related.ideaz.config.js must be included", entries.contains("myproj/related.ideaz.config.js"))

        assertFalse("the top-level cache/ management dir must be skipped", entries.contains("cache/http/entry1"))
        assertFalse(
            "per-project checkpoint scratch data must be skipped",
            entries.contains("myproj/.ideaz-edit-checkpoints/1/message.txt"),
        )
        assertFalse("debug.keystore must be skipped", entries.contains("myproj/keystores/debug.keystore"))
    }

    @Test
    fun importData_restoresAnExportedArchiveIntoFilesDir() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val filesDir = context.filesDir
        File(filesDir, "myproj/src/App.jsx").apply { parentFile?.mkdirs(); writeText("hello") }

        val outZip = tempFolder.newFile("out.zip")
        assertTrue(runBlocking { BackupManager.exportData(context, android.net.Uri.fromFile(outZip)) })

        // Simulate restoring onto a clean device: remove the source, then import.
        File(filesDir, "myproj").deleteRecursively()
        assertFalse(File(filesDir, "myproj/src/App.jsx").exists())

        val ok = runBlocking { BackupManager.importData(context, android.net.Uri.fromFile(outZip)) }
        assertTrue("import should succeed", ok)
        assertEquals("hello", File(filesDir, "myproj/src/App.jsx").readText())
    }
}
