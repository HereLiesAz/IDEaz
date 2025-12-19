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
 * Manages application self-updates by checking GitHub Releases.
 * Downloads and installs APKs directly from the 'HereLiesAz/IDEaz' repository.
 *
 * @param application The Application context.
 * @param settingsViewModel ViewModel to access GitHub token.
 * @param scope CoroutineScope for background network operations.
 * @param onOverlayLog Callback to log messages to the UI.
 */
class UpdateDelegate(
    private val application: Application,
    private val settingsViewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val onOverlayLog: (String) -> Unit
) {
    private val _updateStatus = MutableStateFlow<String?>(null)
    /** Current status message of the update process (e.g., "Downloading..."). */
    val updateStatus = _updateStatus.asStateFlow()

    private val _updateVersion = MutableStateFlow<String?>(null)
    /** The version string of the detected update. */
    val updateVersion = _updateVersion.asStateFlow()

    private val _showUpdateWarning = MutableStateFlow<Boolean>(false)
    /** Whether to show the update confirmation dialog. */
    val showUpdateWarning = _showUpdateWarning.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    /** Detailed message explaining the update (Upgrade/Downgrade/Reinstall). */
    val updateMessage = _updateMessage.asStateFlow()

    private var pendingUpdateAssetUrl: String? = null

    /**
     * Checks for experimental updates (pre-releases or debug builds) on GitHub.
     * Updates [updateMessage] and [showUpdateWarning] if an update is found.
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

                // User requirement: Look for the latest pre-release debug build.
                // The release tag (e.g., "latest-debug-v1.5") might contain multiple APKs with different versions.
                // We must find the latest APK version within that release.

                val update = releases.firstOrNull {
                    it.prerelease && it.tagName.startsWith("latest-debug-")
                } ?: releases.firstOrNull { it.tagName.startsWith("latest-debug-") }

                if (update != null) {
                    _updateVersion.value = update.tagName

                    // Find the APK asset with the highest version by parsing filename
                    val validAssets = update.assets.filter {
                        it.name.endsWith(".apk") && it.name.contains("debug") && VersionUtils.extractVersionFromFilename(it.name) != null
                    }

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
