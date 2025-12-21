package com.hereliesaz.ideaz.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RedwoodCodegenTest {

    @Test
    fun testConstructCommandHost() {
        val outputDir = File("/tmp/out")
        val codegen = RedwoodCodegen(
            javaPath = "/bin/java",
            schemaType = "com.example.Schema",
            outputDir = outputDir,
            isHost = true,
            filesDir = File("/tmp")
        )

        val classpath = listOf(File("/tmp/lib.jar"))
        val command = codegen.constructCommand(classpath)

        assertEquals("/bin/java", command[0])
        assertEquals("-cp", command[1])
        assertTrue(command[2].contains("lib.jar"))
        assertEquals("app.cash.redwood.tooling.codegen.Main", command[3])
        assertEquals("--schema", command[4])
        assertEquals("com.example.Schema", command[5])
        assertEquals("--out", command[6])
        assertEquals(outputDir.absolutePath, command[7])
        assertEquals("--protocol-host", command[8])
        assertEquals("--widget", command[9])
    }

    @Test
    fun testConstructCommandGuest() {
        val outputDir = File("/tmp/out")
        val codegen = RedwoodCodegen(
            javaPath = "/bin/java",
            schemaType = "com.example.Schema",
            outputDir = outputDir,
            isHost = false,
            filesDir = File("/tmp")
        )

        val classpath = listOf(File("/tmp/lib.jar"))
        val command = codegen.constructCommand(classpath)

        assertTrue(command.contains("--protocol-guest"))
        assertTrue(command.contains("--compose"))
        assertTrue(!command.contains("--widget"))
    }
}
