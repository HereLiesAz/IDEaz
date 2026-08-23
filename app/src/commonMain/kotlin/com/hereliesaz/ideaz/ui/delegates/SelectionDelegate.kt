package com.hereliesaz.ideaz.ui.delegates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns "select mode": the state of the tap-an-element gesture that starts the
 * visual edit loop.
 *
 * This replaces the former `OverlayDelegate`, which drove a
 * `TYPE_APPLICATION_OVERLAY` window, a foreground service, a persistent
 * notification, and a MediaProjection screenshot pipeline. None of that was ever
 * needed: the element being tapped lives in *this app's own WebView*, so the
 * gesture is an in-app Compose layer and requires no permissions at all.
 *
 * Three concrete bugs died with that machinery:
 *  - Select mode was gated on `SYSTEM_ALERT_WINDOW`, and the permission bounce
 *    returned *before* setting the mode flag, so the first tap always no-opped.
 *  - Selecting an element set the flag directly instead of going through the
 *    toggle, so the service that painted a device-wide dim scrim was never
 *    stopped and the scrim outlived the app.
 *  - Screen capture was gated to Android-target projects, which are not
 *    selectable, so the whole capture path was dormant code carrying a
 *    dangerous permission.
 */
class SelectionDelegate(
    private val onLog: (String) -> Unit,
) {

    private val _isSelectMode = MutableStateFlow(false)

    /** True while the user is picking an element in the preview. */
    val isSelectMode = _isSelectMode.asStateFlow()

    private val _isContextualChatVisible = MutableStateFlow(false)

    /** True while the prompt panel for a tapped element is on screen. */
    val isContextualChatVisible = _isContextualChatVisible.asStateFlow()

    /**
     * The element the user tapped, as the bridge reported it. Prefixed onto the
     * next prompt so the model knows what is being talked about. See
     * `AiRepoContext.systemPreamble` for how the model is told to read it.
     */
    var pendingContextInfo: String? = null
        private set

    /** Enter or leave select mode. No permissions, no services, no side effects. */
    fun toggleSelectMode(enable: Boolean) {
        _isSelectMode.value = enable
    }

    /**
     * Called when the WebView bridge delivers context for a tapped element.
     * Shows the prompt panel with that element attached.
     */
    fun onWebElementContext(json: String) {
        pendingContextInfo = json
        _isSelectMode.value = false
        _isContextualChatVisible.value = true
    }

    /** The user tapped but the bridge could not identify an element. */
    fun onSelectionMissed() {
        _isSelectMode.value = false
        onLog("[IDE] Nothing selectable at that point. Try tapping directly on an element.\n")
    }

    /** Dismiss the prompt panel without sending anything. */
    fun dismissContextualChat() {
        _isContextualChatVisible.value = false
        pendingContextInfo = null
    }

    /** Clear context when the project changes, so it can't leak across projects. */
    fun clearContext() {
        pendingContextInfo = null
        _isContextualChatVisible.value = false
        _isSelectMode.value = false
    }
}
