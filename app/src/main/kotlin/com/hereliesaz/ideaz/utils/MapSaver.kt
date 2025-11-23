package com.hereliesaz.ideaz.utils

import androidx.compose.runtime.toMutableStateMap
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.saveable.Saver

fun <K, V> mapSaver(): Saver<SnapshotStateMap<K, V>, Any> = Saver(
    save = { it.toList() },
    restore = {
        @Suppress("UNCHECKED_CAST")
        (it as? List<Pair<K, V>>)?.toMutableStateMap()
    }
)
