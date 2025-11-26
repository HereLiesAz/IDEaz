package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor
import org.junit.Assert.*
import org.junit.Rule
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProcessAarsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testProcessAars_extractsAndCompiles() {
        // Setup
        val localRepoDir = tempFolder.newFolder("local-repo")
        val buildDir = tempFolder.newFolder("build")
        val aapt2Path = "echo" // Mock aapt2

        // Create a dummy AAR
        val aarFile = File(localRepoDir, "test-lib.aar")
        createDummyAar(aarFile)

        val processAars = ProcessAars(listOf(aarFile), buildDir, aapt2Path)

        // Implement Interface directly to avoid Stub/Binder issues in unit test
        val callback = object : IBuildCallback {
            override fun onLog(message: String) {
                println(message)
            }
            override fun onSuccess(apkPath: String) {}
            override fun onFailure(error: String) {}
            override fun asBinder(): android.os.IBinder? = null
        }

        // Execute
        val result = runBlocking { processAars.execute(callback) }

        // Verify
        assertTrue("Build step should succeed", result.success)
        assertTrue("Should find jars", processAars.jars.isNotEmpty())
        assertTrue("Should find compiled resources", processAars.compiledAars.isNotEmpty())

        // Verify extraction
        val explodedDir = File(buildDir, "exploded_aars")
        assertTrue(explodedDir.exists())
        val children = explodedDir.listFiles()
        assertNotNull(children)
        assertTrue(children!!.any { it.name.startsWith("test-lib") })
    }

    private fun createDummyAar(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            // classes.jar
            zip.putNextEntry(ZipEntry("classes.jar"))
            zip.write("dummy content".toByteArray())
            zip.closeEntry()

            // res/values/strings.xml
            zip.putNextEntry(ZipEntry("res/values/strings.xml"))
            zip.write("<resources></resources>".toByteArray())
            zip.closeEntry()

            // libs/internal.jar
            zip.putNextEntry(ZipEntry("libs/internal.jar"))
            zip.write("dummy internal lib".toByteArray())
            zip.closeEntry()
        }
    }
}
