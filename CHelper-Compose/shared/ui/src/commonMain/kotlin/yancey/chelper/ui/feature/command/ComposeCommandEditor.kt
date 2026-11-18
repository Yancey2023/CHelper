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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.domain.command.CommandProblem
import yancey.chelper.resources.Res
import yancey.chelper.resources.layout_completion_command_hint
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.text.highlightCommand
import yancey.chelper.ui.designsystem.widget.Text

data class CommandErrorSelection(val start: Int, val end: Int, val request: Long)

/** Transparent foreground for the native layout when the editor paints its glyphs separately. */
val CommandGlyphMaskColor = Color(0x00434850)

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
    var cacheableText by remember { mutableStateOf<String?>(null) }
    var paintedLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val highlightViewport = remember { CommandHighlightViewport() }
    val viewportMeasurer = rememberTextMeasurer(cacheSize = 1)
    val glyphMeasurer = rememberTextMeasurer(cacheSize = 1)
    val currentEditorMode = rememberUpdatedState(editorMode)
    // Keep Foundation responsible for caret, selection and composing text. Its composing
    // annotations are copied into the colored glyph layout, rather than redrawn by hand.
    val useGlyphCache =
        !editorMode &&
            tokens != null &&
            command.text.length > COMMAND_HIGHLIGHT_FULL_LENGTH &&
            cacheableText == command.text.toString()
    val currentUseGlyphCache = rememberUpdatedState(useGlyphCache)
    LaunchedEffect(command, scroll, viewportMeasurer) {
        snapshotFlow {
            val bucketWidth = (viewport.width / 4).coerceAtLeast(1)
            CommandHighlightQuery(
                layout = textLayout?.invoke(),
                viewport = viewport,
                scroll = scroll.value / bucketWidth * bucketWidth,
                editorMode = currentEditorMode.value,
                enabled = currentTokens.value != null,
                text = command.text,
            )
        }
            .collect { query ->
                val layout = query.layout
                highlightRange =
                    if (
                        query.editorMode ||
                            query.length <= COMMAND_HIGHLIGHT_FULL_LENGTH ||
                            !query.enabled ||
                            layout == null ||
                            query.viewport.width == 0
                    ) {
                        cacheableText = null
                        TextRange.Zero
                    } else {
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
                            if (input.layoutDirection == LayoutDirection.Rtl)
                                layout.size.width - query.viewport.width - query.scroll.toFloat()
                            else query.scroll.toFloat()
                        // Query in quarter-viewport steps, with half a viewport of overscan. The
                        // visible glyphs remain colored between queries, even during fast
                        // scrolling.
                        val visible =
                            highlightViewport.range(
                                hitLayout,
                                left - query.viewport.width / 2f,
                                left + query.viewport.width * 1.5f,
                            )
                        // Selection and composing spans may change the snapshot wrapper
                        // while the underlying command and its direction remain the same.
                        cacheableText =
                            if (
                                highlightViewport.isLeftToRight &&
                                    input.text.text == query.text.toString()
                            )
                                input.text.text
                            else null
                        commandHighlightRange(query.length, visible, highlightRange)
                    }
            }
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
            if (currentUseGlyphCache.value) return@OutputTransformation
            // Android's TextLine scans the line's CharacterStyles for each color run.
            // Bound that work for a long horizontal line, including its first layout.
            val range =
                if (currentEditorMode.value || length <= COMMAND_HIGHLIGHT_FULL_LENGTH)
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
            modifier
                .padding(if (editorMode) 8.dp else 0.dp)
                .focusRequester(focus)
                .onSizeChanged {
                    viewport = it
                }
                .semantics {
                    getTextLayoutResult { results ->
                        val layout =
                            if (currentUseGlyphCache.value) paintedLayout else textLayout?.invoke()
                        if (layout != null) results.add(layout)
                        layout != null
                    }
                },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
        lineLimits =
            if (editorMode) TextFieldLineLimits.MultiLine() else TextFieldLineLimits.SingleLine,
        inputTransformation = inputTransformation,
        textStyle =
            TextStyle(
                color = if (useGlyphCache) CommandGlyphMaskColor else colors.textMain,
                fontSize = 16.sp,
            ),
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
                    Modifier.testTag(
                            when {
                                useGlyphCache -> "command-glyph-cache"
                                command.composition != null -> "command-glyph-composition"
                                !command.selection.collapsed -> "command-glyph-selection"
                                else -> "command-glyph-native"
                            }
                        )
                        .drawWithCache {
                            val layout = textLayout?.invoke()
                            val stroke = 1.dp.toPx()
                            val underlines =
                                if (layout == null) emptyList()
                                else
                                    commandUnderlines(
                                        layout,
                                        currentErrors.value,
                                        stroke,
                                        2.dp.toPx(),
                                    )
                            val glyphLayer =
                                if (!currentUseGlyphCache.value || layout == null) null
                                else {
                                    val input = layout.layoutInput
                                    val range = highlightRange
                                    val styled =
                                        AnnotatedString.Builder(
                                                highlightCommand(
                                                    input.text.text,
                                                    currentTokens.value,
                                                    currentDark.value,
                                                    range.start,
                                                    range.end,
                                                )
                                            )
                                            .apply {
                                                input.text.spanStyles.forEach {
                                                    addStyle(it.item, it.start, it.end)
                                                }
                                                input.text.paragraphStyles.forEach {
                                                    addStyle(it.item, it.start, it.end)
                                                }
                                            }
                                            .toAnnotatedString()
                                    val colored =
                                        glyphMeasurer.measure(
                                            text = styled,
                                            style = input.style.copy(color = colors.textMain),
                                            overflow = input.overflow,
                                            softWrap = input.softWrap,
                                            maxLines = input.maxLines,
                                            constraints = input.constraints,
                                            layoutDirection = input.layoutDirection,
                                            density = input.density,
                                            fontFamilyResolver = input.fontFamilyResolver,
                                        )
                                    paintedLayout = colored
                                    val start = range.start.coerceIn(0, styled.length)
                                    val end = range.end.coerceIn(start, styled.length)
                                    val origin =
                                        floor(layout.getHorizontalPosition(start, true) - 2f)
                                    val right = ceil(layout.getHorizontalPosition(end, true) + 2f)
                                    val layer =
                                        obtainGraphicsLayer().apply {
                                            // Clip at the host viewport when drawing. A layer's
                                            // default
                                            // outline can be empty before its first recording.
                                            translationX = origin
                                        }
                                    CommandGlyphLayer(
                                        layer,
                                        colored,
                                        origin,
                                        IntSize(
                                            (right - origin).toInt().coerceAtLeast(1),
                                            colored.size.height,
                                        ),
                                    )
                                }
                            var glyphsRecorded = false
                            onDrawWithContent {
                                // Selection backgrounds belong below the syntax glyphs; the
                                // collapsed
                                // caret belongs above them. Foundation still draws both
                                // decorations.
                                val selected = !command.selection.collapsed
                                if (selected) drawContent()
                                if (glyphLayer != null) {
                                    if (!glyphsRecorded) {
                                        glyphLayer.layer.record(size = glyphLayer.size) {
                                            drawText(
                                                glyphLayer.layout,
                                                topLeft = Offset(-glyphLayer.origin, 0f),
                                            )
                                        }
                                        glyphsRecorded = true
                                    }
                                    val scrollX =
                                        if (layoutDirection == LayoutDirection.Rtl)
                                            size.width - layout!!.size.width + scroll.value
                                        else -scroll.value.toFloat()
                                    clipRect {
                                        translate(scrollX, 0f) { drawLayer(glyphLayer.layer) }
                                    }
                                }
                                if (!selected) drawContent()
                                if (layout == null) return@onDrawWithContent
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
                                            underlines,
                                            colors.textErrorReason,
                                            stroke,
                                        )
                                    }
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

private data class CommandGlyphLayer(
    val layer: GraphicsLayer,
    val layout: TextLayoutResult,
    val origin: Float,
    val size: IntSize,
)

private const val COMMAND_HIGHLIGHT_FULL_LENGTH = 1024

private data class CommandHighlightQuery(
    val layout: TextLayoutResult?,
    val viewport: IntSize,
    val scroll: Int,
    val editorMode: Boolean,
    val enabled: Boolean,
    val text: CharSequence,
) {
    val length: Int
        get() = text.length
}

/** Small color windows with hysteresis keep Android's span drawing within the frame budget. */
internal fun commandHighlightRange(
    length: Int,
    visible: TextRange,
    retained: TextRange = TextRange.Zero,
): TextRange {
    val start = (visible.min / COMMAND_HIGHLIGHT_CHUNK_SIZE - 1).coerceAtLeast(0)
    val end = visible.max / COMMAND_HIGHLIGHT_CHUNK_SIZE + 2
    val candidate =
        TextRange(
            (start * COMMAND_HIGHLIGHT_CHUNK_SIZE).coerceIn(0, length),
            (end.toLong() * COMMAND_HIGHLIGHT_CHUNK_SIZE).coerceIn(0, length.toLong()).toInt(),
        )
    val margin = COMMAND_HIGHLIGHT_CHUNK_SIZE / 2
    val retainedStart = if (retained.start == 0) 0 else retained.start + margin
    val retainedEnd = if (retained.end == length) length else retained.end - margin
    return if (
        !retained.collapsed &&
            retained.end <= length &&
            retained.length <= candidate.length + COMMAND_HIGHLIGHT_CHUNK_SIZE * 2 &&
            visible.min >= retainedStart &&
            visible.max <= retainedEnd
    )
        retained
    else candidate
}

private data class CommandUnderline(val path: Path, val lines: List<Pair<Offset, Offset>>)

/** Build once per layout/error update, rather than hit-testing styled text on every frame. */
private fun commandUnderlines(
    layout: TextLayoutResult,
    errors: List<CommandProblem>,
    stroke: Float,
    baselineOffset: Float,
): List<CommandUnderline> {
    val text = layout.layoutInput.text
    return errors.mapNotNull { problem ->
        val range =
            commandUnderlineRange(text, problem.start, problem.end) ?: return@mapNotNull null
        // The selection path handles wrapping and disjoint bidirectional text runs.
        CommandUnderline(
            layout.getPathForRange(range.start, range.end),
            buildList {
                val firstLine = layout.getLineForOffset(range.start)
                val lastLine = layout.getLineForOffset(range.end - 1)
                for (line in firstLine..lastLine) {
                    val y =
                        minOf(
                            layout.getLineBaseline(line) + baselineOffset,
                            layout.getLineBottom(line) - stroke / 2,
                        )
                    add(Offset(layout.getLineLeft(line), y) to Offset(layout.getLineRight(line), y))
                }
            },
        )
    }
}

/** Draw decorations separately: SpanStyle's foreground also colors the glyphs. */
private fun DrawScope.drawCommandUnderlines(
    underlines: List<CommandUnderline>,
    color: Color,
    stroke: Float,
) {
    underlines.forEach { underline ->
        clipPath(underline.path) {
            underline.lines.forEach { (start, end) ->
                drawLine(color, start, end, strokeWidth = stroke)
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
