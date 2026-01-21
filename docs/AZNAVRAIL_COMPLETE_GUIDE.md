# AzNavRail Complete Guide

Welcome to the comprehensive guide for **AzNavRail**. This document contains everything you need to know to use the library, including setup instructions, a full API and DSL reference, layout rules, and complete sample code.

---

## Table of Contents

1.  [Getting Started](#getting-started)
2.  [AzNavHost Layout Rules](#aznavhost-layout-rules)
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

**IMPORTANT:** `AzNavRail` **MUST** be used within an `AzNavHost` container.

```kotlin
AzNavHost(navController = navController) {
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
        Text("My Content")
    }
}
```

---

## AzNavHost Layout Rules

`AzNavHost` enforces a "Strict Mode" layout system to ensure consistent UX and prevent overlap.

1.  **Rail Avoidance**: Content in the `onscreen` block is automatically padded to avoid the rail.
2.  **Safe Zones**: Content is restricted from the **Top 20%** and **Bottom 10%** of the screen.
3.  **Automatic Flipping**: Alignments passed to `onscreen` (e.g., `TopStart`) are mirrored if the rail is docked to the Right.
4.  **Backgrounds**: Use the `background(weight)` DSL to place full-screen content (e.g., maps) behind the UI. Backgrounds **ignore safe zones**.

**Example:**

```kotlin
AzNavHost(navController = navController) {
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

The DSL is used inside `AzNavHost` to configure the rail and items.

### AzNavHost Scope

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

### `AzNavHost`
```kotlin
@Composable
fun AzNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    currentDestination: String? = null,
    isLandscape: Boolean? = null,
    initiallyExpanded: Boolean = false,
    disableSwipeToOpen: Boolean = false,
    content: AzNavHostScope.() -> Unit
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hereliesaz.aznavrail.*
import com.hereliesaz.aznavrail.model.AzButtonShape

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
import androidx.navigation.compose.NavHost
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

    AzNavHost(
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

        // RAIL ITEMS
        azMenuItem(id = "home", text = "Home", route = "home", info = "Navigate to the Home screen", onClick = { Log.d(TAG, "Home menu item clicked") })
        azMenuItem(id = "multi-line", text = "This is a\nmulti-line item", route = "multi-line", info = "Shows how multi-line text is handled", onClick = { Log.d(TAG, "Multi-line menu item clicked") })

        azRailToggle(
            id = "pack-rail",
            isChecked = packRailButtons,
            toggleOnText = "Pack Rail",
            toggleOffText = "Unpack Rail",
            route = "pack-rail",
            info = "Toggle to pack items together or space them out",
            onClick = {
                packRailButtons = !packRailButtons
                Log.d(TAG, "Pack rail toggled to: $packRailButtons")
            }
        )

        azRailItem(
            id = "profile",
            text = "Profile",
            shape = AzButtonShape.CIRCLE,
            disabled = true,
            route = "profile",
            info = "User profile settings (Disabled)"
        )

        azDivider()

        azRailToggle(
            id = "online",
            isChecked = isOnline,
            toggleOnText = "Online",
            toggleOffText = "Offline",
            shape = AzButtonShape.SQUARE,
            route = "online",
            onClick = {
                isOnline = !isOnline
                Log.d(TAG, "Online toggled to: $isOnline")
            }
        )

        azMenuToggle(
            id = "dark-mode",
            isChecked = isDarkMode,
            toggleOnText = "Dark Mode",
            toggleOffText = "Light Mode",
            route = "dark-mode",
            onClick = {
                isDarkMode = !isDarkMode
                Log.d(TAG, "Dark mode toggled to: $isDarkMode")
            }
        )

        azMenuToggle(
            id = "overlay-mode",
            isChecked = useBasicOverlay,
            toggleOnText = "Using Basic Overlay",
            toggleOffText = "Using Foreground Overlay",
            route = "overlay-mode",
            onClick = {
                useBasicOverlay = !useBasicOverlay
                Log.d(TAG, "Overlay mode toggled to: $useBasicOverlay")
            }
        )

        azMenuToggle(
            id = "docking-side",
            isChecked = isDockingRight,
            toggleOnText = "Dock: Right",
            toggleOffText = "Dock: Left",
            route = "docking-side",
            onClick = {
                isDockingRight = !isDockingRight
                Log.d(TAG, "Docking side toggled to: ${if (isDockingRight) "Right" else "Left"}")
            }
        )

        azMenuToggle(
            id = "no-menu",
            isChecked = noMenu,
            toggleOnText = "No Menu: On",
            toggleOffText = "No Menu: Off",
            route = "no-menu",
            onClick = {
                noMenu = !noMenu
                Log.d(TAG, "No Menu toggled to: $noMenu")
            }
        )

        azRailItem(
            id = "toggle-help",
            text = "Help",
            info = "Toggle help screen mode",
            onClick = { showHelp = !showHelp }
        )

        azDivider()

        azRailCycler(
            id = "rail-cycler",
            options = railCycleOptions,
            selectedOption = railSelectedOption,
            disabledOptions = listOf("C"),
            route = "rail-cycler",
            onClick = {
                val currentIndex = railCycleOptions.indexOf(railSelectedOption)
                val nextIndex = (currentIndex + 1) % railCycleOptions.size
                railSelectedOption = railCycleOptions[nextIndex]
                Log.d(TAG, "Rail cycler clicked, new option: $railSelectedOption")
            }
        )

        azMenuCycler(
            id = "menu-cycler",
            options = menuCycleOptions,
            selectedOption = menuSelectedOption,
            route = "menu-cycler",
            onClick = {
                val currentIndex = menuCycleOptions.indexOf(menuSelectedOption)
                val nextIndex = (currentIndex + 1) % menuCycleOptions.size
                menuSelectedOption = menuCycleOptions[nextIndex]
                Log.d(TAG, "Menu cycler clicked, new option: $menuSelectedOption")
            }
        )


        azRailItem(id = "loading", text = "Load", route = "loading", onClick = {
            isLoading = !isLoading
            Log.d(TAG, "Loading toggled to: $isLoading")
        })

        azDivider()

        azMenuHostItem(id = "menu-host", text = "Menu Host", route = "menu-host", onClick = { Log.d(TAG, "Menu host item clicked") })
        azMenuSubItem(id = "menu-sub-1", hostId = "menu-host", text = "Menu Sub 1", route = "menu-sub-1", onClick = { Log.d(TAG, "Menu sub item 1 clicked") })
        azMenuSubItem(id = "menu-sub-2", hostId = "menu-host", text = "Menu Sub 2", route = "menu-sub-2", onClick = { Log.d(TAG, "Menu sub item 2 clicked") })

        azRailHostItem(id = "rail-host", text = "Rail Host", route = "rail-host", onClick = { Log.d(TAG, "Rail host item clicked") })
        azRailSubItem(id = "rail-sub-1", hostId = "rail-host", text = "Rail Sub 1", route = "rail-sub-1", onClick = { Log.d(TAG, "Rail sub item 1 clicked") })
        azMenuSubItem(id = "rail-sub-2", hostId = "rail-host", text = "Menu Sub 2", route = "rail-sub-2", onClick = { Log.d(TAG, "Menu sub item 2 (from rail host) clicked") })

        azMenuSubToggle(
            id = "sub-toggle",
            hostId = "menu-host",
            isChecked = isDarkMode,
            toggleOnText = "Sub Toggle On",
            toggleOffText = "Sub Toggle Off",
            route = "sub-toggle",
            onClick = {
                isDarkMode = !isDarkMode
                Log.d(TAG, "Sub toggle clicked, dark mode is now: $isDarkMode")
            }
        )

        azRailSubCycler(
            id = "sub-cycler",
            hostId = "rail-host",
            options = menuCycleOptions,
            selectedOption = menuSelectedOption,
            route = "sub-cycler",
            shape = null,
            onClick = {
                val currentIndex = menuCycleOptions.indexOf(menuSelectedOption)
                val nextIndex = (currentIndex + 1) % menuCycleOptions.size
                menuSelectedOption = menuCycleOptions[nextIndex]
                Log.d(TAG, "Sub cycler clicked, new option: $menuSelectedOption")
            }
        )

        // BACKGROUNDS
        background(weight = 0) {
            Box(Modifier.fillMaxSize().background(Color(0xFFEEEEEE)))
        }

        background(weight = 10) {
             // Example overlay background
             Box(Modifier.fillMaxSize().padding(50.dp).background(Color.Blue.copy(alpha = 0.1f))) {
                 Text("Background Layer (Weight 10)", color = Color.Blue)
             }
        }

        // ONSCREEN COMPONENTS
        if (showContent) {
            onscreen(alignment = Alignment.TopStart) {
                Text("Aligned TopStart (Flips)", modifier = Modifier.padding(16.dp))
            }

            onscreen(alignment = Alignment.TopEnd) {
                Text("Aligned TopEnd (Flips)", modifier = Modifier.padding(16.dp))
            }

            onscreen(alignment = Alignment.Center) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Uncontrolled AzTextBox with history context
                    AzTextBox(
                        modifier = Modifier.padding(bottom = 16.dp),
                        hint = "Uncontrolled (History: Search)",
                        historyContext = "search_history",
                        onSubmit = { text ->
                            Log.d(TAG, "Submitted text from uncontrolled AzTextBox: $text")
                        },
                        submitButtonContent = {
                            Text("Go")
                        }
                    )

                    // Controlled AzTextBox with a different history context
                    var controlledText by remember { mutableStateOf("") }
                    AzTextBox(
                        modifier = Modifier.padding(bottom = 16.dp),
                        value = controlledText,
                        onValueChange = { controlledText = it },
                        hint = "Controlled (History: Usernames)",
                        historyContext = "username_history",
                        onSubmit = { text ->
                            Log.d(TAG, "Submitted text from controlled AzTextBox: $text")
                        },
                        submitButtonContent = {
                            Text("Go")
                        }
                    )

                    // AzTextBox with inverted outline
                    AzTextBox(
                        modifier = Modifier.padding(bottom = 16.dp),
                        hint = "Uncontrolled (No Outline)",
                        outlined = false,
                        onSubmit = { text ->
                            Log.d(TAG, "Submitted text from no-outline AzTextBox: $text")
                        },
                        submitButtonContent = {
                            Text("Go")
                        }
                    )

                    // Disabled AzTextBox
                    AzTextBox(
                        modifier = Modifier.padding(bottom = 16.dp),
                        hint = "Disabled",
                        enabled = false,
                        onSubmit = { Log.d(TAG, "Submitted disabled") }
                    )

                    AzForm(
                        formName = "loginForm",
                        modifier = Modifier.padding(bottom = 16.dp),
                        onSubmit = { formData ->
                            Log.d(TAG, "Form submitted: $formData")
                        },
                        submitButtonContent = {
                            Text("Login")
                        }
                    ) {
                        entry(entryName = "username", hint = "Username")
                        entry(entryName = "password", hint = "Password", secret = true)
                        entry(entryName = "bio", hint = "Biography", multiline = true)
                    }

                    AzForm(
                        formName = "registrationForm",
                        outlined = false,
                        onSubmit = { formData ->
                            Log.d(TAG, "Registration Form submitted: $formData")
                        },
                        submitButtonContent = {
                            Text("Register")
                        }
                    ) {
                        entry(entryName = "email", hint = "Email", enabled = false)
                        entry(entryName = "confirm_password", hint = "Confirm Password", secret = true)
                    }

                    Row {
                        var buttonLoading by remember { mutableStateOf(false) }
                        AzButton(
                            onClick = {
                                Log.d(TAG, "Standalone AzButton clicked")
                                buttonLoading = !buttonLoading
                            },
                            text = "Button",
                            shape = AzButtonShape.SQUARE,
                            isLoading = buttonLoading,
                            contentPadding = PaddingValues(16.dp)
                        )

                        AzButton(
                            onClick = { Log.d(TAG, "Disabled clicked") },
                            text = "Disabled",
                            enabled = false
                        )

                        var isToggled by remember { mutableStateOf(false) }
                        AzToggle(
                            isChecked = isToggled,
                            onToggle = { isToggled = !isToggled },
                            toggleOnText = "On",
                            toggleOffText = "Off",
                            shape = AzButtonShape.RECTANGLE
                        )
                        val cyclerOptions = remember { listOf("1", "2", "3") }
                        var selectedCyclerOption by remember { mutableStateOf(cyclerOptions.first()) }
                        AzCycler(
                            options = cyclerOptions,
                            selectedOption = selectedCyclerOption,
                            onCycle = {
                                val currentIndex = cyclerOptions.indexOf(selectedCyclerOption)
                                val nextIndex = (currentIndex + 1) % cyclerOptions.size
                                selectedCyclerOption = cyclerOptions[nextIndex]
                            },
                            shape = AzButtonShape.CIRCLE
                        )
                    }

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") { Text("Home Screen") }
                        composable("multi-line") { Text("Multi-line Screen") }
                        composable("menu-host") { Text("Menu Host Screen") }
                        composable("menu-sub-1") { Text("Menu Sub 1 Screen") }
                        composable("menu-sub-2") { Text("Menu Sub 2 Screen") }
                        composable("rail-host") { Text("Rail Host Screen") }
                        composable("rail-sub-1") { Text("Rail Sub 1 Screen") }
                        composable("rail-sub-2") { Text("Rail Sub 2 Screen") }
                        composable("sub-toggle") { Text("Sub Toggle Screen") }
                        composable("sub-cycler") { Text("Sub Cycler Screen") }
                        composable("pack-rail") { Text("Pack Rail Screen") }
                        composable("profile") { Text("Profile Screen") }
                        composable("online") { Text("Online Screen") }
                        composable("dark-mode") { Text("Dark Mode Screen") }
                        composable("rail-cycler") { Text("Rail Cycler Screen") }
                        composable("menu-cycler") { Text("Menu Cycler Screen") }
                        composable("loading") { Text("Loading Screen") }
                    }
                }
            }
        }
    }
}
```
