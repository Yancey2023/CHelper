package yancey.chelper.ui.feature.command

import androidx.compose.ui.text.TextRange
import kotlin.test.*

class CommandEditorRangesTest {
    @Test
    fun longCommandHighlightingUsesStableChunksWithOverscan() {
        assertEquals(TextRange(0, 512), commandHighlightRange(10000, TextRange(0, 30)))
        assertEquals(TextRange(0, 512), commandHighlightRange(10000, TextRange(10, 40)))
        assertEquals(TextRange(4864, 5632), commandHighlightRange(10000, TextRange(5200, 5240)))
        assertEquals(TextRange(9728, 10000), commandHighlightRange(10000, TextRange(10000)))
        assertEquals(TextRange(0, 0), commandHighlightRange(0, TextRange.Zero))
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
