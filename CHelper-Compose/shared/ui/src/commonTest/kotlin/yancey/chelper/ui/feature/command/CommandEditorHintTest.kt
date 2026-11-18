package yancey.chelper.ui.feature.command

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandEditorHintTest {

    @Test
    fun `18 个 node 不显示提示`() {
        assertFalse(
            shouldShowCommandEditorHint(18, isCommandEditorMode = false, isScreenVisible = true)
        )
    }

    @Test
    fun `第 19 个 node 显示提示`() {
        assertTrue(
            shouldShowCommandEditorHint(19, isCommandEditorMode = false, isScreenVisible = true)
        )
    }

    @Test
    fun `已进入编辑模式或页面不可见时不显示`() {
        assertFalse(
            shouldShowCommandEditorHint(19, isCommandEditorMode = true, isScreenVisible = true)
        )
        assertFalse(
            shouldShowCommandEditorHint(19, isCommandEditorMode = false, isScreenVisible = false)
        )
    }
}
