package yancey.chelper.ui.feature.command

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection

internal const val COMMAND_HIGHLIGHT_CHUNK_SIZE = 32

/**
 * Hit-test the edges rather than asking for every character's bounds: getRangeForRect's
 * compatibility implementation measures the entire line on older Android versions.
 */
internal class CommandHighlightViewport {
    private var text: String? = null
    private var direction: LayoutDirection? = null
    private var bidiBoundaries = emptyList<Int>()
    private var positions: CommandTextPositions? = null
    private var positionLayout: MultiParagraph? = null
    val isLeftToRight: Boolean
        get() = positions != null && bidiBoundaries.isEmpty()

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
        if (
            bidiBoundaries.isEmpty() && layout.getBidiRunDirection(0) == ResolvedTextDirection.Ltr
        ) {
            // TextMeasurer returns a new TextLayoutResult wrapper even on a cache hit.
            // The underlying glyph layout is the stable geometry we are caching positions for.
            if (positionLayout !== layout.multiParagraph) {
                positionLayout = layout.multiParagraph
                positions =
                    // The color window already rounds outward to chunks. Probing within
                    // them would add native advance queries without reducing drawing work.
                    CommandTextPositions(input.length, step = COMMAND_HIGHLIGHT_CHUNK_SIZE) {
                        layout.getHorizontalPosition(it, true)
                    }
            }
            return positions!!.range(
                left.coerceIn(lineLeft + inset, lineRight - inset),
                right.coerceIn(lineLeft + inset, lineRight - inset),
            )
        }
        // Some backends return the logical end for points outside an RTL line, including on
        // its leading edge. Keep overscan hit tests inside the actual glyph bounds.
        positions = null
        positionLayout = null
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

/** Cache native advances and find conservative bounds on a monotonic, left-to-right line. */
internal class CommandTextPositions(
    private val length: Int,
    private val step: Int = 1,
    private val position: (Int) -> Float,
) {
    private val positions = mutableMapOf<Int, Float>()
    private val last = length / step + if (length % step == 0) 0 else 1

    fun range(left: Float, right: Float): TextRange {
        val start = boundary(minOf(left, right), false)
        val end = boundary(maxOf(left, right), true)
        return TextRange(((start - 1).coerceAtLeast(0)) * step, offsetAt(end))
    }

    // Use the exact text length for the final slot, including a partial chunk.
    private fun offsetAt(slot: Int): Int = if (slot == last) length else slot * step

    private fun boundary(x: Float, after: Boolean): Int {
        var low = 0
        var high = last
        while (low < high) {
            val mid = low + (high - low) / 2
            val offset =
                positions[mid]
                    ?: position(offsetAt(mid)).also {
                        // Keep memory bounded even when a command contains millions of characters.
                        if (positions.size < 4096) positions[mid] = it
                    }
            if (offset < x || (after && offset == x)) low = mid + 1 else high = mid
        }
        return low
    }
}
