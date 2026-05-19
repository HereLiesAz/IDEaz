package com.hereliesaz.ideaz.ai.bridge

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.ConversationalAiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Routes prompts through the user's installed Gemini app. Sends the prompt
 * via [Intent.ACTION_SEND] (the Gemini app is registered as a share target
 * for `text/plain`) and reads the response off the Gemini app's UI via the
 * companion [GeminiAppBridgeAccessibilityService].
 *
 * The user must have granted the accessibility service permission. If
 * granted but the Gemini app isn't installed, [chat] throws; the factory
 * wraps this adapter with a fallback to the standard Gemini API.
 *
 * Conversation history is **not** replayed. The Gemini app maintains its
 * own session state. Each [chat] call forwards only the last user turn.
 */
class GeminiAppBridgeAdapter(
    private val context: Context,
) : ConversationalAiClient {

    override suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.Main) {
        if (!isAccessibilityServiceEnabled(context)) {
            error("Gemini app bridge: accessibility service not enabled.")
        }

        val geminiPackage = resolveGeminiPackage(context)
            ?: error("Gemini app bridge: Gemini app not installed.")

        val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content
            ?: error("Gemini app bridge: no user message to forward.")

        GeminiAppBridge.reset()
        GeminiAppBridge.pendingPrompt = lastUserMessage
        GeminiAppBridge.isWaiting = true

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, lastUserMessage)
            setPackage(geminiPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(sendIntent)

        try {
            withTimeout(TIMEOUT_MS) {
                GeminiAppBridge.channel.receive()
            }
        } catch (e: TimeoutCancellationException) {
            GeminiAppBridge.isWaiting = false
            error("Gemini app bridge: no response within ${TIMEOUT_MS / 1000}s.")
        }
    }

    companion object {
        private const val TIMEOUT_MS = 90_000L

        private val SERVICE_NAME =
            "com.hereliesaz.ideaz/com.hereliesaz.ideaz.ai.bridge.GeminiAppBridgeAccessibilityService"

        private val GEMINI_PACKAGES = listOf(
            "com.google.android.apps.bard",
            "com.google.android.googlequicksearchbox",
        )

        /**
         * Returns true if the IDEaz accessibility service is currently
         * enabled by the user. Reads the system setting directly because
         * [AccessibilityManager.getEnabledAccessibilityServiceList] only
         * reflects services with matching feedback types.
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val setting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return setting.split(':').any { it.equals(SERVICE_NAME, ignoreCase = true) }
        }

        /**
         * Returns the first Gemini-capable package that's installed, or null.
         */
        fun resolveGeminiPackage(context: Context): String? {
            val pm = context.packageManager
            for (pkg in GEMINI_PACKAGES) {
                runCatching {
                    pm.getPackageInfo(pkg, 0)
                    return pkg
                }
            }
            return null
        }

        /**
         * Intent that opens the system Accessibility settings, where the
         * user enables the IDEaz service. UI code uses this to launch
         * during the first-run flow or from the settings row.
         */
        fun openAccessibilitySettingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/**
 * Thin decorator: runs [primary], falls back to [fallback] on any
 * non-cancellation exception. Used by [AiAdapterFactory] to make the bridge
 * silently fall through to the Gemini API when the bridge can't operate.
 */
class FallbackAdapter(
    private val primary: ConversationalAiClient,
    private val fallback: ConversationalAiClient,
    private val onFallback: (Throwable) -> Unit = {},
) : ConversationalAiClient {
    override suspend fun chat(messages: List<ChatMessage>): String = try {
        primary.chat(messages)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        onFallback(e)
        fallback.chat(messages)
    }
}

