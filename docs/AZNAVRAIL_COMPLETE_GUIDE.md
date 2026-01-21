# AzNavRail Complete Guide

Welcome to the comprehensive guide for **AzNavRail**. This document contains everything you need to know to use the library, including setup instructions, a full API and DSL reference, layout rules, and complete sample code.

---

## Table of Contents

1.  [Getting Started](#getting-started)
2.  [AzHostActivityLayout Layout Rules](#azhostactivitylayout-layout-rules)
3.  [DSL Reference](#dsl-reference)
4.  [API Reference](#api-reference)
5.  [Sample Application Source Code](#sample-application-source-code)

---

## Getting Started

### Installation

To use AzNavRail, add JitPack to your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.HereLiesAz:AzNavRail:VERSION") // Replace VERSION with the latest release
}
```

### Basic Usage

**IMPORTANT:** `AzNavRail` **MUST** be used within an `AzHostActivityLayout` container.

```kotlin
AzHostActivityLayout(navController = navController) {
    azSettings(
        displayAppNameInHeader = true,
        dockingSide = AzDockingSide.LEFT
    )

    // Define Rail Items (Visible on collapsed rail)
    azRailItem(id = "home", text = "Home", route = "home", onClick = { /* navigate */ })

    // Define Menu Items (Visible only when expanded)
    azMenuItem(id = "settings", text = "Settings", route = "settings", onClick = { /* navigate */ })

    // Define Content
    onscreen(Alignment.Center) {
        AzNavHost(navController = navController, startDestination = "home") {
             composable("home") { Text("Home Screen") }
             // ...
        }
    }
}
```

---

## AzHostActivityLayout Layout Rules

`AzHostActivityLayout` enforces a "Strict Mode" layout system to ensure consistent UX and prevent overlap.

1.  **Rail Avoidance**: Content in the `onscreen` block is automatically padded to avoid the rail.
2.  **Safe Zones**: Content is restricted from the **Top 20%** and **Bottom 10%** of the screen.
3.  **Automatic Flipping**: Alignments passed to `onscreen` (e.g., `TopStart`) are mirrored if the rail is docked to the Right.
4.  **Backgrounds**: Use the `background(weight)` DSL to place full-screen content (e.g., maps) behind the UI. Backgrounds **ignore safe zones**.

**Example:**

```kotlin
AzHostActivityLayout(navController = navController) {
    // Full screen background
    background(weight = 0) {
        GoogleMap(...)
    }

    // Safe UI content
    onscreen(Alignment.TopEnd) {
        Text("Overlay")
    }
}
```

---

## DSL Reference

The DSL is used inside `AzHostActivityLayout` to configure the rail and items.

### AzHostActivityLayout Scope

-   `background(weight: Int, content: @Composable () -> Unit)`: Adds a background layer ignoring safe zones.
-   `onscreen(alignment: Alignment, content: @Composable () -> Unit)`: Adds content to the safe area.

### AzNavRail Scope

**Settings:**
-   `azSettings(...)`: Configures global settings. Parameters:
    - `displayAppNameInHeader`: Boolean
    - `packRailButtons`: Boolean
    - `expandedRailWidth`: Dp
    - `collapsedRailWidth`: Dp
    - `showFooter`: Boolean
    - `isLoading`: Boolean
    - `defaultShape`: AzButtonShape
    - `enableRailDragging`: Boolean
    - `headerIconShape`: AzHeaderIconShape
    - `onUndock`: (() -> Unit)?
    - `overlayService`: Class<out Service>?
    - `onOverlayDrag`: ((Float, Float) -> Unit)?
    - `onItemGloballyPositioned`: ((String, Rect) -> Unit)?
    - `infoScreen`: Boolean
    - `onDismissInfoScreen`: (() -> Unit)?
    - `activeColor`: Color?
    - `vibrate`: Boolean
    - `dockingSide`: AzDockingSide (LEFT/RIGHT)
    - `noMenu`: Boolean

**Items:**
-   `azMenuItem(...)`: Item visible only in expanded menu.
-   `azRailItem(...)`: Item visible in rail and menu.
-   `azMenuToggle(...)` / `azRailToggle(...)`: Toggle buttons.
-   `azMenuCycler(...)` / `azRailCycler(...)`: Cycle through options.
-   `azDivider()`: Horizontal divider.
-   `azMenuHostItem(...)` / `azRailHostItem(...)`: Parent items for nested menus.
-   `azMenuSubItem(...)` / `azRailSubItem(...)`: Child items.
-   `azRailRelocItem(...)`: Reorderable drag-and-drop items.

**Common Parameters:**
-   `id`: Unique identifier.
-   `text`: Display label.
-   `route`: Navigation route (optional).
-   `icon`: (Implicitly handled by shapes/text in this library).
-   `disabled`: Boolean state.
-   `info`: Help text for Info Screen mode.
-   `onClick`: Lambda action.

---

## API Reference

### `AzHostActivityLayout`
```kotlin
@Composable
fun AzHostActivityLayout(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    currentDestination: String? = null,
    isLandscape: Boolean? = null,
    initiallyExpanded: Boolean = false,
    disableSwipeToOpen: Boolean = false,
    content: AzNavHostScope.() -> Unit
)
```

### `AzNavHost`
```kotlin
@Composable
fun AzNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    route: String? = null,
    // ... transition params
    builder: NavGraphBuilder.() -> Unit
)
```

### `AzTextBox`
A versatile text input component.
```kotlin
@Composable
fun AzTextBox(
    modifier: Modifier = Modifier,
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    hint: String = "",
    outlined: Boolean = true,
    multiline: Boolean = false,
    secret: Boolean = false,
    isError: Boolean = false,
    historyContext: String? = null,
    submitButtonContent: (@Composable () -> Unit)? = null,
    onSubmit: (String) -> Unit
)
```

### `AzForm`
Groups `AzTextBox` fields.
```kotlin
@Composable
fun AzForm(
    formName: String,
    onSubmit: (Map<String, String>) -> Unit,
    content: AzFormScope.() -> Unit
)
```

### `AzButton`, `AzToggle`, `AzCycler`
Standalone versions of the rail components are available for general UI use.

---

## Sample Application Source Code

Below is the complete source code for a functional Sample App demonstrating all features.

### `MainActivity.kt`

```kotlin
package com.example.sampleapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current

                    // Request Notification Permission for Android 13+
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                         Log.d("MainActivity", "Notification permission granted: $isGranted")
                    }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    val startOverlay = {
                        if (Settings.canDrawOverlays(context)) {
                            val intent = Intent(context, SampleOverlayService::class.java)
                            ContextCompat.startForegroundService(context, intent)
                        } else {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    }

                    SampleScreen(
                        onUndockOverride = {
                            startOverlay()
                        }
                    )
                }
            }
        }
    }
}
```

### `SampleScreen.kt`

```kotlin
package com.example.sampleapp

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hereliesaz.aznavrail.*
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.aznavrail.model.AzDockingSide

@Composable
fun SampleScreen(
    enableRailDragging: Boolean = true,
    initiallyExpanded: Boolean = false,
    onUndockOverride: (() -> Unit)? = null,
    onRailDrag: ((Float, Float) -> Unit)? = null,
    showContent: Boolean = true
) {
    val TAG = "SampleApp"
    val navController = rememberNavController()
    val currentDestination by navController.currentBackStackEntryAsState()
    var isOnline by remember { mutableStateOf(true) }
    var isDarkMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var packRailButtons by remember { mutableStateOf(false) }
    val railCycleOptions = remember { listOf("A", "B", "C", "D") }
    var railSelectedOption by remember { mutableStateOf(railCycleOptions.first()) }
    val menuCycleOptions = remember { listOf("X", "Y", "Z") }
    var menuSelectedOption by remember { mutableStateOf(menuCycleOptions.first()) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // Set the global suggestion limit for all AzTextBox instances
    AzTextBoxDefaults.setSuggestionLimit(3)

    var useBasicOverlay by remember { mutableStateOf(false) }
    var isDockingRight by remember { mutableStateOf(false) }
    var noMenu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    AzHostActivityLayout(
        navController = navController,
        modifier = Modifier.fillMaxSize(),
        currentDestination = currentDestination?.destination?.route,
        isLandscape = isLandscape,
        initiallyExpanded = initiallyExpanded
    ) {
        azSettings(
            packRailButtons = packRailButtons,
            isLoading = isLoading,
            defaultShape = AzButtonShape.RECTANGLE,
            enableRailDragging = enableRailDragging,
            onUndock = onUndockOverride,
            onRailDrag = onRailDrag,
            overlayService = if (useBasicOverlay) SampleBasicOverlayService::class.java else SampleOverlayService::class.java,
            dockingSide = if (isDockingRight) AzDockingSide.RIGHT else AzDockingSide.LEFT,
            noMenu = noMenu,
            infoScreen = showHelp,
            onDismissInfoScreen = { showHelp = false }
        )

        // RAIL ITEMS ... (same as previous examples)
        azMenuItem(id = "home", text = "Home", route = "home", onClick = { Log.d(TAG, "Home menu item clicked") })
        // ...

        // BACKGROUNDS
        background(weight = 0) {
            Box(Modifier.fillMaxSize().background(Color(0xFFEEEEEE)))
        }

        // ONSCREEN COMPONENTS
        if (showContent) {
            onscreen(alignment = Alignment.Center) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // ... AzTextBox examples ...

                    AzNavHost(navController = navController, startDestination = "home") {
                        composable("home") { Text("Home Screen") }
                        // ...
                    }
                }
            }
        }
    }
}
```
