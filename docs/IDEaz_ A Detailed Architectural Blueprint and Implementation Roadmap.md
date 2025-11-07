# **IDEaz: A Detailed Architectural Blueprint and Implementation Roadmap**

... (Sections 1.1, 1.2, 1.3, 2, 3 remain largely the same, but with subtle language changes as reflected in `blueprint.md`. The most significant changes are in Sections 3.2, 4.3, and 5) ...

---

## **Section 3: The Visual Interaction Overlay: UI Inspection and Source Mapping**

...

### **3.2 The Invisible Overlay and Element Selection**

The AccessibilityService API includes the ability to draw system-level overlays that appear on top of all other applications.2 When the user activates "Inspection Mode" in the IDEaz, the service will perform the following actions:

1.  **Overlay Drawing:** It will create and display a full-screen, transparent View as a system overlay.
2.  **Event Interception:** This overlay will be configured to intercept touch events. When the user taps the screen, the service will capture the event and its coordinates (X, Y).
3.  **Node Identification:** Using the captured coordinates, the service will query the accessibility framework to find the AccessibilityNodeInfo object corresponding to the UI element at that location in the target application's window.
4.  **Property Retrieval:** Once the target AccessibilityNodeInfo is identified, the service will retrieve its properties, including its resource ID name (e.g., "com.example.myapp:id/login\_button").
5.  **IPC Communication:** The service signals the IDEaz Host App via `LocalBroadcastManager`, passing along the crucial resource ID.
6.  **Contextual UI Trigger:** This is a key step. The Host App receives the resource ID, performs its source-map lookup, and then **commands the UIInspectionService via a new AIDL interface** to render a floating UI element. This UI, managed by the service, consists of a prompt for user input and a log box for AI chat feedback, positioned near the selected element.

...

---

## **Section 4: AI-Powered Code Generation via the Jules API**

...

### **4.3 The Jules API Interaction Workflow**

The process of modifying code via the AI is an automated, multi-step workflow orchestrated by the Host Application, with two distinct contexts:

**Workflow 1: Contextual (Element-Specific) Task**

1.  **Step 1: Commit Current State:** The IDE automatically creates a new commit of the current state to establish a clean baseline.
2.  **Step 2: Create Session (POST /v1alpha/sessions):** The Host App initiates the process by sending an HTTP POST request, using the rich prompt gathered from the **overlay UI**.
3.  **Step 3: Stream Logs to Overlay:** The Host App polls the `/activities` endpoint. All AI-generated messages and progress updates are **streamed via AIDL to the UIInspectionService**, which displays them in the floating log box. If the AI requires user input, the overlay's prompt box is re-enabled.
4.  **Step 4: Receive and Process the Patch:** The AI's final output (a `gitPatch`) is received by the Host App.
5.  **Step 5: Apply the Patch:** The Host App uses JGit to apply this patch to the local on-device repository.
6.  **Step 6: Trigger Rebuild:** The Host App sends an IPC message to the On-Device Build Service. All **build and compile logs** for this task are streamed *only* to the Host App's main build console (the bottom sheet).
7.  **Step 7: Task Finished:** Upon build success or failure, the Host App commands the `UIInspectionService` to close the floating overlay UI.

**Workflow 2: Contextless (Global) Task**

1.  **Step 1:** User types a general prompt (e.g., "refactor my viewmodel") into the **global `ContextlessChatInput`** at the bottom of the main UI.
2.  **Step 2:** The Host App creates a Jules session.
3.  **Step 3:** All AI activity logs are streamed *only* to the main build console (the bottom sheet).
4.  **Step 4-7:** The patch, apply, and rebuild steps are identical to the contextual workflow, with all logs (AI and build) appearing in the global console.

...

---

## **Section 5: User Interface and Experience (UI/UX) Design for a Mobile IDE**

...

### **5.2 The AI Interaction Flow**

The UI for the visual, AI-driven modification loop is seamless and stateful, managed by the `UIInspectionService`.

* **Activation:** A toggle button activates "Inspection Mode." The `UIInspectionService`'s transparent touch-interceptor overlay becomes active.
* **The Contextual Prompt/Log:** Upon selecting a UI element, the Host App commands the service to render a floating UI. This UI is a single "chat" window:
    * It first appears with a **prompt input box**, allowing the user to type their instruction.
    * Once the prompt is submitted, the input box hides, and the window becomes a **log box**, displaying the AI's progress for that specific task.
    * If the AI needs clarification, the log box displays the question, and the **prompt input box reappears** below it, allowing the user to continue the conversation.
    * This entire UI element is "owned" by the `UIInspectionService` and drawn on the `WindowManager`, outside the Host App's window.

### **5.3 The Build & Debug Console**

A swipe-up bottom card in the Host App provides access to global logs.

* **Build Log Tab:** This tab displays the real-time, raw output from the On-Device Build Service. All build/compile logs from *all* workflows appear here.
* **Global AI Chat Tab:** This tab displays the AI chat history for the **global, contextless chat input**. This separates general AI tasks from the specific, element-bound tasks.