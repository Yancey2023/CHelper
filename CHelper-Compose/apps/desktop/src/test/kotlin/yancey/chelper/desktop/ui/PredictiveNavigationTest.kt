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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import yancey.chelper.ui.navigation.AboutScreenKey
import yancey.chelper.ui.navigation.ClientNavHost
import yancey.chelper.ui.navigation.HomeScreenKey
import yancey.chelper.ui.navigation.SettingsScreenKey

/** Real NavHost seeking: verify geometry and the back stack, not just callback delivery. */
class PredictiveNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var input: DirectNavigationEventInput
    private lateinit var nav: NavHostController

    private fun openDetail(settle: Boolean = true, nested: Boolean = false) {
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
                    composable<AboutScreenKey> { Page("about") }
                    composable<SettingsScreenKey> { Page("detail") }
                }
            }
        }
        compose.waitForIdle()
        if (nested) {
            compose.runOnIdle { nav.navigate(AboutScreenKey) }
            compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = settle
        compose.runOnIdle { nav.navigate(SettingsScreenKey) }
        if (settle) compose.waitForIdle()
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
    fun cancellingBackDuringEntryKeepsBothPagesTogetherAndRestoresTheDestination() {
        checkInterruptedEntry(commit = false)
    }

    @Test
    fun completingBackDuringEntryKeepsBothPagesTogetherAndPopsOnce() {
        checkInterruptedEntry(commit = true)
    }

    @Test
    fun cancellingBackToAnAlreadyEnteredPageKeepsTheSlidesTogether() {
        checkInterruptedEntry(commit = false, nested = true)
    }

    @Test
    fun completingBackToAnAlreadyEnteredPagePopsOnlyOnce() {
        checkInterruptedEntry(commit = true, nested = true)
    }

    private fun checkInterruptedEntry(commit: Boolean, nested: Boolean = false) {
        openDetail(settle = false, nested = nested)
        val backgroundTag = if (nested) "about" else "home"
        var first = true
        for (elapsed in listOf(64L, 144L, 224L)) {
            for (edge in listOf(NavigationEvent.EDGE_LEFT, NavigationEvent.EDGE_RIGHT)) {
                if (!first) {
                    compose.mainClock.autoAdvance = false
                    compose.runOnIdle { nav.navigate(SettingsScreenKey) }
                }
                first = false
                compose.mainClock.advanceTimeBy(elapsed)
                val entering = detailLeft()
                assertTrue(
                    message = "The gesture must interrupt a moving page",
                    actual = entering in 171f..529f,
                )
                assertPagesAreAdjacent(backgroundTag)
                compose.runOnUiThread { input.backStarted(event(0f, edge)) }
                compose.mainClock.advanceTimeByFrame()
                assertTrue(
                    message = "Starting back must not reset the page",
                    actual = abs(detailLeft() - entering) < 64f,
                )
                assertPagesAreAdjacent(backgroundTag)
                for (progress in listOf(0.02f, 0.05f, 0.1f, 0.15f)) {
                    compose.runOnUiThread { input.backProgressed(event(progress, edge)) }
                    compose.mainClock.advanceTimeByFrame()
                    assertPagesAreAdjacent(backgroundTag)
                }
                // Cover both a quick release and a held gesture after the entry has settled.
                val quickRelease = elapsed == 144L
                if (!quickRelease) compose.mainClock.advanceTimeBy(350)
                val dragged = detailLeft()
                assertTrue(
                    message = "An interrupted return must still slide right",
                    actual = dragged > 170f,
                )
                assertPagesAreAdjacent(backgroundTag)
                compose.runOnUiThread {
                    if (commit) input.backCompleted() else input.backCancelled()
                }
                compose.mainClock.advanceTimeByFrame()
                val settling = detailLeft()
                assertPagesAreAdjacent(backgroundTag)
                if (commit) {
                    assertTrue(
                        message = "Commit must continue from the drag",
                        actual =
                            if (quickRelease) abs(settling - dragged) < 64f
                            else settling >= dragged - 1f,
                    )
                } else {
                    assertTrue(
                        message = "Cancel must retrace the drag",
                        actual = settling in 169f..(dragged + 1f),
                    )
                }
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                compose.runOnIdle {
                    if (commit) {
                        if (nested) {
                            assertTrue(
                                nav.currentBackStackEntry!!.destination.hasRoute<AboutScreenKey>()
                            )
                            assertTrue(
                                nav.previousBackStackEntry!!.destination.hasRoute<HomeScreenKey>()
                            )
                        } else {
                            assertTrue(
                                nav.currentBackStackEntry!!.destination.hasRoute<HomeScreenKey>()
                            )
                            assertNull(nav.previousBackStackEntry)
                        }
                    } else {
                        assertTrue(
                            nav.currentBackStackEntry!!.destination.hasRoute<SettingsScreenKey>()
                        )
                    }
                }
                if (commit) {
                    compose.onNodeWithTag("detail").assertDoesNotExist()
                } else {
                    // Quick cancellation can finish while the interrupted entry is still settling.
                    assertEquals(170f, detailLeft(), if (quickRelease) 3f else 0.5f)
                    compose.runOnIdle { input.backCompleted() }
                    compose.waitForIdle()
                }
            }
        }
    }

    private fun detailLeft() =
        compose.onNodeWithTag("detail-marker").getUnclippedBoundsInRoot().left.value

    private fun assertPagesAreAdjacent(backgroundTag: String) {
        val current = compose.onNodeWithTag("detail-marker").getUnclippedBoundsInRoot()
        val previous = compose.onNodeWithTag("$backgroundTag-marker").getUnclippedBoundsInRoot()
        assertEquals(360f, (current.left - previous.left).value, 1f)
        assertEquals(current.top.value, previous.top.value, 0.5f)
    }

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
            assertTrue(
                message = "Return must reverse the forward page slide",
                actual = current.left > original.left,
            )
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
                message = "Cancel must retrace the dragged path",
                actual =
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
            message = "Commit must not restart the slide",
            actual = settling.left.value >= dragged.left.value - 1f,
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
