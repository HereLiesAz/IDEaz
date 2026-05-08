# AzNavRail Complete Guide (Sample App Edition)

This guide documents the complete configuration and usage of the AzNavRail library as demonstrated in the official **Sample App**. It serves as the definitive reference for setting up layouts, configuring the rail, and implementing all supported components.

---

## 1. Top-Level Setup: Host Activity Layout

Every AzNavRail implementation **must** start with `AzHostActivityLayout`. This container manages safe zones, device rotation (0°, 90°, 270°), and z-ordering.

**Sample App Implementation:**
```kotlin
AzHostActivityLayout(
    navController = navController,
    modifier = Modifier.fillMaxSize(),
    currentDestination = currentDestination?.destination?.route,
    isLandscape = isLandscape, // derived from LocalConfiguration
    initiallyExpanded = false
) {
    // 1. Configure the Rail here (DSL)
    // 2. Define Background layers here (DSL)
    // 3. Define Onscreen content here (DSL)
}
```

---

## 2. Rail Configuration (DSL)

Inside the `AzHostActivityLayout` content block, you configure the rail using three primary functions: `azConfig`, `azTheme`, and `azAdvanced`.

### A. General Configuration (`azConfig`)
Controls layout behavior and docking logic.

```kotlin
azConfig(
    packButtons = packRailButtons,       // Boolean: Pack items tightly vs spaced
    dockingSide = AzDockingSide.LEFT,    // Enum: LEFT or RIGHT
    noMenu = noMenu,                     // Boolean: Disable the side drawer entirely
    usePhysicalDocking = usePhysicalDocking // Boolean: Anchor to physical hardware edge vs visual left
)
```

### B. Theming (`azTheme`)
Controls visual style defaults.

```kotlin
azTheme(
    defaultShape = AzButtonShape.RECTANGLE, // Default shape for all items
    activeColor = MaterialTheme.colorScheme.primary, // Color for active state
    helpLineColors = listOf(Color.Red, Color.Green, Color.Blue) // Colors for Help card lines
)
```

### C. Advanced Features (`azAdvanced`)
Enables complex behaviors like drag-and-drop and help overlays.

```kotlin
azAdvanced(
    isLoading = isLoading,               // Boolean: Show global loading overlay
    enableRailDragging = true,           // Boolean: Enable FAB Mode (detach rail)
    helpEnabled = showHelp,              // Boolean: Show Help Overlay
    helpList = mapOf("home" to "Home screen") // Map<String, Any>: Extra help texts
    onDismissHelp = { showHelp = false }
)
```

---


### D. Item Customization (Colors & Text)
Most navigation items (`azRailItem`, `azMenuItem`, toggles, cyclers, etc.) support overriding their display text and colors when shown in the menu versus the rail:
- `menuText`: Optional alternate text to display when the item is expanded in the side menu (overrides `text`).
- `menuToggleOnText`, `menuToggleOffText`: Optional alternate text for toggles when in the menu.
- `menuOptions`: Optional alternate list of strings for cyclers when in the menu.
- `textColor`: Custom color for the text itself.
- `fillColor`: Custom color for the button's translucent background surface. By default, the `fillColor` is Black (with 25% opacity), unless the item's main color is Black, in which case it is White (with 25% opacity) to ensure proper contrast.

### E. Menu Font Size & Theming
The expanded menu text font size (and the footer items text size) is strictly controlled by your app's `MaterialTheme.typography.titleLarge`. To adjust the text size inside the side menu drawer, simply customize the `titleLarge` attribute in your app's typography theme!

## 3. Navigation Items (DSL)

Items are added sequentially. The order in the DSL determines the order in the rail/menu.

### Standard Items
*   **Menu Item:** Only appears in the expanded drawer.
*   **Help Rail Item:** Dedicated trigger for the Help overlay.
*   **Rail Item:** Appears in the rail (and drawer).
*   **Content Types:** Supports Text, resource IDs (Icons), and `Color`.

```kotlin
// Menu-only item
azMenuItem(
    id = "home",
    text = "Home",
    route = "home",
    info = "Navigate to the Home screen",
    onClick = { /* log click */ }
)

// Multi-line text support
azMenuItem(id = "multi-line", text = "This is a\nmulti-line item", route = "multi-line")

// Help trigger rail item
azHelpRailItem(id = "help-trigger", text = "Get Help")

// Help trigger as a sub-item
azHelpSubItem(id = "help-sub-trigger", hostId = "rail-host", text = "Get Help Here")

// Rail item with Color content
azRailItem(id = "color-item", text = "Color", content = Color.Red)

// Rail item with Icon Resource
azRailItem(id = "icon-item", text = "Icon", content = android.R.drawable.ic_menu_agenda)

// Rail item with specific shape override
azRailItem(id = "none-shape", text = "No Shape", shape = AzButtonShape.NONE)

// Disabled item
azRailItem(id = "profile", text = "Profile", disabled = true, route = "profile")

// Rail item with custom @Composable content via AzComposableContent
azRailItem(
    id = "size_item",
    text = "Size",
    content = AzComposableContent { isEnabled ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isEnabled) {
                    if (isEnabled) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            // Drag logic
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
)
```

### Toggles
Binary switches for state (e.g., Online/Offline, Dark Mode).

```kotlin
// Rail Toggle
azRailToggle(
    id = "pack-rail",
    isChecked = packRailButtons,
    toggleOnText = "Packed",
    toggleOffText = "Unpacked",
    route = "pack-rail",
    onClick = { packRailButtons = !packRailButtons }
)

// Menu Toggle
azMenuToggle(
    id = "dark-mode",
    isChecked = isDarkMode,
    toggleOnText = "Dark Mode",
    toggleOffText = "Light Mode",
    onClick = { isDarkMode = !isDarkMode }
)
```

### Cyclers
Multi-state buttons that cycle through a list of options.

```kotlin
// Rail Cycler (with disabled specific option)
azRailCycler(
    id = "rail-cycler",
    options = listOf("A", "B", "C", "D"),
    selectedOption = "A",
    disabledOptions = listOf("C"),
    onClick = { /* cycle logic */ }
)

// Menu Cycler
azMenuCycler(
    id = "menu-cycler",
    options = listOf("X", "Y", "Z"),
    selectedOption = "X",
    onClick = { /* cycle logic */ }
)
```

### Dividers
Visual separators.
```kotlin
azDivider()
```

---

## 4. Hierarchical Navigation (Hosts)

Hosts are accordion-style items that expand to reveal sub-items.

```kotlin
// Menu Host
azMenuHostItem(id = "menu-host", text = "Menu Host")
// Sub-items must reference the hostId
azMenuSubItem(id = "menu-sub-1", hostId = "menu-host", text = "Menu Sub 1")
azMenuSubToggle(id = "sub-toggle", hostId = "menu-host", isChecked = true, toggleOnText = "On", toggleOffText = "Off")

// Rail Host
azRailHostItem(id = "rail-host", text = "Rail Host")
azRailSubItem(id = "rail-sub-1", hostId = "rail-host", text = "Rail Sub 1")
azHelpSubItem(id = "help-sub-item", hostId = "rail-host", text = "Help Sub")
azRailSubCycler(id = "sub-cycler", hostId = "rail-host", options = listOf("A", "B"), selectedOption = "A")
```

---

## 5. Drag & Drop (Relocatable Items)

Items that can be reordered by the user.
**Requirement:** Minimum of 2 items with the same `hostId`.

```kotlin
azRailRelocItem(
    id = "reloc-1",
    hostId = "rail-host", // Cluster ID
    text = "Reloc Item 1",
    onRelocate = { from, to, newOrder -> /* handle reorder */ }
) {
    // Hidden Context Menu (Tap to open)
    listItem(text = "Action 1", onClick = { })
}
```

---

## 6. Nested Rails (Popups)

Secondary rails that open in a popup overlay. Do NOT assign a route to the parent item.

**Dynamic Bumping Effect:** When a vertical nested rail is opened, the main navigation rail will dynamically decrease its width (shrinking to the button width) to simulate the nested rail bumping it out of the way. Closing the nested rail restores the main rail to its original width.

```kotlin
// Vertical Nested Rail
azNestedRail(
    id = "nested-rail",
    text = "Vertical Nested",
    alignment = AzNestedRailAlignment.VERTICAL,
    keepNestedRailOpen = true // Remains open until parent is tapped again
) {
    azRailItem(id = "nested-1", text = "Nested Item 1", route = "nested-1")
}

// Horizontal Nested Rail
azNestedRail(
    id = "nested-horizontal",
    text = "Horizontal Nested",
    alignment = AzNestedRailAlignment.HORIZONTAL
) {
    azRailItem(id = "nested-h-1", text = "H-Item 1")
}
```

---

## 7. Layout Layers (Background & Onscreen)

AzNavRail allows defining content layers relative to the rail.

### Background Layers
Content placed *behind* the rail.

```kotlin
background(weight = 0) {
    // Full screen background (e.g. Map)
    Box(Modifier.fillMaxSize().background(Color(0xFFEEEEEE)))
}

background(weight = 10) {
    // Layer with padding
    Box(...)
}
```

### Onscreen Content
The main UI content, automatically padded to respect safe zones and rail width.

**Usage:**
~~~kotlin
// Basic Usage
azRailRelocItem(
    id = "1",
    hostId = "favs",
    text = "Favorite A",
    onRelocate = { from, to, newOrder -> }
) {
    // Define Hidden Context Menu (Fallback)
    listItem("Delete") { }
}

// As a Nested Rail Parent
azRailRelocItem(
    id = "tools_reloc",
    hostId = "toolbar",
    text = "Drag Me",
    nestedRailAlignment = AzNestedRailAlignment.HORIZONTAL, // Customize direction
    keepNestedRailOpen = true, // Remains open until parent is tapped again
    nestedContent = {
        // This content appears in the popup when the item is clicked (not dragged)
        azRailItem("hammer", "Hammer")
        azRailItem("wrench", "Wrench")
    }
) {
    // Hidden Menu (optional if nestedContent is provided)
    listItem("Remove Tool") { }
}
~~~

---

## 8. Standalone Components

These components are used within your screens (e.g., inside `AzNavHost`), not inside the rail configuration.

### Standalone Buttons
You can use `AzButton`, `AzToggle`, and `AzCycler` anywhere in your app to match the aesthetic of the rail. They support text or custom composable content.

```kotlin
AzButton(
    text = "Click Me",
    onClick = { /* Do something */ },
    shape = AzButtonShape.RECTANGLE,
    color = MaterialTheme.colorScheme.secondary,
    itemContent = { Icon(Icons.Default.Add, contentDescription = "Add") } // Optional custom content
)
```

### AzTextBox
Advanced text input with history support.

*   **Uncontrolled (History):** `historyContext` persists values.
    ```kotlin
    AzTextBox(hint = "Search", historyContext = "search_history", onSubmit = {})
    ```
*   **Controlled:** Manually manage state via `value` and `onValueChange`.
    ```kotlin
    AzTextBox(value = text, onValueChange = { text = it }, hint = "Controlled")
    ```
*   **No Outline:** `outlined = false`
*   **Disabled:** `enabled = false`

### AzForm
Groups AzTextBoxes for validation and traversal.

```kotlin
AzForm(
    formName = "loginForm",
    onSubmit = { formData -> /* Map<String, String> */ }
) {
    entry(entryName = "username", hint = "Username")
    entry(entryName = "password", hint = "Password", secret = true) // Password mask
    entry(entryName = "bio", hint = "Biography", multiline = true)  // Multi-line
}
```

### AzRoller
Slot-machine style selector.

```kotlin
AzRoller(
    options = listOf("Cherry", "Bell", "Bar"),
    selectedOption = "Cherry",
    onOptionSelected = { it -> }
)
```

### AzButton / AzToggle / AzCycler
Standalone versions of rail components for general UI use.

```kotlin
AzButton(text = "Button", onClick = {}, shape = AzButtonShape.SQUARE)
AzToggle(isChecked = true, onToggle = {}, toggleOnText = "On", toggleOffText = "Off")
AzCycler(options = listOf("1", "2"), selectedOption = "1", onCycle = {})
```


## Tutorial Framework

AzNavRail features a powerful tutorial framework allowing you to script interactive scenes, dim the screen, and highlight specific items via an easy-to-use DSL. Tutorials are passed in `azAdvanced` or `azSettings`. When a tutorial is associated with an item ID, tapping that item's Help card will launch the tutorial sequence.

```kotlin
import com.hereliesaz.aznavrail.tutorial.AzHighlight
import com.hereliesaz.aznavrail.tutorial.azTutorial

azAdvanced(
    helpEnabled = true,
    tutorials = mapOf(
        "my-item-id" to azTutorial {
            // A scene displays custom composable content underneath the tutorial overlay
            scene(
                id = "scene1",
                content = {
                    Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
                        Text("Scripted App Screen", color = Color.White)
                    }
                }
            ) {
                // Cards display textual instructions with next/skip actions
                card(
                    title = "Welcome",
                    text = "Welcome to the tutorial.",
                    highlight = AzHighlight.FullScreen
                )
                card(
                    title = "Highlighting",
                    text = "Notice the highlighted item.",
                    highlight = AzHighlight.Item("my-item-id"),
                    actionText = "Finish"
                )
            }
        }
    )
)
```

### Programmatic Control (AzTutorialController)

You can programmatically initiate, end, and check the completion status of tutorials using the `AzTutorialController`, accessible via a `CompositionLocal` anywhere within the `AzNavHost` hierarchy.

```kotlin
import com.hereliesaz.aznavrail.tutorial.LocalAzTutorialController

@Composable
fun MyScreen() {
    val tutorialController = LocalAzTutorialController.current

    // Check if the tutorial was completed
    val hasReadTutorial = tutorialController.isTutorialRead("my-item-id")

    Button(onClick = { tutorialController.startTutorial("my-item-id") }) {
        Text("Replay Tutorial")
    }
}
```
