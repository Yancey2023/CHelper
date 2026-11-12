package yancey.chelper.ui.feature.library.upload

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryUploadViewModelTest {
    @Test
    fun `生成 MCD 时 note 换行不会截断元数据头`() {
        val viewModel =
            LibraryUploadViewModel().apply {
                name.setTextAndPlaceCursorAtEnd("示例")
                version.setTextAndPlaceCursorAtEnd("1.0.0")
                description.setTextAndPlaceCursorAtEnd("第一行\n第二行")
                commands.setTextAndPlaceCursorAtEnd("> H\nsay hello")
                useV2 = true
            }

        val source = viewModel.buildFullMCD()

        assertTrue(source.contains("@note=第一行 第二行\n@mcd_version=2"))
        assertFalse(source.contains("@note=第一行\n第二行"))
    }
}
