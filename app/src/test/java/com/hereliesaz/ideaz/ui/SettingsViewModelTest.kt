package com.hereliesaz.ideaz.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testAppNamePersistence() {
        assertNull(viewModel.getAppName())
        viewModel.setAppName("TestApp")
        assertEquals("TestApp", viewModel.getAppName())
        assertEquals("TestApp", viewModel.currentAppName.value)
    }

    @Test
    fun testApiKeyPersistence() {
        assertNull(viewModel.getApiKey())
        viewModel.saveApiKey("secret_key")
        assertEquals("secret_key", viewModel.getApiKey())
        assertEquals("secret_key", viewModel.apiKey.value)
    }

    @Test
    fun testProjectList() {
        assertTrue(viewModel.getProjectList().isEmpty())
        viewModel.addProject("Project1")
        assertTrue(viewModel.getProjectList().contains("Project1"))
        viewModel.removeProject("Project1")
        assertFalse(viewModel.getProjectList().contains("Project1"))
    }

    @Test
    fun testProjectTypePersistence() {
        assertEquals("UNKNOWN", viewModel.getProjectType())
        viewModel.setProjectType("ANDROID")
        assertEquals("ANDROID", viewModel.getProjectType())
        assertEquals("ANDROID", viewModel.projectType.value)
    }
}
