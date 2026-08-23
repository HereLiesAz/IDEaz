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
import com.hereliesaz.ideaz.ai.AiEditApproval
import com.hereliesaz.ideaz.ai.AiEditApprovalRequiredException
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
                    stateDelegate.setEditReview(
                        EditReviewState(
                            AiEditApproval(
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

        // Was also re-broadcasting every line as ACTION_AI_LOG "for the Overlay UI
        // (which runs in a separate Service process)". It never ran in another
        // process, and SystemEventDelegate received the broadcast in-process and
        // appended it a second time - so every AI line appeared twice and also
        // leaked into the Build tab. One append, one destination.
        override fun onAiLog(msg: String) { stateDelegate.appendAiLog(msg) }

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

    fun sendChatMessage(text: String) = sendChatMessage(text, emptyList())


    fun sendChatMessage(text: String, referenceParts: List<com.hereliesaz.ideaz.ai.ChatPart>) {
        // An edit is waiting on the user. Previously this returned silently, so the
        // send button kept working and kept doing nothing - the single worst bug in
        // the visual-select loop. Say so instead, in the conversation, where the
        // user is already looking.
        val editStatus = stateDelegate.editReview.value?.status
        if (editStatus == EditReviewStatus.PENDING || editStatus == EditReviewStatus.PROCESSING) {
            stateDelegate.setChatFailure(
                "There's an edit waiting for your review. Approve or reject it, then send this."
            )
            return
        }
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
            stateDelegate.appendChatMessage(
                ChatMessage.error("Error: No API key set for ${model.displayName}. Go to Settings → AI Providers.")
            )
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
            } catch (e: AiEditApprovalRequiredException) {
                stateDelegate.editReview.value
                    ?.takeIf { it.status == EditReviewStatus.APPROVED }
                    ?.approval?.review?.checkpoint
                    ?.let { previous ->
                        withContext(Dispatchers.IO) {
                            runCatching {
                                IdeTools(File(previous.projectPath)).discardEditCheckpoint(previous)
                            }
                        }
                    }
                stateDelegate.setEditReview(EditReviewState(e.approval))
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


    /** Approves validated local changes, records the model response, then reloads. */
    fun approveEdit(checkpointId: String) {
        val state = stateDelegate.editReview.value ?: return
        if (state.status != EditReviewStatus.PENDING ||
            state.approval.review.checkpoint.checkpointId != checkpointId
        ) return
        stateDelegate.setEditReview(state.copy(status = EditReviewStatus.PROCESSING))
        stateDelegate.setChatLoading(true)
        viewModelScope.launch {
            try {
                val reviewed = state.approval.review
                val current = withContext(Dispatchers.IO) {
                    IdeTools(File(reviewed.checkpoint.projectPath)).reviewEdits(reviewed.checkpoint)
                }
                if (reviewed.validationErrors.isNotEmpty()) {
                    stateDelegate.setEditReview(
                        state.copy(
                            approval = state.approval.copy(review = reviewed.refreshedFrom(current)),
                            status = EditReviewStatus.PENDING,
                        )
                    )
                    return@launch
                }
                if (current.validationErrors.isNotEmpty()) {
                    stateDelegate.setEditReview(
                        state.copy(
                            approval = state.approval.copy(review = reviewed.refreshedFrom(current)),
                            status = EditReviewStatus.PENDING,
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
                    stateDelegate.setEditReview(
                        state.copy(
                            approval = state.approval.copy(review = reviewed.refreshedFrom(current)),
                            status = EditReviewStatus.PENDING,
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
                stateDelegate.setEditReview(state.copy(status = EditReviewStatus.APPROVED))
                stateDelegate.appendChatMessage(ChatMessage("model", state.approval.response, state.approval.source))
                stateDelegate.triggerFileTreeReload()
                stateDelegate.triggerWebHardReload()
            } catch (_: Exception) {
                stateDelegate.setEditReview(state)
                stateDelegate.appendChatMessage(
                    ChatMessage.error("Approval stopped: the project changed after review.")
                )
            } finally {
                stateDelegate.setChatLoading(false)
            }
        }
    }

    /** Rejects pending changes and restores their pre-edit checkpoint. */
    fun rejectEdit(checkpointId: String) {
        restoreEdit(checkpointId, EditReviewStatus.REJECTED)
    }

    /** Undoes an approved edit only while its changed-file set remains untouched. */
    fun undoEdit(checkpointId: String) {
        restoreEdit(checkpointId, EditReviewStatus.UNDONE)
    }

    private fun restoreEdit(
        checkpointId: String,
        targetStatus: EditReviewStatus,
    ) {
        val state = stateDelegate.editReview.value ?: return
        val approval = state.approval
        if (approval.review.checkpoint.checkpointId != checkpointId) return
        if (targetStatus == EditReviewStatus.REJECTED && state.status != EditReviewStatus.PENDING) return
        if (targetStatus == EditReviewStatus.UNDONE && state.status != EditReviewStatus.APPROVED) return
        if (!approval.review.rollbackAllowed) return
        stateDelegate.setEditReview(state.copy(status = EditReviewStatus.PROCESSING))
        stateDelegate.setChatLoading(true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    IdeTools(File(approval.review.checkpoint.projectPath)).restoreEditCheckpoint(
                        approval.review.checkpoint,
                        approval.review.contentFingerprint,
                    )
                }
                stateDelegate.setEditReview(state.copy(status = targetStatus))
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
                stateDelegate.setEditReview(null)
                stateDelegate.appendChatMessage(
                    ChatMessage.error("Discarded: the project changed after review, so nothing was restored.")
                )
            } catch (_: Exception) {
                stateDelegate.setEditReview(state)
                stateDelegate.appendChatMessage(
                    ChatMessage.error("Restore stopped: the project changed after review.")
                )
            } finally {
                stateDelegate.setChatLoading(false)
            }
        }
    }

    val selectionDelegate = SelectionDelegate(logHandler::onAiLog)

    val gitDelegate = GitDelegate(settingsViewModel, viewModelScope, logHandler::onBuildLog, logHandler::onProgress)

    val repoDelegate = RepoDelegate(
        application,
        settingsViewModel,
        viewModelScope,
        logHandler::onBuildLog,
        logHandler::onAiLog,
        logHandler::onProgress,
        logHandler::onGitProgress
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

    val isSelectMode = selectionDelegate.isSelectMode
    val isContextualChatVisible = selectionDelegate.isContextualChatVisible

    // Track the last user prompt to restore context after an app restart
    private var lastPrompt: String? = null

    val ownedRepos = repoDelegate.ownedRepos
    val repoFetchError = repoDelegate.repoFetchError
    val commitHistory = gitDelegate.commitHistory
    val branches = gitDelegate.branches
    val gitStatus = gitDelegate.gitStatus

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
                    putExtra(CrashReportingService.EXTRA_GITHUB_TOKEN, githubToken)
                    putExtra(CrashReportingService.EXTRA_STACK_TRACE, errors)
                    putExtra(CrashReportingService.EXTRA_GITHUB_USER, githubUser)
                }
                getApplication<Application>().startService(intent)
            }
        }
    }

    // --- Proxy Methods (Forwarding calls to Delegates) ---

    // PREVIEW

    /**
     * Rail "Run" action: mount the current project in the WebView preview.
     *
     * For a web project there is nothing to build - the working tree *is* the
     * app. Previously this went through a BuildDelegate that also owned the
     * remote-APK pipeline and an on-device Wasm compiler; both are gone, and
     * what is left is what actually happened for the only supported project
     * type: point the preview at the project and show it.
     */
    fun openPreview() {
        val appName = settingsViewModel.getAppName()
        if (appName.isNullOrBlank()) {
            logHandler.onBuildLog("[IDE] No project loaded. Open or create one first.\n")
            return
        }
        val projectDir = settingsViewModel.getProjectPath(appName)
        if (!File(projectDir, "index.html").isFile) {
            logHandler.onBuildLog(
                "[IDE] $appName has no index.html at its root, so there is nothing to preview.\n"
            )
            return
        }
        stateDelegate.setCurrentWebProjectDir(projectDir)
        stateDelegate.setCurrentWebUrl(WebProjectUrlUtils.localProjectRootUrl())
        stateDelegate.setTargetAppVisible(true)
        editorViewModel.setProjectDir(projectDir)
    }

    // GIT Operations
    fun refreshGitData() { viewModelScope.launch { gitDelegate.refreshGitData() } }
    fun gitFetch() { viewModelScope.launch { gitDelegate.fetch() } }
    fun gitPull() { viewModelScope.launch { gitDelegate.pull() } }
    fun gitPush() { viewModelScope.launch(Dispatchers.IO) { gitDelegate.push() } }
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

    private var pollingJob: Job? = null

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
    fun sendPrompt(p: String?) = sendPrompt(p, emptyList())

    fun sendPrompt(p: String?, referenceParts: List<com.hereliesaz.ideaz.ai.ChatPart>) {
        if (p.isNullOrBlank() && referenceParts.isEmpty()) return
        val text = p.orEmpty()
        lastPrompt = text
        sendChatMessage(text, referenceParts)
    }

    fun submitContextualPrompt(p: String) {
        lastPrompt = p
        val context = selectionDelegate.pendingContextInfo
        val richPrompt = if (context != null) {
            // Labelled, so the model can tell the element apart from the request.
            // AiRepoContext.systemPreamble tells it how to read this block - in
            // particular to jump straight to `source` when the bridge resolved one.
            "ELEMENT CONTEXT:\n$context\n\nREQUEST:\n$p"
        } else p
        sendChatMessage(richPrompt, emptyList())
    }


    // SELECT MODE

    fun toggleSelectMode(b: Boolean) = selectionDelegate.toggleSelectMode(b)

    /**
     * The user tapped at [x],[y] (device px, relative to the preview) while in
     * select mode. Asks the WebView to identify the element there; the answer
     * comes back through [handleWebElementContext].
     */
    fun handleSelection(x: Float, y: Float) {
        if (stateDelegate.currentWebUrl.value == null) {
            selectionDelegate.onSelectionMissed()
            return
        }
        val intent = Intent("com.hereliesaz.ideaz.INSPECT_WEB").apply {
            putExtra("X", x)
            putExtra("Y", y)
            setPackage(getApplication<Application>().packageName)
        }
        getApplication<Application>().sendBroadcast(intent)
    }

    /**
     * Receives DOM context JSON from the web bridge when the user taps an element
     * in Select Mode while a PWA/Web project is shown.
     *
     * Routes it to [SelectionDelegate] so the prompt panel opens with the
     * element attached.
     *
     * @param json  Raw JSON from [WebViewBridge.onElementTapped].
     */
    fun handleWebElementContext(json: String) {
        stateDelegate.appendAiLog("[WEB-ELEMENT] $json")
        selectionDelegate.onWebElementContext(json)
    }

    fun clearSelection() = selectionDelegate.clearContext()
    fun closeContextualChat() = selectionDelegate.dismissContextualChat()
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
    /** Creates a new repo and initializes the project. */
    fun createGitHubRepository(name: String, desc: String, priv: Boolean, ctx: Context, initialPrompt: String? = null, onSuccess: () -> Unit) {
        repoDelegate.createGitHubRepository(name, desc, priv, ctx) { owner, branch ->
            // Local content is either cloned from the template repo (generate
            // flow, handled in RepoDelegate) or scaffolded from the bundled
            // template by saveAndInitialize's ensureTemplate when the directory
            // is still empty (fallback). Either way ensureTemplate is a no-op
            // once files are present, so we don't copy here. The initial prompt
            // is dispatched by saveAndInitialize AFTER scaffolding, so the AI
            // never runs against an empty project.
            saveAndInitialize(name, owner, branch, ctx, initialPrompt)
            onSuccess()
        }
    }

    /** Selects a repo and prepares it for use. */
    fun selectRepositoryForSetup(repo: GitHubRepoResponse, onSuccess: () -> Unit) {
        repoDelegate.selectRepositoryForSetup(repo) { owner, branch ->
            repoDelegate.forceUpdateInitFiles()
            onSuccess()
        }
    }

    /**
     * Saves configuration and opens the preview.
     * Common exit path for Setup/Clone/Load flows.
     */
    fun saveAndInitialize(appName: String, user: String, branch: String, context: Context, initialPrompt: String? = null) {
        viewModelScope.launch {
            settingsViewModel.saveProjectConfig(appName, user, branch)

            // Scaffold the project directory from the bundled React starter if
            // it doesn't already contain something previewable. Makes Save &
            // Initialize work for brand-new projects without the user having to
            // write an index.html first.
            val projectDir = context.filesDir.resolve(appName)
            withContext(Dispatchers.IO) {
                projectDir.mkdirs()
                com.hereliesaz.ideaz.utils.TemplateManager.ensureTemplate(context, projectDir)
            }

            // Projects live entirely on-device for the edit loop. No GitHub
            // upload, no Actions workflow scaffold, no Pages deploy - the user
            // explicitly triggers remote hosting via the rail's Deploy item
            // (deployWebProject). This used to be an `if (!type.isWebLike())`
            // guard around forceUpdateInitFiles(); with one project type the
            // guard is always true, so the call is simply gone.

            openPreview()

            // Dispatch the initial prompt only now - the project is scaffolded,
            // so the AI runs against real files (fixes the race where the prompt
            // fired before scaffolding finished).
            if (!initialPrompt.isNullOrBlank()) {
                sendPrompt(initialPrompt)
            }
        }
    }

    fun loadProject(name: String, context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            stateDelegate.clearChatHistory()
            settingsViewModel.setAppName(name)
            // Sync the saved branch name to whatever the local repo is actually on.
            // Previously, KEY_BRANCH_NAME stayed at whatever the prior project used
            // (or the literal "main" default), so loading a "master"-default repo
            // would commit/push to the wrong branch.
            gitDelegate.getCurrentBranch()?.let { actualBranch ->
                settingsViewModel.saveBranchName(actualBranch)
            }

            // Whether a project is previewable is a property of what is on
            // disk, so re-derive it on every load. This used to also re-derive a
            // ProjectType and a target Java package into global settings, then
            // refuse anything outside `ProjectType.selectable` - a list with one
            // entry on it.
            val projectDir = settingsViewModel.getProjectPath(name)
            val previewable = withContext(Dispatchers.IO) { ProjectAnalyzer.isPreviewable(projectDir) }
            if (!previewable) {
                stateDelegate.setCurrentWebUrl(null)
                stateDelegate.setTargetAppVisible(false)
                logHandler.onOverlayLog(
                    "$name has no index.html or package.json, so there is nothing to preview. " +
                        "The project was not modified."
                )
                onSuccess()
                return@launch
            }

            val user = settingsViewModel.getGithubUser()
            if (!user.isNullOrBlank()) {
                // (selectRepositoryForSetup) does this; loading a local project
            }

            // Re-mount only after the previewable check above has passed.
            stateDelegate.setCurrentWebUrl(null)
            launchTargetApp()
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

                // Same check loadProject() enforces. Without it, importing a
                // folder IDEaz cannot preview used to fully initialize it anyway -
                // pushing CI workflows and injecting crash-reporting code for a
                // project type this app no longer edits.
                if (!com.hereliesaz.ideaz.utils.ProjectAnalyzer.isPreviewable(destDir)) {
                    destDir.deleteRecursively()
                    val message = "$projectName has no index.html or package.json, so IDEaz " +
                        "cannot preview it. The imported folder was not kept."
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

                val owner = settingsViewModel.getGithubUser() ?: "local"
                val branch = "main"

                withContext(Dispatchers.Main) {
                    saveAndInitialize(projectName, owner, branch, getApplication())
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

    // MISC

    fun clearLog() = stateDelegate.clearLog()

    /**
     * Shows the project running: mounts it at the asset-loader root and
     * switches to App View.
     *
     * Took a `Context` until the Android branch went away - it resolved a
     * target package name and handed off to the launcher for an installed APK.
     */
    fun launchTargetApp() {
        val appName = settingsViewModel.getAppName() ?: return
        val projectDir = settingsViewModel.getProjectPath(appName)
        if (stateDelegate.currentWebUrl.value == null) {
            // Mount the project at the asset-loader root (same-origin,
            // service-worker safe; resolves root-absolute references).
            if (ProjectAnalyzer.findWebEntryPoint(projectDir) == null) {
                // App View mounting is gated on currentWebUrl being set, so
                // setTargetAppVisible(true) with no URL used to fall through to
                // MainScreen's Android placeholder - "Android target host arrives
                // in Phase 2" - for a web project that was simply missing its
                // entry file. Surface the real problem and stay put.
                logHandler.onOverlayLog(
                    "Can't launch: this project has no index.html. Add one to preview it."
                )
                return
            }
            stateDelegate.setCurrentWebProjectDir(projectDir)
            stateDelegate.setCurrentWebUrl(WebProjectUrlUtils.localProjectRootUrl())
        }
        startFileObservation(projectDir)
        stateDelegate.setTargetAppVisible(true)
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
        // A GitHub token is deliberately NOT required here. You can scaffold a
        // project, edit it with the AI, and see it running with no GitHub account
        // at all; the token is only needed to publish (see deployWebProject).

        val defaultModel = AiModels.findById(
            settingsViewModel.getAiAssignment(SettingsViewModel.KEY_AI_ASSIGNMENT_DEFAULT)
        )
        if (defaultModel != null &&
            defaultModel.requiredKey.isNotEmpty() &&
            settingsViewModel.getApiKey(defaultModel.requiredKey).isNullOrBlank()
        ) {
            missing.add("${defaultModel.displayName} API Key")
        }

        return missing
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
            sendChatMessage(prompt)
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
