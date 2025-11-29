// IAIOverlayCallback.aidl
package com.hereliesaz.ideaz;

oneway interface IAIOverlayCallback {
    /**
     * Called when the user submits a prompt from the overlay UI.
     */
    void onPromptSubmitted(String resourceId, String prompt);
}