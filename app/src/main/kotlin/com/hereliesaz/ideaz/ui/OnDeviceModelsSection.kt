package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.ai.local.LocalModelCatalog
import com.hereliesaz.ideaz.ai.local.LocalModelRuntimes
import com.hereliesaz.ideaz.ai.local.LocalModelStore
import com.hereliesaz.ideaz.ai.local.ModelDownloadManager
import kotlinx.coroutines.launch

private fun humanSize(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1e6)
    else -> "%.0f KB".format(bytes / 1e3)
}

/**
 * Settings section that lets the user download an on-device model (per runtime)
 * and select which one the "On-device model" AI provider uses. AICore is offered
 * as a no-download, system-managed entry.
 */
@Composable
fun OnDeviceModelsSection(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LocalModelStore(context) }
    val downloads = remember { ModelDownloadManager(context) }

    var activeId by remember { mutableStateOf(store.activeModelId) }
    val progress = remember { mutableStateMapOf<String, Float>() }   // 0..1, or -1 = indeterminate
    val downloading = remember { mutableStateMapOf<String, Boolean>() }
    val errors = remember { mutableStateMapOf<String, String>() }
    var refresh by remember { mutableIntStateOf(0) }

    Text(
        "On-device Models",
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Run the AI fully on-device. Download a model (or use the system AICore model), " +
            "select it below, then choose \"On-device model\" as an AI Assignment above.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))

    val hfToken = settingsViewModel.getApiKey(SettingsViewModel.KEY_HF_API_KEY).orEmpty()

    LocalModelCatalog.models.forEach { model ->
        val runtime = LocalModelRuntimes.byId(model.runtimeId)
        val backendOk = runtime?.isAvailable(context) == true
        @Suppress("UNUSED_EXPRESSION") refresh // re-read download state when bumped
        val downloaded = downloads.isDownloaded(model)

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = activeId == model.id,
                        enabled = downloaded && backendOk,
                        onClick = {
                            activeId = model.id
                            store.activeModelId = model.id
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(model.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${runtime?.displayName ?: model.runtimeId}" +
                                (if (model.systemManaged) "" else " · ${humanSize(model.approxSizeBytes)}") +
                                (if (model.requiresAuth) " · gated" else ""),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (model.notes.isNotBlank()) {
                            Text(
                                model.notes,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (!backendOk) {
                            Text(
                                if (model.systemManaged) "Not available on this device."
                                else "Runtime backend not included in this build yet.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        errors[model.id]?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Download / delete controls (system-managed entries need none).
                if (!model.systemManaged) {
                    Spacer(Modifier.height(8.dp))
                    when {
                        downloading[model.id] == true -> {
                            val p = progress[model.id] ?: -1f
                            if (p in 0f..1f) {
                                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                                Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                        downloaded -> {
                            AzButton(
                                onClick = {
                                    downloads.delete(model)
                                    if (activeId == model.id) {
                                        activeId = null
                                        store.activeModelId = null
                                    }
                                    refresh++
                                },
                                text = "Delete",
                                shape = AzButtonShape.RECTANGLE,
                            )
                        }
                        else -> {
                            AzButton(
                                onClick = {
                                    errors.remove(model.id)
                                    downloading[model.id] = true
                                    progress[model.id] = -1f
                                    scope.launch {
                                        try {
                                            downloads.download(model, hfToken.ifBlank { null }) { d, t ->
                                                progress[model.id] = if (t > 0) d.toFloat() / t else -1f
                                            }
                                        } catch (e: Exception) {
                                            errors[model.id] = "Download failed: ${e.message}"
                                        } finally {
                                            downloading[model.id] = false
                                            refresh++
                                        }
                                    }
                                },
                                text = "Download",
                                shape = AzButtonShape.RECTANGLE,
                                enabled = !model.requiresAuth || hfToken.isNotBlank(),
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}
