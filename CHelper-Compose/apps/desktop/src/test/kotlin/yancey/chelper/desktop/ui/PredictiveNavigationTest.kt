package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yancey.chelper.ui.navigation.ClientNavHost
import yancey.chelper.ui.navigation.HomeScreenKey
import yancey.chelper.ui.navigation.SettingsScreenKey

/** Real NavHost seeking: verify geometry and the back stack, not just callback delivery. */
class PredictiveNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var input: DirectNavigationEventInput
    private lateinit var nav: NavHostController

    private fun openDetail() {
        compose.setContent {
            val dispatcher = rememberNavigationEventDispatcherOwner(parent = null)
            val source = remember { DirectNavigationEventInput() }
            val owner = remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            SideEffect { input = source }
            DisposableEffect(dispatcher, owner) {
                dispatcher.navigationEventDispatcher.addInput(source)
                onDispose {
                    dispatcher.navigationEventDispatcher.removeInput(source)
                    owner.viewModelStore.clear()
                }
            }
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides dispatcher,
                LocalViewModelStoreOwner provides owner,
            ) {
                nav = rememberNavController()
                ClientNavHost(nav, HomeScreenKey, Modifier.size(360.dp, 240.dp)) {
                    composable<HomeScreenKey> { Page("home") }
                    composable<SettingsScreenKey> { Page("detail") }
                }
            }
        }
        compose.runOnIdle { nav.navigate(SettingsScreenKey) }
        compose.waitForIdle()
    }

    @Composable
    private fun Page(tag: String) {
        Box(Modifier.fillMaxSize().testTag(tag)) {
            Box(Modifier.align(Alignment.Center).size(20.dp).testTag("$tag-marker"))
        }
    }

    private fun event(progress: Float, edge: Int) =
        NavigationEvent(progress = progress, swipeEdge = edge, touchX = 0f, touchY = 100f)

    @Test
    fun bothEdgesSeekTheOriginalReverseSlideAndCancellationRestoresThePage() {
        openDetail()
        val original = compose.onNodeWithTag("detail-marker").getUnclippedBoundsInRoot()
        for (edge in listOf(NavigationEvent.EDGE_LEFT, NavigationEvent.EDGE_RIGHT)) {
            compose.runOnIdle {
                input.backStarted(event(0f, edge))
                input.backProgressed(event(0.15f, edge))
            }
            compose.waitForIdle()
            val current = compose.onNodeWithTag("detail-marker").getUnclippedBoundsInRoot()
            val previous = compose.onNodeWithTag("home-marker").getUnclippedBoundsInRoot()
            assertTrue("Return must reverse the forward page slide", current.left > original.left)
            assertEquals(360f, (current.left - previous.left).value, 1f)
            assertEquals(
                (original.right - original.left).value,
                (current.right - current.left).value,
                0.5f,
            )
            assertEquals(
                (original.bottom - original.top).value,
                (current.bottom - current.top).value,
                0.5f,
            )
            assertEquals(original.top.value, current.top.value, 0.5f)
            compose.runOnIdle {
                assertTrue(nav.currentBackStackEntry!!.destination.hasRoute<SettingsScreenKey>())
            }
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread { input.backCancelled() }
            compose.mainClock.advanceTimeByFrame()
            val returning = compose.onNodeWithTag("detail-marker").getUnclippedBoundsInRoot()
            assertTrue(
                "Cancel must retrace the dragged path",
                returning.left.value in (original.left.value - 1f)..(current.left.value + 1f),
            )
            assertEquals(original.top.value, returning.top.value, 0.5f)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            val restored = compose.onNodeWithTag("detail-marker").getUnclippedBoundsInRoot()
            assertEquals(original.left.value, restored.left.value, 0.5f)
            compose.runOnIdle {
                assertTrue(nav.currentBackStackEntry!!.destination.hasRoute<SettingsScreenKey>())
            }
        }
        compose.runOnIdle { input.backCompleted() }
        compose.onNodeWithTag("home").assertExists()
        compose.onNodeWithTag("detail").assertDoesNotExist()
    }

    @Test
    fun committingContinuesFromTheDraggedPositionAndPopsOnlyOnce() {
        openDetail()
        compose.runOnIdle {
            input.backStarted(event(0f, NavigationEvent.EDGE_LEFT))
            input.backProgressed(event(0.05f, NavigationEvent.EDGE_LEFT))
        }
        compose.waitForIdle()
        val dragged = compose.onNodeWithTag("detail-marker").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { input.backCompleted() }
        compose.mainClock.advanceTimeByFrame()
        val settling = compose.onNodeWithTag("detail-marker").getUnclippedBoundsInRoot()
        assertTrue(
            "Commit must not restart the slide",
            settling.left.value >= dragged.left.value - 1f,
        )
        assertEquals(dragged.top.value, settling.top.value, 0.5f)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(nav.currentBackStackEntry!!.destination.hasRoute<HomeScreenKey>())
            assertNull(nav.previousBackStackEntry)
        }
        compose.onNodeWithTag("detail").assertDoesNotExist()
    }
}
