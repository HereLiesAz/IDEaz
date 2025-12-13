
package com.hereliesaz.ideaz.utils

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A helper class to manage the lifecycle of a Compose view outside of a standard Activity.
 *
 * This is crucial for components like overlays hosted in a Service, which need to provide
 * the necessary LifecycleOwner, ViewModelStoreOwner, and OnBackPressedDispatcherOwner
 * for Jetpack Compose components (like NavHost) to function correctly.
 *
 * The class creates and manages a LifecycleRegistry, a ViewModelStore, and an
 * OnBackPressedDispatcher, and it hooks them into the provided View's hierarchy.
 */
class ComposeLifecycleHelper(
    private val view: View
) : LifecycleOwner, ViewModelStoreOwner, OnBackPressedDispatcherOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val onBackPressedDispatcher = OnBackPressedDispatcher {
        // No default behavior for back presses in this custom context.
        // Can be customized if needed.
    }
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = onBackPressedDispatcher

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry


    init {
        // Connect the owners to the view tree.
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeOnBackPressedDispatcherOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    /**
     * To be called from the hosting component's onCreate.
     */
    fun onCreate() {
        savedStateRegistryController.performRestore(null) // No saved bundle for now
        handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /**
     * To be called from the hosting component's onStart.
     */
    fun onStart() {
        handleLifecycleEvent(Lifecycle.Event.ON_START)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /**
     * To be called from the hosting component's onStop.
     */
    fun onStop() {
        handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    /**
     * To be called from the hosting component's onDestroy.
     */
    fun onDestroy() {
        handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        // Dispose of the composition.
        (view as? androidx.compose.ui.platform.ComposeView)?.disposeComposition()
    }

    private fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
