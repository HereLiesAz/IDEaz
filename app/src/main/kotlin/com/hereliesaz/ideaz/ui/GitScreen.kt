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

    LaunchedEffect(Unit) {
        viewModel.refreshGitData()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Spacer(modifier = Modifier.height(64.dp))

        // Force Commit Button
        AzButton(
            onClick = { viewModel.forceUpdateInitFiles() },
            text = "Push Inits",
            shape = AzButtonShape.NONE
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Git Commands
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AzButton(onClick = { viewModel.gitFetch() }, text = "Fetch", shape = AzButtonShape.RECTANGLE)
            AzButton(onClick = { viewModel.gitPull() }, text = "Pull", shape = AzButtonShape.RECTANGLE)
            AzButton(onClick = { viewModel.gitPush() }, text = "Push", shape = AzButtonShape.RECTANGLE)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            AzButton(onClick = { viewModel.gitStash("Stash") }, text = "Stash", shape = AzButtonShape.RECTANGLE)
            AzButton(onClick = { viewModel.gitUnstash() }, text = "Unstash", shape = AzButtonShape.RECTANGLE)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Branches", style = MaterialTheme.typography.headlineSmall)

        // Branch Tree
        if (branches.isEmpty()) {
            GitEmptyState("No branches found")
        } else {
            LazyColumn(modifier = Modifier.height(200.dp)) {
                item {
                    BranchTree(branches) { branch ->
                        selectedBranch = branch
                        viewModel.switchBranch(branch)
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
