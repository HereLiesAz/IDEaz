package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.models.SourceMapEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SourceContextHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun resolveContext_reactNativeTag_success() {
        val projectDir = tempFolder.newFolder("rn_project")
        val appJs = File(projectDir, "App.js")
        appJs.writeText("""
            import React from 'react';
            import { Text } from 'react-native';

            export default function App() {
              return <Text>Hello</Text>;
            }
        """.trimIndent())

        // Line 5 is "return <Text>Hello</Text>;"
        val resourceId = "__source:App.js:5__"

        val result = SourceContextHelper.resolveContext(resourceId, projectDir, emptyMap())

        assertEquals(false, result.isError)
        assertEquals(appJs.absolutePath, result.file)
        assertEquals(5, result.line)
        assertEquals("return <Text>Hello</Text>;", result.snippet)
    }

    @Test
    fun resolveContext_reactNativeTag_fileNotFound() {
        val projectDir = tempFolder.newFolder("rn_project_missing")
        val resourceId = "__source:Missing.js:5__"

        val result = SourceContextHelper.resolveContext(resourceId, projectDir, emptyMap())

        assertEquals(true, result.isError)
        assertTrue(result.errorMessage?.contains("Source file not found") == true)
    }

    @Test
    fun resolveContext_androidId_simple_success() {
        val projectDir = tempFolder.newFolder("android_project")
        val layoutFile = File(projectDir, "layout.xml")
        layoutFile.writeText("""
            <LinearLayout>
                <Button android:id="@+id/myButton" />
            </LinearLayout>
        """.trimIndent())

        val sourceMap = mapOf(
            "myButton" to SourceMapEntry("myButton", layoutFile.absolutePath, 2)
        )

        val result = SourceContextHelper.resolveContext("myButton", projectDir, sourceMap)

        assertEquals(false, result.isError)
        assertEquals(layoutFile.absolutePath, result.file)
        assertEquals(2, result.line)
        assertEquals("<Button android:id=\"@+id/myButton\" />", result.snippet)
    }

    @Test
    fun resolveContext_androidId_full_success() {
        val projectDir = tempFolder.newFolder("android_project_full")
        val layoutFile = File(projectDir, "layout.xml")
        layoutFile.writeText("""
            <TextView android:id="@+id/title" />
        """.trimIndent())

        val sourceMap = mapOf(
            "title" to SourceMapEntry("title", layoutFile.absolutePath, 1)
        )

        // Pass full ID string
        val result = SourceContextHelper.resolveContext("com.example:id/title", projectDir, sourceMap)

        assertEquals(false, result.isError)
        assertEquals(layoutFile.absolutePath, result.file)
        assertEquals(1, result.line)
        assertEquals("<TextView android:id=\"@+id/title\" />", result.snippet)
    }

    @Test
    fun resolveContext_androidId_notFound() {
        val projectDir = tempFolder.newFolder("android_project_404")
        val result = SourceContextHelper.resolveContext("unknown_id", projectDir, emptyMap())

        assertEquals(true, result.isError)
        assertTrue(result.errorMessage?.contains("not found in source map") == true)
    }

    @Test
    fun resolveContext_androidId_strips_package_correctly() {
         // Test that "android:id/content" maps to "content"
         val projectDir = tempFolder.newFolder("android_sys")
         val result = SourceContextHelper.resolveContext("android:id/content", projectDir, emptyMap())

         // Since map is empty, it should fail, but we want to ensure it tried "content" key if we could mock map behavior deeply.
         // But here we just check it fails with "not found"
         assertEquals(true, result.isError)
    }
}
