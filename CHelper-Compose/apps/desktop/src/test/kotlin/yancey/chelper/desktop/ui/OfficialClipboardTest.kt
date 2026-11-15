package yancey.chelper.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yancey.chelper.desktop.platform.DesktopTextClipCodec
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Button
import yancey.chelper.ui.platform.ClientInteractionHost
import yancey.chelper.ui.platform.TextClipboard
import yancey.chelper.ui.platform.rememberCopyText
import yancey.chelper.ui.platform.rememberTextClipboard

@OptIn(ExperimentalComposeUiApi::class)
class OfficialClipboardTest {
    @get:Rule val compose = createComposeRule()
    private val navigation = NavigationTestInput()
    private lateinit var reader: TextClipboard

    private class TestClipboard(val fail: Boolean = false) : Clipboard {
        val allowWrite = CompletableDeferred<Unit>()
        var requested = false
        var entry: ClipEntry? = null

        override suspend fun getClipEntry() = entry

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            requested = true
            if (fail) error("Clipboard busy")
            allowWrite.await()
            entry = clipEntry
        }
    }

    private fun content(clipboard: TestClipboard) {
        compose.setContent {
            navigation.Content {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    CHelperTheme(CHelperTheme.Theme.Light, null) {
                        ClientInteractionHost {
                            val clipboardReader = rememberTextClipboard(DesktopTextClipCodec)
                            SideEffect { reader = clipboardReader }
                            val copy = rememberCopyText(codec = DesktopTextClipCodec)
                            Button("复制") { copy("say 你好🙂") }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun copySuccessIsReportedOnlyAfterTheOfficialClipboardWriteCompletes() {
        val clipboard = TestClipboard()
        content(clipboard)
        compose.onNodeWithText("复制").performClick()
        compose.runOnIdle {
            assertTrue(clipboard.requested)
            assertNull(clipboard.entry)
        }
        compose.onNodeWithText("已复制").assertDoesNotExist()
        compose.runOnIdle { clipboard.allowWrite.complete(Unit) }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("已复制").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle {
            val native = clipboard.entry?.nativeClipEntry as Transferable
            assertEquals("say 你好🙂", native.getTransferData(DataFlavor.stringFlavor))
            assertEquals("say 你好🙂", runBlocking { reader.clipboardText() })
        }
    }

    @Test
    fun failedClipboardWritesDoNotProduceASuccessMessage() {
        content(TestClipboard(fail = true))
        compose.onNodeWithText("复制").performClick()
        compose.onNodeWithText("复制失败").assertExists()
        compose.onNodeWithText("已复制").assertDoesNotExist()
    }
}
