/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey & Ankanyi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.ui.feature.command

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.domain.command.CommandProblem
import yancey.chelper.resources.Res
import yancey.chelper.resources.layout_completion_command_hint
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.text.highlightCommand
import yancey.chelper.ui.designsystem.widget.Text

data class CommandErrorSelection(val start: Int, val end: Int, val request: Long)

/** The Compose implementation of the original editor: wrapped display still holds one command. */
@Composable
fun ComposeCommandEditor(
    command: TextFieldState,
    editorMode: Boolean,
    tokens: IntArray?,
    errors: List<CommandProblem>,
    modifier: Modifier,
    errorSelection: CommandErrorSelection?,
) {
    val focus = remember { FocusRequester() }
    val dark = CHelperTheme.theme == CHelperTheme.Theme.Dark
    val colors = CHelperTheme.colors
    val currentTokens = rememberUpdatedState(tokens)
    val currentErrors = rememberUpdatedState(errors)
    val currentDark = rememberUpdatedState(dark)
    val scroll = rememberScrollState()
    var textLayout by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var highlightRange by remember { mutableStateOf(TextRange.Zero) }
    val highlightViewport = remember { CommandHighlightViewport() }
    val viewportMeasurer = rememberTextMeasurer(cacheSize = 1)
    val currentEditorMode = rememberUpdatedState(editorMode)
    LaunchedEffect(command, scroll) {
        snapshotFlow {
            val layout = textLayout?.invoke()
            if (
                currentEditorMode.value ||
                    command.text.length <= COMMAND_HIGHLIGHT_CHUNK_SIZE * 4 ||
                    currentTokens.value == null ||
                    layout == null ||
                    viewport.width == 0
            )
                TextRange.Zero
            else {
                // Hit-testing a styled Android line also scans its spans repeatedly. Colors
                // do not affect geometry, so cache an unstyled layout for viewport queries.
                val input = layout.layoutInput
                val hitLayout =
                    viewportMeasurer.measure(
                        text = input.text.text,
                        style = input.style,
                        overflow = input.overflow,
                        softWrap = input.softWrap,
                        maxLines = input.maxLines,
                        constraints = input.constraints,
                        layoutDirection = input.layoutDirection,
                        density = input.density,
                        fontFamilyResolver = input.fontFamilyResolver,
                    )
                val left =
                    if (layout.layoutInput.layoutDirection == LayoutDirection.Rtl)
                        layout.size.width - viewport.width - scroll.value.toFloat()
                    else scroll.value.toFloat()
                val visible =
                    highlightViewport.range(
                        hitLayout,
                        left - viewport.width,
                        left + viewport.width * 2f,
                    )
                val retained = highlightRange
                val margin = COMMAND_HIGHLIGHT_CHUNK_SIZE / 2
                val retainedStart = if (retained.start == 0) 0 else retained.start + margin
                val retainedEnd =
                    if (retained.end >= command.text.length) command.text.length
                    else retained.end - margin
                if (
                    !retained.collapsed &&
                        visible.min >= retainedStart &&
                        visible.max <= retainedEnd
                )
                    retained
                else commandHighlightRange(command.text.length, visible)
            }
        }
            .collect { highlightRange = it }
    }
    val inputTransformation = remember {
        InputTransformation {
            for (i in length - 1 downTo 0) {
                if (charAt(i) == '\n' || charAt(i) == '\r') replace(i, i + 1, " ")
            }
        }
    }
    // BasicTextField keys its internal transformed state on this instance. Styling changes
    // must invalidate the output through snapshot reads, not replace the active IME session.
    val outputTransformation = remember {
        OutputTransformation {
            // Android's TextLine scans the line's CharacterStyles for each color run.
            // Bound that work for a long horizontal line, including its first layout.
            val range =
                if (currentEditorMode.value || length <= COMMAND_HIGHLIGHT_CHUNK_SIZE * 4)
                    TextRange(0, length)
                else if (highlightRange.collapsed)
                    commandHighlightRange(length, TextRange(selection.end))
                else highlightRange
            val styled =
                highlightCommand(
                    asCharSequence().toString(),
                    currentTokens.value,
                    currentDark.value,
                    range.start,
                    range.end,
                )
            styled.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
        }
    }
    LaunchedEffect(command, errorSelection) {
        errorSelection?.let {
            validCommandSelection(command.text.length, it.start, it.end)?.let { range ->
                command.edit { selection = range }
                focus.requestFocus()
            }
        }
    }
    BasicTextField(
        state = command,
        modifier =
            modifier.padding(if (editorMode) 8.dp else 0.dp).focusRequester(focus).onSizeChanged {
                viewport = it
            },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
        lineLimits =
            if (editorMode) TextFieldLineLimits.MultiLine() else TextFieldLineLimits.SingleLine,
        inputTransformation = inputTransformation,
        textStyle = TextStyle(color = colors.textMain, fontSize = 16.sp),
        cursorBrush = SolidColor(colors.mainColor),
        outputTransformation = outputTransformation,
        onTextLayout = { textLayout = it },
        scrollState = scroll,
        decorator = { input ->
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = if (editorMode) Alignment.TopStart else Alignment.CenterStart,
            ) {
                if (command.text.isEmpty())
                    Text(
                        stringResource(Res.string.layout_completion_command_hint),
                        style = TextStyle(color = colors.textHint),
                    )
                Box(
                    Modifier.drawWithContent {
                        drawContent()
                        val layout = textLayout?.invoke() ?: return@drawWithContent
                        val scrollX =
                            when {
                                editorMode -> 0f
                                layoutDirection == LayoutDirection.Rtl ->
                                    size.width - layout.size.width + scroll.value
                                else -> -scroll.value.toFloat()
                            }
                        val scrollY = if (editorMode) -scroll.value.toFloat() else 0f
                        clipRect {
                            translate(scrollX, scrollY) {
                                drawCommandUnderlines(
                                    layout,
                                    currentErrors.value,
                                    colors.textErrorReason,
                                )
                            }
                        }
                    }
                ) {
                    input()
                }
            }
        },
    )
}

private const val COMMAND_HIGHLIGHT_CHUNK_SIZE = 256

/** Quantize with overscan so ordinary horizontal scrolling does not restyle every pixel. */
internal fun commandHighlightRange(length: Int, visible: TextRange): TextRange {
    val start = (visible.min / COMMAND_HIGHLIGHT_CHUNK_SIZE - 1).coerceAtLeast(0)
    val end = visible.max / COMMAND_HIGHLIGHT_CHUNK_SIZE + 2
    return TextRange(
        (start * COMMAND_HIGHLIGHT_CHUNK_SIZE).coerceIn(0, length),
        (end.toLong() * COMMAND_HIGHLIGHT_CHUNK_SIZE).coerceIn(0, length.toLong()).toInt(),
    )
}

/** Draw decorations separately: SpanStyle's foreground also colors the glyphs. */
private fun DrawScope.drawCommandUnderlines(
    layout: TextLayoutResult,
    errors: List<CommandProblem>,
    color: Color,
) {
    val text = layout.layoutInput.text
    val stroke = 1.dp.toPx()
    errors.forEach { problem ->
        val range = commandUnderlineRange(text, problem.start, problem.end) ?: return@forEach
        // The selection path handles wrapping and disjoint bidirectional text runs.
        clipPath(layout.getPathForRange(range.start, range.end)) {
            val firstLine = layout.getLineForOffset(range.start)
            val lastLine = layout.getLineForOffset(range.end - 1)
            for (line in firstLine..lastLine) {
                val y =
                    minOf(
                        layout.getLineBaseline(line) + 2.dp.toPx(),
                        layout.getLineBottom(line) - stroke / 2,
                    )
                drawLine(
                    color,
                    Offset(layout.getLineLeft(line), y),
                    Offset(layout.getLineRight(line), y),
                    strokeWidth = stroke,
                )
            }
        }
    }
}

/**
 * Ranges are UTF-16 offsets from the native command protocol. Invalid navigation requests do
 * nothing.
 */
internal fun validCommandSelection(length: Int, start: Int, end: Int): TextRange? =
    if (start < 0 || end < start || end > length) null else TextRange(start, end)

/** Empty errors still mark the adjacent code point, including at end of input. */
internal fun commandUnderlineRange(text: CharSequence, start: Int, end: Int): TextRange? {
    if (text.isEmpty()) return null
    var left = start.coerceIn(0, text.length)
    var right = end.coerceIn(left, text.length)
    if (left == right) {
        if (left == text.length) left-- else right++
    }
    if (left > 0 && text[left] in '\uDC00'..'\uDFFF' && text[left - 1] in '\uD800'..'\uDBFF') left--
    if (
        right < text.length &&
            text[right - 1] in '\uD800'..'\uDBFF' &&
            text[right] in '\uDC00'..'\uDFFF'
    )
        right++
    return TextRange(left, right)
}
