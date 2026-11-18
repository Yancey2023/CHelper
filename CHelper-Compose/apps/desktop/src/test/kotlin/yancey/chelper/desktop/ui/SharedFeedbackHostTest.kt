package yancey.chelper.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.AppDialog
import yancey.chelper.ui.designsystem.widget.Button
import yancey.chelper.ui.platform.ClientInteractionHost
import yancey.chelper.ui.platform.ClientMessages
import yancey.chelper.ui.platform.rememberOpenUrl

class SharedFeedbackHostTest {
    @get:Rule val compose = createComposeRule()
    private val navigation = NavigationTestInput()

    @Test
    fun feedbackFromALineCopyStyleModalIsVisibleAboveTheInWindowDialog() {
        compose.setContent {
            navigation.Content {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    ClientInteractionHost {
                        AppDialog(onDismissRequest = {}) {
                            Button("下一条") { ClientMessages.show("已复制下一条") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("下一条").performClick()
        compose.onNode(isDialog()).assertExists()
        compose.onAllNodes(isRoot()).assertCountEquals(1)
        compose.onNodeWithText("已复制下一条").assertIsDisplayed()
        compose.onAllNodesWithText("已复制下一条").assertCountEquals(1)
    }

    @Test
    fun completingAModalKeepsFeedbackVisibleInItsParent() {
        val visible = mutableStateOf(true)
        compose.setContent {
            navigation.Content {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    ClientInteractionHost {
                        if (visible.value) {
                            AppDialog(onDismissRequest = { visible.value = false }) {
                                Button("完成") {
                                    visible.value = false
                                    ClientMessages.show("已完成")
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("完成").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onAllNodesWithText("已完成").assertCountEquals(1)
    }

    @Test
    fun aLinkFailureAppearsInTheSameSharedHostAsFeatureMessages() {
        compose.setContent {
            navigation.Content {
                CompositionLocalProvider(
                    LocalUriHandler provides
                        object : UriHandler {
                            override fun openUri(uri: String) {
                                error("No browser installed")
                            }
                        }
                ) {
                    CHelperTheme(CHelperTheme.Theme.Light, null) {
                        ClientInteractionHost {
                            val openUrl = rememberOpenUrl()
                            Button("帮助") { openUrl("https://example.com") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("帮助").performClick()
        compose.onNodeWithText("无法打开链接").assertExists()
        compose.runOnIdle { ClientMessages.show("已复制") }
        compose.onNodeWithText("已复制").assertExists()
        compose.onNodeWithText("无法打开链接").assertDoesNotExist()
    }
}
