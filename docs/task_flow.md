# Cortex IDE: Example Task Flow

This document describes a typical end-to-end task flow for a developer using the Cortex IDE. It illustrates the "Agent-to-Application" (A2A) workflow, which shifts the developer's role from writing line-by-line code to providing high-level, contextual direction to the AI agent.

**Goal:** Add a "Forgot Password" button to an existing login screen.

---

**Step 1: Open the Project**
- The developer launches the Cortex IDE. From the **Project Management Screen**, they select their existing project from the "Recent Projects" list.
- The IDE loads the project, and the **Main IDE Screen** appears, showing the file explorer, an empty code editor, and the visual preview panel.

**Step 2: Navigate to the Relevant Screen**
- The developer uses the **File Explorer** to open the `LoginScreen.kt` file.
- The file's content loads into the **Code Editor**, and the **Visual Previewer** immediately renders the Jetpack Compose UI for the login screen, showing the email field, password field, and the main login button.

**Step 3: Initiate the AI Interaction (The "Tap-to-Prompt")**
- Instead of manually searching for the right place in the code to add a new button, the developer interacts directly with the visual representation of the app.
- They tap on the area in the **Visual Previewer** just below the password field where they want the new button to appear.
- A **Contextual Prompt Overlay** (a floating text box) appears near their finger tap.

**Step 4: Provide High-Level Instructions**
- The developer types a natural language instruction into the prompt overlay:
  > "Add a 'Forgot Password?' text button here. Make it aligned to the right. When clicked, it should navigate to the `ForgotPasswordScreen`."
- The client-side agent packages this prompt along with the context (the file is `LoginScreen.kt`, the tap location was near the password field) and sends it to the backend AI service.

**Step 5: Review the AI Agent's Plan**
- The developer navigates to the **Agent Log & Chat Screen**.
- A new entry appears in the log, showing the plan the AI has generated to fulfill the request:
  ```
  Plan:
  1.  Add a new `TextButton` Composable to `LoginScreen.kt`.
  2.  Apply styling to the button to align it to the end (right).
  3.  Add an `onClick` lambda to the button.
  4.  Inside the `onClick` lambda, add the navigation call to the `ForgotPasswordScreen`.
  ```
- This allows the developer to verify that the agent's approach is correct before any code is modified.

**Step 6: Review and Accept the Code Changes**
- The agent executes its plan. In the **Agent Log**, a code diff appears showing the precise changes made to `LoginScreen.kt`.
- Simultaneously, the **Code Editor** view for `LoginScreen.kt` is automatically updated with the new code.
- Crucially, the **Visual Previewer** refreshes instantly, now showing the new "Forgot Password?" button correctly positioned on the screen.
- The developer visually confirms the change is correct and can even interact with the button in the preview to test its behavior.

**Step 7: Commit the Changes**
- Satisfied with the AI's work, the developer switches to the **Git Version Control Screen**.
- They see `LoginScreen.kt` listed as a modified file.
- They type a commit message, such as "feat: Add forgot password button to login screen," and tap the "Commit" button.
- Finally, they tap "Push" to send their changes to the remote repository.

---

This workflow demonstrates how Cortex IDE streamlines development by allowing the user to operate at a higher level of abstraction, focusing on *what* they want to achieve rather than the tedious details of *how* to implement it.
