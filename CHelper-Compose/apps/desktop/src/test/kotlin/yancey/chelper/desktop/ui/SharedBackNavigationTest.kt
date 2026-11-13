package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.*
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.AppDialog
import yancey.chelper.ui.designsystem.layout.Header
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.platform.AppBackHandler
import yancey.chelper.ui.platform.ClientInteractionHost

class SharedBackNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val navigation = NavigationTestInput()

    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent { navigation.Content(content) }
    }

    private fun SemanticsNodeInteraction.clickOnMain() {
        compose.runOnUiThread { performClick() }
        compose.waitForIdle()
    }

    @Test
    fun headerBackClosesThePreviewBeforeLeavingTheUploadPage() {
        val preview = mutableStateOf(true)
        var navigations = 0
        setContent {
            ClientInteractionHost(onBack = { navigations++ }) {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    Column {
                        if (preview.value) {
                            AppBackHandler { preview.value = false }
                            Header("预览")
                        } else Header("编辑")
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("返回").clickOnMain()
        compose.onNodeWithText("编辑").assertExists()
        compose.runOnIdle { assertEquals(0, navigations) }
        compose.onNodeWithContentDescription("返回").clickOnMain()
        compose.runOnIdle { assertEquals(1, navigations) }
    }

    @Test
    fun backDismissesTheDialogBeforeThePageGuard() {
        val dialog = mutableStateOf(true)
        var guarded = 0
        var navigations = 0
        setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                ClientInteractionHost(onBack = { navigations++ }) {
                    AppBackHandler { guarded++ }
                    Header("编辑")
                    if (dialog.value) {
                        AppDialog(onDismissRequest = { dialog.value = false }) { Text("确认") }
                    }
                }
            }
        }
        compose.runOnUiThread { navigation.back() }
        compose.onNodeWithText("确认").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, guarded)
            assertEquals(0, navigations)
        }
        compose.runOnUiThread { navigation.back() }
        compose.runOnIdle {
            assertEquals(1, guarded)
            assertEquals(0, navigations)
        }
    }

    @Test
    fun headerAndSystemBackUseTheSameGuardAndCancelledGesturesDoNotNavigate() {
        lateinit var input: DirectNavigationEventInput
        var guarded = 0
        setContent {
            val owner = rememberNavigationEventDispatcherOwner(parent = null)
            input = remember { DirectNavigationEventInput() }
            DisposableEffect(owner) {
                owner.navigationEventDispatcher.addInput(input)
                onDispose { owner.navigationEventDispatcher.removeInput(input) }
            }
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    ClientInteractionHost {
                        AppBackHandler { guarded++ }
                        Header("编辑")
                    }
                }
            }
        }
        compose.runOnIdle {
            input.backStarted(
                NavigationEvent(
                    progress = 0.4f,
                    swipeEdge = NavigationEvent.EDGE_LEFT,
                    touchX = 0f,
                    touchY = 0f,
                )
            )
            input.backCancelled()
            assertEquals(0, guarded)
            input.backCompleted()
            assertEquals(1, guarded)
        }
        compose.onNodeWithContentDescription("返回").clickOnMain()
        compose.runOnIdle { assertEquals(2, guarded) }
    }

    @Test
    fun disablingAnExitGuardRestoresNavHostBackNavigation() {
        val dirty = mutableStateOf(true)
        var guarded = 0
        lateinit var navigate: () -> Unit
        setContent {
            val nav = rememberNavController()
            val owner = remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
            navigate = { nav.navigate("edit") }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    ClientInteractionHost {
                        NavHost(nav, "home") {
                            composable("home") { Header("首页", showBack = false) }
                            composable("edit") {
                                AppBackHandler(enabled = dirty.value) { guarded++ }
                                Header("编辑")
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { navigate() }
        compose.onNodeWithContentDescription("返回").clickOnMain()
        compose.runOnIdle {
            assertEquals(1, guarded)
            dirty.value = false
        }
        compose.onNodeWithContentDescription("返回").clickOnMain()
        compose.onNodeWithText("首页").assertExists()
        compose.onNodeWithText("编辑").assertDoesNotExist()
    }
}
