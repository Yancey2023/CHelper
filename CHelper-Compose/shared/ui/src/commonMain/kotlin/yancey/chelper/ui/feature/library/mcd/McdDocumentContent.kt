/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * MCD (Minecraft Command Data) 可视化渲染器
 * 支持 v1 (纯指令列表) 和 v2 (带状态行/链分隔符的结构化格式) 两种格式
 */

package yancey.chelper.ui.feature.library.mcd

/** 首屏先组合这么多行，尽快出 UI */
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
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.domain.library.BlockType
import yancey.chelper.domain.library.ChainItem
import yancey.chelper.domain.library.MCDBlock
import yancey.chelper.domain.library.MCDMeta
import yancey.chelper.domain.library.ParsedMCD
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.text.SyntaxTheme as Theme
import yancey.chelper.ui.designsystem.text.highlightCommand as textHighlightCommand
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text

val BlockType.label: String
    get() =
        when (this) {
            BlockType.IMPULSE -> "脉冲"
            BlockType.CHAIN -> "连锁"
            BlockType.REPEAT -> "循环"
            BlockType.CHAT -> "手动键入"
        }
val BlockType.lightColor: Color
    get() =
        Color(
            when (this) {
                BlockType.IMPULSE -> 0xFFFF9933
                BlockType.CHAIN -> 0xFF4FC1A6
                BlockType.REPEAT -> 0xFF9966FF
                BlockType.CHAT -> 0xFF607D8B
            }
        )
val BlockType.darkColor: Color
    get() =
        Color(
            when (this) {
                BlockType.IMPULSE -> 0xFFCC7A29
                BlockType.CHAIN -> 0xFF3A9C83
                BlockType.REPEAT -> 0xFF7A52CC
                BlockType.CHAT -> 0xFF455A64
            }
        )

/** 展平为可分页渲染的行，方便「先显示前 N 条」而不用 LazyColumn */
sealed class MCDRenderRow {
    data class Meta(val items: List<MCDMeta>) : MCDRenderRow()

    data class RootComment(val text: String) : MCDRenderRow()

    data class Header(val name: String) : MCDRenderRow()

    data class Item(val item: ChainItem) : MCDRenderRow()

    data object ChainGap : MCDRenderRow()
}

fun flattenParsedMCD(parsed: ParsedMCD, showMetadata: Boolean): List<MCDRenderRow> {
    val rows =
        ArrayList<MCDRenderRow>(
            parsed.rootComments.size +
                parsed.chains.sumOf { it.items.size + 2 } +
                if (showMetadata && parsed.metaInfo.isNotEmpty()) 1 else 0
        )
    if (showMetadata && parsed.metaInfo.isNotEmpty()) {
        rows.add(MCDRenderRow.Meta(parsed.metaInfo))
    }
    for (comment in parsed.rootComments) {
        rows.add(MCDRenderRow.RootComment(comment))
    }
    for (chain in parsed.chains) {
        val shouldShowHeader = chain.name != "分离的指令" && chain.name != "默认主链"
        if (shouldShowHeader) {
            rows.add(MCDRenderRow.Header(chain.name))
        }
        for (item in chain.items) {
            rows.add(MCDRenderRow.Item(item))
        }
        rows.add(MCDRenderRow.ChainGap)
    }
    return rows
}

val LocalMcdCopy = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun MCDRowsContent(
    rows: List<MCDRenderRow>,
    highlightRevision: Int = 0,
    modifier: Modifier = Modifier,
    batch: Boolean = true,
    onCopy: (String) -> Unit,
) {
    var visible by remember(rows) { mutableIntStateOf(if (batch) 64 else rows.size) }
    LaunchedEffect(rows, batch) {
        while (visible < rows.size) {
            withFrameNanos {}
            visible = (visible + 128).coerceAtMost(rows.size)
        }
    }
    CompositionLocalProvider(LocalMcdCopy provides onCopy) {
        Column(modifier = modifier) {
            rows.take(visible).forEach { row ->
                when (row) {
                    is MCDRenderRow.Meta -> {
                        MetaSection(row.items)
                        Spacer(Modifier.height(8.dp))
                    }

                    is MCDRenderRow.RootComment -> {
                        CommentItem(row.text)
                        Spacer(Modifier.height(4.dp))
                    }

                    is MCDRenderRow.Header -> {
                        ChainHeader(row.name)
                    }

                    is MCDRenderRow.Item -> {
                        when (val item = row.item) {
                            is ChainItem.Comment -> CommentItem(item.text)
                            is ChainItem.RawCommand ->
                                RawCommandItem(
                                    item.command,
                                    item.syntaxHighlightTokens,
                                    highlightRevision,
                                )

                            is ChainItem.Block -> BlockItem(item.block, highlightRevision)
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    is MCDRenderRow.ChainGap -> {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaSection(metaInfo: List<MCDMeta>) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(12.dp)
    ) {
        metaInfo.forEach { meta ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "@${meta.key}",
                    style =
                        TextStyle(
                            fontSize = 12.sp,
                            color = CHelperTheme.colors.mainColor,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = meta.value,
                    style =
                        TextStyle(
                            fontSize = 12.sp,
                            color = CHelperTheme.colors.textMain,
                            fontFamily = FontFamily.Monospace,
                        ),
                )
            }
        }
    }
}

@Composable
private fun CommentItem(text: String) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(CHelperTheme.colors.backgroundComponent.copy(alpha = 0.5f))
                .padding(10.dp, 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(id = Res.drawable.pencil, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
        )
    }
}

@Composable
private fun ChainHeader(name: String) {
    Row(
        modifier = Modifier.padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(id = Res.drawable.share, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            style =
                TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = CHelperTheme.colors.textMain,
                ),
        )
    }
}

@Composable
private fun RawCommandItem(command: String, tokens: IntArray?, highlightRevision: Int) {
    val copy = LocalMcdCopy.current
    val isDark = CHelperTheme.theme == CHelperTheme.Theme.Dark
    val highlightedText =
        remember(command, tokens, isDark, highlightRevision) {
            highlightCommand(command, tokens, isDark)
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(10.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = highlightedText,
            modifier = Modifier.weight(1f),
            style =
                TextStyle(
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CHelperTheme.colors.textMain,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            id = Res.drawable.copy,
            contentDescription = "复制指令",
            modifier = Modifier.size(18.dp).clickable { copy(command) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockItem(block: MCDBlock, highlightRevision: Int) {
    val copy = LocalMcdCopy.current
    val blockColor =
        if (CHelperTheme.theme == CHelperTheme.Theme.Dark) {
            block.type.darkColor
        } else {
            block.type.lightColor
        }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                // 左边框色带，模拟命令方块颜色
                .border(
                    width = 1.dp,
                    color = blockColor.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                )
                .background(CHelperTheme.colors.backgroundComponent)
    ) {
        // 顶部 Badge 区
        FlowRow(
            modifier =
                Modifier.fillMaxWidth()
                    .background(blockColor.copy(alpha = 0.1f))
                    .padding(8.dp, 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
            modifier = Modifier.fillMaxWidth().padding(10.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isDark = CHelperTheme.theme == CHelperTheme.Theme.Dark
            val highlightedText =
                remember(block.command, block.syntaxHighlightTokens, isDark, highlightRevision) {
                    highlightCommand(block.command, block.syntaxHighlightTokens, isDark)
                }
            Text(
                text = highlightedText,
                modifier = Modifier.weight(1f),
                style =
                    TextStyle(
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CHelperTheme.colors.textMain,
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                id = Res.drawable.copy,
                contentDescription = "复制指令",
                modifier = Modifier.size(18.dp).clickable { copy(block.command) },
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color),
        )
    }
}

fun highlightCommand(command: String, tokens: IntArray?, isDark: Boolean): AnnotatedString =
    textHighlightCommand(command, tokens, isDark)
