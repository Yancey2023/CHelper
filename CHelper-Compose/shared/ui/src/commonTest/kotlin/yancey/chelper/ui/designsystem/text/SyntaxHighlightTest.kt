package yancey.chelper.ui.designsystem.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntaxHighlightTest {
    @Test
    fun windowKeepsTheFullCommandButOnlyStylesRequestedTokens() {
        val command = "say 你好🙂 @e[type=pig] ".repeat(1000)
        val tokens = IntArray(command.length) { it % 8 }
        for (dark in listOf(false, true)) {
            val full = highlightCommand(command, tokens, dark)
            val window = highlightCommand(command, tokens, dark, 5000, 5500)
            assertEquals(command, window.text)
            assertEquals(full.subSequence(5000, 5500), window.subSequence(5000, 5500))
            assertTrue(window.spanStyles.size <= 500)
            assertTrue(window.spanStyles.all { it.start >= 5000 && it.end <= 5500 })
        }
    }

    @Test
    fun windowSafelyClampsToAvailableTokensAndSupportsDisabledHighlighting() {
        assertTrue(highlightCommand("say hi", null, false, 4, 6).spanStyles.isEmpty())
        assertTrue(highlightCommand("say hi", intArrayOf(7, 7), false, 4, 6).spanStyles.isEmpty())
        assertEquals(
            highlightCommand("say hi", intArrayOf(7, 7), false),
            highlightCommand("say hi", intArrayOf(7, 7), false, -5, 100),
        )
    }
}
