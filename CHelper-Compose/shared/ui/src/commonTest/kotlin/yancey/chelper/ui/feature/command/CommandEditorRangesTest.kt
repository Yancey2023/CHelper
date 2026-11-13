package yancey.chelper.ui.feature.command

import androidx.compose.ui.text.TextRange
import kotlin.test.*

class CommandEditorRangesTest {
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
