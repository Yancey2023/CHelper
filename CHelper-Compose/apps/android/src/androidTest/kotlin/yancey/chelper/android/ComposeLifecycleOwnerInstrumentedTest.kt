package yancey.chelper.android

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import yancey.chelper.android.window.ComposeLifecycleOwner

@RunWith(AndroidJUnit4::class)
class ComposeLifecycleOwnerInstrumentedTest {
    @Test
    fun hiddenWindowsStopCollectorsButRetainViewModels() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val owner = ComposeLifecycleOwner()
            val events = mutableListOf<Lifecycle.Event>()
            var cleared = 0
            val model =
                object : ViewModel() {
                    override fun onCleared() {
                        cleared++
                    }
                }
            owner.lifecycle.addObserver(LifecycleEventObserver { _, event -> events.add(event) })
            owner.viewModelStore.put("editor", model)
            owner.onCreate()
            owner.hide()
            assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
            owner.show()
            owner.show()
            assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
            owner.hide()
            owner.hide()
            assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
            assertSame(model, owner.viewModelStore["editor"])
            assertEquals(0, cleared)
            owner.show()
            owner.onDestroy()
            owner.onDestroy()
            owner.show()
            assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
            assertEquals(1, cleared)
            assertEquals(
                listOf(
                    Lifecycle.Event.ON_CREATE,
                    Lifecycle.Event.ON_START,
                    Lifecycle.Event.ON_RESUME,
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP,
                    Lifecycle.Event.ON_START,
                    Lifecycle.Event.ON_RESUME,
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP,
                    Lifecycle.Event.ON_DESTROY,
                ),
                events,
            )
        }
    }

    @Test
    fun destructionBeforeFirstShowIsSafe() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val owner = ComposeLifecycleOwner()
            owner.onDestroy()
            assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        }
    }

    @Test
    fun onlyVisibleWindowsDispatchBackAndDestroyedOwnersStayInactive() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val owner = ComposeLifecycleOwner()
            var count = 0
            owner.navigationEventDispatcher.addHandler(
                object :
                    NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, true) {
                    override fun onBackCompleted() {
                        count++
                    }
                }
            )
            owner.onCreate()
            owner.back()
            assertEquals(0, count)
            owner.show()
            owner.back()
            assertEquals(1, count)
            owner.hide()
            owner.back()
            assertEquals(1, count)
            owner.show()
            owner.back()
            assertEquals(2, count)
            owner.onDestroy()
            owner.show()
            owner.back()
            assertEquals(2, count)
            assertFalse(owner.navigationEventDispatcher.isEnabled)
        }
    }
}
