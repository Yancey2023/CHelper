package yancey.chelper.ui.feature.command

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandTextPositionsTest {
    @Test
    fun cachedSearchEnclosesVariableWidthAndZeroWidthCharacters() {
        // Repeated advances cover surrogate halves, combining marks and ligature clusters.
        val advances = floatArrayOf(0f, 4f, 13f, 13f, 13f, 21f, 23f, 35f)
        val positions = CommandTextPositions(advances.lastIndex) { advances[it] }
        for (left in -2..36) for (right in left..36) {
            val first =
                advances.indexOfFirst { it >= left }.let { if (it < 0) advances.lastIndex else it }
            val last =
                advances.indexOfFirst { it > right }.let { if (it < 0) advances.lastIndex else it }
            val expected = TextRange((first - 1).coerceAtLeast(0), last)
            assertEquals(expected, positions.range(left.toFloat(), right.toFloat()))
            assertEquals(expected, positions.range(right.toFloat(), left.toFloat()))
        }
    }

    @Test
    fun nearbyAndRepeatedWindowsReuseExpensiveNativeAdvances() {
        var calls = 0
        val positions =
            CommandTextPositions(100000) {
                calls++
                it * 7.5f
            }
        val first = positions.range(30000f, 31000f)
        val afterFirst = calls
        assertEquals(first, positions.range(30000f, 31000f))
        assertEquals(afterFirst, calls)
        positions.range(30010f, 31010f)
        assertTrue(calls - afterFirst < afterFirst / 2)
    }

    @Test
    fun chunkedSearchCoversTheExactRangeWithFewerNativeQueries() {
        val length = 4927
        val advances = FloatArray(length + 1)
        for (offset in 1..length) {
            advances[offset] = advances[offset - 1] + if (offset % 7 == 0) 0f else 5f + offset % 9
        }
        var exactCalls = 0
        var chunkedCalls = 0
        val exact =
            CommandTextPositions(length) {
                exactCalls++
                advances[it]
            }
        val chunked =
            CommandTextPositions(length, step = 32) {
                chunkedCalls++
                advances[it]
            }
        for (left in 0..advances.last().toInt() + 300 step 137) {
            val right = left + 1200
            val exactRange = exact.range(left.toFloat(), right.toFloat())
            val chunkedRange = chunked.range(left.toFloat(), right.toFloat())
            assertTrue(chunkedRange.start <= exactRange.start)
            assertTrue(chunkedRange.end >= exactRange.end)
            assertTrue(exactRange.start - chunkedRange.start < 32)
            assertTrue(chunkedRange.end - exactRange.end < 32)
        }
        assertTrue(chunkedCalls * 4 < exactCalls, "$chunkedCalls vs $exactCalls native queries")
    }

    @Test
    fun cacheRemainsBoundedDuringVeryLargeScrolls() {
        val visited = mutableSetOf<Int>()
        var calls = 0
        val positions =
            CommandTextPositions(1000000) {
                calls++
                visited += it
                it.toFloat()
            }
        for (offset in 0..1000000 step 100) positions.range(offset.toFloat(), offset + 50f)
        val before = calls
        positions.range(0f, 50f)
        assertEquals(before, calls)
        assertTrue(visited.size > 4096)
        val lateRange = positions.range(999900f, 999950f)
        val afterLateRange = calls
        assertEquals(lateRange, positions.range(999900f, 999950f))
        assertTrue(
            calls > afterLateRange,
            "Later lookups must not grow the full cache indefinitely",
        )
    }
}
