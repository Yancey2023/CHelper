package yancey.chelper.ui.feature.library.local

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import yancey.chelper.data.library.document.toFullLocalMcd
import yancey.chelper.data.local.library.LocalLibraryData
import yancey.chelper.data.local.library.LocalLibraryEditDraft
import yancey.chelper.data.local.library.LocalLibrarySerializer
import yancey.chelper.domain.library.ChainItem
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.parseMCDStructure

class LocalLibraryEditViewModelTest {
    @Test
    fun `新建模式初始干净且恢复草稿后变脏`() {
        val viewModel = LocalLibraryEditViewModel()
        viewModel.ensureEditingTarget(null, null, null)

        assertFalse(viewModel.isDirty)
        assertEquals("local-library:add", viewModel.draftKey)

        viewModel.restoreDraft(LocalLibraryEditDraft(name = "草稿", commands = "say hi"))

        assertTrue(viewModel.isDirty)
        assertEquals("草稿", viewModel.name.text.toString())
    }

    @Test
    fun `编辑模式使用稳定本地 ID 作为草稿键`() {
        val viewModel = LocalLibraryEditViewModel()
        val library =
            LibraryFunction(
                localEntryId = "stable",
                name = "库",
                content = "say hello",
                localIsV2 = false,
            )

        viewModel.ensureEditingTarget("stable", null, library)

        assertEquals("local-library:update:stable", viewModel.draftKey)
        assertFalse(viewModel.isDirty)
    }

    @Test
    fun `模板追加而不覆盖已有内容`() {
        val viewModel = LocalLibraryEditViewModel()
        viewModel.ensureEditingTarget(null, null, null)
        viewModel.commands.setTextAndPlaceCursorAtEnd("say first")

        viewModel.applyTemplate(LocalLibraryTemplate("模板", "say second"))

        assertEquals("say first\n\nsay second", viewModel.commands.text.toString())
    }

    @Test
    fun `校验拦截完整套壳和非法状态行`() {
        val wrapped =
            LocalLibraryEditViewModel().apply {
                ensureEditingTarget(null, null, null)
                name.setTextAndPlaceCursorAtEnd("库")
                commands.setTextAndPlaceCursorAtEnd("###Function###\nsay hi\n###End###")
            }
        assertTrue(wrapped.validationError()!!.contains("Function"))

        val invalidState =
            LocalLibraryEditViewModel().apply {
                ensureEditingTarget(null, null, null)
                name.setTextAndPlaceCursorAtEnd("库")
                commands.setTextAndPlaceCursorAtEnd("> BAD\nsay hi")
            }
        assertTrue(invalidState.validationError()!!.contains("V2 状态"))
    }

    @Test
    fun `合法 V2 和聊天文本通过校验`() {
        val viewModel =
            LocalLibraryEditViewModel().apply {
                ensureEditingTarget(null, null, null)
                name.setTextAndPlaceCursorAtEnd("库")
                commands.setTextAndPlaceCursorAtEnd("> H\n中文聊天\n> C?!t5\nsay hi")
            }

        assertNull(viewModel.validationError())
    }

    @Test
    fun `聊天文本可以使用元数据样式内容`() {
        val viewModel =
            LocalLibraryEditViewModel().apply {
                ensureEditingTarget(null, null, null)
                name.setTextAndPlaceCursorAtEnd("聊天")
                commands.setTextAndPlaceCursorAtEnd("> H\n@name=Steve")
            }

        assertNull(viewModel.validationError())
    }

    @Test
    fun `V2 状态行必须绑定正文`() {
        val dangling =
            LocalLibraryEditViewModel().apply {
                ensureEditingTarget(null, null, null)
                name.setTextAndPlaceCursorAtEnd("库")
                commands.setTextAndPlaceCursorAtEnd("> I")
            }
        val overwritten =
            LocalLibraryEditViewModel().apply {
                ensureEditingTarget(null, null, null)
                name.setTextAndPlaceCursorAtEnd("库")
                commands.setTextAndPlaceCursorAtEnd("> I\n> C\nsay hello")
            }

        assertTrue(dangling.validationError()!!.contains("没有对应"))
        assertTrue(overwritten.validationError()!!.contains("第 1 行"))
    }

    @Test
    fun `开启 V2 保存重载后开关和可视化结构保持 V2`() {
        val editor =
            LocalLibraryEditViewModel().apply {
                ensureEditingTarget(null, null, null)
                name.setTextAndPlaceCursorAtEnd("V2 回归")
                commands.setTextAndPlaceCursorAtEnd("> I\nsay hello")
                useV2 = true
            }

        val saved =
            editor.buildLocalLibrary(existingLibrary = null, requestedLocalEntryId = "stable")
        val persisted =
            Buffer()
                .also { output ->
                    runBlocking {
                        LocalLibrarySerializer.writeTo(LocalLibraryData(listOf(saved)), output)
                    }
                }
                .readByteArray()
        val reloaded = runBlocking {
            LocalLibrarySerializer.readFrom(Buffer().write(persisted)).functions.single()
        }
        val reopened =
            LocalLibraryEditViewModel().apply { ensureEditingTarget("stable", null, reloaded) }
        val rendered = parseMCDStructure(reloaded.toFullLocalMcd())

        assertTrue(reloaded.localIsV2 == true)
        assertTrue(reopened.useV2)
        assertTrue(rendered.isV2)
        assertTrue(rendered.chains.single().items.single() is ChainItem.Block)
    }

    @Test
    fun `保存已有库只替换编辑字段并保留云端元数据`() {
        val existing =
            LibraryFunction(
                id = 7,
                uuid = "cloud-uuid",
                localEntryId = "stable",
                name = "旧名称",
                content = "say old",
                localIsV2 = false,
                likeCount = 12,
            )
        val editor =
            LocalLibraryEditViewModel().apply {
                ensureEditingTarget("stable", null, existing)
                name.setTextAndPlaceCursorAtEnd("新名称")
                commands.setTextAndPlaceCursorAtEnd("> C\nsay new")
                useV2 = true
            }

        val saved = editor.buildLocalLibrary(existing, "stable")

        assertEquals(7, saved.id)
        assertEquals("cloud-uuid", saved.uuid)
        assertEquals(12, saved.likeCount)
        assertEquals("stable", saved.localEntryId)
        assertEquals("新名称", saved.name)
        assertEquals("> C\nsay new", saved.content)
        assertTrue(saved.localIsV2 == true)
        assertTrue(saved.localUnsynced)
    }
}
