package com.hereliesaz.ideaz.buildlogic

import org.junit.Test
import org.junit.Assert.*

class DependencyParsingTest {

    @Test
    fun testCleanDependencyLine_standard() {
        val input = "com.google.code.gson:gson:2.8.8"
        assertEquals("com.google.code.gson:gson:2.8.8", cleanDependencyLine(input))
    }

    @Test
    fun testCleanDependencyLine_withEquals() {
        val input = "\"com.google.code.gson:gson\" = \"2.8.8\""
        assertEquals("com.google.code.gson:gson:2.8.8", cleanDependencyLine(input))
    }

    @Test
    fun testCleanDependencyLine_withEqualsAndSpaces() {
        val input = " \"com.google.code.gson:gson\" = \"2.8.8\" "
        assertEquals("com.google.code.gson:gson:2.8.8", cleanDependencyLine(input))
    }

    @Test
    fun testCleanDependencyLine_singleQuotes() {
        val input = "'com.google.code.gson:gson' = '2.8.8'"
        assertEquals("com.google.code.gson:gson:2.8.8", cleanDependencyLine(input))
    }
}
