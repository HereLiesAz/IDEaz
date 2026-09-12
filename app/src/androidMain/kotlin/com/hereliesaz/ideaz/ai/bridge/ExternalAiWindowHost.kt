package com.hereliesaz.ideaz.ai.bridge

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import android.view.WindowManager
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.BuildConfig
import com.hereliesaz.ideaz.services.ExternalAiOverlayService

/**
 * Presentation strategies for an installed AI app.
 *
 * OVERLAY_SHELL and BUBBLE are visual shells: the real target app remains a
 * separate Android task underneath, while IDEaz draws only the surrounding
 * chrome, leaving the live app/touch region uncovered. FREEFORM asks Android to
 * launch the real app in a bounded system window. EMBEDDED is best-effort: true
 * cross-app Activity Embedding is honored only when the target explicitly opts
 * in, so we request adjacent/bounded launch and fall back naturally when Android
 * refuses it.
 */
enum class ExternalAiWindowMode(val wireName: String) {
    OVERLAY_SHELL("overlay_shell"),
    FREEFORM("freeform"),
    BUBBLE("bubble"),
    EMBEDDED("embedded"),
    FULLSCREEN("fullscreen");

    companion object {
        fun fromWireName(value: String?): ExternalAiWindowMode =
            entries.firstOrNull { it.wireName == value } ?: OVERLAY_SHELL
    }
}

object ExternalAiWindowHost {
    const val PREF_WINDOW_MODE = "external_ai_window_mode"

    fun preferredMode(context: Context): ExternalAiWindowMode =
        ExternalAiWindowMode.fromWireName(
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_WINDOW_MODE, ExternalAiWindowMode.OVERLAY_SHELL.wireName)
        )

    fun setPreferredMode(context: Context, mode: ExternalAiWindowMode) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_WINDOW_MODE, mode.wireName)
            .apply()
    }

    fun launch(
        context: Context,
        targetIntent: Intent,
        label: String,
        mode: ExternalAiWindowMode = preferredMode(context),
    ) {
        val intent = Intent(targetIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        when (mode) {
            ExternalAiWindowMode.OVERLAY_SHELL -> {
                if (startShell(context, label, compact = false)) {
                    context.startActivity(intent)
                } else {
                    launchFreeform(context, intent, adjacent = false)
                }
            }
            ExternalAiWindowMode.BUBBLE -> {
                // Android does not expose a public API that lets one app forcibly
                // bubble a third-party app. A compact four-sided overlay shell
                // produces the same floating-window illusion while leaving the
                // target's live surface and touch handling entirely its own.
                if (startShell(context, label, compact = true)) {
                    context.startActivity(intent)
                } else {
                    launchFreeform(context, intent, adjacent = false, compact = true)
                }
            }
            ExternalAiWindowMode.FREEFORM -> launchFreeform(context, intent, adjacent = false)
            ExternalAiWindowMode.EMBEDDED -> launchFreeform(context, intent, adjacent = true)
            ExternalAiWindowMode.FULLSCREEN -> context.startActivity(intent)
        }
    }

    fun stopShell(context: Context) {
        if (!BuildConfig.EXTERNAL_AI_OVERLAY) return
        runCatching {
            context.startService(
                Intent(context, ExternalAiOverlayService::class.java)
                    .setAction(ExternalAiOverlayService.ACTION_HIDE)
            )
        }
    }

    private fun startShell(context: Context, label: String, compact: Boolean): Boolean {
        if (!BuildConfig.EXTERNAL_AI_OVERLAY || !Settings.canDrawOverlays(context)) return false
        return runCatching {
            context.startForegroundService(
                Intent(context, ExternalAiOverlayService::class.java)
                    .setAction(ExternalAiOverlayService.ACTION_SHOW)
                    .putExtra(ExternalAiOverlayService.EXTRA_LABEL, label)
                    .putExtra(ExternalAiOverlayService.EXTRA_COMPACT, compact)
            )
            true
        }.getOrDefault(false)
    }

    private fun launchFreeform(
        context: Context,
        intent: Intent,
        adjacent: Boolean,
        compact: Boolean = false,
    ) {
        val wm = context.getSystemService(WindowManager::class.java)
        val screen = wm.maximumWindowMetrics.bounds
        val widthFraction = if (compact) 0.62f else 0.86f
        val heightFraction = if (compact) 0.58f else 0.82f
        val width = (screen.width() * widthFraction).toInt()
        val height = (screen.height() * heightFraction).toInt()
        val left = screen.left + (screen.width() - width) / 2
        val top = screen.top + (screen.height() - height) / 2
        val bounds = Rect(left, top, left + width, top + height)
        if (adjacent) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)

        val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
        runCatching { context.startActivity(intent, options.toBundle()) }
            .getOrElse { context.startActivity(intent) }
    }
}
