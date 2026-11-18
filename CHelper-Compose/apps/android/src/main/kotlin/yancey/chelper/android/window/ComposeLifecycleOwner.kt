/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.android.window

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * copy form:
 * https://github.com/Petterpx/FloatingX/blob/main/floatingx_compose/src/main/java/com/petterp/floatingx/compose/FxComposeLifecycleOwner.kt
 */
class ComposeLifecycleOwner :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, NavigationEventDispatcherOwner {

    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val navigationEventDispatcher = NavigationEventDispatcher().apply { isEnabled = false }
    private val backInput = DirectNavigationEventInput().also(navigationEventDispatcher::addInput)

    fun back() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            backInput.backCompleted()
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    fun onCreate() {
        if (lifecycleRegistry.currentState != Lifecycle.State.INITIALIZED) return
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        onCreate()
        if (lifecycleRegistry.currentState == Lifecycle.State.CREATED)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        navigationEventDispatcher.isEnabled = true
    }

    fun onResume() {
        onStart()
        if (lifecycleRegistry.currentState == Lifecycle.State.STARTED)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun onStop() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        onPause()
        navigationEventDispatcher.isEnabled = false
        if (lifecycleRegistry.currentState == Lifecycle.State.STARTED)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    /** Hide suspends STARTED collectors while retaining composition state and ViewModels. */
    fun hide() = onStop()

    fun show() = onResume()

    fun onDestroy() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        onCreate()
        onStop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        navigationEventDispatcher.dispose()
        store.clear()
    }

    /**
     * Compose uses the Window's decor view to locate the Lifecycle/ViewModel/SavedStateRegistry
     * owners. Therefore, we need to set this class as the "owner" for the decor view.
     */
    fun attachToDecorView(decorView: View?) {
        decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeViewModelStoreOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
            it.setViewTreeNavigationEventDispatcherOwner(this)
        } ?: return
    }

    fun detachFromDecorView(decorView: View?) {
        decorView?.let {
            it.setViewTreeLifecycleOwner(null)
            it.setViewTreeViewModelStoreOwner(null)
            it.setViewTreeSavedStateRegistryOwner(null)
            it.setViewTreeNavigationEventDispatcherOwner(null)
        } ?: return
    }
}
