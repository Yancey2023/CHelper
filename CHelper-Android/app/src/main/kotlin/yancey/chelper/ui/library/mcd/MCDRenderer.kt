/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * MCD (Minecraft Command Data) 可视化渲染器。
 * 参考 Web 端 renderMCDToUI() 的解析逻辑，用 Compose 还原命令方块链 UI。
 * 支持 v1 (纯指令列表) 和 v2 (带状态行/链分隔符的结构化格式) 两种格式。
 */

package yancey.chelper.ui.library.mcd

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 数据模型：解析后的 MCD AST
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/** 元数据行，例如 @author = xxx */
data class MCDMeta(val key: String, val value: String)

/** 命令方块的类型标识 */
enum class BlockType(val label: String, val lightColor: Color, val darkColor: Color) {
    // 颜色参照 MC 命令方块的原版色调
    IMPULSE("脉冲", Color(0xFFFF9933), Color(0xFFCC7A29)),
    CHAIN("连锁", Color(0xFF4FC1A6), Color(0xFF3A9C83)),
    REPEAT("循环", Color(0xFF9966FF), Color(0xFF7A52CC)),
    CHAT("手动键入", Color(0xFF607D8B), Color(0xFF455A64))
}

/** v2 格式的命令方块，携带方块类型和状态信息 */
data class MCDBlock(
    val type: BlockType = BlockType.CHAIN,
    val conditional: Boolean = false,
    val alwaysActive: Boolean = true,
    val needsRedstone: Boolean = false,
    val tickDelay: Int = 0,
    val command: String = ""
)

/** 链中的一个元素：可能是注释、v1 原始指令、或 v2 命令方块 */
sealed class ChainItem {
    data class Comment(val text: String) : ChainItem()
    data class RawCommand(val command: String) : ChainItem()
    data class Block(val block: MCDBlock) : ChainItem()
}

/** 一条命令链 */
data class MCDChain(
    val name: String,
    val items: MutableList<ChainItem> = mutableListOf()
)

/** 解析后的完整 MCD 结构 */
data class ParsedMCD(
    val metaInfo: List<MCDMeta> = emptyList(),
    val rootComments: List<String> = emptyList(),
    val chains: List<MCDChain> = emptyList(),
    val isV2: Boolean = false
)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 解析器
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

fun parseMCD(content: String?, ambiguousDefault: String = "comment"): ParsedMCD {
    if (content.isNullOrBlank()) return ParsedMCD()

    val lines = content.split(Regex("\\r?\n"))
    val metaInfo = mutableListOf<MCDMeta>()
    val rootComments = mutableListOf<String>()
    val chains = mutableListOf<MCDChain>()
    var currentChain: MCDChain? = null

    // 先扫一遍确认是否是 v2
    val isV2 = lines.any { it.trim().startsWith("@mcd_version=2") }

    var pendingBlockType = BlockType.CHAIN
    var pendingConditional = false
    var pendingAlwaysActive = true
    var pendingNeedsRedstone = false
    var pendingTickDelay = 0
    var hasPendingState = false

    for (line in lines) {
        val tline = line.trim()
        if (tline.isEmpty()) continue

        // 杂项标记 ###Function### / ###End###
        if (tline.startsWith("###") && tline.endsWith("###")) continue

        // 若当前等待的是 CHAT 状态，则无论下面是什么前缀，都当成指令文本吃掉
        if (isV2 && hasPendingState && pendingBlockType == BlockType.CHAT) {
            // 确保有容纳容器
            if (currentChain == null) {
                currentChain = MCDChain(name = "分离的指令")
                chains.add(currentChain)
            }
            val block = MCDBlock(
                type = pendingBlockType,
                conditional = false,
                alwaysActive = true,
                needsRedstone = false,
                tickDelay = 0,
                command = tline
            )
            currentChain.items.add(ChainItem.Block(block))
            hasPendingState = false
            continue
        }

        // 元数据行
        if (tline.startsWith("@")) {
            val splitIdx = tline.indexOf('=')
            if (splitIdx > 0) {
                metaInfo.add(
                    MCDMeta(
                        key = tline.substring(1, splitIdx).trim(),
                        value = tline.substring(splitIdx + 1).trim()
                    )
                )
            }
            continue
        }

        // v2 链分割符 ---链名---
        if (tline.startsWith("---") && tline.endsWith("---")) {
            val chainName = tline.replace("---", "").trim().ifEmpty { "未命名命令链" }
            currentChain = MCDChain(name = chainName)
            chains.add(currentChain)
            hasPendingState = false
            continue
        }

        // 注释行（# 和 // 两种格式）
        if (tline.startsWith("#")) {
            val commentText = tline.substring(1).trim()
            if (currentChain != null) {
                currentChain.items.add(ChainItem.Comment(commentText))
            } else {
                rootComments.add(commentText)
            }
            continue
        }
        if (tline.startsWith("//")) continue

        // v2 状态行 > 开头，正则精确匹配，支持 _ 占位符
        // 格式: > [ICR_][?_][!_][tN|t_]
        if (isV2 && tline.startsWith(">")) {
            // 捕获组: (1)type (2)cond (3)rs (4)tick
            val stateRegex = Regex(
                """^>\s*([ICRH_])?([?_])?([!_])?(?:t(\d+|_))?\s*$""",
                RegexOption.IGNORE_CASE
            )
            val match = stateRegex.matchEntire(tline)
            if (match != null) {
                val rawType = (match.groupValues[1].ifEmpty { "C" }).uppercase()
                // _ 占位符视为缺省值 C
                val effectiveType = if (rawType == "_") "C" else rawType
                pendingBlockType = when (effectiveType) {
                    "I" -> BlockType.IMPULSE
                    "R" -> BlockType.REPEAT
                    "H" -> BlockType.CHAT
                    else -> BlockType.CHAIN
                }
                
                if (pendingBlockType == BlockType.CHAT) {
                    pendingConditional = false
                    pendingAlwaysActive = true
                    pendingNeedsRedstone = false
                    pendingTickDelay = 0
                } else {
                    val cond = match.groupValues[2]
                    val rs = match.groupValues[3]
                    val tick = match.groupValues[4]
                    pendingConditional = cond == "?"          // _ 或空都是无条件
                    pendingAlwaysActive = rs != "!"           // 只有显式 ! 才需要红石
                    pendingNeedsRedstone = rs == "!"
                    pendingTickDelay =
                        if (tick.isNotEmpty() && tick != "_") tick.toIntOrNull() ?: 0 else 0
                }
            } else {
                // 正则不匹配时的兜底：全缺省
                pendingBlockType = BlockType.CHAIN
                pendingConditional = false
                pendingAlwaysActive = true
                pendingNeedsRedstone = false
                pendingTickDelay = 0
            }
            hasPendingState = true
            continue
        }

        // 确保有容纳容器
        if (currentChain == null) {
            currentChain = MCDChain(name = "分离的指令")
            chains.add(currentChain)
        }

        // 正式的命令指令
        if (isV2) {
            val block = if (hasPendingState) {
                MCDBlock(
                    type = pendingBlockType,
                    conditional = pendingConditional,
                    alwaysActive = pendingAlwaysActive,
                    needsRedstone = pendingNeedsRedstone,
                    tickDelay = pendingTickDelay,
                    command = tline
                )
            } else {
                // 省略 > 时默认按连锁处理
                MCDBlock(command = tline)
            }
            currentChain.items.add(ChainItem.Block(block))
            hasPendingState = false
        } else {
            // v1: 行首是英文字母或斜杠才视为指令
            val firstChar = tline.firstOrNull()
            if (firstChar != null && (firstChar.isLetter() && firstChar.code < 128 || firstChar == '/')) {
                currentChain.items.add(ChainItem.RawCommand(tline))
            } else {
                // 无法推断的行：根据用户设置决定 fallback
                if (ambiguousDefault == "command") {
                    currentChain.items.add(ChainItem.RawCommand(tline))
                } else {
                    currentChain.items.add(ChainItem.Comment(tline))
                }
            }
        }
    }

    return ParsedMCD(
        metaInfo = metaInfo,
        rootComments = rootComments,
        chains = chains,
        isV2 = isV2
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Compose 渲染
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("command", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

@Composable
fun MCDContentView(
    content: String?,
    modifier: Modifier = Modifier,
    ambiguousDefault: String = "comment",
    showMetadata: Boolean = true
) {
    val parsed = remember(content, ambiguousDefault) { parseMCD(content, ambiguousDefault) }

    Column(modifier = modifier) {
        // 元数据区（可通过设置隐藏）
        if (showMetadata && parsed.metaInfo.isNotEmpty()) {
            MetaSection(parsed.metaInfo)
            Spacer(Modifier.height(8.dp))
        }

        // 链前游离注释
        parsed.rootComments.forEach { comment ->
            CommentItem(comment)
            Spacer(Modifier.height(4.dp))
        }

        // 命令链
        parsed.chains.forEach { chain ->
            ChainView(chain)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaSection(metaInfo: List<MCDMeta>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CHelperTheme.colors.backgroundComponent)
            .padding(12.dp)
    ) {
        metaInfo.forEach { meta ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@${meta.key}",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = CHelperTheme.colors.mainColor,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = meta.value,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = CHelperTheme.colors.textMain,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }
}

@Composable
private fun CommentItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CHelperTheme.colors.backgroundComponent.copy(alpha = 0.5f))
            .padding(10.dp, 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            id = R.drawable.pencil,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = TextStyle(
                fontSize = 12.sp,
                color = CHelperTheme.colors.textSecondary
            )
        )
    }
}

@Composable
private fun ChainView(chain: MCDChain) {
    Column {
        // 只有非默认链名才显示标题
        if (chain.name != "分离的指令" && chain.name != "默认主链") {
            Row(
                modifier = Modifier.padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    id = R.drawable.share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = chain.name,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = CHelperTheme.colors.textMain
                    )
                )
            }
        }

        chain.items.forEach { item ->
            when (item) {
                is ChainItem.Comment -> CommentItem(item.text)
                is ChainItem.RawCommand -> RawCommandItem(item.command)
                is ChainItem.Block -> BlockItem(item.block)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun RawCommandItem(command: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CHelperTheme.colors.backgroundComponent)
            .padding(10.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = command,
            modifier = Modifier
                .weight(1f),
            style = TextStyle(
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = CHelperTheme.colors.textMain
            )
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            id = R.drawable.copy,
            contentDescription = "复制指令",
            modifier = Modifier
                .size(18.dp)
                .clickable { copyToClipboard(context, command) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockItem(block: MCDBlock) {
    val context = LocalContext.current
    val blockColor = if (CHelperTheme.theme == CHelperTheme.Theme.Dark) {
        block.type.darkColor
    } else {
        block.type.lightColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            // 左边框色带，模拟命令方块颜色
            .border(
                width = 1.dp,
                color = blockColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .background(CHelperTheme.colors.backgroundComponent)
    ) {
        // 顶部 Badge 区
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(blockColor.copy(alpha = 0.1f))
                .padding(8.dp, 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Badge(block.type.label, blockColor)
            if (block.type != BlockType.CHAT) {
                if (block.conditional) Badge("条件", Color(0xFFE65100))
                if (block.alwaysActive) Badge("保持开启", Color(0xFF2E7D32))
                if (block.needsRedstone) Badge("红石控制", Color(0xFFB71C1C))
                if (block.tickDelay > 0) Badge("${block.tickDelay} 延迟", Color(0xFF1565C0))
            }
        }

        // 指令内容 + 复制按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = block.command,
                modifier = Modifier
                    .weight(1f),
                style = TextStyle(
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CHelperTheme.colors.textMain
                )
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                id = R.drawable.copy,
                contentDescription = "复制指令",
                modifier = Modifier
                    .size(18.dp)
                    .clickable { copyToClipboard(context, block.command) }
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        )
    }
}
