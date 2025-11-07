package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hereliesaz.ideaz.api.Activity
import com.hereliesaz.ideaz.api.Session
import com.hereliesaz.ideaz.api.Source

@Composable
fun IdeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: MainViewModel,
    buildStatus: String,
    aiStatus: String,
    session: Session?,
    sessions: List<Session>,
    activities: List<Activity>,
    sources: List<Source>
) {
    NavHost(
        navController = navController,
        startDestination = "main",
        modifier = modifier
    ) {
        composable("main") {
            // This column now ONLY contains status information
            // Re-added the top 20% spacer
            Column {
                Spacer(modifier = Modifier.weight(0.2f))
                Column(
                    modifier = Modifier.padding(all = 8.dp).weight(0.8f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = "Build Status: $buildStatus")
                    Text(text = "AI Status: $aiStatus")
                    session?.let {
                        Text(text = "Session: ${it.name}")
                        it.outputs?.firstOrNull()?.pullRequest?.let { pr ->
                            Text(text = "Pull Request: ${pr.title}")
                        }
                    }
                    Text(text = "Sessions:")
                    sessions.forEach {
                        Text(text = it.name)
                    }
                    Text(text = "Activities:")
                    activities.forEach {
                        Text(text = it.description)
                    }
                }
            }
        }
        composable("settings") {
            SettingsScreen(sessions = sessions)
        }
        composable("project_settings") {
            ProjectSettingsScreen(viewModel = viewModel, sources = sources)
        }
    }
}