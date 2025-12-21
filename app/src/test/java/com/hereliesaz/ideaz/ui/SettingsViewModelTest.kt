package com.hereliesaz.ideaz.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hereliesaz.ideaz.ui.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = SettingsViewModel(app)
    }

    @Test
    fun testAppName() {
        viewModel.setAppName("TestApp")
        assertEquals("TestApp", viewModel.getAppName())
        assertEquals("TestApp", viewModel.currentAppName.value)
    }

    @Test
    fun testGithubUser() {
        viewModel.setGithubUser("TestUser")
        assertEquals("TestUser", viewModel.getGithubUser())
    }

    @Test
    fun testRequiredKeys() {
        // Clear keys first
        viewModel.saveApiKey("")
        viewModel.saveGithubToken("")

        val missing = viewModel.checkRequiredKeys()
        assertTrue(missing.contains("Jules API Key"))
        assertTrue(missing.contains("GitHub Token"))

        viewModel.saveApiKey("key")
        viewModel.saveGithubToken("token")

        val missing2 = viewModel.checkRequiredKeys()
        assertTrue(missing2.isEmpty())
    }

    @Test
    fun testProjectType() {
        viewModel.setProjectType("ANDROID")
        assertEquals("ANDROID", viewModel.getProjectType())
        assertEquals("ANDROID", viewModel.projectType.value)
    }
}
