package yancey.chelper.ui.feature.command

import androidx.compose.ui.text.TextRange
import kotlin.test.*

class CommandEditorRangesTest {
    @Test
    fun longCommandHighlightingUsesStableChunksWithOverscan() {
        assertEquals(TextRange(0, 64), commandHighlightRange(10000, TextRange(0, 30)))
        assertEquals(TextRange(0, 64), commandHighlightRange(10000, TextRange(10, 20)))
        assertEquals(TextRange(5152, 5280), commandHighlightRange(10000, TextRange(5200, 5240)))
        assertEquals(TextRange(9952, 10000), commandHighlightRange(10000, TextRange(10000)))
        assertEquals(TextRange(0, 0), commandHighlightRange(0, TextRange.Zero))
    }

    @Test
    fun highlightWindowDoesNotOscillateAtChunkEdgesAndShrinksAfterWideBidiRanges() {
        val initial = commandHighlightRange(10000, TextRange(0, 30))
        assertEquals(initial, commandHighlightRange(10000, TextRange(31, 33), initial))
        val moved = commandHighlightRange(10000, TextRange(40, 50), initial)
        assertEquals(TextRange(0, 96), moved)
        assertEquals(moved, commandHighlightRange(10000, TextRange(31, 33), moved))
        assertEquals(
            TextRange(5152, 5280),
            commandHighlightRange(10000, TextRange(5200, 5240), moved),
        )
        assertEquals(initial, commandHighlightRange(10000, TextRange(0, 30), TextRange(0, 10000)))
        assertEquals(
            TextRange(0, 64),
            commandHighlightRange(64, TextRange(0, 30), TextRange(0, 10000)),
        )
    }

    @Test
    fun invalidErrorNavigationPreservesTheCurrentSelection() {
        assertNull(validCommandSelection(6, -1, 4))
        assertNull(validCommandSelection(6, 2, 100))
        assertNull(validCommandSelection(6, 4, 2))
        assertEquals(TextRange(4, 6), validCommandSelection(6, 4, 6))
    }

    @Test
    fun emptyErrorsAtEndAndInsideAnEmojiUnderlineTheWholeCodePoint() {
        val command = "say 🙂"
        assertEquals(TextRange(4, 6), commandUnderlineRange(command, 6, 6))
        assertEquals(TextRange(4, 6), commandUnderlineRange(command, 5, 5))
        assertEquals(TextRange(4, 6), commandUnderlineRange(command, 4, 4))
        assertNull(commandUnderlineRange("", 0, 0))
    }

    @Test
    fun multilineAndOutOfBoundsErrorsHaveSafeDisplayRanges() {
        assertEquals(TextRange(0, 7), commandUnderlineRange("say\n你好!", -1, 99))
        assertEquals(TextRange(5, 6), commandUnderlineRange("say hi", 6, 6))
    }
}
