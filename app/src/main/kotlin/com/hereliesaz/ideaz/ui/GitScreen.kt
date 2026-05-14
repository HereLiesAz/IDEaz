package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    val commitHistory by viewModel.commitHistory.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val gitStatus by viewModel.gitStatus.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var selectedBranch by remember { mutableStateOf(settingsViewModel.getBranchName()) }
    var commitMessage by remember { mutableStateOf("") }
    var showStashDialog by remember { mutableStateOf(false) }
    var stashMessage by remember { mutableStateOf("") }
    var pendingBranchSwitch by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshGitData()
    }

    if (showStashDialog) {
        AlertDialog(
            onDismissRequest = { showStashDialog = false },
            title = { Text("Stash changes") },
            text = {
                Column {
                    Text("Save a message for this stash (optional).")
                    Spacer(modifier = Modifier.height(8.dp))
                    AzTextBox(
                        value = stashMessage,
                        onValueChange = { stashMessage = it },
                        hint = "Stash message",
                        onSubmit = {}
                    )
                }
            },
            confirmButton = {
                AzButton(
                    onClick = {
                        viewModel.gitStash(stashMessage.ifBlank { null })
                        stashMessage = ""
                        showStashDialog = false
                    },
                    text = "Stash",
                    shape = AzButtonShape.RECTANGLE
                )
            },
            dismissButton = {
                TextButton(onClick = { showStashDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (pendingBranchSwitch != null) {
        val targetBranch = pendingBranchSwitch!!
        AlertDialog(
            onDismissRequest = { pendingBranchSwitch = null },
            title = { Text("Uncommitted changes") },
            text = {
                Text(
                    "Switching to '$targetBranch' with a dirty working tree may " +
                    "fail or overwrite changes. Commit or stash first, or proceed " +
                    "anyway."
                )
            },
            confirmButton = {
                AzButton(
                    onClick = {
                        viewModel.switchBranch(targetBranch)
                        selectedBranch = targetBranch
                        pendingBranchSwitch = null
                    },
                    text = "Switch anyway",
                    shape = AzButtonShape.RECTANGLE
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingBranchSwitch = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // Top spacer leaves room for the rail-provided "Git" screen title.
        Spacer(modifier = Modifier.height(80.dp))

        // Commit a custom message — Phase 1 had no UI for this, so commits only
        // happened indirectly through deploy/build with timestamp-only messages.
        Text("Commit", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        AzTextBox(
            value = commitMessage,
            onValueChange = { commitMessage = it },
            hint = "Commit message",
            onSubmit = { msg ->
                if (msg.isNotBlank()) {
                    viewModel.gitCommit(msg)
                    commitMessage = ""
                }
            },
            submitButtonContent = { Text("Commit") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Git Commands
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AzButton(onClick = { viewModel.gitFetch() }, text = "Fetch", shape = AzButtonShape.RECTANGLE)
            AzButton(onClick = { viewModel.gitPull() }, text = "Pull", shape = AzButtonShape.RECTANGLE)
            AzButton(onClick = { viewModel.gitPush() }, text = "Push", shape = AzButtonShape.RECTANGLE)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            AzButton(onClick = { showStashDialog = true }, text = "Stash", shape = AzButtonShape.RECTANGLE)
            AzButton(onClick = { viewModel.gitUnstash() }, text = "Unstash", shape = AzButtonShape.RECTANGLE)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Regenerate the GitHub Actions workflow, setup.sh, and AGENTS_SETUP.md.
        // Previously labeled "Push Inits" which gave no clue what it does.
        AzButton(
            onClick = { viewModel.forceUpdateInitFiles() },
            text = "Regenerate CI Files",
            shape = AzButtonShape.RECTANGLE
        )
        Text(
            text = "Rebuilds workflow YAML, setup.sh, and AGENTS_SETUP.md, then commits and pushes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Branches", style = MaterialTheme.typography.headlineSmall)

        // Branch Tree. Tapping a branch when the working tree is dirty pops a
        // confirmation dialog rather than immediately invoking JGit's switch
        // (which would fail or overwrite).
        if (branches.isEmpty()) {
            GitEmptyState("No branches found")
        } else {
            LazyColumn(modifier = Modifier.height(200.dp)) {
                item {
                    BranchTree(branches) { branch ->
                        if (gitStatus.isEmpty()) {
                            selectedBranch = branch
                            viewModel.switchBranch(branch)
                        } else {
                            pendingBranchSwitch = branch
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Status", style = MaterialTheme.typography.headlineSmall)

        if (gitStatus.isEmpty()) {
            GitEmptyState("Working tree clean")
        } else {
            LazyColumn(modifier = Modifier.height(100.dp)) {
                items(gitStatus) { status ->
                    Text(status)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Commit History", style = MaterialTheme.typography.headlineSmall)

        if (commitHistory.isEmpty()) {
            GitEmptyState("No history available")
        } else {
            LazyColumn {
                items(commitHistory) { commit ->
                    Text(commit)
                }
            }
        }
    }
}

@Composable
private fun GitEmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BranchTree(branches: List<String>, onBranchSelected: (String) -> Unit) {
    val rootNodes = remember(branches) { buildTree(branches) }
    Column {
        rootNodes.forEach { node ->
            BranchNodeView(node, 0, onBranchSelected)
        }
    }
}

@Composable
fun BranchNodeView(node: BranchNode, depth: Int, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp)) {
        val path = node.fullPath
        if (path != null) {
            Text(
                text = node.name,
                modifier = Modifier.clickable { onSelect(path) },
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(text = node.name + "/", style = MaterialTheme.typography.bodyMedium)
        }
    }
    node.children.values.sortedBy { it.name }.forEach {
        BranchNodeView(it, depth + 1, onSelect)
    }
}

data class BranchNode(val name: String, var fullPath: String? = null, val children: MutableMap<String, BranchNode> = mutableMapOf())

fun buildTree(branches: List<String>): List<BranchNode> {
    val root = BranchNode("root")
    for (branch in branches) {
        val parts = branch.split("/")
        var current = root
        for ((index, part) in parts.withIndex()) {
            val isLeaf = index == parts.size - 1
            val node = current.children.getOrPut(part) {
                BranchNode(part, if (isLeaf) branch else null)
            }
            if (isLeaf && node.fullPath == null) {
                node.fullPath = branch
            }
            current = node
        }
    }
    return root.children.values.toList().sortedBy { it.name }
}
