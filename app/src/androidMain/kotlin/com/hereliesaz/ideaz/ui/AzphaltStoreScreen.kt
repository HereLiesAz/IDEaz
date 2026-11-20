package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.azphalt.AzphaltPackageSummary
import com.hereliesaz.ideaz.azphalt.AzphaltPreparedLlmInstall
import com.hereliesaz.ideaz.azphalt.AzphaltStore
import com.hereliesaz.ideaz.azphalt.InstalledStoreLlm
import com.hereliesaz.ideaz.azphalt.describePromptHandling
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Azphalt store, LLM packages only: search, inspect a verified package's disclosure, install it
 * as an AI provider, and remove installed ones. Keys for installed models are set in Settings.
 */
@Composable
fun AzphaltStoreScreen(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { AzphaltStore.service(context) }
    val installed by remember { AzphaltStore.llms(context).installed }.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AzphaltPackageSummary>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var prepared by remember { mutableStateOf<AzphaltPreparedLlmInstall?>(null) }
    var confirmRemove by remember { mutableStateOf<InstalledStoreLlm?>(null) }

    fun run(label: String, block: suspend () -> Unit) {
        scope.launch {
            busy = label
            error = null
            notice = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "$label failed: ${e.message ?: e::class.simpleName}"
            } finally {
                busy = null
            }
        }
    }

    fun search() = run("Search") {
        results = service.search(query)
        searched = true
    }

    LaunchedEffect(Unit) {
        runCatching { service.recheckRevocations() }
        search()
    }

    confirmRemove?.let { llm ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${llm.name}?") },
            text = {
                Text("It stops being an AI provider, its saved key is deleted, and any AI assignment using it falls back to the default.")
            },
            confirmButton = {
                AzButton(
                    onClick = {
                        confirmRemove = null
                        service.removeLlm(llm.packageId)
                        settingsViewModel.saveString(llm.providerId, "")
                        notice = "Removed ${llm.name}"
                    },
                    text = "Remove",
                    shape = AzButtonShape.NONE,
                )
            },
            dismissButton = {
                AzButton(onClick = { confirmRemove = null }, text = "Cancel", shape = AzButtonShape.NONE)
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Heading("Azphalt store")
            Muted(
                "Hosted language models published on ${com.hereliesaz.ideaz.azphalt.AZPHALT_STORE_URL}. " +
                    "Only models IDEaz can call directly (an HTTPS endpoint speaking the OpenAI chat API) are listed. " +
                    "Installing one adds it as an AI provider; it is never used automatically as a fallback.",
            )
            Spacer(Modifier.height(12.dp))
            AzTextBox(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                hint = "Search language models",
                onSubmit = { search() },
                submitButtonContent = { Text("Search") },
            )

            busy?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("$it…", color = MaterialTheme.colorScheme.onBackground)
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            notice?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }

            prepared?.let { p ->
                Spacer(Modifier.height(16.dp))
                PreparedLlmPanel(
                    prepared = p,
                    enabled = busy == null,
                    onCancel = { prepared = null },
                    onInstall = { inputs, allowUntrusted, allowPublisherChange ->
                        run("Install") {
                            val llm = service.installLlm(
                                p,
                                nowEpochMillis = System.currentTimeMillis(),
                                inputValues = inputs,
                                allowUntrustedSigner = allowUntrusted,
                                allowPublisherChange = allowPublisherChange,
                            )
                            prepared = null
                            notice = "Installed ${llm.name} ${llm.version}. " +
                                if (llm.acceptsKey) "Add its key under Settings → API Keys, then pick it in AI Assignments."
                                else "Pick it in Settings → AI Assignments."
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
            Heading("Installed")
            if (installed.isEmpty()) {
                Muted("No language models installed from the store.")
            }
            installed.forEach { llm ->
                Spacer(Modifier.height(8.dp))
                Text("${llm.name} ${llm.version}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                Muted("${llm.defaultModel} at ${llm.baseUrl}")
                Muted(llm.promptHandling)
                llm.revokedReason?.let { Muted("Switched off: the store revoked this version ($it). Remove it or install a newer one.") }
                AzButton(
                    onClick = { confirmRemove = llm },
                    text = "Remove",
                    shape = AzButtonShape.NONE,
                    enabled = busy == null,
                )
            }

            Spacer(Modifier.height(24.dp))
            Heading("Results")
            if (searched && results.isEmpty() && busy == null) {
                Muted("No language models IDEaz can call directly match this search.")
            }
            results.forEach { item ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                StoreLlmResult(
                    item = item,
                    installed = installed.firstOrNull { it.packageId == item.id },
                    enabled = busy == null,
                    onInspect = {
                        run("Verify ${item.name}") {
                            prepared = null
                            prepared = service.prepareLlmInstall(item.id)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StoreLlmResult(
    item: AzphaltPackageSummary,
    installed: InstalledStoreLlm?,
    enabled: Boolean,
    onInspect: () -> Unit,
) {
    val endpoint = item.llm?.endpoint
    val keyless = endpoint?.auth != "required-bearer"
    Text(item.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
    Muted(
        listOfNotNull(
            if (keyless) "No key needed" else "Needs a key",
            item.llm?.dataHandling?.operator?.takeIf(String::isNotBlank),
            item.author?.takeIf(String::isNotBlank),
            item.latest,
        ).joinToString(" · "),
    )
    item.description?.takeIf(String::isNotBlank)?.let { Muted(it) }
    item.llm?.dataHandling?.let { Muted(describePromptHandling(it.operator, it.prompts, it.modelPinned)) }
    AzButton(
        onClick = onInspect,
        text = when {
            installed == null -> "Inspect & install"
            installed.version == item.latest -> "Reinstall"
            else -> "Update to ${item.latest}"
        },
        shape = AzButtonShape.NONE,
        enabled = enabled,
    )
}

/** The disclosure shown before install: who gets prompts, trust, inputs, and explicit approvals. */
@Composable
private fun PreparedLlmPanel(
    prepared: AzphaltPreparedLlmInstall,
    enabled: Boolean,
    onCancel: () -> Unit,
    onInstall: (inputs: Map<String, String>, allowUntrusted: Boolean, allowPublisherChange: Boolean) -> Unit,
) {
    val verification = prepared.verification
    val endpoint = prepared.endpoint
    val handling = prepared.llm.dataHandling
    val inputValues = remember(prepared) { mutableStateMapOf<String, String>() }
    var allowUntrusted by remember(prepared) { mutableStateOf(false) }
    var allowPublisherChange by remember(prepared) { mutableStateOf(false) }

    Heading("${prepared.detail.name} ${prepared.version}")
    Text(
        when {
            verification.trusted -> "Trusted"
            verification.packageContents.signed -> "Unknown signer"
            else -> "Unsigned"
        } + " — " + verification.trustReason,
        style = MaterialTheme.typography.bodyMedium,
        color = if (verification.trusted) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
    )
    Muted(
        listOfNotNull(endpoint.defaultModel?.takeIf(String::isNotBlank), endpoint.baseUrl?.takeIf(String::isNotBlank))
            .joinToString(" at ") + ". IDEaz calls this endpoint directly with your prompts, project file tree and the files the model reads.",
    )
    handling?.let {
        Label("Where prompts go")
        Muted(describePromptHandling(it.operator, it.prompts, it.modelPinned))
        it.operator?.takeIf(String::isNotBlank)?.let { op -> Muted("Operator: $op") }
        it.terms?.takeIf(String::isNotBlank)?.let { terms -> Muted("Terms: $terms") }
    }
    Label("Key")
    Muted(
        when (endpoint.auth) {
            "required-bearer" -> "Needs a key" + (prepared.keyInput?.description?.let { ": $it" } ?: ".")
            "optional-bearer" -> "No key needed" + (prepared.keyInput?.description?.let { "; $it" } ?: ".")
            else -> "No key needed."
        } + if (endpoint.auth != "none") " A key you add in Settings is stored encrypted and sent only to this endpoint." else "",
    )
    prepared.configurationInputs.forEach { input ->
        Spacer(Modifier.height(8.dp))
        AzTextBox(
            modifier = Modifier.fillMaxWidth(),
            value = inputValues[input.id].orEmpty(),
            onValueChange = { inputValues[input.id] = it },
            hint = (input.description ?: input.id) + if (input.optional) " (optional)" else "",
            onSubmit = {},
        )
    }
    if (!verification.trusted) {
        ConfirmRow(
            checked = allowUntrusted,
            onChange = { allowUntrusted = it },
            text = if (verification.packageContents.signed) "Install despite an unrecognized signing key"
            else "Install this unsigned package",
        )
    }
    if (verification.publisherChanged) {
        ConfirmRow(
            checked = allowPublisherChange,
            onChange = { allowPublisherChange = it },
            text = "Approve the publisher key change for this package",
        )
    }
    val trustReady = verification.trusted || allowUntrusted
    val publisherReady = !verification.publisherChanged || allowPublisherChange
    val inputsReady = prepared.configurationInputs.all { it.optional || inputValues[it.id]?.isNotBlank() == true }
    if (!publisherReady) {
        Muted("The publisher key changed since you last installed this package; approve it to continue.")
    } else if (!trustReady) {
        Muted("The store does not vouch for this publisher; confirm above to install anyway.")
    } else if (!inputsReady) {
        Muted("Fill in the required fields above.")
    }
    Row {
        AzButton(
            onClick = { onInstall(inputValues.toMap(), allowUntrusted, allowPublisherChange) },
            text = "Install",
            shape = AzButtonShape.NONE,
            enabled = enabled && trustReady && publisherReady && inputsReady,
        )
        Spacer(Modifier.width(8.dp))
        AzButton(onClick = onCancel, text = "Cancel", shape = AzButtonShape.NONE)
    }
}

@Composable
private fun ConfirmRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Checkbox),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun Muted(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
}
