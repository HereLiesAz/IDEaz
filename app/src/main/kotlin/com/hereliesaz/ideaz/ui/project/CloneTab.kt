package com.hereliesaz.ideaz.ui.project

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.ideaz.api.GitHubRepoResponse
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel

@Composable
fun ProjectCloneTab(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    context: Context,
    onRepoSelected: (GitHubRepoResponse) -> Unit,
    onCreateNewSelected: () -> Unit
) {
    val ownedRepos by viewModel.ownedRepos.collectAsState()
    var cloneUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.fetchGitHubRepos()
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            // Row 1: Fork URL
            AzTextBox(
                value = cloneUrl,
                onValueChange = { cloneUrl = it },
                hint = "https://github.com/user/repo (to fork)",
                modifier = Modifier.fillMaxWidth(),
                onSubmit = {
                    viewModel.forkRepository(cloneUrl)
                    Toast.makeText(context, "Forking...", Toast.LENGTH_SHORT).show()
                },
                submitButtonContent = { Text("Fork") }
            )
            Spacer(Modifier.height(8.dp))

            // Row 2: Create New Button + Refresh Icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                AzButton(
                    modifier = Modifier.weight(1f),
                    onClick = onCreateNewSelected,
                    text = "Create New Repository",
                    shape = AzButtonShape.RECTANGLE
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { viewModel.fetchGitHubRepos() }) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        if (ownedRepos.isNotEmpty()) {
            item {
                Text("Your Repositories:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            items(ownedRepos) { repo ->
                Card(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                        .clickable { onRepoSelected(repo) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(repo.fullName, style = MaterialTheme.typography.bodyLarge)
                        Text(repo.htmlUrl, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}