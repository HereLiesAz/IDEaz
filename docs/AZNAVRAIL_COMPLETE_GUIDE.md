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


**React (React Native / react-native-web) Equivalent:**
While Android uses `AzHostActivityLayout` and a DSL to manage positioning and Safe Zones automatically, React projects explicitly construct their layout and pass properties and arrays of objects. The React version enforces the same visual rules via standard flex layouts.

```tsx
import { AzNavRail, AzNavItem, AzNavRailSettings } from '@HereLiesAz/aznavrail-react';
import { View } from 'react-native';

const settings: AzNavRailSettings = {
    dockingSide: AzDockingSide.LEFT,
    packRailButtons: false,
    usePhysicalDocking: false,
    defaultShape: AzButtonShape.RECTANGLE,
    activeColor: '#6200EE',
    translucentBackground: 'rgba(0,0,0,0.5)',
    enableRailDragging: true,
    isLoading: false,
    helpList: { "home": "Home screen" },
    infoScreen: false,
    onDismissInfoScreen: () => {},
};

const items: AzNavItem[] = [
    // Define items array here
];

export default function AppLayout() {
    return (
        <View style={{ flex: 1, flexDirection: 'row' }}>
            <AzNavRail
                appName="My App"
                items={items}
                expanded={false}
                settings={settings}
                onToggleExpand={() => {}}
            />
            {/* Background and Onscreen Content */}
        </View>
    );
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
    translucentBackground = Color.Black.copy(alpha = 0.5f) // Set the background color for menus/overlays!
)
```

**React Implementation:**
```tsx
const settings: AzNavRailSettings = {
    defaultShape: AzButtonShape.RECTANGLE,
    activeColor: '#6200EE',
    translucentBackground: 'rgba(0,0,0,0.5)',
};
// Pass this object to the settings prop on AzNavRail
```



### C. Advanced Features (`azAdvanced`)
Enables complex behaviors like drag-and-drop and help overlays.

```kotlin
azAdvanced(
    isLoading = isLoading,               // Boolean: Show global loading overlay
    enableRailDragging = true,           // Boolean: Enable FAB Mode (detach rail)
    helpEnabled = showHelp,              // Boolean: Show Help Overlay
    helpList = mapOf("home" to "Home screen"), // Map<String, Any>: Extra help texts
    onDismissHelp = { showHelp = false }
)
```

**React Implementation:**
```tsx
const settings: AzNavRailSettings = {
    isLoading: isLoading,
    enableRailDragging: true,
    infoScreen: showHelp,
    helpList: { "home": "Home screen" },
    onDismissInfoScreen: () => setShowHelp(false),
};
// Pass this object to the settings prop on AzNavRail
```


> **Note on Help Overlay:**
> The `HelpOverlay` displays a short, truncated entry for each item to conserve space. Tapping a help card expands it to reveal the full description and any extra text provided in `helpList`. Furthermore, `helpList` can be supplied dynamically to `AzNestedRail` components for distinct, localized help data.

---

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

// Rail item with Custom Composable Content Size
azRailItem(id = "wide-composable", text = "Wide", content = AzComposableContent {
    Box(Modifier.width(120.dp).background(Color.Blue))
}) // Will not clip to rail width!

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

**React Implementation:**
```tsx
// Tutorials are mapped through helpList in React
const settings: AzNavRailSettings = {
    infoScreen: true,
    helpList: {
        "item-1": "Help text for item 1"
    }
};
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
    forceHiddenMenuOpen = false, // Programmatic control for hidden context menu
    onHiddenMenuDismiss = { /* Menu was closed! */ },
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
    entry(entryName = "username", hint = "Username", initialValue = "AzRailFan") // Pre-filled!
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


## 9. Tutorial Framework

The tutorial framework scripts interactive, multi-scene walkthroughs over a dimmed overlay. Each tutorial has one or more scenes; each scene has one or more cards. Cards can spotlight rail items, require user actions before advancing, show inline media, and present interactive checklists. Scenes can branch based on runtime variables or based on which highlighted item the user taps.

### 9.1 Concepts

**Scene** — a "scripted screen state." You provide a `content` composable/component that renders behind the overlay. The overlay dims everything outside the spotlight and shows the current card.

**Card** — a single instructional step. It has a title, body text, an optional spotlight (`AzHighlight`), and an advance condition (`AzAdvanceCondition`).

**Advance conditions:**
- `Button` (default) — a "Next" button is shown.
- `TapTarget` — user must tap the spotlighted item.
- `TapAnywhere` — user taps anywhere to advance.
- `Event(name)` — advances when the app calls `controller.fireEvent(name)`.

**Highlights:**
- `AzHighlight.None` — no spotlight.
- `AzHighlight.FullScreen` — full-screen highlight.
- `AzHighlight.Item(id)` — spotlights a named rail item (uses measured bounds).
- `AzHighlight.Area(rect)` — spotlights an arbitrary rect.

Card auto-positioning: defaults to bottom. Flips to top when highlight center Y > 60% of screen height. `TapTarget` degrades to `TapAnywhere` if the highlight is not `AzHighlight.Item`.

### 9.2 Help/Info Overlay Integration

- **Collapsed card:** Shows a "Tutorial available" hint when a tutorial exists for that item.
- **Expanded card:** Shows a "Start Tutorial" button. Tapping it calls `tutorialController.startTutorial(id)` and dismisses the help overlay.
- The old behavior (any tap immediately starts the tutorial) is removed.

### 9.3 Android — Full Example

```kotlin
import com.hereliesaz.aznavrail.tutorial.*

// 1. Define the tutorial
val myTutorial = azTutorial {
    onComplete { /* fired when last scene finishes */ }
    onSkip { /* fired when Skip Tutorial tapped */ }

    // Invisible redirect node: routes based on a variable
    scene(id = "gate", content = { /* empty backdrop */ }) {
        branch(varName = "userLevel", mapOf(
            "advanced" to "scene-advanced",
            "basic"    to "scene-basic"
        ))
    }

    scene(id = "scene-advanced", content = { AdvancedScreen() }) {

        // TapTarget with per-item branching
        card(
            title = "Pick a path",
            text = "Tap the item you want to learn about.",
            highlight = AzHighlight.Item("nav-menu"),
            advanceCondition = AzAdvanceCondition.TapTarget,
            branches = mapOf(
                "settings-btn" to "scene-settings",
                "profile-btn"  to "scene-profile"
            )
        )

        // Event-driven advance
        card(
            title = "Open the menu",
            text = "Swipe right or tap the rail header.",
            highlight = AzHighlight.Item("rail-header"),
            advanceCondition = AzAdvanceCondition.Event("menu_opened")
        )

        // Checklist card — Next disabled until all items checked
        card(
            title = "Before you continue",
            text = "Confirm the following:",
            checklistItems = listOf("I read the docs", "I set up my account")
        )

        // Media card — rendered between title and text
        card(
            title = "The Rail",
            text = "Sits on the left or right edge.",
            mediaContent = { Image(painterResource(R.drawable.rail), contentDescription = null) }
        )
    }

    scene(id = "scene-basic", content = { BasicScreen() }) {
        card(
            title = "Basic path",
            text = "Here is the simplified view.",
            highlight = AzHighlight.FullScreen,
            advanceCondition = AzAdvanceCondition.TapAnywhere
        )
    }
}

// 2. Register and wire
azAdvanced(
    helpEnabled = true,
    onItemGloballyPositioned = { id, rect -> boundsMap[id] = rect },
    tutorials = mapOf("tut-1" to myTutorial)
)

// 3. Mount the controller and overlay
val controller = rememberAzTutorialController()
CompositionLocalProvider(LocalAzTutorialController provides controller) {
    // ... your content ...
    if (controller.activeTutorialId.value == "tut-1") {
        AzTutorialOverlay(
            tutorialId = "tut-1",
            tutorial = myTutorial,
            onDismiss = { controller.endTutorial() },
            itemBoundsCache = boundsMap
        )
    }
}

// 4. Start with variables (drives the gate scene branch)
controller.startTutorial("tut-1", variables = mapOf("userLevel" to "advanced"))

// 5. Fire an event from your app logic
controller.fireEvent("menu_opened")

// 6. Check read status
val hasRead = controller.isTutorialRead("tut-1")
```

Persistence: `SharedPreferences` file `az_tutorial_prefs`, key `az_navrail_read_tutorials`. State is read on `rememberAzTutorialController()` and written on each `markTutorialRead()`.

### 9.4 React Native — Full Example

```tsx
import {
    AzTutorialProvider,
    useAzTutorialController,
    AzTutorial,
} from '@HereLiesAz/aznavrail-react';

// 1. Define the tutorial
const myTutorial: AzTutorial = {
    onComplete: () => console.log('done'),
    onSkip: () => console.log('skipped'),
    scenes: [
        {
            id: 'gate',
            content: () => null,
            cards: [],
            branchVar: 'userLevel',
            branches: { advanced: 'scene-advanced', basic: 'scene-basic' },
        },
        {
            id: 'scene-advanced',
            content: () => <AdvancedScreen />,
            cards: [
                {
                    title: 'Pick a path',
                    text: 'Tap the item you want to learn about.',
                    highlight: { type: 'Item', id: 'nav-menu' },
                    advanceCondition: { type: 'TapTarget' },
                    branches: {
                        'settings-btn': 'scene-settings',
                        'profile-btn': 'scene-profile',
                    },
                },
                {
                    title: 'Open the menu',
                    text: 'Swipe right or tap the rail header.',
                    highlight: { type: 'Item', id: 'rail-header' },
                    advanceCondition: { type: 'Event', name: 'menu_opened' },
                },
                {
                    title: 'Before you continue',
                    text: 'Confirm the following:',
                    checklistItems: ['I read the docs', 'I set up my account'],
                },
                {
                    title: 'The Rail',
                    text: 'Sits on the left or right edge.',
                    mediaContent: () => <Image source={require('./rail.png')} style={{ height: 120 }} />,
                },
            ],
        },
    ],
};

// 2. Wrap your app root
function Root() {
    return (
        <AzTutorialProvider tutorials={{ 'tut-1': myTutorial }}>
            <App />
        </AzTutorialProvider>
    );
}

// 3. Start and fire events from anywhere in the tree
function TutorialLauncher() {
    const controller = useAzTutorialController();
    return (
        <Button
            title="Start Tutorial"
            onPress={() => controller.startTutorial('tut-1', { userLevel: 'advanced' })}
        />
    );
}

// Fire an event from app logic
controller.fireEvent('menu_opened');
```

Persistence: `@react-native-async-storage/async-storage` (optional peer dependency). Falls back to in-memory if not installed. Key: `az_navrail_read_tutorials`.

### 9.5 Web — Full Example

The web library is a TypeScript port of Android. New files: `web/AzTutorialController.tsx`, `web/AzTutorialOverlay.tsx`, `web/HelpOverlay.tsx`.

```tsx
import {
    AzWebTutorialProvider,
    useAzWebTutorialController,
    AzTutorial,
} from '@HereLiesAz/aznavrail-web';

// Tutorial definition is identical in shape to the React Native example above.

function Root() {
    return (
        <AzWebTutorialProvider tutorials={{ 'tut-1': myTutorial }}>
            <App />
        </AzWebTutorialProvider>
    );
}

function TutorialLauncher() {
    const ctrl = useAzWebTutorialController();
    return (
        <button onClick={() => ctrl.startTutorial('tut-1', { userLevel: 'advanced' })}>
            Start Tutorial
        </button>
    );
}
```

Spotlight implementation: `box-shadow: 0 0 0 9999px rgba(0,0,0,0.7)` applied to the highlighted element — the CSS equivalent of Android's `BlendMode.Clear` punch-out.

Persistence: `localStorage`. Key: `az_navrail_read_tutorials`.

### 9.6 Variable Branching

Pass a `variables` map to `startTutorial`. Scenes with `branchVar` set evaluate their `branches` map on entry and redirect to the matching scene ID. A scene used only for branching can have an empty `cards` list and a transparent `content`.

```kotlin
// Android
controller.startTutorial("tut-1", variables = mapOf("userLevel" to "advanced"))
```

```typescript
// React Native / Web
controller.startTutorial('tut-1', { userLevel: 'advanced' });
```

Circular branch detection: if a branch chain loops back to an already-visited scene, a warning is logged and the tutorial advances to the next scene by index. If no next scene exists, the tutorial ends.

### 9.7 Event-Driven Advance

Use `AzAdvanceCondition.Event("event_name")` (Kotlin) or `{ type: 'Event', name: 'event_name' }` (TS) on a card. When your app logic fires that event, the overlay automatically advances.

```kotlin
// Fire from anywhere — e.g., in a real menu open handler
controller.fireEvent("menu_opened")
```

The overlay observes `pendingEvent` and calls `consumeEvent()` internally on match. You do not need to call `consumeEvent()` yourself.

### 9.8 Checklist Cards

Provide `checklistItems` on a card. The Next button is disabled until every item is checked. Compatible with any advance condition (the checklist gates the advance even for `TapAnywhere`).

### 9.9 Media Cards

Provide `mediaContent` (a composable/component) on a card. It is rendered between the title and the body text, clipped to a max height of 120dp/120px with 8dp/8px corner rounding. Useful for images, animated GIFs, or short video previews.
