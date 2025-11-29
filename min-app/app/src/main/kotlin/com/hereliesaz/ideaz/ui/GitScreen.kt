package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

        Text("Branches", style = MaterialTheme.typography.headlineSmall)

        // Branch Tree
        LazyColumn(modifier = Modifier.height(200.dp)) {
             item {
                 BranchTree(branches) { branch ->
                     selectedBranch = branch
                      // viewModel.switchBranch(branch) // Removed in min-app
                 }
             }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Status", style = MaterialTheme.typography.headlineSmall)

        LazyColumn(modifier = Modifier.height(100.dp)) {
            items(gitStatus) { status ->
                Text(status)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Commit History", style = MaterialTheme.typography.headlineSmall)

        LazyColumn {
            items(commitHistory) { commit ->
                Text(commit)
            }
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
