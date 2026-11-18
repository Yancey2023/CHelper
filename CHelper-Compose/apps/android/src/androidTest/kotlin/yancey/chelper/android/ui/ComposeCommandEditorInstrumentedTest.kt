package yancey.chelper.android.ui

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import yancey.chelper.domain.command.CommandProblem
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.text.highlightCommand
import yancey.chelper.ui.feature.command.CommandErrorSelection
import yancey.chelper.ui.feature.command.ComposeCommandEditor

@OptIn(ExperimentalFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class ComposeCommandEditorInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun cachedLongLineDrawsActualSyntaxPixelsAtBothEndsAndAfterSelection() {
        val text = "say hello command ".repeat(200)
        val state = TextFieldState(text, TextRange.Zero)
        val tokens = mutableStateOf<IntArray?>(IntArray(text.length) { 6 })
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                ComposeCommandEditor(
                    state,
                    false,
                    tokens.value,
                    emptyList(),
                    Modifier.size(240.dp, 72.dp).background(Color.White).testTag("editor"),
                    null,
                )
            }
        }
        val editor = compose.onNodeWithTag("editor")
        editor.performClick()
        for (range in
            listOf(
                TextRange.Zero,
                TextRange(text.length),
                TextRange(text.length - 10, text.length),
                TextRange(text.length),
            )) {
            compose.runOnIdle { state.edit { selection = range } }
            textLayout(editor)
            if (range.collapsed)
                compose.onNodeWithTag("command-glyph-cache", useUnmergedTree = true).assertExists()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            var green = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (
                    pixel.green > pixel.red + 0.2f &&
                        pixel.green > pixel.blue + 0.1f &&
                        pixel.alpha > 0.5f
                )
                    green++
            }
            assertTrue(
                "Syntax glyphs must be drawn for $range, found $green green pixels",
                green > 100,
            )
        }
        compose.runOnIdle {
            tokens.value = null
            state.edit { selection = TextRange.Zero }
        }
        val plain = textLayout(editor)
        assertTrue(plain.layoutInput.text.spanStyles.isEmpty())
        val textColor = plain.layoutInput.style.color
        val pixels = editor.captureToImage().toPixelMap()
        var plainPixels = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            if (
                pixel.alpha > 0.5f &&
                    abs(pixel.red - textColor.red) < 0.08f &&
                    abs(pixel.green - textColor.green) < 0.08f &&
                    abs(pixel.blue - textColor.blue) < 0.08f
            )
                plainPixels++
        }
        assertTrue(
            "Long plain text must remain visible after turning highlighter off",
            plainPixels > 100,
        )
    }

    @Test
    fun longCommandsKeepNativeCompositionUnderlinesAndTheSameInputConnection() {
        val text = "say 你好🙂 @e[type=pig] ".repeat(100)
        val state = TextFieldState(text)
        val tokens = mutableStateOf(syntaxTokens(text))
        var connection: InputConnection? = null
        var sessions = 0
        var cancellations = 0
        val interceptor = PlatformTextInputInterceptor { request, _ ->
            sessions++
            connection = request.createInputConnection(EditorInfo())
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
                        false,
                        tokens.value,
                        emptyList(),
                        Modifier.size(240.dp, 72.dp).testTag("editor"),
                        null,
                    )
                }
            }
        }
        val editor = compose.onNodeWithTag("editor")
        editor.performClick()
        compose.waitUntil(5000) { connection != null }
        for (composing in listOf("你", "你好", "你好🙂")) {
            compose.runOnIdle {
                assertTrue(connection!!.setComposingText(composing, 1))
                assertNotNull(state.composition)
                tokens.value = syntaxTokens(state.text)
            }
            assertTrue(
                "IME composition must use the native underline",
                textLayout(editor).layoutInput.text.spanStyles.any {
                    it.item.textDecoration == TextDecoration.Underline
                },
            )
            compose.runOnIdle {
                assertEquals(1, sessions)
                assertEquals(0, cancellations)
            }
        }
        compose.runOnIdle {
            connection!!.finishComposingText()
            assertNull(state.composition)
        }
        assertTrue(
            textLayout(editor).layoutInput.text.spanStyles.none {
                it.item.textDecoration == TextDecoration.Underline
            }
        )
        compose.runOnIdle {
            assertEquals(1, sessions)
            assertEquals(0, cancellations)
        }
    }

    @Test
    fun longSingleLineCommandsBoundStylesAndKeepColorsAndInputSessionWhenScrolling() {
        val text = "say 你好🙂 @e[type=pig] ".repeat(300)
        val state = TextFieldState(text, TextRange.Zero)
        val tokens = mutableStateOf<IntArray?>(syntaxTokens(text))
        val theme = mutableStateOf(CHelperTheme.Theme.Light)
        val wrapped = mutableStateOf(false)
        val fontScale = mutableStateOf(1f)
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
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale.value)
            ) {
                InterceptPlatformTextInput(interceptor) {
                    CHelperTheme(theme.value, null) {
                        ComposeCommandEditor(
                            state,
                            wrapped.value,
                            tokens.value,
                            emptyList(),
                            Modifier.size(240.dp, 100.dp).testTag("editor"),
                            null,
                        )
                    }
                }
            }
        }
        val editor = compose.onNodeWithTag("editor")
        editor.performClick().assertIsFocused()
        compose.waitUntil(5000) { sessions > 0 }
        compose.runOnIdle { state.edit { replace(0, 3, "msg") } }
        assertEquals(text.replaceRange(0, 3, "msg"), textLayout(editor).layoutInput.text.text)
        compose.runOnIdle { state.edit { replace(0, 3, "say") } }
        for (dark in listOf(false, true)) {
            compose.runOnIdle {
                theme.value = if (dark) CHelperTheme.Theme.Dark else CHelperTheme.Theme.Light
            }
            for (offset in listOf(0, text.length / 2, text.length)) {
                compose.runOnIdle { state.edit { selection = TextRange(offset) } }
                val layout = textLayout(editor)
                val styles = layout.layoutInput.text.spanStyles
                assertEquals(1, layout.lineCount)
                assertTrue("Long lines must not style every off-screen token", styles.size < 256)
                val probe = offset.coerceIn(0, text.lastIndex)
                assertTrue(
                    "Scrolling must highlight the cursor's new viewport",
                    styles.any {
                        probe in it.start until it.end
                    },
                )
                val start = styles.minOf { it.start }
                val end = styles.maxOf { it.end }
                assertEquals(
                    highlightCommand(text, tokens.value, dark).subSequence(start, end),
                    layout.layoutInput.text.subSequence(start, end),
                )
                compose.runOnIdle {
                    assertEquals(1, sessions)
                    assertEquals(0, cancellations)
                }
            }
        }
        for (scale in listOf(1.3f, 1f)) {
            compose.runOnIdle {
                fontScale.value = scale
                state.edit { selection = TextRange(text.length / 2) }
            }
            val layout = textLayout(editor)
            assertTrue(
                "Changing font geometry must invalidate cached character positions",
                layout.layoutInput.text.spanStyles.any {
                    text.length / 2 in it.start until it.end
                },
            )
            compose.runOnIdle {
                assertEquals(1, sessions)
                assertEquals(0, cancellations)
                state.edit { selection = TextRange(text.length) }
            }
        }
        val beforeScroll = textLayout(editor).layoutInput.text.spanStyles.maxOf { it.end }
        repeat(10) { editor.performTouchInput { swipeRight(durationMillis = 50) } }
        compose.waitForIdle()
        assertTrue(
            "Highlighting must follow scrolling independently of the cursor",
            textLayout(editor).layoutInput.text.spanStyles.maxOf { it.end } < beforeScroll,
        )
        compose.runOnIdle {
            assertEquals(TextRange(text.length), state.selection)
            state.edit { selection = TextRange(0) }
        }
        compose.runOnIdle { state.edit { selection = TextRange(text.length) } }
        editor.performTextInput("好🙂")
        compose.runOnIdle { tokens.value = syntaxTokens(state.text) }
        compose.runOnIdle {
            assertEquals(text + "好🙂", state.text.toString())
            assertEquals(1, sessions)
            assertEquals(0, cancellations)
            state.undoState.undo()
            assertEquals(text, state.text.toString())
            tokens.value = null
        }
        assertTrue(textLayout(editor).layoutInput.text.spanStyles.isEmpty())
        compose.runOnIdle {
            tokens.value = syntaxTokens(text)
            wrapped.value = true
        }
        assertEquals(
            highlightCommand(text, tokens.value, true),
            textLayout(editor).layoutInput.text,
        )
    }

    private fun syntaxTokens(text: CharSequence): IntArray =
        IntArray(text.length) { if (text[it] in '\uD800'..'\uDFFF') 6 else it % 8 }

    private fun textLayout(editor: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        editor.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

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
