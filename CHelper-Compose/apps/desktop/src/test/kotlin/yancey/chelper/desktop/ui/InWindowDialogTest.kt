package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.AppDialog
import yancey.chelper.ui.designsystem.dialog.AppDialogProperties
import yancey.chelper.ui.designsystem.widget.Button
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.platform.AppBackHandler
import yancey.chelper.ui.platform.ClientInteractionHost

class InWindowDialogTest {
    @get:Rule val compose = createComposeRule()
    private val navigation = NavigationTestInput()

    private fun content(body: @Composable BoxScope.() -> Unit) {
        compose.setContent {
            navigation.Content {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    Box(Modifier.requiredSize(400.dp)) { ClientInteractionHost(content = body) }
                }
            }
        }
    }

    @Test
    fun aNestedRequestUsesTheWholeExistingHostAndKeepsItsCompositionLocals() {
        val label = staticCompositionLocalOf { "wrong context" }
        val message = mutableStateOf("弹层")
        content {
            Box(Modifier.size(40.dp)) {
                CompositionLocalProvider(label provides "原页面") {
                    AppDialog(onDismissRequest = {}) { Text(label.current + message.value) }
                }
            }
        }
        compose.onAllNodes(isRoot()).assertCountEquals(1)
        compose.onNode(isDialog()).assertWidthIsEqualTo(400.dp).assertHeightIsEqualTo(400.dp)
        compose.onNodeWithText("原页面弹层").assertIsDisplayed()
        compose.runOnIdle { message.value = "已更新" }
        compose.onNodeWithText("原页面已更新").assertIsDisplayed()
    }

    @Test
    fun insideClicksStayInsideAndOutsideClicksNeverReachThePage() {
        val visible = mutableStateOf(true)
        var pageClicks = 0
        var insideClicks = 0
        content {
            Button("页面按钮", modifier = Modifier.size(60.dp)) { pageClicks++ }
            if (visible.value) {
                AppDialog(onDismissRequest = { visible.value = false }) {
                    Button("弹层按钮") { insideClicks++ }
                }
            }
        }
        compose.onNodeWithText("弹层按钮").performClick()
        compose.runOnIdle { assertEquals(1, insideClicks) }
        compose.onNode(isDialog()).assertExists()
        compose.onRoot().performTouchInput { click(Offset(10f, 10f)) }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, pageClicks) }
        compose.onNodeWithText("页面按钮").performClick()
        compose.runOnIdle { assertEquals(1, pageClicks) }
    }

    @Test
    fun aLockedModalConsumesBackAndOutsideClicksBeforeThePageGuard() {
        var dismissals = 0
        var guards = 0
        content {
            AppBackHandler { guards++ }
            AppDialog(
                onDismissRequest = { dismissals++ },
                properties =
                    AppDialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            ) {
                Text("必须确认")
            }
        }
        compose.onRoot().performTouchInput { click(Offset(10f, 10f)) }
        compose.runOnUiThread { navigation.back() }
        compose.runOnIdle {
            assertEquals(0, dismissals)
            assertEquals(0, guards)
        }
        compose.onNodeWithText("必须确认").assertIsDisplayed()
    }

    @Test
    fun nestedDialogsCloseFromTheTopBeforeThePage() {
        val outer = mutableStateOf(true)
        val inner = mutableStateOf(true)
        var backs = 0
        content {
            AppBackHandler { backs++ }
            if (outer.value) {
                AppDialog(onDismissRequest = { outer.value = false }) {
                    Column {
                        Text("第一层")
                        if (inner.value) {
                            AppDialog(onDismissRequest = { inner.value = false }) { Text("第二层") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("第二层").assertIsDisplayed()
        compose.runOnUiThread { navigation.back() }
        compose.onNodeWithText("第二层").assertDoesNotExist()
        compose.onNodeWithText("第一层").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, backs) }
        compose.runOnUiThread { navigation.back() }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnUiThread { navigation.back() }
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test
    fun typingAndTabStayInTheDialogAndClosingRestoresPageFocus() {
        val visible = mutableStateOf(false)
        var pageText by mutableStateOf("")
        var modalText by mutableStateOf("")
        content {
            BasicTextField(
                pageText,
                { pageText = it },
                Modifier.testTag("page-field"),
                singleLine = true,
            )
            if (visible.value) {
                AppDialog(onDismissRequest = { visible.value = false }) {
                    Column {
                        BasicTextField(
                            modalText,
                            { modalText = it },
                            Modifier.testTag("modal-field"),
                            singleLine = true,
                        )
                        Button("关闭") { visible.value = false }
                    }
                }
            }
        }
        compose.onNodeWithTag("page-field").performClick()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("page-field").assertDoesNotExist()
        compose.onNodeWithTag("modal-field").performClick().performTextInput("中文🙂")
        compose.onRoot().performKeyInput { repeat(8) { pressKey(Key.Tab) } }
        compose.runOnIdle { assertEquals("", pageText) }
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("page-field").assertIsFocused()
        compose.runOnIdle { assertEquals("中文🙂", modalText) }
    }
}
