package com.hereliesaz.ideaz

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.hereliesaz.ideaz.ui.AiModel
import com.hereliesaz.ideaz.ui.AiModels
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class MainViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = TestCoroutineDispatcher()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var settingsViewModel: SettingsViewModel

    private lateinit var mainViewModel: MainViewModel
    private lateinit var mockedAiModels: MockedStatic<AiModels>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Mock the static findById method
        mockedAiModels = Mockito.mockStatic(AiModels::class.java)
        mockedAiModels.`when`<AiModel?> { AiModels.findById(AiModels.GEMINI_CLI) }.thenReturn(
            AiModel(id = "GEMINI_CLI", displayName = "Gemini CLI", requiredKey = "none")
        )

        mainViewModel = MainViewModel(application, settingsViewModel)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
        mockedAiModels.close()
    }

    @Test
    fun `sendPrompt with Gemini CLI model updates log with CLI response`() = testDispatcher.runBlockingTest {
        // Given
        val prompt = "test prompt"
        `when`(settingsViewModel.getAiAssignment(SettingsViewModel.KEY_AI_ASSIGNMENT_CONTEXTLESS)).thenReturn(AiModels.GEMINI_CLI)
        `when`(settingsViewModel.getApiKey("none")).thenReturn("not_blank_for_check")

        // When
        mainViewModel.sendPrompt(prompt)

        // Then
        val expectedError = "Error: Received null response from Gemini CLI."
        val logValue = mainViewModel.buildLog.value
        assertTrue("Log should contain the prompt", logValue.contains(prompt))
        assertTrue("Log should contain the error from GeminiCliClient", logValue.contains(expectedError))
        assertTrue("Log should indicate AI status is Idle", logValue.contains("[INFO] AI Status: Idle"))
    }
}
