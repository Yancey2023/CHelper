package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import yancey.chelper.domain.settings.Settings
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.settings.SettingsContent
import yancey.chelper.ui.platform.ClientInteractionHost

class SettingsViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun settingsComposeTheViewportAndKeepOffscreenActionsAndDialogsWorking() {
        val settings = mutableStateOf(Settings())
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                ClientInteractionHost {
                    Box(Modifier.requiredSize(420.dp, 640.dp)) {
                        SettingsContent(
                            settings.value,
                            arrayOf("测试资源" to "release-experiment"),
                            {},
                            {},
                            { change -> settings.value = change(settings.value) },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("命令库默认主页流").assertDoesNotExist()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("无法推断行的默认处理"))
        compose.onNodeWithText("无法推断行的默认处理").performClick()
        compose.onNodeWithText("当作指令").performClick()
        compose.onNodeWithText("确认").performClick()
        compose.onNodeWithText("当前: 当作指令").assertIsDisplayed()
        compose.onNodeWithTag("settings-list").performScrollToIndex(0)
        compose.onNodeWithText("命令库默认主页流").assertDoesNotExist()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("无法推断行的默认处理"))
        compose.onNodeWithText("当前: 当作指令").assertIsDisplayed()
    }
}
