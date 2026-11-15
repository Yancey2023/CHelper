package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import yancey.chelper.ui.app.AppSession
import yancey.chelper.ui.app.PersistedEditor
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.navigation.ClientNavHost
import yancey.chelper.ui.navigation.CompletionScreenKey
import yancey.chelper.ui.navigation.HomeScreenKey
import yancey.chelper.ui.navigation.SettingsScreenKey

class EditorNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aFreshEditorAppearsInTheFirstCompositionWithoutFlushingItself() {
        val session = AppSession()
        val owner = Any()
        var editorComposed = false
        var firstCompositionReady: Boolean? = null
        var flushed = false
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                PersistedEditor(session, owner) {
                    SideEffect {
                        editorComposed = true
                        session.register(owner) { flushed = true }
                    }
                    Box(Modifier.fillMaxSize().testTag("editor"))
                }
                SideEffect {
                    if (firstCompositionReady == null) firstCompositionReady = editorComposed
                }
            }
        }
        compose.onNodeWithTag("editor").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(true, firstCompositionReady)
            assertFalse(flushed)
        }
    }

    @Test fun settingsKeepsItsSlideWhileAwaitingPreviousWrites() = checkEntry(SettingsScreenKey)

    @Test
    fun applicationModeKeepsItsSlideWhileAwaitingPreviousWrites() = checkEntry(CompletionScreenKey)

    private fun checkEntry(destination: Any) {
        val session = AppSession()
        val editor = Any()
        val saved = CompletableDeferred<Unit>()
        session.register(editor) { saved.await() }
        lateinit var nav: NavHostController
        compose.setContent {
            val owner = remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    Box(Modifier.size(360.dp, 240.dp)) {
                        nav = rememberNavController()
                        ClientNavHost(nav, HomeScreenKey) {
                            composable<HomeScreenKey> {
                                Box(Modifier.fillMaxSize().testTag("home"))
                            }
                            composable<SettingsScreenKey> { Editor(session, editor) }
                            composable<CompletionScreenKey> { Editor(session, editor) }
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { nav.navigate(destination) }
        compose.mainClock.advanceTimeBy(64)
        val waiting = compose.onNodeWithTag("gate").getUnclippedBoundsInRoot()
        assertEquals(360f, (waiting.right - waiting.left).value, 0.5f)
        assertEquals(240f, (waiting.bottom - waiting.top).value, 0.5f)
        compose.onNodeWithTag("editor").assertDoesNotExist()
        compose.onNodeWithText("正在恢复编辑内容…").assertDoesNotExist()
        compose.runOnUiThread { saved.complete(Unit) }
        compose.mainClock.advanceTimeByFrame()
        val entering = compose.onNodeWithTag("editor").getUnclippedBoundsInRoot()
        assertTrue(
            entering.left.value > 1f,
            "The restored editor must still be sliding in: $entering",
        )
        assertTrue(entering.left.value < 360f)
        val home = compose.onNodeWithTag("home").getUnclippedBoundsInRoot()
        assertEquals(0f, home.left.value, 1f)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("editor").assertIsDisplayed()
        val settled = compose.onNodeWithTag("editor").getUnclippedBoundsInRoot()
        assertEquals(0f, settled.left.value, 0.5f)
        compose.mainClock.autoAdvance = true
    }

    @Composable
    private fun Editor(session: AppSession, owner: Any) {
        Box(Modifier.testTag("gate")) {
            PersistedEditor(session, owner) { Box(Modifier.fillMaxSize().testTag("editor")) }
        }
    }
}
