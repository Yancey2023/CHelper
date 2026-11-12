package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.layout.Header
import yancey.chelper.ui.platform.AppBackDispatcher
import yancey.chelper.ui.platform.LocalAppBackAction
import yancey.chelper.ui.platform.LocalAppBackDispatcher
import yancey.chelper.ui.platform.PlatformBackHandler

class SharedBackNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun headerBackClosesThePreviewBeforeLeavingTheUploadPage() {
        val preview = mutableStateOf(true)
        val dispatcher = AppBackDispatcher()
        var navigations = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalAppBackDispatcher provides dispatcher,
                LocalAppBackAction provides { navigations++ },
            ) {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    Column {
                        if (preview.value) {
                            PlatformBackHandler { preview.value = false }
                            Header("预览")
                        } else Header("编辑")
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("编辑").assertExists()
        compose.runOnIdle { assertEquals(0, navigations) }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(1, navigations) }
    }
}
