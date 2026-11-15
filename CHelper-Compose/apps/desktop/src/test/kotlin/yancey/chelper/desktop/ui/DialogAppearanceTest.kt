package yancey.chelper.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import yancey.chelper.resources.Res
import yancey.chelper.resources.chevron_left
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.*
import yancey.chelper.ui.designsystem.widget.Button
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.IconButton
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextButton
import yancey.chelper.ui.platform.ClientInteractionHost

class DialogAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private val navigation = NavigationTestInput()

    @Test
    fun restoredDialogsRenderInTheExistingHost() {
        val variant = mutableStateOf("confirm")
        val options = arrayOf("浅色模式" to "light", "深色模式" to "dark")
        val input = TextFieldState("我的命令库")
        compose.setContent {
            navigation.Content {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    Box(
                        Modifier.requiredSize(420.dp, 740.dp)
                            .background(Color.White)
                            .testTag("host")
                    ) {
                        ClientInteractionHost {
                            when (variant.value) {
                                "confirm" ->
                                    IsConfirmDialog({}, title = "确认删除", content = "删除后无法恢复，是否继续？")
                                "input" ->
                                    InputStringDialog(
                                        {},
                                        title = "重命名",
                                        textFieldState = input,
                                        onConfirm = {},
                                    )
                                "menu" -> MenuDialog(options, {}, {})
                                "selection" ->
                                    SelectionDialog(
                                        "选择主题",
                                        options,
                                        onChoose = {},
                                        onDismissRequest = {},
                                    )
                                "policy" -> PolicyGrantDialog(content = "请阅读并同意隐私政策后继续使用。")
                                "report" ->
                                    ReportDialog({}, targetDescription = "命令库示例", onConfirm = {})
                            }
                        }
                    }
                }
            }
        }
        // The old confirmation has two full-width halves, with a fixed 45 dp action bar.
        compose.onNodeWithText("取消").assertHeightIsEqualTo(45.dp)
        compose.onNodeWithText("确认").assertHeightIsEqualTo(45.dp)
        assertTrue(
            abs(
                compose.onNodeWithText("取消").fetchSemanticsNode().boundsInRoot.width -
                    compose.onNodeWithText("确认").fetchSemanticsNode().boundsInRoot.width
            ) <= 1f,
            "The action halves may differ only by one pixel of rounding",
        )
        val directory = File(System.getProperty("chelper.screenshotsDir"))
        directory.mkdirs()
        for (name in listOf("confirm", "input", "menu", "selection", "policy", "report")) {
            compose.runOnIdle { variant.value = name }
            compose.onAllNodes(isRoot()).assertCountEquals(1)
            compose.onNode(isDialog()).assertIsDisplayed()
            ImageIO.write(
                compose.onNodeWithTag("host").captureToImage().toAwtImage(),
                "png",
                File(directory, "dialog-$name.png"),
            )
        }
        compose.onNodeWithText("提交").assertIsNotEnabled()
        compose.onNodeWithText("广告 / 引流").performClick()
        compose.onNodeWithText("提交").assertIsEnabled()
    }

    @Test
    fun pressingButtonsAndClickableIconsDoesNotChangeTheirPixels() {
        var clicks = 0
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                Column(Modifier.requiredSize(240.dp, 300.dp).background(Color.White)) {
                    Button("按钮", Modifier.testTag("button")) { clicks++ }
                    TextButton({ clicks++ }, Modifier.testTag("text-button")) { Text("文字按钮") }
                    IconButton({ clicks++ }, Modifier.testTag("icon-button")) {
                        Icon(Res.drawable.chevron_left, Modifier.size(24.dp))
                    }
                    Icon(
                        Res.drawable.chevron_left,
                        Modifier.size(48.dp).testTag("icon").clickable { clicks++ },
                    )
                }
            }
        }
        for (tag in listOf("button", "text-button", "icon-button", "icon")) {
            val node = compose.onNodeWithTag(tag)
            val before = node.captureToImage().toPixelMap()
            node.performTouchInput { down(center) }
            val pressed = node.captureToImage().toPixelMap()
            for (y in 0 until before.height) {
                for (x in 0 until before.width) {
                    assertEquals(before[x, y], pressed[x, y], "$tag changed at $x,$y while pressed")
                }
            }
            node.performTouchInput { up() }
        }
        compose.runOnIdle { assertEquals(4, clicks) }
    }
}
