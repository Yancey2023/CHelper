package yancey.chelper.ui.feature.library.upload

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import yancey.chelper.data.remote.NetworkServices

class LibraryUploadViewModelTest {
    @Test
    fun `生成 MCD 时 note 换行不会截断元数据头`() =
        NetworkServices({ MockEngine { error("Unexpected request") } }).use { network ->
            val viewModel =
                LibraryUploadViewModel(network).apply {
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
