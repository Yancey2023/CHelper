package yancey.chelper.desktop.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yancey.chelper.desktop.ui.NavigationTestInput
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.loongflow.LoongFlowMiniPanel
import yancey.chelper.ui.feature.loongflow.LoongFlowMode
import yancey.chelper.ui.feature.loongflow.LoongFlowPanel
import yancey.chelper.ui.feature.loongflow.LoongFlowViewModel
import yancey.chelper.ui.feature.loongflow.importMiniState
import yancey.chelper.ui.platform.ClientInteractionHost

class DesktopLoongFlowTest {
    @get:Rule val compose = createComposeRule()
    private val navigation = NavigationTestInput()

    @Test
    fun windowsUsesTheOriginalExportWizardAndItsCloseConfirmation() {
        val copied = mutableListOf<String>()
        val host = DesktopLoongFlow(copied::add, { "say from Windows" }, {})
        host.openExport()
        val model = requireNotNull(host.model)
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                Box(Modifier.requiredSize(400.dp, 600.dp)) {
                    navigation.Content {
                        ClientInteractionHost {
                            LoongFlowPanel(
                                model.mode,
                                null,
                                model,
                                onMinimize = { host.minimized = true },
                                onDismiss = host::dismiss,
                                closeRequest = host.closeRequest,
                                onMessage = {},
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("开始录入 ▸").performClick()
        compose.onNodeWithText("尝试从剪贴板粘贴").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("保存并下一条 ▸").performClick()
        compose.onNodeWithText("完成录入 (1) →").performClick()
        compose.onNodeWithText("MCDv2 预览").assertExists()
        compose.runOnIdle {
            model.copyExportText()
            assertTrue(copied.single().contains("say from Windows"))
            host.requestClose()
        }
        compose.onNodeWithText("关闭游龙导出？").assertExists()
        compose.runOnIdle { assertSame(model, host.model) }
    }

    @Test
    fun miniPanelCopiesCommandsAndCanReturnFromCompletion() {
        val copied = mutableListOf<String>()
        val model = LoongFlowViewModel(copied::add, { null })
        model.initImport(LibraryFunction(content = "say one\nsay two"))
        model.startImportCopy()
        var dismissed = false
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Dark, null) {
                Box(Modifier.requiredSize(318.dp, 88.dp)) {
                    LoongFlowMiniPanel(model, {}, { dismissed = true }, {})
                }
            }
        }
        compose.onNodeWithText("1/2").assertExists()
        compose.onNodeWithText("下一条").performClick()
        compose.onNodeWithText("2/2").assertExists()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("导入完成").assertExists()
        compose.onNodeWithText("上一条").performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertEquals(listOf("say one", "say two", "say two"), copied)
        }
    }

    @Test
    fun importSessionCanMinimizeAndNewSessionStartsClean() {
        val host = DesktopLoongFlow({}, { null }, {})
        host.openImport(LibraryFunction(name = "shared", content = "say one"))
        val old = requireNotNull(host.model)
        assertTrue(old.startImportCopy())
        host.minimized = true
        assertEquals("1/1", importMiniState(old, false).progress)
        host.openExport()
        assertNotSame(old, host.model)
        assertFalse(host.minimized)
        assertNull(host.library)
        assertEquals(LoongFlowMode.EXPORT, host.model?.mode)
        host.dismiss()
        assertNull(host.model)
    }
}
