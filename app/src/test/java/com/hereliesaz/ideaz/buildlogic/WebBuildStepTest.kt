package com.hereliesaz.ideaz.buildlogic

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WebBuildStepTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testValidateHtml_Valid() {
        val projectDir = tempFolder.newFolder("valid_html")
        val outputDir = tempFolder.newFolder("output_1")
        val indexHtml = File(projectDir, "index.html")
        indexHtml.writeText("<!DOCTYPE html><html><body></body></html>")

        val step = WebBuildStep(projectDir, outputDir)
        assertTrue(step.validateHtml(indexHtml))
    }

    @Test
    fun testValidateHtml_Invalid() {
        val projectDir = tempFolder.newFolder("invalid_html")
        val outputDir = tempFolder.newFolder("output_2")
        val indexHtml = File(projectDir, "index.html")
        indexHtml.writeText("<html><body></body>") // Missing closing tag (and DOCTYPE)

        val step = WebBuildStep(projectDir, outputDir)
        assertFalse(step.validateHtml(indexHtml))
    }

    @Test
    fun testMinifyJs() {
        val projectDir = tempFolder.newFolder("js_test")
        val outputDir = tempFolder.newFolder("output_js")
        val step = WebBuildStep(projectDir, outputDir)

        val input = """
            function hello() {
                // This is a comment
                console.log("Hello");
            }
        """.trimIndent()

        val expected = """
            function hello() {
            // This is a comment
            console.log("Hello");
            }
        """.trimIndent()

        val result = step.minifyJs(input)
        assertEquals(expected, result)
    }

    @Test
    fun testMinifyCss() {
        val projectDir = tempFolder.newFolder("css_test")
        val outputDir = tempFolder.newFolder("output_css")
        val step = WebBuildStep(projectDir, outputDir)

        val input = """
            body {
                color: red; /* comment */

                background: blue;
            }
            /* Block comment
               spanning lines */
            div { width: 100%; }
        """.trimIndent()

        val result = step.minifyCss(input)

        // Block comments should be gone
        assertFalse("Should not contain block comments", result.contains("/*"))
        // Newlines should be gone
        assertFalse("Should not contain newlines", result.contains("\n"))
        // Content should adhere
        assertTrue(result.contains("body {color: red;background: blue;}"))
        assertTrue(result.contains("div { width: 100%; }")) // trim("div { width: 100%; }") -> "div { width: 100%; }"
    }
}
