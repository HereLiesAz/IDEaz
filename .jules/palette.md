# PALETTE'S JOURNAL - CRITICAL LEARNINGS ONLY

This journal records critical UX/accessibility learnings discovered during development.

## Format
`## YYYY-MM-DD - [Title]`
`**Learning:** [UX/a11y insight]`
`**Action:** [How to apply next time]`

## 2025-12-20 - Toggleable Rows for Accessibility
**Learning:** Standard Composable Switches and Checkboxes have small touch targets and separate semantics from their labels.
**Action:** Always wrap the Switch/Checkbox and its label in a `Row` with `Modifier.toggleable(role = Role.Switch/Checkbox)` and set `onCheckedChange = null` on the child control to delegate interaction to the Row.
