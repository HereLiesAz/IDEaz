package com.hereliesaz.ideaz.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectNamesTest {
    @Test
    fun `accepts ordinary names`() {
        listOf("IDEazProject", "my-app", "my_app.v2", "a", "App1").forEach {
            assertNull(it, ProjectNames.validate(it))
        }
    }

    @Test
    fun `rejects traversal, spaces, empty and leading dot or dash`() {
        listOf("", "..", "../x", "a/b", "My App", ".hidden", "-flag", "a..b", "a\\b", "ü").forEach {
            assertNotNull(it, ProjectNames.validate(it))
            assertFalse(ProjectNames.isValid(it))
        }
    }

    @Test
    fun `rejects over-long names`() {
        assertNotNull(ProjectNames.validate("a".repeat(101)))
        assertNull(ProjectNames.validate("a".repeat(100)))
    }

    @Test
    fun `sanitize always yields a valid name`() {
        assertEquals("My_App", ProjectNames.sanitize("My App"))
        assertEquals("Imported", ProjectNames.sanitize("..", "Imported"))
        assertEquals("Imported", ProjectNames.sanitize(null))
        assertEquals("x", ProjectNames.sanitize("..-x"))
        listOf("a b", "../../etc", "", "...", "--", "日本語").forEach {
            assertTrue(it, ProjectNames.isValid(ProjectNames.sanitize(it)))
        }
    }

    @Test
    fun `safeFileName rejects separators and dot names`() {
        assertNull(ProjectNames.safeFileName(null))
        assertNull(ProjectNames.safeFileName(""))
        assertNull(ProjectNames.safeFileName(".."))
        assertNull(ProjectNames.safeFileName("../evil"))
        assertNull(ProjectNames.safeFileName("a\\b"))
        assertEquals("index.html", ProjectNames.safeFileName("index.html"))
        assertEquals("my file.txt", ProjectNames.safeFileName("my file.txt"))
    }
}
