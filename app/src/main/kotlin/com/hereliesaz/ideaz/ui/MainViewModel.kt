package com.hereliesaz.ideaz.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.api.GitHubApiClient
import com.hereliesaz.ideaz.api.GitHubRepoResponse
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.services.CrashReportingService
import com.hereliesaz.ideaz.ui.delegates.*
import com.hereliesaz.ideaz.ui.editor.EditorViewModel
import com.hereliesaz.ideaz.utils.ErrorCollector
import com.hereliesaz.ideaz.ui.web.WebProjectUrlUtils
import com.hereliesaz.ideaz.R
import com.hereliesaz.ideaz.utils.ProjectAnalyzer
import com.hereliesaz.ideaz.utils.ProjectFileObserver
import com.hereliesaz.ideaz.utils.VersionUtils
import com.hereliesaz.ideaz.ai.AiAdapterFactory
import com.hereliesaz.ideaz.ai.local.LocalProviderException
import com.hereliesaz.ideaz.ai.local.LocalEditApprovalRequiredException
import com.hereliesaz.ideaz.ai.local.LocalCloudConsultApprovalRequiredException
import com.hereliesaz.ideaz.ai.local.CLOUD_CONSULT_USED_MARKER
import com.hereliesaz.ideaz.ai.local.LocalRecoveryAction
import com.hereliesaz.ideaz.ai.ChatMessage
import com.hereliesaz.ideaz.ai.GeminiAdapter
import com.hereliesaz.ideaz.ai.GeminiConsultant
import com.hereliesaz.ideaz.ai.IdeTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The central ViewModel for the IDEaz application.
 *
 * **Role:**
 * This class serves as the "Brain" of the IDE. It orchestrates the interaction between:
 * - The UI layer (Screens, Composables).
 * - The background build system ([BuildService]).
 * - The AI agent ([AIDelegate]).
 * - The Version Control System ([GitDelegate]).
 * - The Host Environment (WebView).
 *
 * **Architecture:**
 * To avoid a "God Class" anti-pattern, logic is split into specialized [Delegates]:
 * - [AIDelegate]: Manages AI sessions and Jules API calls.
 * - [BuildDelegate]: Manages the connection to the remote BuildService and build execution.
 * - [GitDelegate]: Manages local Git operations (clone, commit, push).
 * - [RepoDelegate]: Manages remote repository operations (GitHub API).
 * - [OverlayDelegate]: Manages the visual overlay and selection mode.
 * - [StateDelegate]: Centralizes shared UI state (logs, progress).
 * - [SystemEventDelegate]: Handles system broadcasts (screen on/off, package changes).
 * - [UpdateDelegate]: Handles self-updates.
 *
 * @param application The Android Application context.
 * @param settingsViewModel The ViewModel for accessing and modifying user settings.
 */
class MainViewModel(
    application: Application,
    val settingsViewModel: SettingsViewModel
) : AndroidViewModel(application) {

    // --- Sub-ViewModels ---

    /**
     * Lazy instantiation of [EditorViewModel] to avoid overhead if the editor is not used.
     */
    val editorViewModel: EditorViewModel by lazy {
        EditorViewModel()
    }

    // --- Core Infrastructure ---

    /**
     * Shared State Delegate. Holds all StateFlows used by the UI.
     */
    val stateDelegate = StateDelegate(viewModelScope)

    init {
        // Start observing system logs (logcat) immediately.
        viewModelScope.launch {
            com.hereliesaz.ideaz.utils.LogcatReader.observe().collect {
                stateDelegate.appendSystemLog(it)
            }
        }
        settingsViewModel.getAppName()?.let { appName ->
            viewModelScope.launch {
                val recovered = withContext(Dispatchers.IO) {
                    IdeTools(settingsViewModel.getProjectPath(appName)).reconcileEditCheckpoints()
                }
                recovered.firstOrNull()?.let { review ->
                    // createEditCheckpoint's caller-supplied message (persisted as
                    // message.txt alongside the snapshot) already records which
                    // adapter opened it - e.g. "IDEaz: checkpoint before Claude
                    // edit" - reading it back gives an honest source instead of
                    // hardcoding "on-device" for every recovered edit regardless
                    // of which provider actually made it.
                    val message = runCatching {
                        java.io.File(review.checkpoint.snapshotPath, "message.txt").readText()
                    }.getOrNull()
                    val source = message
                        ?.substringAfter("checkpoint before ", missingDelimiterValue = "")
                        ?.removeSuffix(" edit")
                        ?.takeIf { it.isNotBlank() }
                        ?: "an interrupted session"
                    stateDelegate.setLocalEditReview(
                        LocalEditReviewState(
                            com.hereliesaz.ideaz.ai.local.LocalEditApproval(
                                review,
                                "Recovered an edit from $source for review.",
                                source = source,
                            )
                        )
                    )
                    if (recovered.size > 1) {
                        // The review UI only has one pending-review slot; the rest
                        // aren't lost (they stay in `review` status and will
                        // resurface here once this one is resolved and the app is
                        // relaunched) but were previously dropped with no
                        // indication anything else needed attention.
                        stateDelegate.appendAiLog(
                            "[AI] ${recovered.size - 1} more interrupted edit(s) are pending review " +
                                "for this project - they'll appear after this one is resolved."
                        )
                    }
                }
            }
        }
    }

    // --- Delegation Glue ---

    /**
     * Anonymous implementation of [LogHandler] to pass to delegates.
     * This acts as the "bridge" allowing delegates to push logs/progress back to the [StateDelegate]
     * without knowing about the specific implementation details or coupling directly to [MainViewModel].
     */
    private val logHandler = object : LogHandler {
        override fun onBuildLog(msg: String) { stateDelegate.appendBuildLog(msg) }

        override fun onAiLog(msg: String) {
            stateDelegate.appendAiLog(msg)
            // Broadcast for the Overlay UI (which runs in a separate Service process/context)
            application.sendBroadcast(Intent("com.hereliesaz.ideaz.AI_LOG").apply {
                putExtra("MESSAGE", msg)
                setPackage(application.packageName)
            })
        }

        override fun onProgress(p: Int?) { stateDelegate.setLoadingProgress(p) }

        override fun onGitProgress(p: Int, t: String) {
            stateDelegate.setLoadingProgress(if (p >= 100) null else p)
            stateDelegate.appendBuildLog("[GIT] $t\n")
        }

        override fun onOverlayLog(msg: String) {
             stateDelegate.appendAiLog(msg) // Fallback to AI log for now
        }
    }

    // --- Delegate Initialization ---

    val aiDelegate = AIDelegate(
        settingsViewModel,
        viewModelScope,
        logHandler::onAiLog,
        { diff -> applyUnidiffPatchInternal(diff) },
        geminiAdapterFactory = { apiKey, appName -> geminiAdapterFor(apiKey, appName) },
        // Just nudge the file tree — the WebView already reloads via
        // ProjectFileObserver when files actually change on disk, so a
        // hard reload here would be redundant work for every prompt.
        onFilesChanged = { stateDelegate.triggerFileTreeReload() },
        aiClientProvider = { model ->
            val appName = settingsViewModel.getAppName() ?: return@AIDelegate null
            AiAdapterFactory.create(
                model = model,
                context = getApplication(),
                tools = IdeTools(settingsViewModel.getProjectPath(appName)),
                settings = settingsViewModel,
            )
        },
        // PR-based Android loop: when Jules opens a PR, auto-merge it and rebuild +
        // re-sideload the APK. buildDelegate is declared below; the lambda reads the
        // property at call time, so the forward reference is fine.
        onAgentPullRequest = { url -> buildDelegate.installFromMergedPr(url) },
    )

    /**
     * Send a user message to the conversational AI chat.
     *
     * Creates a [GeminiAdapter] on demand (reads API key and project path at call time
     * so the user can set the key after app launch). Appends the user message and the
     * model's response to [stateDelegate.chatMessages]. Triggers a hard WebView reload
     * after each response in case Gemini wrote files.
     */
    // Cache the GeminiAdapter keyed by (apiKey, appName). Recreating it per message
    // throws away the lazy-init google-genai Client; on a long conversation that's
    // dozens of redundant builder calls. The adapter is rebuilt only when the user
    // changes their API key or switches projects.
    private var cachedGeminiAdapter: GeminiAdapter? = null
    private var cachedGeminiKey: Pair<String, String>? = null

    private fun geminiAdapterFor(apiKey: String, appName: String): GeminiAdapter {
        val key = apiKey to appName
        val existing = cachedGeminiAdapter
        if (existing != null && cachedGeminiKey == key) return existing
        val projectDir = settingsViewModel.getProjectPath(appName)
        val fresh = GeminiAdapter(apiKey = apiKey, tools = IdeTools(projectDir))
        cachedGeminiAdapter = fresh
        cachedGeminiKey = key
        return fresh
    }

    fun sendChatMessage(text: String) = sendChatMessage(text, emptyList())

    /** True when the one-shot fallback button can construct the approved Gemini client. */
    fun hasCloudFallbackCredential(): Boolean =
        !settingsViewModel.getApiKey(SettingsViewModel.KEY_GOOGLE_API_KEY).isNullOrBlank()

    /** Replays the existing conversation against the local provider without adding another user turn. */
    fun retryLocalFailure(diagnosticId: String) {
        recoverFromLocalFailure(diagnosticId, useCloud = false)
    }

    /**
     * One-shot, user-approved Gemini fallback. Nothing is transmitted until this
     * method is called from the explicit disclosure button in [AiChatTab].
     */
    fun approveCloudFallback(diagnosticId: String) {
        recoverFromLocalFailure(diagnosticId, useCloud = true)
    }

    private fun recoverFromLocalFailure(diagnosticId: String, useCloud: Boolean) {
        val failure = stateDelegate.chatFailure.value ?: return
        val action = if (useCloud) LocalRecoveryAction.CLOUD_ONCE else LocalRecoveryAction.RETRY_LOCAL
        if (!failure.permits(action, diagnosticId, hasCloudFallbackCredential())) return
        val appName = settingsViewModel.getAppName() ?: return
        val model = if (useCloud) AiModels.GEMINI else AiModels.LOCAL
        val client = AiAdapterFactory.create(
            model = model,
            context = getApplication(),
            tools = IdeTools(settingsViewModel.getProjectPath(appName)),
            settings = settingsViewModel,
        ) ?: return

        stateDelegate.setChatFailure(null)
        stateDelegate.setChatLoading(true)
        viewModelScope.launch {
            try {
                val response = client.chat(stateDelegate.chatMessages.value)
                stateDelegate.appendChatMessage(ChatMessage("model", response, model.displayName))
                stateDelegate.triggerWebHardReload()
            } catch (e: LocalEditApprovalRequiredException) {
                stateDelegate.setLocalEditReview(LocalEditReviewState(e.approval))
            } catch (e: LocalCloudConsultApprovalRequiredException) {
                stateDelegate.setLocalCloudConsult(
                    LocalCloudConsultState(e.request.boundTo(stateDelegate.chatMessages.value))
                )
            } catch (e: LocalProviderException) {
                stateDelegate.setChatFailure(e.failure)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (useCloud) {
                    val id = com.hereliesaz.ideaz.ai.local.LocalProviderDiagnostics.record(
                        kind = com.hereliesaz.ideaz.ai.local.LocalProviderFailureKind.GENERATION_FAILED,
                        runtimeId = "gemini-cloud",
                        cause = e,
                    )
                    stateDelegate.setChatFailure(
                        failure.copy(
                            message = "Gemini fallback failed. ${failure.message}",
                            diagnosticId = id,
                        )
                    )
                } else {
                    stateDelegate.setChatFailure(failure)
                }
            } finally {
                stateDelegate.setChatLoading(false)
            }
        }
    }

    fun sendChatMessage(text: String, referenceParts: List<com.hereliesaz.ideaz.ai.ChatPart>) {
        if (stateDelegate.localCloudConsult.value != null) return
        val editStatus = stateDelegate.localEditReview.value?.status
        if (editStatus == LocalEditReviewStatus.PENDING || editStatus == LocalEditReviewStatus.PROCESSING) return
        val appName = settingsViewModel.getAppName()
        if (appName == null) {
            stateDelegate.appendChatMessage(ChatMessage.error("Error: No project open."))
            return
        }

        // Route through the AI provider factory so the user's chosen default
        // model (Gemini, Nano, Groq, Cerebras, HF, Mistral, etc.) backs the
        // chat tab the same way it backs contextual prompts.
        val modelId = settingsViewModel.getAiAssignment(SettingsViewModel.KEY_AI_ASSIGNMENT_DEFAULT)
        val model = AiModels.findById(modelId) ?: AiModels.GEMINI
        val projectDir = settingsViewModel.getProjectPath(appName)
        val tools = IdeTools(projectDir)
        val client = AiAdapterFactory.create(
            model = model,
            context = getApplication(),
            tools = tools,
            settings = settingsViewModel,
        )
        if (client == null) {
            val msg = when (model.id) {
                AiModels.JULES_DEFAULT ->
                    "Error: Jules is not supported in the chat tab. Pick a different provider in Settings."
                else ->
                    "Error: No API key set for ${model.displayName}. Go to Settings → AI Providers."
            }
            stateDelegate.appendChatMessage(ChatMessage.error(msg))
            return
        }

        val userParts = buildList {
            add(com.hereliesaz.ideaz.ai.ChatPart.Text(text))
            addAll(referenceParts)
        }
        stateDelegate.appendChatMessage(ChatMessage("user", userParts))
        stateDelegate.setChatFailure(null)
        stateDelegate.setChatLoading(true)

        viewModelScope.launch {
            try {
                val response = client.chat(stateDelegate.chatMessages.value)
                stateDelegate.appendChatMessage(ChatMessage("model", response, model.displayName))
                // Any file writes have already happened inside the tool-use loop;
                // hard-reload so the WebView picks up the changes immediately.
                stateDelegate.triggerWebHardReload()
            } catch (e: LocalEditApprovalRequiredException) {
                stateDelegate.localEditReview.value
                    ?.takeIf { it.status == LocalEditReviewStatus.APPROVED }
                    ?.approval?.review?.checkpoint
                    ?.let { previous ->
                        withContext(Dispatchers.IO) {
                            runCatching {
                                IdeTools(File(previous.projectPath)).discardEditCheckpoint(previous)
                            }
                        }
                    }
                stateDelegate.setLocalEditReview(LocalEditReviewState(e.approval))
            } catch (e: LocalCloudConsultApprovalRequiredException) {
                stateDelegate.setLocalCloudConsult(
                    LocalCloudConsultState(e.request.boundTo(stateDelegate.chatMessages.value))
                )
            } catch (e: LocalProviderException) {
                stateDelegate.setChatFailure(e.failure)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stateDelegate.appendChatMessage(
                    ChatMessage.error("Error: ${e.message}")
                )
            } finally {
                stateDelegate.setChatLoading(false)
            }
        }
    }

    /** Performs the exact previewed, tool-less Gemini request after one-shot consent. */
    fun approveLocalCloudConsult(requestId: String) {
        val pending = stateDelegate.localCloudConsult.value ?: return
        if (pending.status != LocalCloudConsultStatus.PENDING) return
        val request = pending.request
        val appName = settingsViewModel.getAppName() ?: return
        val projectPath = settingsViewModel.getProjectPath(appName).canonicalPath
        val messages = stateDelegate.chatMessages.value
        if (!request.matches(requestId, projectPath, messages)) return
        val apiKey = settingsViewModel.getGoogleApiKey().orEmpty()
        if (apiKey.isBlank() && pending.advice == null) {
            stateDelegate.setLocalCloudConsult(pending.copy(error = "Add a Gemini API key in Settings."))
            return
        }
        stateDelegate.setLocalCloudConsult(pending.copy(status = LocalCloudConsultStatus.PROCESSING, error = null))
        stateDelegate.setChatLoading(true)
        viewModelScope.launch {
            var advice = pending.advice
            try {
                if (advice == null) {
                    advice = GeminiConsultant(apiKey, request.model).consult(request.prompt)
                    stateDelegate.setLocalCloudConsult(
                        pending.copy(status = LocalCloudConsultStatus.PROCESSING, error = null, advice = advice)
                    )
                }
                resumeLocalAfterCloudConsult(requestId, checkNotNull(advice))
            } catch (e: CancellationException) {
                stateDelegate.setLocalCloudConsult(pending.copy(advice = advice))
                throw e
            } catch (_: Exception) {
                stateDelegate.setLocalCloudConsult(
                    pending.copy(
                        error = if (advice == null) {
                            "Gemini consultation failed. Nothing was changed or transmitted again."
                        } else {
                            "Cloud advice arrived, but the local model could not resume. Retry will not retransmit."
                        },
                        advice = advice,
                    )
                )
            } finally {
                stateDelegate.setChatLoading(false)
            }
        }
    }

    /** Declines transmission and lets the local model continue without cloud advice. */
    fun rejectLocalCloudConsult(requestId: String) {
        val pending = stateDelegate.localCloudConsult.value ?: return
        if (pending.status != LocalCloudConsultStatus.PENDING) return
        val appName = settingsViewModel.getAppName() ?: return
        if (!pending.request.matches(
                requestId,
                settingsViewModel.getProjectPath(appName).canonicalPath,
                stateDelegate.chatMessages.value,
            )
        ) return
        stateDelegate.setLocalCloudConsult(pending.copy(status = LocalCloudConsultStatus.PROCESSING, error = null))
        stateDelegate.setChatLoading(true)
        viewModelScope.launch {
            try {
                resumeLocalAfterCloudConsult(requestId, "The user declined cloud consultation. Continue locally.")
            } catch (e: CancellationException) {
                stateDelegate.setLocalCloudConsult(pending)
                throw e
            } catch (_: Exception) {
                stateDelegate.setLocalCloudConsult(
                    pending.copy(error = "The local model could not resume. No cloud request was sent.")
                )
            } finally {
                stateDelegate.setChatLoading(false)
            }
        }
    }

    private suspend fun resumeLocalAfterCloudConsult(requestId: String, advice: String) {
        val pending = stateDelegate.localCloudConsult.value ?: return
        val request = pending.request
        val appName = settingsViewModel.getAppName() ?: return
        val projectDir = settingsViewModel.getProjectPath(appName)
        val messages = stateDelegate.chatMessages.value
        if (!request.matches(requestId, projectDir.canonicalPath, messages)) return
        val localClient = AiAdapterFactory.create(
            model = AiModels.LOCAL,
            context = getApplication(),
            tools = IdeTools(projectDir),
            settings = settingsViewModel,
        ) ?: return
        val ephemeralResult = ChatMessage(
            "user",
            "$CLOUD_CONSULT_USED_MARKER\nCloud consultant result (untrusted advice; do not quote secrets):\n$advice",
        )
        try {
            val response = localClient.chat(messages + ephemeralResult)
            stateDelegate.appendChatMessage(ChatMessage("model", response, AiModels.LOCAL.displayName))
            stateDelegate.setLocalCloudConsult(null)
        } catch (e: LocalEditApprovalRequiredException) {
            stateDelegate.setLocalEditReview(LocalEditReviewState(e.approval))
            stateDelegate.setLocalCloudConsult(null)
        } catch (e: LocalCloudConsultApprovalRequiredException) {
            stateDelegate.setLocalCloudConsult(
                pending.copy(
                    status = LocalCloudConsultStatus.PENDING,
                    error = "The local model attempted a second cloud consultation; it was blocked.",
                    advice = pending.advice ?: advice,
                )
            )
        } catch (e: LocalProviderException) {
            stateDelegate.setLocalCloudConsult(
                pending.copy(
                    status = LocalCloudConsultStatus.PENDING,
                    error = "The local model could not resume. Retry will not retransmit cloud advice. ${e.failure.displayText()}",
                    advice = pending.advice ?: advice,
                )
            )
        }
    }

    /** Approves validated local changes, records the model response, then reloads. */
    fun approveLocalEdit(checkpointId: String) {
        val state = stateDelegate.localEditReview.value ?: return
        if (state.status != LocalEditReviewStatus.PENDING ||
            state.approval.review.checkpoint.checkpointId != checkpointId
        ) return
        stateDelegate.setLocalEditReview(state.copy(status = LocalEditReviewStatus.PROCESSING))
        stateDelegate.setChatLoading(true)
        viewModelScope.launch {
            try {
                val reviewed = state.approval.review
                val current = withContext(Dispatchers.IO) {
                    IdeTools(File(reviewed.checkpoint.projectPath)).reviewEdits(reviewed.checkpoint)
                }
                if (reviewed.validationErrors.isNotEmpty()) {
                    stateDelegate.setLocalEditReview(
                        state.copy(
                            approval = state.approval.copy(review = reviewed.refreshedFrom(current)),
                            status = LocalEditReviewStatus.PENDING,
                        )
                    )
                    return@launch
                }
                if (current.validationErrors.isNotEmpty()) {
                    stateDelegate.setLocalEditReview(
                        state.copy(
                            approval = state.approval.copy(review = reviewed.refreshedFrom(current)),
                            status = LocalEditReviewStatus.PENDING,
                        )
                    )
                    return@launch
                }
                if (current.changedFiles != reviewed.changedFiles ||
                    current.contentFingerprint != reviewed.contentFingerprint
                ) {
                    // Previously a bare check() here threw into the generic catch
                    // below, which just restored the review to its stale PENDING
                    // state verbatim - every future Approve attempt hit the exact
                    // same mismatch forever, since nothing about the review ever
                    // changed. Refreshing against `current` (as the validation-error
                    // branches above already do) means a second Approve tap now
                    // succeeds cleanly once the user has seen the updated diff,
                    // instead of the card being permanently stuck.
                    stateDelegate.setLocalEditReview(
                        state.copy(
                            approval = state.approval.copy(review = reviewed.refreshedFrom(current)),
                            status = LocalEditReviewStatus.PENDING,
                        )
                    )
                    stateDelegate.appendChatMessage(
                        ChatMessage.error("The project changed after this edit was reviewed - re-approve to continue.")
                    )
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    IdeTools(File(reviewed.checkpoint.projectPath))
                        .markEditCheckpointApproved(reviewed.checkpoint)
                }
                stateDelegate.setLocalEditReview(state.copy(status = LocalEditReviewStatus.APPROVED))
                stateDelegate.appendChatMessage(ChatMessage("model", state.approval.response, state.approval.source))
                stateDelegate.triggerFileTreeReload()
                stateDelegate.triggerWebHardReload()
            } catch (_: Exception) {
                stateDelegate.setLocalEditReview(state)
                stateDelegate.appendChatMessage(
                    ChatMessage.error("Approval stopped: the project changed after review.")
                )
            } finally {
                stateDelegate.setChatLoading(false)
            }
        }
    }

    /** Rejects pending changes and restores their pre-edit checkpoint. */
    fun rejectLocalEdit(checkpointId: String) {
        restoreLocalEdit(checkpointId, LocalEditReviewStatus.REJECTED)
    }

    /** Undoes an approved edit only while its changed-file set remains untouched. */
    fun undoLocalEdit(checkpointId: String) {
        restoreLocalEdit(checkpointId, LocalEditReviewStatus.UNDONE)
    }

    private fun restoreLocalEdit(
        checkpointId: String,
        targetStatus: LocalEditReviewStatus,
    ) {
        val state = stateDelegate.localEditReview.value ?: return
        val approval = state.approval
        if (approval.review.checkpoint.checkpointId != checkpointId) return
        if (targetStatus == LocalEditReviewStatus.REJECTED && state.status != LocalEditReviewStatus.PENDING) return
        if (targetStatus == LocalEditReviewStatus.UNDONE && state.status != LocalEditReviewStatus.APPROVED) return
        if (!approval.review.rollbackAllowed) return
        stateDelegate.setLocalEditReview(state.copy(status = LocalEditReviewStatus.PROCESSING))
        stateDelegate.setChatLoading(true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    IdeTools(File(approval.review.checkpoint.projectPath)).restoreEditCheckpoint(
                        approval.review.checkpoint,
                        approval.review.contentFingerprint,
                    )
                }
                stateDelegate.setLocalEditReview(state.copy(status = targetStatus))
                stateDelegate.triggerFileTreeReload()
                stateDelegate.triggerWebHardReload()
            } catch (e: IllegalStateException) {
                // restoreEditCheckpoint's fingerprint check throws exactly this
                // when the files changed since review (protecting against
                // clobbering an intervening manual edit). Previously this just
                // restored the review card to its stale PENDING/APPROVED state
                // verbatim - since nothing about the underlying mismatch ever
                // changes, every future Reject/Undo attempt hit the identical
                // failure forever, permanently blocking the chat tab (a new
                // message is refused while a review is pending). The user's
                // intent here is unambiguous - discard this review - and doing
                // so never writes to disk, so it's always safe: clean up the
                // checkpoint bookkeeping and clear the card instead of leaving
                // it stuck.
                withContext(Dispatchers.IO) {
                    runCatching {
                        IdeTools(File(approval.review.checkpoint.projectPath))
                            .discardEditCheckpoint(approval.review.checkpoint)
                    }
                }
                stateDelegate.setLocalEditReview(null)
                stateDelegate.appendChatMessage(
                    ChatMessage.error("Discarded: the project changed after review, so nothing was restored.")
                )
            } catch (_: Exception) {
                stateDelegate.setLocalEditReview(state)
                stateDelegate.appendChatMessage(
                    ChatMessage.error("Restore stopped: the project changed after review.")
                )
            } finally {
                stateDelegate.setChatLoading(false)
            }
        }
    }

    val overlayDelegate = OverlayDelegate(application, settingsViewModel, viewModelScope, logHandler::onAiLog)

    val gitDelegate = GitDelegate(settingsViewModel, viewModelScope, logHandler::onBuildLog, logHandler::onProgress)

    val buildDelegate = BuildDelegate(
        application,
        settingsViewModel,
        viewModelScope,
        logHandler::onBuildLog,
        logHandler::onAiLog,
        { log -> handleBuildFailure(log) },
        { path ->
            // Web Build Success Callback. `path` is the project's index.html, so its
            // parent is the project root. The project is mounted at the asset-loader
            // root (see WebProjectPathHandler) and loaded from there.
            val projectDir = File(path).parentFile
            stateDelegate.setCurrentWebProjectDir(projectDir)
            stateDelegate.setCurrentWebUrl(WebProjectUrlUtils.localProjectRootUrl())
            stateDelegate.setTargetAppVisible(true) // Switch to "App View"

            // Update EditorViewModel with project context for file browsing
            if (projectDir != null) {
                editorViewModel.setProjectDir(projectDir)
            }
        },
        {
            // Android Build Success Callback
            // Notify System that update is ready (if applicable) or just launch
            val intent = Intent("com.hereliesaz.ideaz.SHOW_UPDATE_POPUP").apply {
                setPackage(application.packageName)
                if (lastPrompt != null) {
                    putExtra("PROMPT", lastPrompt)
                }
            }
            application.sendBroadcast(intent)
            launchTargetApp(application)
        },
        gitDelegate,
        { wwwDir ->
            // Wasm Preview Ready Callback. The compiled binary lives in
            // filesDir/www and is mounted at the asset-loader root; switching to
            // "App View" puts WebProjectHost on screen, which then hot-reloads on
            // every subsequent compile. The editor keeps pointing at the project
            // sources — www holds build output, not editable files.
            stateDelegate.setCurrentWebProjectDir(wwwDir)
            stateDelegate.setCurrentWebUrl(WebProjectUrlUtils.localProjectRootUrl())
            stateDelegate.setTargetAppVisible(true)
        }
    )

    val repoDelegate = RepoDelegate(
        application,
        settingsViewModel,
        viewModelScope,
        logHandler::onBuildLog,
        logHandler::onAiLog,
        logHandler::onProgress,
        logHandler::onGitProgress
    )

    val updateDelegate = UpdateDelegate(application, settingsViewModel, viewModelScope, logHandler::onAiLog)

    // Handle System Events (Broadcasts)
    val systemEventDelegate = SystemEventDelegate(
        application,
        aiDelegate,
        overlayDelegate,
        stateDelegate
    )

    // --- Public State Exposure (Delegated) ---

    // Expose StateFlows directly from delegates to avoid boilerplate duplication
    val loadingProgress = stateDelegate.loadingProgress
    val isTargetAppVisible = stateDelegate.isTargetAppVisible
    val currentWebUrl = stateDelegate.currentWebUrl
    val currentWebProjectDir = stateDelegate.currentWebProjectDir
    val buildLog = stateDelegate.buildLog
    val filteredLog = stateDelegate.filteredLog
    val pendingRoute = stateDelegate.pendingRoute
    val webReloadTrigger = stateDelegate.webReloadTrigger
    val webHardReloadTrigger = stateDelegate.webHardReloadTrigger

    val isSelectMode = overlayDelegate.isSelectMode
    val activeSelectionRect = overlayDelegate.activeSelectionRect
    val isContextualChatVisible = overlayDelegate.isContextualChatVisible
    val requestScreenCapture = overlayDelegate.requestScreenCapture

    // Track the last user prompt to restore context after an app restart
    private var lastPrompt: String? = null

    val ownedRepos = repoDelegate.ownedRepos
    val repoFetchError = repoDelegate.repoFetchError
    val sessions = aiDelegate.sessions
    val commitHistory = gitDelegate.commitHistory
    val branches = gitDelegate.branches
    val gitStatus = gitDelegate.gitStatus
    val updateStatus = updateDelegate.updateStatus
    val updateVersion = updateDelegate.updateVersion
    val showUpdateWarning = updateDelegate.showUpdateWarning
    val updateMessage = updateDelegate.updateMessage
    val julesResponse = aiDelegate.julesResponse
    val julesHistory = aiDelegate.julesHistory
    val isLoadingJulesResponse = aiDelegate.isLoadingJulesResponse
    val julesError = aiDelegate.julesError
    val currentJulesSessionId = aiDelegate.currentJulesSessionId

    // --- Artifact Check State ---

    data class ArtifactCheckResult(
        val remoteVersion: String,
        val downloadUrl: String,
        val localVersion: String?,
        val isRemoteNewer: Boolean
    )
    private val _artifactCheckResult = MutableStateFlow<ArtifactCheckResult?>(null)
    /** Holds the result of a check for remote GitHub artifacts (APKs). */
    val artifactCheckResult = _artifactCheckResult.asStateFlow()

    fun dismissArtifactDialog() { _artifactCheckResult.value = null }

    /**
     * Hides the global "Working..." dialog without cancelling whatever
     * operation is actually running - that operation has no cancellation
     * handle exposed to the UI today. This previously had no dismiss path at
     * all: opening the Clone tab on a slow or offline connection locked the
     * entire app behind an unresponsive modal until the underlying network
     * call timed out on its own. The operation still completes (or fails) in
     * the background and updates its own state normally; this just stops
     * blocking the screen while it does.
     */
    fun dismissLoadingDialog() = stateDelegate.setLoadingProgress(null)

    // --- File Observation ---

    private var fileObserver: ProjectFileObserver? = null

    /**
     * Starts watching the project directory for file changes.
     * Currently used to trigger WebView reloads for Web projects.
     */
    private fun startFileObservation(projectDir: File) {
        fileObserver?.stopWatching()
        fileObserver = ProjectFileObserver(projectDir.absolutePath) {
            stateDelegate.triggerWebReload()
        }
        fileObserver?.startWatching()
    }

    // --- Lifecycle ---

    override fun onCleared() {
        super.onCleared()
        fileObserver?.stopWatching()
        buildDelegate.unbindService(getApplication())
        systemEventDelegate.cleanup()
    }

    /**
     * Public entry point for releasing this ViewModel's resources.
     *
     * This ViewModel is deliberately Application-scoped - constructed once
     * directly in [com.hereliesaz.ideaz.MainApplication.onCreate], not
     * through a [androidx.lifecycle.ViewModelProvider] - so the framework
     * never calls [onCleared] (that's `protected`, and only a
     * `ViewModelStore` invokes it). Exposed here, and called from
     * `MainApplication.onTerminate()`, so the release path is at least
     * reachable rather than permanently dead code.
     */
    fun releaseResources() {
        onCleared()
    }

    /**
     * Called by UI when a screen transition occurs to flush non-fatal errors.
     *
     * **Logic:**
     * 1. Retrieves unique, non-fatal errors collected by [ErrorCollector].
     * 2. If errors exist, starts the [CrashReportingService] with an intent to report them to the configured backend (GitHub/Jules).
     * This ensures that "silent" errors don't pile up without user/dev visibility.
     */
    fun flushNonFatalErrors() {
        val errors = ErrorCollector.getAndClear()
        if (errors != null) {
            val apiKey = settingsViewModel.getApiKey()
            val githubToken = settingsViewModel.getGithubToken()
            val githubUser = settingsViewModel.getGithubUser() ?: "Unknown"
            val reportToGithub = settingsViewModel.isReportIdeErrorsEnabled()

            // See CrashHandler.handleCrash for why this can't require the
            // Jules key alone - a GitHub-only reporting user needs neither it
            // nor a Jules project ID.
            val canReportToGithub = reportToGithub && !githubToken.isNullOrBlank()
            if (!apiKey.isNullOrBlank() || canReportToGithub) {
                val intent = Intent(getApplication(), CrashReportingService::class.java).apply {
                    action = CrashReportingService.ACTION_REPORT_NON_FATAL
                    putExtra(CrashReportingService.EXTRA_API_KEY, apiKey)
                    putExtra(CrashReportingService.EXTRA_JULES_PROJECT_ID, settingsViewModel.getJulesProjectId())
                    putExtra(CrashReportingService.EXTRA_GITHUB_TOKEN, githubToken)
                    putExtra(CrashReportingService.EXTRA_STACK_TRACE, errors)
                    putExtra(CrashReportingService.EXTRA_GITHUB_USER, githubUser)
                    putExtra(CrashReportingService.EXTRA_REPORT_TO_GITHUB, reportToGithub)
                }
                getApplication<Application>().startService(intent)
            }
        }
    }

    // --- Proxy Methods (Forwarding calls to Delegates) ---

    // BUILD Operations
    fun bindBuildService(c: Context) = buildDelegate.bindService(c)
    fun unbindBuildService(c: Context) = buildDelegate.unbindService(c)
    fun startBuild(c: Context, p: File? = null) = buildDelegate.startBuild(p)

    /**
     * Rail "Build" action: (re)build the current project and switch to its
     * preview. Web-like projects verify `index.html` and the build-success
     * callback flips to the WebView preview; Android kicks the remote build,
     * whose success callback launches the app view (`launchTargetApp`). No-ops
     * with a log line when no project is loaded, so the button never silently
     * does nothing.
     */
    fun startBuild() {
        if (settingsViewModel.getAppName().isNullOrBlank()) {
            logHandler.onBuildLog("[IDE] Build skipped: no project loaded. Open or create a project first.\n")
            return
        }
        buildDelegate.startBuild()
    }

    // GIT Operations
    fun refreshGitData() { viewModelScope.launch { gitDelegate.refreshGitData() } }
    fun gitFetch() { viewModelScope.launch { gitDelegate.fetch() } }
    fun gitPull() { viewModelScope.launch { gitDelegate.pull() } }
    fun gitPush() {
        viewModelScope.launch(Dispatchers.IO) {
            gitDelegate.push()
            // After pushing, start polling for artifacts if it's an app that produces them
            val appName = settingsViewModel.getAppName()
            val user = settingsViewModel.getGithubUser()
            if (!appName.isNullOrBlank() && !user.isNullOrBlank()) {
                val type = ProjectType.fromString(settingsViewModel.readProjectType())
                if (type == ProjectType.ANDROID) {
                    startArtifactPolling(user, appName)
                }
            }
        }
    }
    fun gitStash(m: String?) { viewModelScope.launch { gitDelegate.stash(m) } }
    fun gitUnstash() { viewModelScope.launch { gitDelegate.unstash() } }
    fun switchBranch(b: String) { viewModelScope.launch { gitDelegate.switchBranch(b) } }
    fun gitCommit(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch { gitDelegate.commit(message) }
    }

    /**
     * Triggers deployment for Web Projects (GitHub Pages).
     */
    fun deployWebProject() {
        val appName = settingsViewModel.getAppName()
        val projectTypeStr = settingsViewModel.readProjectType()
        val projectType = ProjectType.fromString(projectTypeStr)
        if (!projectType.isWebLike()) return

        viewModelScope.launch {
            logHandler.onBuildLog("Deploying Web Project (Push to GitHub)...")
            try {
                // Ensure GitHub Pages workflow / AGENTS.md / setup script are
                // in the project before deploying. Save & Initialize for PWAs
                // is local-only now, so the first Deploy is what generates
                // these and pushes them.
                repoDelegate.forceUpdateInitFiles()
                // Ensure latest changes are committed
                gitDelegate.commit("Deploy: ${System.currentTimeMillis()}")
                // Use default push (uses settings creds)
                gitDelegate.push()
                logHandler.onBuildLog("Pushed successfully. GitHub Actions will handle deployment.")
                logHandler.onBuildLog("[Instruction] Ensure 'GitHub Pages' is enabled in repository settings (Source: gh-pages branch).")
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.deploy_instruction_gh_pages), Toast.LENGTH_LONG).show()
                }

                // Start Polling for Deployment completion
                val user = settingsViewModel.getGithubUser()
                if (appName != null && user != null) {
                    startWebDeploymentPolling(user, appName)
                }

            } catch (e: Exception) {
                logHandler.onBuildLog("Deploy failed: ${e.message}")
            }
        }
    }

    private fun startWebDeploymentPolling(user: String, repo: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            logHandler.onBuildLog("Polling GitHub Pages status for $user/$repo...")
            val startTime = System.currentTimeMillis()
            val timeout = 10 * 60 * 1000L // 10 minutes

            while (System.currentTimeMillis() - startTime < timeout) {
                val token = settingsViewModel.getGithubToken()
                if (token.isNullOrBlank()) {
                    logHandler.onBuildLog("GitHub token missing, stopping poll.")
                    break
                }

                try {
                    val service = GitHubApiClient.createService(token)
                    val response = service.getPages(user, repo)

                    if (response.isSuccessful) {
                        val body = response.body()
                        val status = body?.status
                        val url = body?.htmlUrl

                        if (status == "built" && url != null) {
                            // Don't swap the WebView to the public URL automatically — the user
                            // is likely still iterating on local edits served from
                            // appassets.androidplatform.net, and silently switching away from
                            // them was a confusing surprise (userflow audit #11). Just announce
                            // the deployed URL prominently; the user can open it in a browser
                            // when they're ready.
                            logHandler.onBuildLog("Deployment successful: $url")
                            logHandler.onAiLog("Deployment successful: $url (open in browser to verify the live site)")
                            break
                        } else {
                            logHandler.onBuildLog("Deployment status: $status...")
                        }
                    } else if (response.code() == 404) {
                        logHandler.onBuildLog("Waiting for GitHub Pages to be available (404)...")
                    } else {
                        logHandler.onBuildLog("Error polling pages: Code ${response.code()}")
                    }

                } catch (e: Exception) {
                    logHandler.onBuildLog("Error polling pages: ${e.message}")
                    android.util.Log.w("MainViewModel", "Operation failed", e)
                }

                delay(15_000) // 15 seconds
            }
        }
    }

    // AI Operations
    //
    // Phase 1 default: every prompt entry point routes through [sendChatMessage] so
    // the GeminiAdapter conversational/tool-use path drives all AI work. The legacy
    // [AIDelegate.startContextualAITask] (Jules + Gemini one-shot) is invoked only
    // when the user has explicitly assigned Jules to a task slot. Without this
    // unification, the rail's Prompt popup, the bottom-sheet log-tab input, and the
    // contextual chat overlay each ran on different code paths with different
    // capabilities (text-only vs tool-using) — a surprise documented in the
    // userflow audit.
    private fun isJulesAssigned(taskKey: String): Boolean =
        settingsViewModel.getAiAssignment(taskKey) == AiModels.JULES_DEFAULT

    fun sendPrompt(p: String?) = sendPrompt(p, emptyList())

    fun sendPrompt(p: String?, referenceParts: List<com.hereliesaz.ideaz.ai.ChatPart>) {
        if (p.isNullOrBlank() && referenceParts.isEmpty()) return
        val text = p.orEmpty()
        lastPrompt = text
        if (isJulesAssigned(SettingsViewModel.KEY_AI_ASSIGNMENT_CONTEXTLESS)) {
            // Jules path can't carry binary parts — it polls for unidiff
            // patches via a GitHub session. Reference parts are dropped here
            // with a note; the chat tab + factory-routed contextual path
            // both handle them correctly.
            val notice = if (referenceParts.isNotEmpty()) {
                "\n\n[${referenceParts.size} reference attachment(s) dropped — Jules can't accept inline files.]"
            } else ""
            aiDelegate.startContextualAITask(text + notice)
        } else {
            sendChatMessage(text, referenceParts)
        }
    }

    fun submitContextualPrompt(p: String) {
        lastPrompt = p
        val context = overlayDelegate.pendingContextInfo ?: "No context"
        val richPrompt = "$context\n\n$p"
        if (isJulesAssigned(SettingsViewModel.KEY_AI_ASSIGNMENT_OVERLAY)) {
            // Phase 2: Jules path retains screenshot attachment when available.
            val base64 = overlayDelegate.pendingBase64Screenshot
            val julesPrompt = if (base64 != null) {
                "$richPrompt\n\n[IMAGE: data:image/png;base64,$base64]"
            } else {
                richPrompt
            }
            aiDelegate.startContextualAITask(julesPrompt)
        } else {
            // Conversational/bridge path. Carry the highlighted screenshot too —
            // ChatMessage supports image parts, and the bridge forwards them.
            val base64 = overlayDelegate.pendingBase64Screenshot
            val parts = if (!base64.isNullOrBlank()) {
                runCatching {
                    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                    listOf<com.hereliesaz.ideaz.ai.ChatPart>(
                        com.hereliesaz.ideaz.ai.ChatPart.Image(bytes, "image/png")
                    )
                }.getOrDefault(emptyList())
            } else emptyList()
            sendChatMessage(richPrompt, parts)
        }
    }

    fun resumeSession(id: String) = aiDelegate.resumeSession(id)
    fun fetchSessionsForRepo(r: String) = aiDelegate.fetchSessionsForRepo(r)

    // OVERLAY Operations
    fun toggleSelectMode(b: Boolean) = overlayDelegate.toggleSelectMode(b)

    fun handleSelection(rect: android.graphics.Rect) {
        overlayDelegate.onSelectionMade(rect)
        // Broadcast specifically for Web inspection if active
        if (stateDelegate.currentWebUrl.value != null) {
            val intent = Intent("com.hereliesaz.ideaz.INSPECT_WEB").apply {
                putExtra("X", rect.centerX().toFloat())
                putExtra("Y", rect.centerY().toFloat())
                setPackage(getApplication<Application>().packageName)
            }
            getApplication<Application>().sendBroadcast(intent)
        }
    }

    /**
     * Receives DOM context JSON from the web bridge when the user taps an element
     * in Select Mode while a PWA/Web project is shown.
     *
     * Logs the raw JSON to the AI console and routes it to [OverlayDelegate] so
     * [isContextualChatVisible] becomes true.
     *
     * @param json  Raw JSON from [WebViewBridge.onElementTapped].
     */
    fun handleWebElementContext(json: String) {
        stateDelegate.appendAiLog("[WEB-ELEMENT] $json")
        overlayDelegate.onWebElementContext(json)
    }

    fun clearSelection() = overlayDelegate.clearSelection()
    fun closeContextualChat() = overlayDelegate.clearSelection()
    fun requestScreenCapturePermission() = overlayDelegate.requestScreenCapturePermission()
    fun screenCaptureRequestHandled() = overlayDelegate.screenCaptureRequestHandled()
    fun setScreenCapturePermission(c: Int, d: Intent?) = overlayDelegate.setScreenCapturePermission(c, d)
    fun hasScreenCapturePermission() = overlayDelegate.hasScreenCapturePermission()
    fun setPendingRoute(r: String?) = stateDelegate.setPendingRoute(r)

    /** Triggers a soft reload of the WebView (no cache bust). */
    fun triggerWebReload() = stateDelegate.triggerWebReload()

    /** Clears the WebView cache and triggers a hard reload. */
    fun triggerWebHardReload() = stateDelegate.triggerWebHardReload()

    // REPO Operations
    fun fetchGitHubRepos() = repoDelegate.fetchGitHubRepos()
    fun scanLocalProjects() = repoDelegate.scanLocalProjects()
    fun getLocalProjectsWithMetadata() = repoDelegate.getLocalProjectsWithMetadata()
    fun forceUpdateInitFiles() = repoDelegate.forceUpdateInitFiles()
    fun uploadProjectSecrets(o: String, r: String) = repoDelegate.uploadProjectSecrets(o, r)

    /** Creates a new repo and initializes the project. */
    fun createGitHubRepository(name: String, desc: String, priv: Boolean, type: ProjectType, pkg: String, ctx: Context, initialPrompt: String? = null, onSuccess: () -> Unit) {
        repoDelegate.createGitHubRepository(name, desc, priv, type, pkg, ctx) { owner, branch ->
            // Local content is either cloned from the template repo (generate
            // flow, handled in RepoDelegate) or scaffolded from the bundled
            // template by saveAndInitialize's ensureTemplate when the directory
            // is still empty (fallback). Either way ensureTemplate is a no-op
            // once files are present, so we don't copy here. The initial prompt
            // is dispatched by saveAndInitialize AFTER scaffolding, so the AI
            // never runs against an empty project.
            saveAndInitialize(name, owner, branch, pkg, type, ctx, initialPrompt)
            onSuccess()
        }
    }

    /** Selects a repo and prepares it for use. */
    fun selectRepositoryForSetup(repo: GitHubRepoResponse, onSuccess: () -> Unit) {
        repoDelegate.selectRepositoryForSetup(repo) { owner, branch ->
            repoDelegate.uploadProjectSecrets(owner, repo.name)
            aiDelegate.fetchSessionsForRepo(repo.fullName)
            repoDelegate.forceUpdateInitFiles()
            onSuccess()
        }
    }

    /**
     * Saves configuration and triggers initial build.
     * Common exit path for Setup/Clone/Load flows.
     */
    fun saveAndInitialize(appName: String, user: String, branch: String, pkg: String, type: ProjectType, context: Context, initialPrompt: String? = null) {
        viewModelScope.launch {
            aiDelegate.clearSession()
            settingsViewModel.saveProjectConfig(appName, user, branch)
            settingsViewModel.saveTargetPackageName(pkg)
            settingsViewModel.setProjectType(type.name)

            // Scaffold the project directory from the bundled template if it
            // doesn't already contain a recognisable project. Makes Save &
            // Initialize work for brand-new projects without the user having
            // to populate index.html (PWA) or build.gradle.kts (Android)
            // first.
            val projectDir = context.filesDir.resolve(appName)
            withContext(Dispatchers.IO) {
                projectDir.mkdirs()
                com.hereliesaz.ideaz.utils.TemplateManager.ensureTemplate(
                    context, type, projectDir, pkg, appName
                )
            }

            // Web/PWA projects live entirely on-device for the edit loop. No
            // GitHub upload, no Actions workflow scaffold, no Pages deploy.
            // The user explicitly triggers remote hosting via the rail's
            // Deploy item / deployWebProject(). For Android, init still pushes
            // GitHub Actions workflows because there's no on-device toolchain.
            if (!type.isWebLike()) {
                repoDelegate.uploadProjectSecrets(user, appName)
                repoDelegate.forceUpdateInitFiles()
            }

            buildDelegate.startBuild(projectDir)

            // Check for remote artifacts if it's an Android project
            if (type == ProjectType.ANDROID) {
                startArtifactPolling(user, appName)
            }

            // Dispatch the initial prompt only now — the project is scaffolded and
            // the build kicked off, so the AI runs against real files (fixes the
            // race where the prompt fired before scaffolding finished).
            if (!initialPrompt.isNullOrBlank()) {
                sendPrompt(initialPrompt)
            }
        }
    }

    private var pollingJob: Job? = null

    private fun startArtifactPolling(user: String, repo: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val timeout = 10 * 60 * 1000L // 10 minutes

            while (System.currentTimeMillis() - startTime < timeout) {
                checkForRemoteArtifact(user, repo, getApplication())

                if (_artifactCheckResult.value?.isRemoteNewer == true) {
                    break
                }
                delay(30_000) // 30 seconds
            }
        }
    }

    /**
     * Checks if a remote artifact (APK) exists and is newer than local.
     */
    private suspend fun checkForRemoteArtifact(user: String, repo: String, context: Context) {
        val token = settingsViewModel.getGithubToken()
        if (token.isNullOrBlank()) return

        try {
            val service = GitHubApiClient.createService(token)
            val releases = withContext(Dispatchers.IO) { service.getReleases(user, repo) }

            // Find valid release
            val release = releases.firstOrNull { r ->
                r.prerelease && r.assets.any { it.name.endsWith(".apk") }
            } ?: releases.firstOrNull { r -> r.assets.any { it.name.endsWith(".apk") } } ?: return

            val validAssets = release.assets.filter {
                it.name.endsWith(".apk") && VersionUtils.extractVersionFromFilename(it.name) != null
            }

            val bestAsset = validAssets.sortedWith { a1, a2 ->
                val v1 = VersionUtils.extractVersionFromFilename(a1.name) ?: "0"
                val v2 = VersionUtils.extractVersionFromFilename(a2.name) ?: "0"
                VersionUtils.compareVersions(v1, v2)
            }.lastOrNull() ?: return

            val remoteVersion = VersionUtils.extractVersionFromFilename(bestAsset.name) ?: return

            // Check Local Version
            val projectDir = context.filesDir.resolve(repo)
            val possibleDirs = listOf(
                File(projectDir, "app/build/outputs/apk/debug"),
                File(projectDir, "android/app/build/outputs/apk/debug")
            )

            val localApk = possibleDirs.asSequence()
                .flatMap { it.walk() }
                .filter { it.extension == "apk" }
                .firstOrNull()

            var localVersion: String? = null
            if (localApk != null) {
                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(localApk.absolutePath, 0)
                localVersion = info?.versionName
            }

            val isNewer = if (localVersion == null) true else {
                VersionUtils.compareVersions(remoteVersion, localVersion) > 0
            }

            _artifactCheckResult.value = ArtifactCheckResult(
                remoteVersion = remoteVersion,
                downloadUrl = bestAsset.browserDownloadUrl,
                localVersion = localVersion,
                isRemoteNewer = isNewer
            )

        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "Operation failed", e)
        }
    }

    /** Downloads the artifact selected by the user. */
    fun downloadLatestArtifact(url: String, onSuccess: (File) -> Unit) {
        viewModelScope.launch {
            stateDelegate.setLoadingProgress(0)
            stateDelegate.setBottomSheetState(com.hereliesaz.aznavrail.model.AzSheetDetent.FULL) // Expand logs
            logHandler.onBuildLog("Downloading latest artifact from $url...")

            val destFile = File(getApplication<Application>().cacheDir, "downloaded_project.apk")
            val success = downloadFile(url, destFile) { progress ->
                stateDelegate.setLoadingProgress(progress)
            }

            if (success) {
                logHandler.onBuildLog("Download complete. Installing/Loading...")
                onSuccess(destFile)
            } else {
                logHandler.onBuildLog("Download failed.")
            }
            stateDelegate.setLoadingProgress(null)
        }
    }

    /**
     * Checks if the local APK selected by URI is older than the remote version.
     * Returns "downgrade", "upgrade", or "same".
     */
    suspend fun checkLocalApkVersion(uri: Uri, remoteVersion: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val tempFile = File(context.cacheDir, "temp_check.apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(tempFile.absolutePath, 0)
                val localVersion = info?.versionName ?: return@withContext "unknown"
                val localPackage = info.packageName

                val targetPackage = settingsViewModel.targetPackageName.value
                if (!targetPackage.isNullOrBlank() && localPackage != targetPackage) {
                    logHandler.onOverlayLog("Error: Selected APK package '$localPackage' does not match project '$targetPackage'.")
                    tempFile.delete()
                    return@withContext "mismatch"
                }

                tempFile.delete()

                if (localVersion == remoteVersion) return@withContext "same"

                val rParts = remoteVersion.split(".").mapNotNull { it.toIntOrNull() }
                val lParts = localVersion.split(".").mapNotNull { it.toIntOrNull() }

                for (i in 0 until maxOf(rParts.size, lParts.size)) {
                    val r = rParts.getOrElse(i) { 0 }
                    val l = lParts.getOrElse(i) { 0 }
                    if (l < r) return@withContext "downgrade"
                    if (l > r) return@withContext "upgrade"
                }
                "same"

            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Operation failed", e)
                "unknown"
            }
        }
    }

    fun loadProject(name: String, context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            aiDelegate.clearSession()
            stateDelegate.clearChatHistory()
            settingsViewModel.setAppName(name)
            // Sync the saved branch name to whatever the local repo is actually on.
            // Previously, KEY_BRANCH_NAME stayed at whatever the prior project used
            // (or the literal "main" default), so loading a "master"-default repo
            // would commit/push to the wrong branch.
            gitDelegate.getCurrentBranch()?.let { actualBranch ->
                settingsViewModel.saveBranchName(actualBranch)
            }

            // Project type & target package are GLOBAL settings, so re-derive them
            // from the project on disk. Without this, a loaded project inherits the
            // previously-open project's type and gets routed down the wrong
            // build/preview path (and a loaded web project never shows a preview).
            val projectDir = settingsViewModel.getProjectPath(name)
            val detectedType = withContext(Dispatchers.IO) {
                ProjectAnalyzer.detectProjectType(projectDir)
            }
            settingsViewModel.setProjectType(detectedType.name)
            if (detectedType !in ProjectType.selectable) {
                stateDelegate.setCurrentWebUrl(null)
                stateDelegate.setTargetAppVisible(false)
                logHandler.onOverlayLog(
                    "${detectedType.displayName} projects are not available in this release. " +
                        "The project was not modified."
                )
                onSuccess()
                return@launch
            }
            if (!detectedType.isWebLike()) {
                withContext(Dispatchers.IO) { ProjectAnalyzer.detectPackageName(projectDir) }
                    ?.let { settingsViewModel.saveTargetPackageName(it) }
            }

            val user = settingsViewModel.getGithubUser()
            if (!user.isNullOrBlank()) {
                repoDelegate.uploadProjectSecrets(user, name)
                // Restore prior AI sessions for this repo. The clone/select flow
                // (selectRepositoryForSetup) does this; loading a local project
                // must too, or the Setup tab shows no resumable sessions.
                aiDelegate.fetchSessionsForRepo("$user/$name")
            }

            // Re-mount only after the release-scope gate above has accepted the
            // detected project type.
            stateDelegate.setCurrentWebUrl(null)
            launchTargetApp(context)
            onSuccess()
        }
    }

    fun forkRepository(u: String, onSuccess: () -> Unit = {}) {
        val parts = u.removePrefix("https://github.com/")
            .removeSuffix(".git")
            .split("/")
            .filter { it.isNotBlank() }

        if (parts.size < 2) {
            logHandler.onOverlayLog("Invalid repository format. Use 'owner/repo'.")
            return
        }

        val owner = parts[0]
        val repo = parts[1]

        repoDelegate.forkRepository(owner, repo) { newOwner, newRepo, _ ->
            viewModelScope.launch {
                repoDelegate.uploadProjectSecrets(newOwner, newRepo)
                aiDelegate.fetchSessionsForRepo("$newOwner/$newRepo")
                repoDelegate.forceUpdateInitFiles()
                onSuccess()
            }
        }
    }

    /**
     * Imports an external project folder via Storage Access Framework URI.
     * Copies the folder to the app's internal storage (`filesDir`) for read/write access.
     * This is necessary because direct in-place editing of `content://` URIs is limited/unreliable for build tools.
     */
    fun registerExternalProject(u: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Convert to DocumentFile
                val documentFile = if (u.scheme == "file" && u.path != null) {
                    androidx.documentfile.provider.DocumentFile.fromFile(File(u.path!!))
                } else {
                    try {
                        // Persist permissions across reboots
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        getApplication<Application>().contentResolver.takePersistableUriPermission(u, takeFlags)
                    } catch (e: Exception) {
                        // Ignore if persistence is not supported (e.g., file://)
                    }
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), u)
                }

                if (documentFile == null || !documentFile.isDirectory) {
                    logHandler.onOverlayLog("Invalid project directory selected.")
                    return@launch
                }

                // Handle Name Collision
                var projectName = documentFile.name ?: "Imported_${System.currentTimeMillis()}"
                var destDir = getApplication<Application>().filesDir.resolve(projectName)

                var counter = 1
                while (destDir.exists()) {
                    projectName = "${documentFile.name ?: "Imported"}_$counter"
                    destDir = getApplication<Application>().filesDir.resolve(projectName)
                    counter++
                }

                logHandler.onOverlayLog("Importing project '$projectName'...")
                logHandler.onProgress(0)

                // Copy files
                copyDocumentFileToLocal(documentFile, destDir)

                logHandler.onOverlayLog("Import complete.")
                logHandler.onProgress(null)

                // Analyze and Load
                val projectType = com.hereliesaz.ideaz.utils.ProjectAnalyzer.detectProjectType(destDir)
                // Same release gate loadProject() enforces: an imported folder can be
                // any project type ProjectAnalyzer recognizes, but only ProjectType.selectable
                // is release-ready. Without this check, importing an Android or plain-web
                // folder fully initialized it anyway - pushing CI workflows and injecting
                // crash-reporting code for a project type this release doesn't support.
                if (projectType !in ProjectType.selectable) {
                    destDir.deleteRecursively()
                    val message = "${projectType.displayName} projects are not available in this release. " +
                        "The imported folder was not kept."
                    logHandler.onOverlayLog(message)
                    // The log line alone previously landed only in the AI Log
                    // tab of a collapsed bottom sheet - from the user's seat,
                    // "Add External Project" appeared to silently do nothing
                    // (no dialog, no toast, the Load tab list unchanged) while
                    // it had actually copied, inspected, and then deleted
                    // their folder.
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val packageName = com.hereliesaz.ideaz.utils.ProjectAnalyzer.detectPackageName(destDir)
                    ?: "com.ideaz.imported.${projectName.filter { it.isLetterOrDigit() }.lowercase()}"

                val owner = settingsViewModel.getGithubUser() ?: "local"
                val branch = "main"

                withContext(Dispatchers.Main) {
                    saveAndInitialize(projectName, owner, branch, packageName, projectType, getApplication())
                }

            } catch (e: Exception) {
                logHandler.onOverlayLog("Failed to import project: ${e.message}")
                android.util.Log.w("MainViewModel", "Operation failed", e)
            } finally {
                logHandler.onProgress(null)
            }
        }
    }

    private fun copyDocumentFileToLocal(src: androidx.documentfile.provider.DocumentFile, dest: File) {
        if (src.isDirectory) {
            if (!dest.exists()) dest.mkdirs()
            src.listFiles().forEach { file ->
                val destFile = File(dest, file.name ?: "unknown")
                copyDocumentFileToLocal(file, destFile)
            }
        } else {
            if (src.name != null) {
                getApplication<Application>().contentResolver.openInputStream(src.uri)?.use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    /** Deletes a project locally. */
    fun deleteProject(n: String) {
        if (n.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                performLocalDeletion(n)
                logHandler.onBuildLog("Project '$n' deleted locally.\n")
            } catch (e: Exception) {
                logHandler.onBuildLog("Error deleting project: ${e.message}\n")
            }
        }
    }

    /**
     * Syncs changes to remote repository before deleting local files.
     * Prevents data loss.
     */
    fun syncAndDeleteProject(n: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val projectDir = settingsViewModel.getProjectPath(n)
                if (projectDir.exists()) {
                    logHandler.onBuildLog("Syncing project '$n' before deletion...\n")
                    try {
                        val git = GitManager(projectDir)

                        if (git.hasChanges()) {
                            git.addAll()
                            git.commit("Sync before delete")
                        }

                        val token = settingsViewModel.getGithubToken()
                        val user = settingsViewModel.getGithubUser() ?: "git"

                        if (!token.isNullOrBlank()) {
                            git.push(user, token) { p, t -> logHandler.onGitProgress(p, t) }
                            logHandler.onBuildLog("Project synced successfully.\n")
                        } else {
                            logHandler.onBuildLog("Warning: No GitHub token found. Skipping push.\n")
                        }
                    } catch (e: Exception) {
                        logHandler.onBuildLog("Sync failed: ${e.message}. Aborting deletion.\n")
                        throw e
                    }
                }
                performLocalDeletion(n)
                logHandler.onBuildLog("Project '$n' deleted.\n")
            } catch (e: Exception) {
                logHandler.onBuildLog("Error syncing/deleting project: ${e.message}\n")
            }
        }
    }

    private suspend fun performLocalDeletion(n: String) {
        val projectDir = settingsViewModel.getProjectPath(n)
        if (projectDir.exists()) {
            if (!projectDir.deleteRecursively()) {
                if (projectDir.exists()) {
                    throw java.io.IOException("Failed to delete project directory: ${projectDir.absolutePath}")
                }
            }
        }
        withContext(Dispatchers.Main) {
            settingsViewModel.removeProject(n)
            settingsViewModel.removeProjectPath(n)
            if (settingsViewModel.getAppName() == n) {
                settingsViewModel.setAppName("")
                stateDelegate.clearChatHistory()
            }
            scanLocalProjects()
        }
    }

    // UPDATE Operations
    fun checkForExperimentalUpdates() = updateDelegate.checkForExperimentalUpdates()
    fun confirmUpdate() = updateDelegate.confirmUpdate()
    fun dismissUpdateWarning() = updateDelegate.dismissUpdateWarning()
    fun dismissUpdateStatus() = updateDelegate.dismissUpdateStatus()

    // MISC

    fun clearLog() = stateDelegate.clearLog()

    /**
     * Launches the target application (Android or Web).
     *
     * **Logic:**
     * - **Web:** Points the WebView to the project's `index.html`.
     * - **Android:** Switches to "App View" (Host) or launches installed APK.
     */
    fun launchTargetApp(c: Context) {
        // Suppress launch if Artifact Dialog is open
        if (_artifactCheckResult.value != null) {
            logHandler.onBuildLog("[IDE] Launch suppressed: Artifact selection dialog is open.")
            return
        }

        val appName = settingsViewModel.getAppName() ?: return
        val projectTypeStr = settingsViewModel.readProjectType()
        val projectType = ProjectType.fromString(projectTypeStr)

        if (projectType.isWebLike()) {
            val projectDir = settingsViewModel.getProjectPath(appName)
            if (stateDelegate.currentWebUrl.value == null) {
                // Mount the project at the asset-loader root (same-origin,
                // service-worker safe; resolves root-absolute references).
                val indexFile = File(projectDir, "index.html")
                if (indexFile.exists()) {
                    stateDelegate.setCurrentWebProjectDir(projectDir)
                    stateDelegate.setCurrentWebUrl(WebProjectUrlUtils.localProjectRootUrl())
                } else {
                    // App View mounting is gated on currentWebUrl being set, not on
                    // project type - setTargetAppVisible(true) below with no URL used
                    // to fall through to MainScreen's Android-placeholder branch,
                    // showing "Android target host arrives in Phase 2" for what's
                    // actually a PWA project missing its entry file. Surface the real
                    // problem instead and stay on the current screen.
                    logHandler.onOverlayLog(
                        "Can't launch: this project has no index.html. Add one to preview it."
                    )
                    return
                }
            }
            startFileObservation(projectDir)
            stateDelegate.setTargetAppVisible(true)
        } else {
            // Android
            val packageName = settingsViewModel.targetPackageName.value
            launchInstalledApk(c, packageName, appName)
        }
    }

    private fun launchInstalledApk(c: Context, packageName: String?, appName: String) {
            try {
                if (packageName.isNullOrBlank()) {
                    // Try to detect
                    val projectDir = settingsViewModel.getProjectPath(appName)
                    val detectedPackage = ProjectAnalyzer.detectPackageName(projectDir)

                    if (!detectedPackage.isNullOrBlank()) {
                        settingsViewModel.saveTargetPackageName(detectedPackage)
                        stateDelegate.setTargetAppVisible(true)
                    } else {
                        Toast.makeText(c, c.getString(R.string.error_app_not_installed), Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                // Switch to "App View"
                stateDelegate.setTargetAppVisible(true)

            } catch (e: Exception) {
                Toast.makeText(c, c.getString(R.string.error_launch_failed, e.message), Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Returns the list of credentials the user still needs to configure before any
     * project flow (Create / Save & Initialize / Clone-select) can succeed.
     *
     * The AI credential checked here follows whichever model the "Default" AI
     * Assignment actually resolves to (SettingsViewModel.getAiAssignment ranks
     * providers by which key the user has saved - see AiModels.defaultRanking),
     * not a hardcoded Gemini assumption. A model with no required key (on-device,
     * the Gemini app bridge) needs nothing here at all. Previously this always
     * demanded a Google AI Studio key even for a user who'd configured a
     * different provider entirely (or none, and only wanted GitHub/manual use).
     * The Jules API key is only required if the user has explicitly assigned
     * Jules to one of the AI task slots (Phase 2 territory).
     */
    fun checkRequiredKeys(): List<String> {
        val missing = mutableListOf<String>()
        if (settingsViewModel.getGithubToken().isNullOrBlank()) missing.add("GitHub Token")

        val defaultModel = AiModels.findById(
            settingsViewModel.getAiAssignment(SettingsViewModel.KEY_AI_ASSIGNMENT_DEFAULT)
        )
        if (defaultModel != null &&
            defaultModel.id != AiModels.JULES_DEFAULT &&
            defaultModel.requiredKey.isNotEmpty() &&
            settingsViewModel.getApiKey(defaultModel.requiredKey).isNullOrBlank()
        ) {
            missing.add("${defaultModel.displayName} API Key")
        }

        // Only flag the Jules key as missing if Jules is actually assigned to a task.
        val julesAssigned = listOf(
            SettingsViewModel.KEY_AI_ASSIGNMENT_DEFAULT,
            SettingsViewModel.KEY_AI_ASSIGNMENT_CONTEXTLESS,
            SettingsViewModel.KEY_AI_ASSIGNMENT_OVERLAY,
        ).any { settingsViewModel.getAiAssignment(it) == AiModels.JULES_DEFAULT }
        if (julesAssigned && settingsViewModel.getApiKey().isNullOrBlank()) {
            missing.add("Jules API Key")
        }

        return missing
    }

    private suspend fun downloadFile(urlStr: String, destination: File, onProgress: (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                connection = (url.openConnection() as HttpURLConnection).also { it.connect() }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext false
                }

                val fileLength = connection.contentLength
                connection.inputStream.use { input ->
                    FileOutputStream(destination).use { output ->
                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            if (fileLength > 0) {
                                onProgress((total * 100 / fileLength).toInt())
                            }
                            output.write(data, 0, count)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Operation failed", e)
                false
            } finally {
                connection?.disconnect()
            }
        }
    }

    private suspend fun applyUnidiffPatchInternal(diff: String): Boolean {
        return gitDelegate.applyUnidiffPatch(diff)
    }

    private fun handleBuildFailure(log: String) {
        val isAutoDebug = settingsViewModel.isAutoDebugBuildsEnabled()
        if (!isAutoDebug) return

        // Heuristics to separate IDE errors from User Project errors.
        // The IDE app id appears in every project file path under filesDir
        // (e.g. "/data/user/0/com.hereliesaz.ideaz/files/foo"), so matching
        // the bare package name treated every missing-file error in a user
        // project as an IDE failure. Anchor to the stack-frame format
        // ("\tat com.hereliesaz.ideaz") so we only flag actual IDE-code
        // throws, not project paths embedded in error text.
        val isIdeError = log.contains("[IDE] Failed") ||
                log.contains("tools not found") ||
                log.contains("\tat com.hereliesaz.ideaz") ||
                (log.contains("FileNotFoundException") && !log.contains("build.gradle")) ||
                log.contains("OutOfMemoryError") ||
                log.contains("No space left on device") ||
                log.contains("Exit code 139") || // Segfault
                log.contains("Exit code 134") || // Abort
                log.contains("Signal 11") ||
                log.contains("Signal 6")

        if (isIdeError) {
            if (settingsViewModel.isReportIdeErrorsEnabled()) {
                val token = settingsViewModel.getGithubToken()
                viewModelScope.launch {
                    val result = com.hereliesaz.ideaz.utils.GithubIssueReporter.reportError(
                        context = getApplication(),
                        token = token,
                        error = null,
                        contextMessage = "Build failed with suspected IDE/Environment error",
                        stackTraceOverride = log
                    )
                    logHandler.onOverlayLog("Environment/IDE Error reported: $result")
                }
            } else {
                logHandler.onOverlayLog("Environment/IDE Error detected. Reporting disabled.")
            }
        } else {
            // Project Error -> route the failing log to the assigned fixer:
            // Jules if it's assigned, otherwise the selected conversational
            // provider (e.g. the Gemini app bridge) so it gets the log and can
            // fix the build automatically.
            val prompt = "Build failed. Fix this:\n$log"
            if (isJulesAssigned(SettingsViewModel.KEY_AI_ASSIGNMENT_DEFAULT)) {
                aiDelegate.startContextualAITask(prompt)
            } else {
                sendChatMessage(prompt)
            }
        }
    }
}

/**
 * Interface for handling log events from delegates.
 * Decouples delegates from MainViewModel implementation details.
 */
interface LogHandler {
    fun onBuildLog(msg: String)
    fun onAiLog(msg: String)
    fun onProgress(p: Int?)
    fun onGitProgress(p: Int, t: String)
    fun onOverlayLog(msg: String)
}
