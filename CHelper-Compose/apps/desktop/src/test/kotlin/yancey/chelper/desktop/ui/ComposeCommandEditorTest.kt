package yancey.chelper.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yancey.chelper.domain.command.CommandProblem
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.command.CommandErrorSelection
import yancey.chelper.ui.feature.command.ComposeCommandEditor

@OptIn(ExperimentalFoundationApi::class)
class ComposeCommandEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun wrappedCommandsKeepUndoRedoAndNeverIntroduceNewlines() {
        val command = TextFieldState("say")
        val wrapped = mutableStateOf(true)
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                ComposeCommandEditor(
                    command,
                    wrapped.value,
                    null,
                    emptyList(),
                    Modifier.size(240.dp, 200.dp).testTag("editor"),
                    null,
                )
            }
        }
        compose.onNodeWithTag("editor").performTextInput(" hello\nworld")
        compose.runOnIdle {
            assertEquals("say hello world", command.text.toString())
            assertTrue(command.undoState.canUndo)
            command.undoState.undo()
            assertEquals("say", command.text.toString())
            command.undoState.redo()
            assertEquals("say hello world", command.text.toString())
            wrapped.value = false
        }
        compose.onNodeWithTag("editor").assertTextEquals("say hello world")
    }

    @Test
    fun repeatedErrorSelectionRequestsMoveTheCursorEachTime() {
        val command = TextFieldState("say hello")
        val request = mutableStateOf(CommandErrorSelection(4, 9, 1))
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                ComposeCommandEditor(
                    command,
                    true,
                    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
                    listOf(CommandProblem("example", 4, 9)),
                    Modifier.size(240.dp, 200.dp).testTag("editor"),
                    request.value,
                )
            }
        }
        compose.runOnIdle { assertEquals(TextRange(4, 9), command.selection) }
        compose.runOnIdle {
            command.edit { selection = TextRange(0) }
            request.value = CommandErrorSelection(4, 9, 2)
        }
        compose.runOnIdle { assertEquals(TextRange(4, 9), command.selection) }
    }
}
