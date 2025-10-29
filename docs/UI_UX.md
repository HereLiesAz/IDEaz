# Cortex IDE: UI/UX Design

## Overview
This document outlines the UI/UX design for the Cortex IDE, a mobile-first, AI-native Integrated Development Environment (IDE) for Android. The UI/UX is designed to be intuitive, efficient, and powerful, allowing developers to build, test, and deploy complex applications directly from their Android devices.

## Core Principles
The UI/UX of the Cortex IDE is guided by the following core principles:

- **Visual-First:** The IDE will provide a highly interactive, visual-first development experience, with a real-time preview of the UI code.
- **AI-Driven:** The IDE will be deeply integrated with an AI agent, "Cortex," which will act as a proactive partner throughout the entire software development lifecycle (SDLC).
- **Mobile-First:** The IDE will be designed from the ground up for mobile devices, with a focus on touch-based interaction and a responsive layout.
- **Modern:** The IDE will be built with modern Android technologies, including Jetpack Compose and Material 3, to provide a modern, aesthetically pleasing, and consistent user experience.

## UI Components
The Cortex IDE will be a single-activity application built entirely in Kotlin. It will adhere to modern Android architecture best practices, employing a Model-View-ViewModel (MVVM) pattern and enforcing a unidirectional data flow. The UI will be structured around several key, high-performance Composable components:

- **Code Editor:** A custom-built, high-performance Composable that supports syntax highlighting, AI-powered code completion, inline error and linting display, and intuitive touch-based text selection and manipulation.
- **Visual Previewer:** A dedicated, resizable panel that renders the UI defined in the active Compose file in real-time.
- **Contextual Prompt Overlay:** A floating text input box that appears near a selected UI component or area, allowing the user to provide natural language instructions to the AI agent.
- **Agent Log & Chat Screen:** A secondary screen that provides a detailed log of the agent's actions, plans, and generated code diffs.
- **File Explorer and Project View:** A familiar tree-based navigation panel that displays the project's file and directory structure.
- **Integrated Terminal:** A fully functional terminal emulator Composable that provides shell access for executing Gradle tasks, managing Git, or running other command-line tools.

## Design System
The Cortex IDE will be designed following the Material 3 design system guidelines. This will ensure a modern, aesthetically pleasing interface that is consistent with the Android platform's native look and feel. The IDE will also support key features like dynamic color and dark mode.
