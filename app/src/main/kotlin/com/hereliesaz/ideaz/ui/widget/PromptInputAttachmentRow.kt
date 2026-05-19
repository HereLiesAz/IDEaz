package com.hereliesaz.ideaz.ui.widget

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * In-memory attachment intent. The user picked a file via SAF; the host
 * (PromptPopup or chat input) collects these and, on submit, materialises
 * them as project files (Asset mode) or `ChatPart`s (Reference mode).
 */
data class Attachment(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val mode: Mode,
) {
    enum class Mode { ASSET, REFERENCE }
}

/**
 * Paperclip button + horizontal chip list for prompt-input attachments.
 *
 * Each chip displays the filename and current mode. Tapping the chip body
 * opens a mode menu (Asset / Reference); Reference is disabled when the
 * active provider lacks image support. The close icon removes the chip.
 *
 * Default mode is **Asset** — saves the file into the project tree.
 *
 * @param attachments Current attachment list (host-owned state).
 * @param onAttachmentsChanged Receives the updated list; host re-renders.
 * @param providerSupportsImages If false, the Reference option is greyed out.
 */
@Composable
fun PromptInputAttachmentRow(
    attachments: List<Attachment>,
    onAttachmentsChanged: (List<Attachment>) -> Unit,
    providerSupportsImages: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        var name = "attached"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0) size = c.getLong(sizeIdx)
            }
        }
        onAttachmentsChanged(
            attachments + Attachment(
                uri = uri,
                displayName = name,
                mimeType = mime,
                sizeBytes = size,
                mode = Attachment.Mode.ASSET,
            )
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = { pickFile.launch("*/*") }) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = "Attach file",
            )
        }
        if (attachments.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 4.dp),
            ) {
                items(attachments, key = { it.uri.toString() }) { att ->
                    AttachmentChip(
                        attachment = att,
                        providerSupportsImages = providerSupportsImages,
                        onModeChanged = { newMode ->
                            onAttachmentsChanged(
                                attachments.map { if (it.uri == att.uri) it.copy(mode = newMode) else it }
                            )
                        },
                        onRemove = {
                            onAttachmentsChanged(attachments.filterNot { it.uri == att.uri })
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: Attachment,
    providerSupportsImages: Boolean,
    onModeChanged: (Attachment.Mode) -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val modeLabel = when (attachment.mode) {
        Attachment.Mode.ASSET -> "Asset"
        Attachment.Mode.REFERENCE -> "Reference"
    }
    AssistChip(
        onClick = { menuOpen = true },
        label = { Text("${attachment.displayName} · $modeLabel") },
        trailingIcon = {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.padding(0.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove ${attachment.displayName}",
                )
            }
        },
        colors = AssistChipDefaults.assistChipColors(),
    )
    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false },
    ) {
        DropdownMenuItem(
            text = { Text("Asset (save to project)") },
            onClick = {
                onModeChanged(Attachment.Mode.ASSET)
                menuOpen = false
            },
        )
        DropdownMenuItem(
            text = {
                if (providerSupportsImages) {
                    Text("Reference (send to AI)")
                } else {
                    Text("Reference — provider can't read files")
                }
            },
            enabled = providerSupportsImages,
            onClick = {
                onModeChanged(Attachment.Mode.REFERENCE)
                menuOpen = false
            },
        )
    }
}
