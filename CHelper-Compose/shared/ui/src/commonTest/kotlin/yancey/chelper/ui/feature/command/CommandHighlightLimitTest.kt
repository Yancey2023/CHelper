package yancey.chelper.ui.feature.command

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import yancey.chelper.domain.settings.Settings
import yancey.chelper.domain.settings.withDefaults

class CommandHighlightLimitTest {
    @Test
    fun commandsPastTheOldLimitStillRequestNativeColorTokens() {
        val limit = Settings().withDefaults().syntaxHighlightMaxLength!!
        for (length in listOf(4000, 20000, 399999)) {
            val text = "x".repeat(length)
            val request = commandAnalysisRequest("test", text, length, true, false, limit)
            assertTrue(request.highlight)
            assertTrue(request.errors)
        }
        val text = "x".repeat(400000)
        assertFalse(commandAnalysisRequest("test", text, text.length, true, false, limit).highlight)
        assertFalse(commandAnalysisRequest("test", "say hi", 6, false, false, limit).highlight)
        assertFalse(commandAnalysisRequest("test", "say hi", 6, true, false, 0).highlight)
        assertTrue(commandAnalysisRequest("test", text, text.length, false, true, limit).errors)
    }
}
