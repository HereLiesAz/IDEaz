package com.hereliesaz.ideaz.utils

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

class ManualBackPressedDispatcherOwner(override val lifecycle: Lifecycle) : OnBackPressedDispatcherOwner {
    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    init {
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    // Clean up callbacks when the lifecycle is destroyed
                }
            }
        })
    }

    fun addCallback(callback: OnBackPressedCallback) {
        onBackPressedDispatcher.addCallback(callback)
    }

    fun removeCallback(callback: OnBackPressedCallback) {
        callback.remove()
    }

    fun goBack() {
        onBackPressedDispatcher.onBackPressed()
    }
}
