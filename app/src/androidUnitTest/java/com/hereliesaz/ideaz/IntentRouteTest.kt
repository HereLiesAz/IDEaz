package com.hereliesaz.ideaz

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouteTest {

    @Test
    fun onlyTopLevelScreensCanBeOpenedByAnExternalIntent() {
        listOf("settings", "project_settings", "file_explorer", "git")
            .forEach { assertTrue(it, isAllowedIntentRoute(it)) }
    }

    @Test
    fun fileContentAndUnknownRoutesAreRefused() {
        listOf(
            "file_content/%2Fdata%2Fdata%2Fcom.hereliesaz.ideaz%2Ffiles%2Fx", "file_content/{filePath}",
            "file_content", "", "Settings", "settings/", "git?x=1", "https://evil.example",
        ).forEach { assertFalse(it, isAllowedIntentRoute(it)) }
    }
}
