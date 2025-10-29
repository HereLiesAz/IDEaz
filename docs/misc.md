# Cortex IDE: Miscellaneous

This document is a collection of miscellaneous notes, ideas, and information related to the Cortex IDE project that do not fit into the other, more specific documentation files.

## Future Feature Ideas
- **Plugin Architecture:** Explore the possibility of a plugin system that would allow third-party developers to extend the functionality of the IDE.
- **Enhanced Collaboration:** Real-time collaborative coding sessions, similar to VS Code Live Share.
- **Deeper Git Integration:** Visual Git history and branch management graphs.
- **Customizable Themes:** Allow users to create and share their own themes for the code editor and the overall IDE.

## Open Questions
- What is the most effective way to handle dependency resolution for Gradle projects on-device without consuming excessive resources?
- How can we best optimize the RAG pipeline for a balance of speed and contextual accuracy on a mobile device's network connection?

## Onboarding Notes for New Contributors
- A good place to start is by looking at the `UI_UX.md` to understand the vision for the application.
- Familiarize yourself with the core technologies: Kotlin, Jetpack Compose, and FastAPI.
- Run the existing test suite to ensure your development environment is set up correctly.
