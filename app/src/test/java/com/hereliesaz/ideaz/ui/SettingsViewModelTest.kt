package com.hereliesaz.ideaz.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.utils.CredentialStore
import com.hereliesaz.ideaz.utils.decryptCredential
import com.hereliesaz.ideaz.utils.encryptCredential
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var application: android.app.Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear().commit()
        viewModel = SettingsViewModel(application)
    }

    @Test
    fun `crash reporting defaults on but the first-run disclosure is unseen until marked`() {
        assertTrue(viewModel.isReportIdeErrorsEnabled())
        assertFalse(viewModel.hasShownCrashReportingFirstRun())

        viewModel.setReportIdeErrorsEnabled(false)
        viewModel.markCrashReportingFirstRunShown()

        assertFalse(viewModel.isReportIdeErrorsEnabled())
        assertTrue(viewModel.hasShownCrashReportingFirstRun())
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
        // The Jules API key is a secure credential now (routes through
        // AndroidKeystoreCredentialStore), so - like every other secure-key
        // test below - use a fake store rather than exercising real Android
        // Keystore crypto, which Robolectric doesn't simulate.
        val credentials = FakeCredentialStore()
        viewModel = SettingsViewModel(application, credentials)

        assertNull(viewModel.getApiKey())
        viewModel.saveApiKey("secret_key")
        assertEquals("secret_key", viewModel.getApiKey())
        assertEquals("secret_key", viewModel.apiKey.value)
        assertEquals("secret_key", credentials.values[SettingsViewModel.KEY_API_KEY])
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
        assertEquals("UNKNOWN", viewModel.readProjectType())
        viewModel.setProjectType("ANDROID")
        assertEquals("ANDROID", viewModel.readProjectType())
        assertEquals("ANDROID", viewModel.projectType.value)
    }

    @Test
    fun `Hugging Face token is stored outside default preferences`() {
        val credentials = FakeCredentialStore()
        viewModel = SettingsViewModel(application, credentials)

        assertTrue(viewModel.saveString(SettingsViewModel.KEY_HF_API_KEY, "  hf_secret  "))

        assertEquals("hf_secret", credentials.values[SettingsViewModel.KEY_HF_API_KEY])
        assertFalse(
            PreferenceManager.getDefaultSharedPreferences(application)
                .contains(SettingsViewModel.KEY_HF_API_KEY)
        )
        assertEquals("hf_secret", viewModel.getApiKey(SettingsViewModel.KEY_HF_API_KEY))
    }

    @Test
    fun `legacy Hugging Face token migrates after secure write succeeds`() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)
        preferences.edit().putString(SettingsViewModel.KEY_HF_API_KEY, "legacy_token").commit()
        val credentials = FakeCredentialStore()
        viewModel = SettingsViewModel(application, credentials)

        assertEquals("legacy_token", viewModel.getApiKey(SettingsViewModel.KEY_HF_API_KEY))
        assertEquals("legacy_token", credentials.values[SettingsViewModel.KEY_HF_API_KEY])
        assertFalse(preferences.contains(SettingsViewModel.KEY_HF_API_KEY))
    }

    @Test
    fun `Gemini key is stored securely and legacy plaintext migrates`() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)
        preferences.edit().putString(SettingsViewModel.KEY_GOOGLE_API_KEY, "legacy_gemini").commit()
        val credentials = FakeCredentialStore()
        viewModel = SettingsViewModel(application, credentials)

        assertEquals("legacy_gemini", viewModel.getGoogleApiKey())
        assertEquals("legacy_gemini", credentials.values[SettingsViewModel.KEY_GOOGLE_API_KEY])
        assertFalse(preferences.contains(SettingsViewModel.KEY_GOOGLE_API_KEY))

        assertTrue(viewModel.saveGoogleApiKey("  replacement_gemini  "))
        assertEquals("replacement_gemini", credentials.values[SettingsViewModel.KEY_GOOGLE_API_KEY])
        assertFalse(preferences.contains(SettingsViewModel.KEY_GOOGLE_API_KEY))
    }

    @Test
    fun `secure Gemini key wins and removes coexisting plaintext`() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)
        preferences.edit().putString(SettingsViewModel.KEY_GOOGLE_API_KEY, "obsolete_plaintext").commit()
        val credentials = FakeCredentialStore().apply {
            values[SettingsViewModel.KEY_GOOGLE_API_KEY] = "secure_gemini"
        }
        val viewModel = SettingsViewModel(application, credentials)

        assertEquals("secure_gemini", viewModel.getGoogleApiKey())
        assertFalse(preferences.contains(SettingsViewModel.KEY_GOOGLE_API_KEY))
    }

    @Test
    fun `failed secure migration retains legacy Hugging Face token`() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)
        preferences.edit().putString(SettingsViewModel.KEY_HF_API_KEY, "legacy_token").commit()
        val credentials = object : CredentialStore {
            override fun get(key: String): String? = null
            override fun put(key: String, value: String) = error("keystore unavailable")
            override fun remove(key: String) = Unit
        }
        viewModel = SettingsViewModel(application, credentials)

        assertEquals("legacy_token", viewModel.getApiKey(SettingsViewModel.KEY_HF_API_KEY))
        assertEquals("legacy_token", preferences.getString(SettingsViewModel.KEY_HF_API_KEY, null))
    }

    @Test
    fun `GitHub token and provider API keys are stored outside default preferences`() {
        // Previously only google_api_key and hf_api_key were secure; every
        // other credential type (GitHub token, Jules key, the six
        // OpenAI-compatible provider keys, signing passwords) lived in
        // plaintext preferences.
        val credentials = FakeCredentialStore()
        viewModel = SettingsViewModel(application, credentials)
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)

        assertTrue(viewModel.saveGithubToken("  ghp_secret  "))
        assertEquals("ghp_secret", credentials.values[SettingsViewModel.KEY_GITHUB_TOKEN])
        assertFalse(preferences.contains(SettingsViewModel.KEY_GITHUB_TOKEN))
        assertEquals("ghp_secret", viewModel.getGithubToken())

        assertTrue(viewModel.saveString(SettingsViewModel.KEY_OPENAI_API_KEY, "sk-secret"))
        assertEquals("sk-secret", credentials.values[SettingsViewModel.KEY_OPENAI_API_KEY])
        assertFalse(preferences.contains(SettingsViewModel.KEY_OPENAI_API_KEY))
    }

    @Test
    fun `signing passwords are stored securely, alias stays plain`() {
        val credentials = FakeCredentialStore()
        viewModel = SettingsViewModel(application, credentials)
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)

        viewModel.saveSigningCredentials(storePass = "storePass", alias = "myAlias", keyPass = "keyPass")

        assertEquals("storePass", credentials.values[SettingsViewModel.KEY_KEYSTORE_PASS])
        assertEquals("keyPass", credentials.values[SettingsViewModel.KEY_KEY_PASS])
        assertFalse(preferences.contains(SettingsViewModel.KEY_KEYSTORE_PASS))
        assertFalse(preferences.contains(SettingsViewModel.KEY_KEY_PASS))
        assertEquals("myAlias", preferences.getString(SettingsViewModel.KEY_KEY_ALIAS, null))

        assertEquals("storePass", viewModel.getKeystorePass())
        assertEquals("keyPass", viewModel.getKeyPass())
        assertEquals("myAlias", viewModel.getKeyAlias())

        viewModel.clearSigningConfig()
        assertNull(credentials.values[SettingsViewModel.KEY_KEYSTORE_PASS])
        assertNull(credentials.values[SettingsViewModel.KEY_KEY_PASS])
        assertEquals("android", viewModel.getKeystorePass())
    }

    @Test
    fun `credential envelope hides plaintext and rejects tampering`() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val envelope = encryptCredential("hf_secret", key, "hf_api_key")

        assertFalse(envelope.contains("hf_secret"))
        assertEquals("hf_secret", decryptCredential(envelope, key, "hf_api_key"))
        val tampered = envelope.dropLast(1) + if (envelope.last() == 'A') "B" else "A"
        assertThrows(Exception::class.java) { decryptCredential(tampered, key, "hf_api_key") }
        assertThrows(Exception::class.java) { decryptCredential(envelope, key, "different_key") }
    }

    private class FakeCredentialStore : CredentialStore {
        val values = mutableMapOf<String, String>()

        override fun get(key: String): String? = values[key]

        override fun put(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }
}
