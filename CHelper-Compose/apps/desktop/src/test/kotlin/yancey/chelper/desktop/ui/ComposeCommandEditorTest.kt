package yancey.chelper.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yancey.chelper.domain.command.CommandProblem
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.text.highlightCommand
import yancey.chelper.ui.feature.command.CommandErrorSelection
import yancey.chelper.ui.feature.command.ComposeCommandEditor

@OptIn(ExperimentalFoundationApi::class)
class ComposeCommandEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longSingleLineHighlightingCoversVisibleBidiRunsInBothLayoutDirections() {
        val text = "say " + "abc ".repeat(300) + "אבג123مرحبا " + "xyz ".repeat(300)
        val command = TextFieldState(text, TextRange.Zero)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val tokens = IntArray(text.length) { it / 3 % 8 }
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    ComposeCommandEditor(
                        command,
                        false,
                        tokens,
                        emptyList(),
                        Modifier.size(240.dp, 72.dp).testTag("editor"),
                        null,
                    )
                }
            }
        }
        val editor = compose.onNodeWithTag("editor")
        editor.performClick()
        for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { direction.value = layoutDirection }
            for (offset in listOf(0, 1205, 1212, text.length)) {
                compose.runOnIdle { command.edit { selection = TextRange(offset) } }
                val layout = textLayout(editor)
                val start = (offset - 20).coerceAtLeast(0)
                val end = (offset + 20).coerceAtMost(text.length)
                assertEquals(
                    highlightCommand(text, tokens, false).subSequence(start, end),
                    layout.layoutInput.text.subSequence(start, end),
                )
                val spans = layout.layoutInput.text.spanStyles
                // Visually adjacent bidi runs can be far apart in logical UTF-16 order.
                if (layoutDirection == LayoutDirection.Ltr)
                    assertTrue(
                        "Long lines must keep a bounded set of color spans",
                        spans.size < 500,
                    )
            }
        }
    }

    @Test
    fun typingAndAnalysisUpdatesKeepTheSameInputSession() {
        val command = TextFieldState("say ")
        val tokens = mutableStateOf<IntArray?>(null)
        val errors = mutableStateOf(emptyList<CommandProblem>())
        val theme = mutableStateOf(CHelperTheme.Theme.Light)
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
                CHelperTheme(theme.value, null) {
                    ComposeCommandEditor(
                        command,
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
                tokens.value = IntArray(command.text.length) { if (it < 3) 7 else 6 }
                errors.value = listOf(CommandProblem("example", 4, command.text.length))
            }
            editor.assertIsFocused()
            assertEquals(
                "Errors must not override syntax colors",
                highlightCommand(command.text.toString(), tokens.value, false),
                textLayout(editor).layoutInput.text,
            )
            assertTrue("New errors must draw red underlines", hasErrorUnderline(editor))
            compose.runOnIdle {
                assertEquals("Analysis must not restart the input session", 1, sessions)
                assertEquals("Typing must not cancel the input session", 0, cancellations)
                tokens.value = null
                errors.value = emptyList()
            }
            assertFalse("Clearing errors must remove the underline", hasErrorUnderline(editor))
        }
        compose.runOnIdle { theme.value = CHelperTheme.Theme.Dark }
        editor.assertIsFocused()
        editor.assertTextEquals("say 你好🙂")
        compose.runOnIdle {
            assertEquals(1, sessions)
            assertEquals(0, cancellations)
            command.undoState.undo()
        }
        editor.assertIsFocused()
        compose.runOnIdle {
            assertEquals(1, sessions)
            assertEquals(0, cancellations)
        }
    }

    @Test
    fun errorUnderlinesKeepSyntaxColorsWhenWrappedAndScrolled() {
        val text = "say 你好🙂 @e[type=pig] ".repeat(20)
        val command = TextFieldState(text)
        val wrapped = mutableStateOf(true)
        val theme = mutableStateOf(CHelperTheme.Theme.Light)
        val tokens = mutableStateOf<IntArray?>(IntArray(text.length) { if (it < 3) 7 else 6 })
        val errors = mutableStateOf(emptyList<CommandProblem>())
        compose.setContent {
            CHelperTheme(theme.value, null) {
                ComposeCommandEditor(
                    command,
                    wrapped.value,
                    tokens.value,
                    errors.value,
                    Modifier.size(160.dp, 72.dp).testTag("editor"),
                    null,
                )
            }
        }
        val editor = compose.onNodeWithTag("editor")
        editor.performClick()
        for (dark in listOf(false, true)) {
            for (editorMode in listOf(true, false)) {
                compose.runOnIdle {
                    theme.value = if (dark) CHelperTheme.Theme.Dark else CHelperTheme.Theme.Light
                    wrapped.value = editorMode
                    command.edit { selection = TextRange(length) }
                    errors.value = listOf(CommandProblem("at end", text.length - 10, text.length))
                }
                assertEquals(
                    highlightCommand(text, tokens.value, dark),
                    textLayout(editor).layoutInput.text,
                )
                assertTrue("Underline must follow text scrolling", hasErrorUnderline(editor))
                compose.runOnIdle { errors.value = listOf(CommandProblem("off screen", 0, 3)) }
                assertFalse("Off-screen errors must be clipped", hasErrorUnderline(editor))
            }
        }
        compose.runOnIdle {
            tokens.value = null
            errors.value = listOf(CommandProblem("no highlighting", text.length - 10, text.length))
        }
        assertTrue(textLayout(editor).layoutInput.text.spanStyles.isEmpty())
        assertTrue("Disabling highlighting must keep error underlines", hasErrorUnderline(editor))
    }

    private fun textLayout(editor: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        editor.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun hasErrorUnderline(editor: SemanticsNodeInteraction): Boolean {
        val pixels = editor.captureToImage().toPixelMap()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red > color.green + 0.2f && color.green == color.blue) return true
            }
        }
        return false
    }

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
