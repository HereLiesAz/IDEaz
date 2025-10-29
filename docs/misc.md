# Cortex IDE: Miscellaneous

This document is a collection of miscellaneous notes, ideas, and information related to the intent-driven Cortex IDE project.

## Future Feature Ideas
-   **Visual Version Control:** Instead of a Git UI, allow users to visually compare two versions of their app side-by-side. A prompt like "Show me what the app looked like yesterday" would have the Cortex Service check out a previous commit, compile it, and run it in a split-screen view.
-   **AI-Powered Analytics:** The user could ask, "Which button on my home screen is clicked the most?" The AI would be responsible for adding the necessary analytics tracking code to the repository and then presenting the results to the user.
-   **A/B Testing:** A user could select a button and say, "Let's test this. Make it blue for 50% of users and green for the other 50%, and tell me which one gets more clicks after a week." The AI would be responsible for implementing the entire A/B testing framework.

## Open Questions
-   How reliable can the visual-to-code mapping be? What are the edge cases (e.g., dynamically generated UI)?
-   What is the user experience if the AI gets stuck in a debugging loop (e.g., repeatedly failing to fix a compile error)? When should we surface a "failed" state to the user?
-   How do we handle a user wanting to "undo" a change that has already been compiled and launched? Is a `git revert` fast enough for a good user experience?
