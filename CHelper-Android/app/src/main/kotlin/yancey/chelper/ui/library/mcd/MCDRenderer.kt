/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * MCD (Minecraft Command Data) 可视化渲染器
 * 支持 v1 (纯指令列表) 和 v2 (带状态行/链分隔符的结构化格式) 两种格式
 */

package yancey.chelper.ui.library.mcd

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import yancey.chelper.R
import yancey.chelper.android.util.MonitorUtil
import yancey.chelper.core.KernelCache
import yancey.chelper.core.Theme
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text

/** 首屏先组合这么多行，尽快出 UI */
private const val MCD_INITIAL_VISIBLE_ITEMS = 40

/** 后续每帧/每批自动往容器追加这么多行（体验上像原来一次全出来，但不卡死） */
private const val MCD_APPEND_BATCH_SIZE = 24

/** 两批追加之间让出主线程，避免 measure/layout 连着炸 */
private const val MCD_APPEND_FRAME_DELAY_MS = 16L

/** 单次打开最多高亮这么多条命令；再多只显示纯文本，防 native 长时间占锁 / OOM */
private const val MCD_HIGHLIGHT_MAX_COMMANDS = 64

/** 单条命令超过这个长度就跳过高亮（超长 execute/json 很容易把 C++ 解析拖死） */
private const val MCD_HIGHLIGHT_MAX_CMD_LEN = 2_500

/** 分批高亮时每处理多少条 yield 一次，让 UI 线程喘口气 */
private const val MCD_HIGHLIGHT_YIELD_EVERY = 8

private val mcdVersion2LineRegex = Regex(
    """^@mcd_version\s*=\s*2$""",
    RegexOption.IGNORE_CASE
)

// 数据模型

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

/** v2 格式的命令方块 */
data class MCDBlock(
    val type: BlockType = BlockType.CHAIN,
    val conditional: Boolean = false,
    val alwaysActive: Boolean = true,
    val needsRedstone: Boolean = false,
    val tickDelay: Int = 0,
    val command: String = "",
    var syntaxHighlightTokens: IntArray? = null
)

/** 链中的一个元素：可能是注释、v1 原始指令、或 v2 命令方块 */
sealed class ChainItem {
    data class Comment(val text: String) : ChainItem()
    data class RawCommand(
        val command: String,
        var syntaxHighlightTokens: IntArray? = null
    ) : ChainItem()

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

// 解析器

/**
 * 解析 MCD 结构；可选同步语法高亮（兼容旧调用方，如逐行复制）。
 * 页面渲染主路径不走这里：MCDContentView 先纯结构解析出 UI，
 * 再由其内部的分批高亮（[applyMcdHighlightItemsAsync] 私有路径）后台补 token，
 * 避免超长库打开详情页时长时间阻塞。
 */
fun parseMCD(
    content: String?,
    ambiguousDefault: String = "comment",
    context: Context? = null,
    cpackBranch: String? = null,
    isEnableMcdHighlight: Boolean = false
): ParsedMCD {
    val parsed = parseMCDStructure(content, ambiguousDefault)
    if (isEnableMcdHighlight && context != null && !cpackBranch.isNullOrEmpty()) {
        applyMcdHighlightSync(parsed, context, cpackBranch)
    }
    return parsed
}

/** 纯结构解析：行扫一遍，不碰 CHelperCore */
fun parseMCDStructure(
    content: String?,
    ambiguousDefault: String = "comment"
): ParsedMCD {
    if (content.isNullOrBlank()) return ParsedMCD()

    return try {
        val metaInfo = mutableListOf<MCDMeta>()
        val rootComments = mutableListOf<String>()
        val chains = mutableListOf<MCDChain>()
        var currentChain: MCDChain? = null
        val functionMarkerIndex = content.lineSequence()
            .indexOfFirst { it.trim() == "###Function###" }

        val isV2 = if (functionMarkerIndex >= 0) {
            content.lineSequence().take(functionMarkerIndex)
                .any { mcdVersion2LineRegex.matches(it.trim()) }
        } else {
            var detected = false
            for (line in content.lineSequence()) {
                val t = line.trim()
                if (mcdVersion2LineRegex.matches(t)) {
                    detected = true
                    break
                }
                if (t.isNotEmpty() && !t.startsWith("@") && !t.startsWith("###")) break
            }
            detected
        }

        var pendingBlockType = BlockType.CHAIN
        var pendingConditional = false
        var pendingAlwaysActive = true
        var pendingNeedsRedstone = false
        var pendingTickDelay = 0
        var hasPendingState = false
        var lastHeaderMetaIndex: Int? = null

        for ((lineIndex, line) in content.lineSequence().withIndex()) {
            val tline = line.trim()

            if (functionMarkerIndex >= 0 && lineIndex < functionMarkerIndex) {
                when {
                    tline.isEmpty() -> lastHeaderMetaIndex = null
                    tline.startsWith("@") -> {
                        val splitIdx = tline.indexOf('=')
                        if (splitIdx > 0) {
                            metaInfo.add(
                                MCDMeta(
                                    key = tline.substring(1, splitIdx).trim(),
                                    value = tline.substring(splitIdx + 1).trim()
                                )
                            )
                            lastHeaderMetaIndex = metaInfo.lastIndex
                        }
                    }

                    tline.startsWith("#") -> {
                        rootComments.add(tline.substring(1).trim())
                        lastHeaderMetaIndex = null
                    }

                    tline.startsWith("//") -> lastHeaderMetaIndex = null
                    else -> {
                        val metaIndex = lastHeaderMetaIndex
                        if (metaIndex != null && metaInfo[metaIndex].key.equals(
                                "note",
                                ignoreCase = true
                            )
                        ) {
                            val note = metaInfo[metaIndex]
                            metaInfo[metaIndex] = note.copy(value = "${note.value}\n$tline")
                        } else {
                            rootComments.add(tline)
                        }
                    }
                }
                continue
            }

            if (tline.isEmpty()) continue

            if (lineIndex == functionMarkerIndex) continue
            if (tline == "###End###") break

            // 杂项标记 ###Function### / ###End###
            if (tline.startsWith("###") && tline.endsWith("###")) continue

            // 若当前等待的是 CHAT 状态，则无论下面是什么前缀，都当成指令文本
            if (isV2 && hasPendingState && pendingBlockType == BlockType.CHAT) {
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

            // v2 状态行 > 开头
            if (isV2 && tline.startsWith(">")) {
                val stateRegex = Regex(
                    """^>\s*([ICRH_])?([?_])?([!_])?(?:t(\d+|_))?\s*$""",
                    RegexOption.IGNORE_CASE
                )
                val match = stateRegex.matchEntire(tline)
                if (match != null) {
                    val rawType = (match.groupValues[1].ifEmpty { "C" }).uppercase()
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
                        pendingConditional = cond == "?"
                        pendingAlwaysActive = rs != "!"
                        pendingNeedsRedstone = rs == "!"
                        pendingTickDelay =
                            if (tick.isNotEmpty() && tick != "_") tick.toIntOrNull() ?: 0 else 0
                    }
                } else {
                    pendingBlockType = BlockType.CHAIN
                    pendingConditional = false
                    pendingAlwaysActive = true
                    pendingNeedsRedstone = false
                    pendingTickDelay = 0
                }
                hasPendingState = true
                continue
            }

            if (currentChain == null) {
                currentChain = MCDChain(name = "分离的指令")
                chains.add(currentChain)
            }

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
                    MCDBlock(command = tline)
                }
                currentChain.items.add(ChainItem.Block(block))
                hasPendingState = false
            } else {
                val firstChar = tline.firstOrNull()
                if (firstChar != null && (firstChar.isLetter() && firstChar.code < 128 || firstChar == '/')) {
                    currentChain.items.add(ChainItem.RawCommand(tline))
                } else {
                    if (ambiguousDefault == "command") {
                        currentChain.items.add(ChainItem.RawCommand(tline))
                    } else {
                        currentChain.items.add(ChainItem.Comment(tline))
                    }
                }
            }
        }

        ParsedMCD(
            metaInfo = metaInfo,
            rootComments = rootComments,
            chains = chains,
            isV2 = isV2
        )
    } catch (e: Exception) {
        Log.e("MCDRenderer", "MCD 解析失败", e)
        MonitorUtil.generateCustomLog(e, "MCDParseError")
        ParsedMCD(
            metaInfo = listOf(MCDMeta(key = "error", value = "解析失败: ${e.message}")),
            rootComments = emptyList(),
            chains = emptyList(),
            isV2 = false
        )
    }
}

/**
 * 同步高亮（兼容旧调用方如逐行复制）。
 * 有条数/长度上限，超长库不会把整库都丢进 native。
 */
private fun applyMcdHighlightSync(
    parsed: ParsedMCD,
    context: Context,
    cpackBranch: String
) {
    val segment = cpackBranch.replace('-', '/')
    val core = KernelCache.acquire(context, segment)
    if (core == null) {
        return
    }
    try {
        var highlighted = 0
        outer@ for (chain in parsed.chains) {
            for (item in chain.items) {
                if (highlighted >= MCD_HIGHLIGHT_MAX_COMMANDS) break@outer
                when (item) {
                    is ChainItem.Block -> {
                        val cmd = item.block.command
                        if (cmd.isEmpty() || cmd.length > MCD_HIGHLIGHT_MAX_CMD_LEN) continue
                        core.createContext(cmd)
                            .use { item.block.syntaxHighlightTokens = it.syntaxToken }
                        highlighted++
                    }

                    is ChainItem.RawCommand -> {
                        val cmd = item.command
                        if (cmd.isEmpty() || cmd.length > MCD_HIGHLIGHT_MAX_CMD_LEN) continue
                        core.createContext(cmd)
                            .use { item.syntaxHighlightTokens = it.syntaxToken }
                        highlighted++
                    }

                    else -> {}
                }
            }
        }
    } catch (e: Exception) {
        Log.e("MCDRenderer", "Failed to highlight MCD blocks", e)
    } finally {
        KernelCache.release(segment)
    }
}

/**
 * 分批高亮：先让结构 UI 出来，再在后台一点点填 token。
 * 每批 yield，并可选回调 [onBatchDone]，方便 UI 边高亮边刷新首屏颜色。
 * @return 实际完成高亮的命令条数
 */
suspend fun applyMcdHighlightAsync(
    parsed: ParsedMCD,
    context: Context,
    cpackBranch: String,
    onBatchDone: (suspend (highlightedSoFar: Int) -> Unit)? = null
): Int = applyMcdHighlightItemsAsync(
    items = parsed.chains.flatMap { it.items },
    context = context,
    cpackBranch = cpackBranch,
    onBatchDone = onBatchDone
)

/**
 * 对当前已显示的一批命令做高亮。
 * 每次调用仍受 [MCD_HIGHLIGHT_MAX_COMMANDS] 保护；渲染器按延迟追加批次调用，
 * 这样首屏后的命令也能高亮，而不会把整库一次压进 native 高亮器。
 * 内核从共享 [KernelCache] 按段租借（与命令补全同段复用），批次结束归还。
 */
private suspend fun applyMcdHighlightItemsAsync(
    items: List<ChainItem>,
    context: Context,
    cpackBranch: String,
    onBatchDone: (suspend (highlightedSoFar: Int) -> Unit)? = null
): Int = withContext(Dispatchers.Default) {
    val segment = cpackBranch.replace('-', '/')
    val core = KernelCache.acquire(context, segment)
    if (core == null) {
        return@withContext 0
    }
    try {
        var highlighted = 0
        var sinceYield = 0
        for (item in items) {
            if (highlighted >= MCD_HIGHLIGHT_MAX_COMMANDS) break
            val applied = when (item) {
                is ChainItem.Block -> {
                    val cmd = item.block.command
                    if (cmd.isEmpty() || cmd.length > MCD_HIGHLIGHT_MAX_CMD_LEN) {
                        false
                    } else {
                        core.createContext(cmd)
                            .use { item.block.syntaxHighlightTokens = it.syntaxToken }
                        true
                    }
                }

                is ChainItem.RawCommand -> {
                    val cmd = item.command
                    if (cmd.isEmpty() || cmd.length > MCD_HIGHLIGHT_MAX_CMD_LEN) {
                        false
                    } else {
                        core.createContext(cmd).use { item.syntaxHighlightTokens = it.syntaxToken }
                        true
                    }
                }

                else -> false
            }
            if (applied) {
                highlighted++
                sinceYield++
                if (sinceYield >= MCD_HIGHLIGHT_YIELD_EVERY) {
                    sinceYield = 0
                    onBatchDone?.invoke(highlighted)
                    yield()
                }
            }
        }
        if (highlighted > 0) {
            onBatchDone?.invoke(highlighted)
        }
        highlighted
    } catch (e: Exception) {
        Log.e("MCDRenderer", "Failed to highlight MCD blocks async", e)
        0
    } finally {
        KernelCache.release(segment)
    }
}

/** 展平为可分页渲染的行，方便「先显示前 N 条」而不用 LazyColumn */
private sealed class MCDRenderRow {
    data class Meta(val items: List<MCDMeta>) : MCDRenderRow()
    data class RootComment(val text: String) : MCDRenderRow()
    data class Header(val name: String) : MCDRenderRow()
    data class Item(val item: ChainItem) : MCDRenderRow()
    data object ChainGap : MCDRenderRow()
}

private fun flattenParsedMCD(parsed: ParsedMCD, showMetadata: Boolean): List<MCDRenderRow> {
    val rows = ArrayList<MCDRenderRow>(
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

// Compose 渲染

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
    val context = LocalContext.current
    val settingsDataStore = remember(context) { SettingsDataStore(context) }
    val cpackBranch by settingsDataStore.cpackBranch()
        .collectAsState(initial = "release/experiment")
    val isEnableMcdHighlight by settingsDataStore.isEnableMcdHighlight()
        .collectAsState(initial = true)

    // 1) 只做结构解析，尽快让 UI 出来
    val parsed by produceState<ParsedMCD?>(initialValue = null, content, ambiguousDefault) {
        value = null
        value = withContext(Dispatchers.Default) {
            parseMCDStructure(content = content, ambiguousDefault = ambiguousDefault)
        }
    }

    if (parsed == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "解析中...",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = CHelperTheme.colors.textSecondary
                )
            )
        }
        return
    }
    val parsedData = parsed ?: return

    // 不用 LazyColumn（超长容易炸）。
    // 首屏先出 N 行，随后自动分批往 Column 追加，滚动体验与原来一致。
    val allRows = remember(parsedData, showMetadata) {
        flattenParsedMCD(parsedData, showMetadata)
    }
    var visibleCount by remember(content, ambiguousDefault, showMetadata) {
        mutableIntStateOf(minOf(MCD_INITIAL_VISIBLE_ITEMS, allRows.size))
    }
    LaunchedEffect(allRows) {
        // 结构一变：立刻出首屏，再按帧追加剩余行
        visibleCount = minOf(MCD_INITIAL_VISIBLE_ITEMS, allRows.size)
        while (visibleCount < allRows.size) {
            delay(MCD_APPEND_FRAME_DELAY_MS)
            visibleCount = minOf(visibleCount + MCD_APPEND_BATCH_SIZE, allRows.size)
        }
    }

    // 结构先出 UI；之后每批延迟显示的行单独进入高亮队列。
    // 不能只高亮整库的前 64 条，否则首屏后的延迟条目永远没有 token。
    var highlightRevision by remember(
        content,
        ambiguousDefault,
        showMetadata
    ) { mutableIntStateOf(0) }
    LaunchedEffect(allRows, cpackBranch, isEnableMcdHighlight) {
        if (!isEnableMcdHighlight || cpackBranch.isNullOrEmpty()) return@LaunchedEffect
        var processedRowCount = 0
        while (processedRowCount < allRows.size) {
            val targetCount = visibleCount.coerceIn(processedRowCount, allRows.size)
            if (targetCount == processedRowCount) {
                delay(MCD_APPEND_FRAME_DELAY_MS)
                continue
            }

            // 即使 visibleCount 快速跳变，也严格按渲染批大小追赶，不能跨过未高亮条目。
            val batchEnd = minOf(
                processedRowCount + MCD_APPEND_BATCH_SIZE,
                targetCount
            )
            val delayedItems = allRows.subList(processedRowCount, batchEnd)
                .mapNotNull { row -> (row as? MCDRenderRow.Item)?.item }
            processedRowCount = batchEnd
            if (delayedItems.isNotEmpty()) {
                applyMcdHighlightItemsAsync(delayedItems, context, cpackBranch) {
                    // 切回主线程再改 state，保证已显示行立即重组。
                    withContext(Dispatchers.Main.immediate) {
                        highlightRevision++
                    }
                }
            }
            yield()
        }
    }
    val visibleRows = remember(allRows, visibleCount) {
        allRows.take(visibleCount.coerceIn(0, allRows.size))
    }

    Column(modifier = modifier) {
        visibleRows.forEach { row ->
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
                        is ChainItem.RawCommand -> RawCommandItem(
                            item.command,
                            item.syntaxHighlightTokens,
                            highlightRevision
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
private fun ChainHeader(name: String) {
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
            text = name,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = CHelperTheme.colors.textMain
            )
        )
    }
}

@Composable
private fun RawCommandItem(command: String, tokens: IntArray?, highlightRevision: Int) {
    val context = LocalContext.current
    val isDark = CHelperTheme.theme == CHelperTheme.Theme.Dark
    val highlightedText = remember(command, tokens, isDark, highlightRevision) {
        highlightCommand(command, tokens, isDark)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CHelperTheme.colors.backgroundComponent)
            .padding(10.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = highlightedText,
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
private fun BlockItem(block: MCDBlock, highlightRevision: Int) {
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
            val isDark = CHelperTheme.theme == CHelperTheme.Theme.Dark
            val highlightedText = remember(
                block.command,
                block.syntaxHighlightTokens,
                isDark,
                highlightRevision
            ) {
                highlightCommand(block.command, block.syntaxHighlightTokens, isDark)
            }
            Text(
                text = highlightedText,
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

fun highlightCommand(command: String, tokens: IntArray?, isDark: Boolean): AnnotatedString {
    if (tokens == null || tokens.isEmpty() || command.isEmpty()) {
        return AnnotatedString(command)
    }
    val theme = if (isDark) Theme.THEME_NIGHT else Theme.THEME_DAY
    val normalColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

    return buildAnnotatedString {
        append(command)
        var lastIndex = 0
        var lastColor = theme.getColorByToken(tokens[0], normalColor)
        val length = minOf(tokens.size, command.length)
        for (i in 1 until length) {
            val color = theme.getColorByToken(tokens[i], normalColor)
            if (color != lastColor) {
                addStyle(
                    style = SpanStyle(color = Color(lastColor)),
                    start = lastIndex,
                    end = i
                )
                lastIndex = i
                lastColor = color
            }
        }
        addStyle(
            style = SpanStyle(color = Color(lastColor)),
            start = lastIndex,
            end = length
        )
    }
}
