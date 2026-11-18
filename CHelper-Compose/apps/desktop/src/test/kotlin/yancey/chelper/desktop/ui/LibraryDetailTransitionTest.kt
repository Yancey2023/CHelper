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
import org.junit.Rule
import org.junit.Test
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.feature.library.browse.LibraryDetailPhase
import yancey.chelper.ui.feature.library.browse.LibraryDetailSkeleton
import yancey.chelper.ui.feature.library.browse.libraryDetailPhase
import yancey.chelper.ui.navigation.ClientNavHost
import yancey.chelper.ui.navigation.HomeScreenKey
import yancey.chelper.ui.navigation.PublicLibraryShowScreenKey

class LibraryDetailTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fastDataDoesNotWaitForEntryAndStaysVisibleOnReturn() = checkReveal(true)

    @Test fun slowDataKeepsOnePlaceholderUntilReady() = checkReveal(false)

    private fun checkReveal(fast: Boolean) {
        val ready = mutableStateOf(false)
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
                    Box(Modifier.size(360.dp, 640.dp)) {
                        nav = rememberNavController()
                        ClientNavHost(nav, HomeScreenKey) {
                            composable<HomeScreenKey> { Box(Modifier.fillMaxSize()) }
                            composable<PublicLibraryShowScreenKey> {
                                val phase = libraryDetailPhase(ready.value, false)
                                Box(Modifier.fillMaxSize().testTag("page")) {
                                    if (phase == LibraryDetailPhase.Ready) Text("完整正文")
                                    else LibraryDetailSkeleton()
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { nav.navigate(PublicLibraryShowScreenKey(7)) }
        compose.mainClock.advanceTimeBy(64)
        if (fast) {
            compose.runOnUiThread { ready.value = true }
            compose.mainClock.advanceTimeByFrame()
        }
        if (fast) {
            compose.onNodeWithText("完整正文").assertIsDisplayed()
            compose.onNodeWithContentDescription("正在加载命令库").assertDoesNotExist()
        } else {
            compose.onNodeWithContentDescription("正在加载命令库").assertExists()
            compose.onNodeWithText("完整正文").assertDoesNotExist()
        }
        compose.mainClock.advanceTimeBy(400)
        if (!fast) {
            compose.onNodeWithContentDescription("正在加载命令库").assertIsDisplayed()
            compose.runOnUiThread { ready.value = true }
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("完整正文").assertIsDisplayed()
        compose.onNodeWithContentDescription("正在加载命令库").assertDoesNotExist()
        compose.runOnUiThread { nav.popBackStack() }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("完整正文").assertExists()
        compose.onNodeWithContentDescription("正在加载命令库").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }
}
