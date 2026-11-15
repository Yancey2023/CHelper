package yancey.chelper.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import yancey.chelper.domain.command.CommandProblem
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.command.CommandErrorSelection
import yancey.chelper.ui.feature.command.ComposeCommandEditor

@OptIn(ExperimentalFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class ComposeCommandEditorInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun analysisUpdatesKeepTheActiveAndroidInputSession() {
        val state = TextFieldState("say ")
        val tokens = mutableStateOf<IntArray?>(null)
        val errors = mutableStateOf(emptyList<CommandProblem>())
        var sessions = 0
        var cancellations = 0
        val interceptor = PlatformTextInputInterceptor { _, _ ->
            sessions++
            try {
                awaitCancellation()
            } finally {
                cancellations++
            }
        }
        compose.setContent {
            InterceptPlatformTextInput(interceptor) {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    ComposeCommandEditor(
                        state,
                        true,
                        tokens.value,
                        errors.value,
                        Modifier.size(240.dp, 200.dp).testTag("editor"),
                        null,
                    )
                }
            }
        }
        val editor = compose.onNodeWithTag("editor")
        editor.performClick().assertIsFocused()
        compose.waitUntil(5000) { sessions > 0 }
        for (text in listOf("你", "好", "🙂")) {
            editor.performTextInput(text)
            compose.runOnIdle {
                tokens.value = IntArray(state.text.length)
                errors.value = listOf(CommandProblem("example", 4, state.text.length))
            }
            editor.assertIsFocused()
            compose.runOnIdle {
                assertEquals(1, sessions)
                assertEquals(0, cancellations)
            }
        }
        editor.assertTextEquals("say 你好🙂")
    }

    @Test
    fun commandInputKeepsUnicodeUndoAndSingleLineStorageInBothModes() {
        val state = TextFieldState("say")
        val wrapped = mutableStateOf(true)
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                ComposeCommandEditor(
                    state,
                    wrapped.value,
                    null,
                    emptyList(),
                    Modifier.size(240.dp, 200.dp).testTag("editor"),
                    null,
                )
            }
        }
        compose.onNodeWithTag("editor").performTextInput(" 你好🙂\nworld")
        compose.runOnIdle {
            assertEquals("say 你好🙂 world", state.text.toString())
            state.undoState.undo()
            assertEquals("say", state.text.toString())
            state.undoState.redo()
            wrapped.value = false
        }
        compose.onNodeWithTag("editor").assertTextEquals("say 你好🙂 world")
    }

    @Test
    fun errorNavigationSelectsUtf16RangesAndIgnoresInvalidRequests() {
        val state = TextFieldState("say 你好")
        val request = mutableStateOf(CommandErrorSelection(4, 6, 1))
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                ComposeCommandEditor(
                    state,
                    true,
                    null,
                    emptyList(),
                    Modifier.size(240.dp, 200.dp).testTag("editor"),
                    request.value,
                )
            }
        }
        compose.runOnIdle {
            assertEquals(TextRange(4, 6), state.selection)
            request.value = CommandErrorSelection(-1, 100, 2)
        }
        compose.runOnIdle { assertEquals(TextRange(4, 6), state.selection) }
    }
}
