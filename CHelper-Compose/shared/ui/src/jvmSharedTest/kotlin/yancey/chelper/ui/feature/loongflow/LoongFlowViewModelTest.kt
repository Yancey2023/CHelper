package yancey.chelper.ui.feature.loongflow

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.domain.library.BlockType
import yancey.chelper.domain.library.ChainItem
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.parseMCDStructure

class LoongFlowViewModelTest {
    @Test
    fun selectedCommandsCopyInOrderAndCanResumeFromCompletion() {
        val copied = mutableListOf<String>()
        val model = LoongFlowViewModel(copied::add, { null })
        model.initImport(LibraryFunction(name = "test", content = "say one\nsay skip\nsay three"))
        model.toggleSelection(1)
        assertTrue(model.startImportCopy())
        assertTrue(model.nextCommand())
        assertFalse(model.nextCommand())
        assertTrue(model.isImportComplete)
        model.prevCommand()
        assertFalse(model.isImportComplete)
        model.prevCommand()
        assertEquals(listOf("say one", "say three", "say three", "say one"), copied)
        assertEquals("1/2", importMiniState(model, false).progress)
    }

    @Test
    fun deletingAnEarlierBlockKeepsTheEditingTargetAndExportUsesInjectedServices() = runBlocking {
        val copied = mutableListOf<String>()
        val shared = mutableListOf<String>()
        val model = LoongFlowViewModel(copied::add, { "say clipboard" }, shared::add)
        model.initExport()
        model.chainName = "共享导出"
        model.authorName = "tester"
        model.startRecording()
        listOf("say first", "say middle", "say last").forEach {
            model.currentBlockInput = it
            model.saveCurrentBlock()
        }
        model.editBlock(2)
        model.deleteBlock(0)
        assertEquals(1, model.editingBlockIndex)
        model.pasteFromClipboard()
        model.currentBlockType = BlockType.IMPULSE
        model.currentTickDelay = 4
        model.saveCurrentBlock()
        model.finishRecording()
        model.copyExportText()
        model.shareExportText()
        val blocks =
            parseMCDStructure(model.exportMcdText)
                .chains
                .single()
                .items
                .filterIsInstance<ChainItem.Block>()
                .map { it.block }
        assertEquals(listOf("say middle", "say clipboard"), blocks.map { it.command })
        assertEquals(4, blocks.last().tickDelay)
        assertEquals(BlockType.IMPULSE, blocks.last().type)
        assertEquals(listOf(model.exportMcdText), copied)
        assertEquals(copied, shared)
    }

    @Test
    fun clipboardCancellationPropagatesAndReadFailurePreservesInput() = runBlocking {
        val unavailable = LoongFlowViewModel({}, { error("clipboard busy") })
        unavailable.currentBlockInput = "keep input"
        unavailable.pasteFromClipboard()
        assertEquals("keep input", unavailable.currentBlockInput)
        assertNotNull(unavailable.toastMessage)
        val cancelled = LoongFlowViewModel({}, { throw CancellationException("closed") })
        try {
            cancelled.pasteFromClipboard()
            fail("Cancellation should propagate")
        } catch (_: CancellationException) {}
    }
}
