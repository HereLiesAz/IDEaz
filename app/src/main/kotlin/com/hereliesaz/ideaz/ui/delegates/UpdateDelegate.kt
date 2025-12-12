package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import com.hereliesaz.ideaz.BuildConfig
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.ApkInstaller
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
import kotlin.math.max

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

                // We check for general debug builds
                val update = releases.firstOrNull {
                    it.tagName.startsWith("latest-debug-")
                }

                if (update != null) {
                    _updateVersion.value = update.tagName
                    val asset = update.assets.firstOrNull { it.name.endsWith(".apk") }
                    pendingUpdateAssetUrl = asset?.browserDownloadUrl

                    if (pendingUpdateAssetUrl != null) {
                        val remoteVersion = Regex("IDEaz-(.*)-debug\\.apk").find(asset!!.name)?.groupValues?.get(1)
                        val localVersion = BuildConfig.VERSION_NAME

                        if (remoteVersion != null) {
                            val diff = compareVersions(remoteVersion, localVersion)
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
                        onOverlayLog("Update found but no APK asset.")
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

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
