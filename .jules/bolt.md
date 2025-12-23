# Bolt's Journal

## 2024-05-23 - StateFlow String Concatenation Bottleneck
**Learning:** Using `MutableStateFlow<String>` and appending to it with `+=` (`value += msg`) is O(N²) complexity because strings are immutable. For logs, this is catastrophic as the log grows.
**Action:** Use `MutableStateFlow<List<String>>` for logs. Append new lines to the list. This avoids copying the entire huge string buffer on every single character update.

## 2024-05-24 - Reuse OkHttpClient in GitHubApiClient
**Learning:** `OkHttpClient` creation is expensive (thread pools, connection pools). Re-creating it for every API call defeats the purpose of HTTP/2 connection pooling and increases memory churn.
**Action:** Cache the `OkHttpClient` and `Retrofit` service instance in `GitHubApiClient`. Reuse it when the authentication token hasn't changed.

## 2024-05-25 - Redundant Collection Sorting
**Learning:** Chaining `sortedBy` calls (e.g., `list.sortedBy { ... }.sortedBy { ... }`) performs multiple O(N log N) sorts and creates intermediate collections. The second sort often invalidates the first if the key is unique, leading to incorrect behavior and wasted cycles.
**Action:** Use `sortedWith(compareBy(..., ...))` for multi-criteria sorting. It performs a single pass, is more efficient, and guarantees the desired grouping order.
