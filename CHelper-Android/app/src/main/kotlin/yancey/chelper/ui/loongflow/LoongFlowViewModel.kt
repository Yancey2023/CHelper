/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * LoongFlow（游龙）悬浮窗的状态管理。
 * 统一管理导入模式（MCD→剪贴板）和导出模式（剪贴板→MCD）的双模式状态机。
 */

package yancey.chelper.ui.loongflow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.library.mcd.BlockType
import yancey.chelper.ui.library.mcd.ChainItem
import yancey.chelper.ui.library.mcd.MCDBlock
import yancey.chelper.ui.library.mcd.parseMCD

/**
 * 游龙两种工作模式
 */
enum class LoongFlowMode {
    IMPORT,  // MCD → 剪贴板 → MC
    EXPORT   // MC → 剪贴板 → MCD
}

class LoongFlowViewModel : ViewModel() {
    // ═══════════════════════════════════
    //  共享状态
    // ═══════════════════════════════════
    var mode by mutableStateOf(LoongFlowMode.IMPORT)
    var toastMessage by mutableStateOf<String?>(null)

    // ═══════════════════════════════════
    //  导入模式状态 (MCD → 剪贴板)
    // ═══════════════════════════════════
    data class ImportCommandCtx(
        val command: String,
        val chainName: String?,
        val blockData: yancey.chelper.ui.library.mcd.MCDBlock?
    ) {
        val blockTypeName: String get() = when (blockData?.type) {
            yancey.chelper.ui.library.mcd.BlockType.IMPULSE -> "脉冲"
            yancey.chelper.ui.library.mcd.BlockType.CHAIN -> "连锁"
            yancey.chelper.ui.library.mcd.BlockType.REPEAT -> "循环"
            yancey.chelper.ui.library.mcd.BlockType.CHAT -> "手动输入"
            null -> "纯命令"
        }
    }

    var importStep by mutableIntStateOf(0)       // 0=选择命令, 1=逐条复制
    var allCommands by mutableStateOf<List<ImportCommandCtx>>(emptyList())
    var selectedIndices by mutableStateOf<Set<Int>>(emptySet())
    var currentCopyIndex by mutableIntStateOf(0)
    var isImportComplete by mutableStateOf(false)
    var libraryName by mutableStateOf("")

    // ═══════════════════════════════════
    //  导出模式状态 (剪贴板 → MCD)
    // ═══════════════════════════════════
    var exportStep by mutableIntStateOf(0)       // 0=链配置, 1=逐条录入, 2=预览导出
    var chainName by mutableStateOf("未命名命令链")
    var authorName by mutableStateOf("")

    /**
     * 已录入的命令方块列表，每个元素是完整的 MCDBlock。
     * 用 mutableStateOf(list) 包装，修改时替换整个列表以触发 recomposition。
     */
    var collectedBlocks by mutableStateOf<List<MCDBlock>>(emptyList())

    // 当前正在编辑的方块字段
    var currentBlockInput by mutableStateOf("")
    var currentBlockType by mutableStateOf(BlockType.CHAIN)
    var currentConditional by mutableStateOf(false)
    var currentNeedsRedstone by mutableStateOf(false)
    var currentTickDelay by mutableIntStateOf(0)

    /** -1 = 正在新建, >=0 = 正在编辑索引 index 处的已录入方块 */
    var editingBlockIndex by mutableIntStateOf(-1)

    var exportMcdText by mutableStateOf("")

    // ═══════════════════════════════════
    //  导入模式操作
    // ═══════════════════════════════════

    /**
     * 初始化导入模式，解析 MCD 内容并提取可执行命令列表。
     */
    fun initImport(library: LibraryFunction) {
        mode = LoongFlowMode.IMPORT
        importStep = 0
        isImportComplete = false
        currentCopyIndex = 0
        libraryName = library.name ?: "命令库"

        val parsed = parseMCD(library.content)
        allCommands = parsed.chains.flatMap { chain ->
            chain.items.mapNotNull { item ->
                when (item) {
                    is ChainItem.Block -> {
                        item.block.command.takeIf { it.isNotEmpty() }?.let { cmd ->
                            ImportCommandCtx(cmd, chain.name, item.block)
                        }
                    }
                    is ChainItem.RawCommand -> {
                        item.command.takeIf { it.isNotEmpty() }?.let { cmd ->
                            ImportCommandCtx(cmd, chain.name, null)
                        }
                    }
                    is ChainItem.Comment -> null
                }
            }
        }
        selectedIndices = allCommands.indices.toSet()
    }

    /** 获取用户勾选后的有效命令上下文列表 */
    val selectedCommands: List<ImportCommandCtx>
        get() = allCommands.filterIndexed { index, _ -> index in selectedIndices }

    fun toggleSelection(index: Int) {
        selectedIndices = if (index in selectedIndices) {
            selectedIndices - index
        } else {
            selectedIndices + index
        }
    }

    fun selectAll() {
        selectedIndices = allCommands.indices.toSet()
    }

    fun deselectAll() {
        selectedIndices = emptySet()
    }

    /** 进入逐条复制步骤 */
    fun startImportCopy() {
        if (selectedCommands.isEmpty()) {
            toastMessage = "请至少选择一条命令"
            return
        }
        importStep = 1
        currentCopyIndex = 0
        isImportComplete = false
    }

    /** 复制当前命令到剪贴板 */
    fun copyCurrentCommand(context: Context) {
        val cmds = selectedCommands
        if (currentCopyIndex < cmds.size) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("command", cmds[currentCopyIndex].command))
            toastMessage = "已复制 (${currentCopyIndex + 1}/${cmds.size})"
        }
    }

    /** 前进到下一条命令，返回 true 表示还有下一条 */
    fun nextCommand(context: Context): Boolean {
        val cmds = selectedCommands
        if (currentCopyIndex < cmds.size - 1) {
            currentCopyIndex++
            copyCurrentCommand(context)
            return true
        } else {
            isImportComplete = true
            return false
        }
    }

    /** 回退到上一条 */
    fun prevCommand(context: Context) {
        if (currentCopyIndex > 0) {
            currentCopyIndex--
            isImportComplete = false
            copyCurrentCommand(context)
        }
    }

    // ═══════════════════════════════════
    //  导出模式操作
    // ═══════════════════════════════════

    /**
     * 初始化导出模式。
     * 不依赖任何现有库——玩家从零开始录入命令方块。
     */
    fun initExport() {
        mode = LoongFlowMode.EXPORT
        exportStep = 0
        chainName = "未命名命令链"
        authorName = ""
        collectedBlocks = emptyList()
        resetCurrentBlock()
        exportMcdText = ""
    }

    /** 进入逐条录入步骤 */
    fun startRecording() {
        exportStep = 1
        resetCurrentBlock()
    }

    /** 从剪贴板读取内容填入当前方块的命令输入框 */
    fun pasteFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                currentBlockInput = text
                toastMessage = "已粘贴"
            } else {
                toastMessage = "剪贴板为空"
            }
        } else {
            toastMessage = "剪贴板为空"
        }
    }

    /**
     * 保存当前正在编辑的方块到录入列表。
     * 如果 editingBlockIndex >= 0 则更新已有方块，否则追加为新方块。
     */
    fun saveCurrentBlock() {
        if (currentBlockInput.isBlank()) {
            toastMessage = "请输入命令内容"
            return
        }

        val block = MCDBlock(
            type = currentBlockType,
            conditional = currentConditional,
            alwaysActive = !currentNeedsRedstone,
            needsRedstone = currentNeedsRedstone,
            tickDelay = currentTickDelay,
            command = currentBlockInput.trim()
        )

        collectedBlocks = if (editingBlockIndex >= 0 && editingBlockIndex < collectedBlocks.size) {
            collectedBlocks.toMutableList().apply { set(editingBlockIndex, block) }
        } else {
            collectedBlocks + block
        }

        resetCurrentBlock()
        toastMessage = "方块已保存 (共 ${collectedBlocks.size} 条)"
    }

    /** 删除指定索引的已录入方块 */
    fun deleteBlock(index: Int) {
        if (index in collectedBlocks.indices) {
            collectedBlocks = collectedBlocks.toMutableList().apply { removeAt(index) }
            // 如果正在编辑被删除的方块，重置为新建模式
            if (editingBlockIndex == index) resetCurrentBlock()
            toastMessage = "已删除"
        }
    }

    /** 跳转编辑已录入的某个方块 */
    fun editBlock(index: Int) {
        if (index in collectedBlocks.indices) {
            val block = collectedBlocks[index]
            editingBlockIndex = index
            currentBlockInput = block.command
            currentBlockType = block.type
            currentConditional = block.conditional
            currentNeedsRedstone = block.needsRedstone
            currentTickDelay = block.tickDelay
        }
    }

    /** 完成录入，生成 MCD v2 并进入预览步骤 */
    fun finishRecording() {
        if (collectedBlocks.isEmpty()) {
            toastMessage = "请至少录入一条命令"
            return
        }
        val meta = buildMap {
            if (authorName.isNotBlank()) put("author", authorName)
        }
        exportMcdText = serializeBlocksToMCDv2(chainName, collectedBlocks, meta)
        exportStep = 2
    }

    /** 复制导出的 MCD 文本到剪贴板 */
    fun copyExportText(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MCD", exportMcdText))
        toastMessage = "已复制 MCD 全文"
    }

    /** 通过系统分享 Intent 分享 MCD 文本 */
    fun shareExportText(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, exportMcdText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "分享 MCD").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** 重置当前编辑方块到默认空白状态 */
    private fun resetCurrentBlock() {
        editingBlockIndex = -1
        currentBlockInput = ""
        currentBlockType = BlockType.CHAIN
        currentConditional = false
        currentNeedsRedstone = false
        currentTickDelay = 0
    }
}
