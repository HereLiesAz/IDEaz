package com.hereliesaz.ideaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hereliesaz.ideaz.git.GitManager
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.theme.IDEazTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        ViewModelFactory(
            GitManager(
                // This is a placeholder path.
                // In a real app, you'd get this from a more robust source.
                File(filesDir, "project")
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure the project directory exists for JGit to open.
        val projectDir = File(filesDir, "project")
        if (!projectDir.exists()) {
            projectDir.mkdirs()
            // Initialize a git repository for the test
            GitManager(projectDir).apply {
                // This is a simplified init, in a real scenario you would clone
                org.eclipse.jgit.api.Git.init().setDirectory(projectDir).call()
            }
        }


        enableEdgeToEdge()
        setContent {
            IDEazTheme {
                MainScreen(mainViewModel)
            }
        }
    }
}

// A simple ViewModelFactory for demonstration purposes.
class ViewModelFactory(private val gitManager: GitManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(gitManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


@Composable
fun MainScreen(viewModel: MainViewModel) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Greeting(name = "Android")
            Button(onClick = { viewModel.applyPatch("dummy patch content") }) {
                Text("Apply Patch and Commit")
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    IDEazTheme {
        // This preview won't have the real ViewModel, so the button's action will do nothing.
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Greeting("Android")
            Button(onClick = { /* Do nothing in preview */ }) {
                Text("Apply Patch and Commit")
            }
        }
    }
}
