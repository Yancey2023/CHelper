package yancey.chelper.android.widget

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommandEditTextInstrumentedTest {

    @Test
    fun validErrorRangeSelectsMatchingUtf16Range() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var editText: CommandEditText
        var result = false

        instrumentation.runOnMainSync {
            editText = CommandEditText(instrumentation.targetContext)
            editText.setText("say 你好")
            result = editText.focusErrorRange(4, 6)
        }

        assertTrue(result)
        assertEquals(4, editText.selectionStart)
        assertEquals(6, editText.selectionEnd)
    }

    @Test
    fun invalidErrorRangeLeavesSelectionUnchanged() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var editText: CommandEditText
        var result = false

        instrumentation.runOnMainSync {
            editText = CommandEditText(instrumentation.targetContext)
            editText.setText("say hello")
            editText.setSelection(1)
            result = editText.focusErrorRange(-1, 4) || editText.focusErrorRange(2, 100)
        }

        assertFalse(result)
        assertEquals(1, editText.selectionStart)
        assertEquals(1, editText.selectionEnd)
    }

    @Test
    fun editorModeWrapsLongCommandWithoutChangingText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var editText: CommandEditText
        var editorLineCount = 0
        var singleLineCount = 0
        val command =
            "execute as @a at @s positioned ~ ~ ~ run tellraw @s {\"rawtext\":[{\"text\":\"hello world\"}]}"

        instrumentation.runOnMainSync {
            editText = CommandEditText(instrumentation.targetContext)
            editText.setText(command)
            editText.setEditorMode(true)
            layoutEditText(editText)
            editorLineCount = editText.layout.lineCount

            editText.setEditorMode(false)
            layoutEditText(editText)
            singleLineCount = editText.layout.lineCount
        }

        assertTrue("编辑器模式应自动折行", editorLineCount > 1)
        assertEquals(1, singleLineCount)
        assertEquals(command, editText.text.toString())
    }

    private fun layoutEditText(editText: CommandEditText) {
        val width = 240
        val height = 400
        editText.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        editText.layout(0, 0, width, height)
    }
}
