package yancey.chelper.ui.feature.command

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.LayoutDirection

/**
 * Hit-test the edges rather than asking for every character's bounds: getRangeForRect's
 * compatibility implementation measures the entire line on older Android versions.
 */
internal class CommandHighlightViewport {
    private var text: String? = null
    private var direction: LayoutDirection? = null
    private var bidiBoundaries = emptyList<Int>()

    fun range(layout: TextLayoutResult, left: Float, right: Float): TextRange {
        val input = layout.layoutInput.text.text
        if (input.isEmpty()) return TextRange.Zero
        if (text != input || direction != layout.layoutInput.layoutDirection) {
            text = input
            direction = layout.layoutInput.layoutDirection
            bidiBoundaries = buildList {
                var previous = layout.getBidiRunDirection(0)
                for (offset in 1 until input.length) {
                    val direction = layout.getBidiRunDirection(offset)
                    if (direction != previous) {
                        add(offset - 1)
                        add(offset)
                        previous = direction
                    }
                }
            }
        }
        val y = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2
        val lineLeft = layout.getLineLeft(0)
        val lineRight = layout.getLineRight(0)
        val inset = minOf(0.5f, (lineRight - lineLeft) / 2)
        // Some backends return the logical end for points outside an RTL line, including on
        // its leading edge. Keep overscan hit tests inside the actual glyph bounds.
        val first =
            layout.getOffsetForPosition(
                Offset(left.coerceIn(lineLeft + inset, lineRight - inset), y)
            )
        val last =
            layout.getOffsetForPosition(
                Offset(right.coerceIn(lineLeft + inset, lineRight - inset), y)
            )
        var start = minOf(first, last)
        var end = maxOf(first, last)
        // A whole bidi run can lie between the viewport edges but outside their logical range.
        // Include its visible boundary characters without measuring all off-screen characters.
        bidiBoundaries.forEach { offset ->
            val bounds = layout.getBoundingBox(offset)
            if (bounds.right >= left && bounds.left <= right) {
                start = minOf(start, offset)
                end = maxOf(end, offset + 1)
            }
        }
        return TextRange(start, end)
    }
}
