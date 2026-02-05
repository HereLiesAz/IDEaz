package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import com.hereliesaz.ideaz.BuildConfig
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.ApkInstaller
import com.hereliesaz.ideaz.utils.VersionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Delegate responsible for managing application self-updates.
 *
 * **Mechanism:**
 * - Checks the `HereLiesAz/IDEaz` GitHub repository for releases.
 * - Specifically looks for `prerelease` tags starting with `latest-debug-` to support the "Dogfooding" cycle.
 * - Downloads the APK asset and triggers installation via [ApkInstaller].
 *
 * @param application The Application context.
 * @param settingsViewModel ViewModel to access GitHub token (needed for API rate limits and private repos if applicable).
 * @param scope CoroutineScope for background network operations.
 * @param onOverlayLog Callback to log messages to the UI.
 */
class UpdateDelegate(
    private val application: Application,
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onOverlayLog: (String) -> Unit
) {
    // --- StateFlows ---

    private val _updateStatus = MutableStateFlow<String?>(null)
    /** Current status message of the update process (e.g., "Downloading..."). Null when idle. */
    val updateStatus = _updateStatus.asStateFlow()

    private val _updateVersion = MutableStateFlow<String?>(null)
    /** The version string of the detected available update. */
    val updateVersion = _updateVersion.asStateFlow()

    private val _showUpdateWarning = MutableStateFlow<Boolean>(false)
    /** Whether to show the update confirmation dialog to the user. */
    val showUpdateWarning = _showUpdateWarning.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    /** Detailed message explaining the update (Upgrade/Downgrade/Reinstall). */
    val updateMessage = _updateMessage.asStateFlow()

    private var pendingUpdateAssetUrl: String? = null

    // --- Public Operations ---

    /**
     * Checks for experimental updates (pre-releases or debug builds) on GitHub.
     *
     * **Logic:**
     * 1. Fetches releases from GitHub.
     * 2. Filters for `prerelease` and tags starting with `latest-debug-`.
     * 3. Parses APK filenames to find the highest version number.
     * 4. Compares with local `BuildConfig.VERSION_NAME`.
     * 5. Sets [showUpdateWarning] if a relevant update/downgrade is found.
     */
    fun checkForExperimentalUpdates() {
        scope.launch {
            val token = settingsViewModel.getGithubToken()

            if (token.isNullOrBlank()) {
                onOverlayLog("Cannot check for updates: Missing GitHub Token.")
                return@launch
            }

            _updateStatus.value = "Checking for updates..."

            try {
                val service = GitHubApiClient.createService(token)
                // Hardcoded to the official repo for IDEaz updates
                val releases = service.getReleases("HereLiesAz", "IDEaz")

                // Strategy: Find the release that matches our specific tag convention.
                // Priority: Prerelease with "latest-debug-" tag.
                val update = releases.firstOrNull {
                    it.prerelease && it.tagName.startsWith("latest-debug-")
                } ?: releases.firstOrNull { it.tagName.startsWith("latest-debug-") }

                if (update != null) {
                    _updateVersion.value = update.tagName

                    // Find the APK asset with the highest version by parsing filename (e.g., IDEaz-1.0.0.apk)
                    val validAssets = update.assets.filter {
                        it.name.endsWith(".apk") && it.name.contains("debug") && VersionUtils.extractVersionFromFilename(it.name) != null
                    }

                    // Sort assets by extracted version
                    val bestAsset = validAssets.sortedWith { a1, a2 ->
                        val v1 = VersionUtils.extractVersionFromFilename(a1.name) ?: "0"
                        val v2 = VersionUtils.extractVersionFromFilename(a2.name) ?: "0"
                        VersionUtils.compareVersions(v1, v2)
                    }.lastOrNull()

                    pendingUpdateAssetUrl = bestAsset?.browserDownloadUrl

                    if (pendingUpdateAssetUrl != null) {
                        val remoteVersion = VersionUtils.extractVersionFromFilename(bestAsset!!.name)
                        val localVersion = BuildConfig.VERSION_NAME

                        if (remoteVersion != null) {
                            val diff = VersionUtils.compareVersions(remoteVersion, localVersion)
                            if (diff > 0) {
                                _updateMessage.value = "New version $remoteVersion is available (Current: $localVersion). Install?"
                            } else if (diff < 0) {
                                _updateMessage.value = "You are running a newer version ($localVersion) than the latest release ($remoteVersion). Downgrade?"
                            } else {
                                _updateMessage.value = "You are already on the latest version ($localVersion). Re-install?"
                            }
                        } else {
                            _updateMessage.value = "Update found: ${update.tagName}. Install?"
                        }
                        _showUpdateWarning.value = true
                        copyToClipboard(_updateMessage.value)
                    } else {
                        onOverlayLog("Update found but no valid debug APK asset.")
                    }
                } else {
                    onOverlayLog("No updates found.")
                }
            } catch (e: Exception) {
                onOverlayLog("Update check failed: ${e.message}")
            } finally {
                _updateStatus.value = null
            }
        }
    }

    /**
     * Confirms the pending update and initiates download and installation.
     */
    fun confirmUpdate() {
        _showUpdateWarning.value = false
        val url = pendingUpdateAssetUrl ?: return

        scope.launch {
            _updateStatus.value = "Downloading update..."
            val file = downloadFile(url, "update.apk")
            if (file != null) {
                _updateStatus.value = "Installing..."
                ApkInstaller.installApk(application, file.absolutePath)
            } else {
                onOverlayLog("Download failed.")
            }
            _updateStatus.value = null
        }
    }

    /**
     * Dismisses the update warning dialog.
     */
    fun dismissUpdateWarning() {
        _showUpdateWarning.value = false
        pendingUpdateAssetUrl = null
    }

    private fun copyToClipboard(text: String?) {
        if (text.isNullOrBlank()) return
        try {
            val clipboard = application.getSystemService(android.content.ClipboardManager::class.java)
            val clip = android.content.ClipData.newPlainText("Update Info", text)
            clipboard?.setPrimaryClip(clip)
            onOverlayLog("Update info copied to clipboard.")
        } catch (e: Exception) {
            onOverlayLog("Failed to copy to clipboard.")
        }
    }

    /**
     * Helper to download a file from a URL.
     */
    private suspend fun downloadFile(urlStr: String, fileName: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }

                val file = File(application.filesDir, fileName)
                val input = connection.inputStream
                val output = FileOutputStream(file)
                input.copyTo(output)
                output.close()
                input.close()
                file
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
