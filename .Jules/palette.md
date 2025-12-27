## 2024-05-24 - [Visual Hierarchy in Tab Navigation]
**Learning:** Pure text tabs in `PrimaryTabRow` lack visual weight and require more cognitive load to scan. Adding standard Material icons significantly improves scanability and delight.
**Action:** Always include icons with tabs when screen space permits, using `contentDescription = null` when text labels are present to avoid screen reader redundancy.
