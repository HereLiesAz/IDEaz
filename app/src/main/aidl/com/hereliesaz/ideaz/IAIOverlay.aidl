// IAIOverlay.aidl
package com.hereliesaz.ideaz;

import com.hereliesaz.ideaz.IAIOverlayCallback;

oneway interface IAIOverlay {
    /**
     * Registers the viewmodel callback with the service.
     */
    void registerCallback(IAIOverlayCallback callback);

    /**
     * Unregisters the viewmodel callback.
     */
    void unregisterCallback(IAIOverlayCallback callback);

    /**
     * Called by the viewmodel to tell the overlay to show a prompt
     * for a given resourceId.
     */
    void showPromptFor(String resourceId);

    /**
     * Streams a new AI log message to the overlay.
     */
    void onAILogMessage(String message);

    /**
     * Called when the task is finished, to hide the UI.
     */
    void onTaskFinished();
}