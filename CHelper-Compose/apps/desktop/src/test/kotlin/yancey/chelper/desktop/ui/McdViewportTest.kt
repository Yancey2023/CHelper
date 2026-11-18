package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import yancey.chelper.domain.library.ChainItem
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.feature.library.mcd.MCDRenderRow
import yancey.chelper.ui.feature.library.mcd.MCDRowsContent

class McdViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longDocumentsOnlyComposeAndHighlightTheViewportAndCanReachTheEnd() {
        val rows = List(10_000) { MCDRenderRow.Item(ChainItem.RawCommand("say row $it")) }
        val active = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        var copied: String? = null
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                Box(Modifier.requiredSize(400.dp, 400.dp)) {
                    MCDRowsContent(
                        rows = rows,
                        modifier = Modifier.testTag("document"),
                        onCopy = { copied = it },
                        header = { Text("预览说明") },
                        footer = { Text("上传确认") },
                        tokensFor = { item ->
                            val command = (item as ChainItem.RawCommand).command
                            DisposableEffect(command) {
                                active += command
                                visited += command
                                onDispose { active -= command }
                            }
                            null
                        },
                    )
                }
            }
        }
        compose.onNodeWithText("预览说明").assertIsDisplayed()
        compose.onNodeWithText("say row 0").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(5_000)
        compose.runOnIdle {
            assertTrue(
                visited.size in 1..50,
                "Only viewport rows should start highlighting: ${visited.size}",
            )
            assertTrue(active.size <= 50)
        }
        compose.onNodeWithTag("document").performScrollToIndex(10_000)
        compose.onNodeWithText("say row 9999").assertIsDisplayed()
        compose.onNodeWithText("上传确认").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("复制指令").onLast().performClick()
        compose.runOnIdle {
            assertEquals("say row 9999", copied)
            assertTrue(active.size <= 50)
            assertTrue(visited.size <= 100)
        }
        compose.onNodeWithTag("document").performScrollToIndex(1)
        compose.onNodeWithText("say row 0").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("复制指令").onFirst().performClick()
        compose.runOnIdle { assertEquals("say row 0", copied) }
    }
}
