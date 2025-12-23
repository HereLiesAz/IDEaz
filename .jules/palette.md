# PALETTE'S JOURNAL - CRITICAL LEARNINGS ONLY

This journal records critical UX/accessibility learnings discovered during development.

## Format
`## YYYY-MM-DD - [Title]`
`**Learning:** [UX/a11y insight]`
`**Action:** [How to apply next time]`

## 2025-12-20 - Toggleable Rows for Accessibility
**Learning:** Standard Composable Switches and Checkboxes have small touch targets and separate semantics from their labels.
**Action:** Always wrap the Switch/Checkbox and its label in a `Row` with `Modifier.toggleable(role = Role.Switch/Checkbox)` and set `onCheckedChange = null` on the child control to delegate interaction to the Row.

## 2025-12-21 - Empty States for Async Data
**Learning:** Empty lists without feedback confuse users ("Is it loading? Is it broken?").
**Action:** Always provide a descriptive Empty State for lists, explaining *why* it's empty (e.g., "No repos found" vs "Not authenticated") and guiding the user's next step.

## 2024-05-22 - Action Buttons in Empty States
**Learning:** Text-only empty states (e.g. "No token found") leave users stranded.
**Action:** Always provide a direct action button (e.g. "Go to Settings") in empty states to unblock the user.
