package com.hereliesaz.ideaz.utils

import android.content.Context
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class ToolManagerTest {

    private lateinit var context: Context
    private lateinit var testZipFile: File
    private lateinit var toolsDir: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        toolsDir = File(context.filesDir, "local_build_tools")
        if (toolsDir.exists()) {
            toolsDir.deleteRecursively()
        }

        // Create a dummy zip file
        testZipFile = File(context.cacheDir, "test_tools.zip")
        createTestZip(testZipFile)
    }

    @After
    fun tearDown() {
        testZipFile.delete()
        toolsDir.deleteRecursively()
    }

    @Test
    fun installToolsFromZip_extractsFilesCorrectly() {
        // Act
        val result = ToolManager.installToolsFromZip(context, testZipFile)

        // Assert
        assertTrue("Installation should return true", result)
        assertTrue("Tools directory should exist", toolsDir.exists())

        val file1 = File(toolsDir, "file1.txt")
        assertTrue("file1.txt should exist", file1.exists())

        val subFile = File(toolsDir, "subdir/subfile.txt")
        assertTrue("subdir/subfile.txt should exist", subFile.exists())
    }

    @Test
    fun areToolsInstalled_returnsTrueWhenToolsExist() {
        // Arrange: Install first
        ToolManager.installToolsFromZip(context, testZipFile)

        // We need to manually create the "marker" files that ToolManager checks for:
        // android.jar, d8.jar in tools/
        // libaapt2.so in native/

        File(toolsDir, "tools").mkdirs()
        File(toolsDir, "native").mkdirs()
        File(toolsDir, "tools/android.jar").createNewFile()
        File(toolsDir, "tools/d8.jar").createNewFile()
        File(toolsDir, "native/libaapt2.so").createNewFile()

        // Act
        val result = ToolManager.areToolsInstalled(context)

        // Assert
        assertTrue("Should return true when marker files exist", result)
    }

    @Test
    fun areToolsInstalled_returnsFalseWhenToolsMissing() {
        // Act
        val result = ToolManager.areToolsInstalled(context)

        // Assert
        assertFalse("Should return false when directory is empty", result)
    }

    private fun createTestZip(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            // Entry 1: file1.txt
            zos.putNextEntry(ZipEntry("file1.txt"))
            zos.write("content1".toByteArray())
            zos.closeEntry()

            // Entry 2: subdir/subfile.txt
            zos.putNextEntry(ZipEntry("subdir/subfile.txt"))
            zos.write("content2".toByteArray())
            zos.closeEntry()
        }
    }
}
