# Bolt's Journal

## 2024-05-23 - StateFlow String Concatenation Bottleneck
**Learning:** Using `MutableStateFlow<String>` and appending to it with `+=` (`value += msg`) is O(N²) complexity because strings are immutable. For logs, this is catastrophic as the log grows.
**Action:** Use `MutableStateFlow<List<String>>` for logs. Append new lines to the list. This avoids copying the entire huge string buffer on every single character update.
