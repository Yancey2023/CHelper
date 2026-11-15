/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * MCD (Minecraft Command Data) 可视化渲染器
 * 支持 v1 (纯指令列表) 和 v2 (带状态行/链分隔符的结构化格式) 两种格式
 */

package yancey.chelper.ui.feature.library.mcd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import yancey.chelper.data.logging.ClientLog as Log
import yancey.chelper.domain.command.CommandAnalysisRequest
import yancey.chelper.domain.command.CommandSession
import yancey.chelper.domain.library.ChainItem
import yancey.chelper.domain.library.ParsedMCD
import yancey.chelper.domain.library.parseMCDStructure
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.platform.ClientServices
import yancey.chelper.ui.platform.LocalClientServices as LocalContext
import yancey.chelper.ui.platform.rememberCopyText

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

/** Highlighting policy is common; only the engine factory accesses JNI. */
suspend fun applyMcdHighlightAsync(
    parsed: ParsedMCD,
    context: ClientServices,
    cpackBranch: String,
    onBatchDone: (suspend (Int) -> Unit)? = null,
): Int =
    CommandSession(context.loadCommandEngine).use { session ->
        applyMcdHighlightItemsAsync(
            parsed.chains.flatMap { it.items },
            session,
            cpackBranch,
            onBatchDone,
        )
    }

private suspend fun applyMcdHighlightItemsAsync(
    items: List<ChainItem>,
    session: CommandSession,
    cpackBranch: String,
    onBatchDone: (suspend (Int) -> Unit)? = null,
): Int {
    var highlighted = 0
    try {
        for (item in items) {
            if (highlighted >= MCD_HIGHLIGHT_MAX_COMMANDS) break
            val command =
                when (item) {
                    is ChainItem.Block -> item.block.command
                    is ChainItem.RawCommand -> item.command
                    else -> continue
                }
            if (command.isEmpty() || command.length > MCD_HIGHLIGHT_MAX_CMD_LEN) continue
            val result =
                session.analyze(
                    CommandAnalysisRequest(
                        highlighted.toLong(),
                        cpackBranch,
                        command,
                        0,
                        true,
                        false,
                    )
                )
            when (item) {
                is ChainItem.Block -> item.block.syntaxHighlightTokens = result.tokens
                is ChainItem.RawCommand -> item.syntaxHighlightTokens = result.tokens
                else -> Unit
            }
            highlighted++
            if (highlighted % MCD_HIGHLIGHT_YIELD_EVERY == 0) {
                onBatchDone?.invoke(highlighted)
                yield()
            }
        }
        if (highlighted > 0) onBatchDone?.invoke(highlighted)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("MCDRenderer", "Failed to highlight MCD blocks", e)
    }
    return highlighted
}

// Compose 渲染

@Composable
fun MCDContentView(
    content: String?,
    modifier: Modifier = Modifier,
    ambiguousDefault: String = "comment",
    showMetadata: Boolean = true,
) {
    val context = LocalContext.current
    val highlighter = remember(context) { CommandSession(context.loadCommandEngine) }
    DisposableEffect(highlighter) { onDispose { highlighter.close() } }
    val copy = rememberCopyText()
    val settingsDataStore = remember(context) { context.settings }
    val cpackBranch by
        settingsDataStore
            .cpackBranch()
            .collectAsStateWithLifecycle(initialValue = "release-experiment")
    val isEnableMcdHighlight by
        settingsDataStore.isEnableMcdHighlight().collectAsStateWithLifecycle(initialValue = true)

    // 1) 只做结构解析，尽快让 UI 出来
    val parsed by
        produceState<ParsedMCD?>(initialValue = null, content, ambiguousDefault) {
            value = null
            value =
                withContext(Dispatchers.Default) {
                    parseMCDStructure(content = content, ambiguousDefault = ambiguousDefault)
                }
        }

    if (parsed == null) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CHelperTheme.colors.backgroundComponent)
                    .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "解析中...",
                style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
            )
        }
        return
    }
    val parsedData = parsed ?: return

    // 不用 LazyColumn（超长容易炸）。
    // 首屏先出 N 行，随后自动分批往 Column 追加，滚动体验与原来一致。
    val allRows = remember(parsedData, showMetadata) { flattenParsedMCD(parsedData, showMetadata) }
    var visibleCount by
        remember(content, ambiguousDefault, showMetadata) {
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
    var highlightRevision by
        remember(content, ambiguousDefault, showMetadata) { mutableIntStateOf(0) }
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
            val batchEnd = minOf(processedRowCount + MCD_APPEND_BATCH_SIZE, targetCount)
            val delayedItems =
                allRows.subList(processedRowCount, batchEnd).mapNotNull { row ->
                    (row as? MCDRenderRow.Item)?.item
                }
            processedRowCount = batchEnd
            if (delayedItems.isNotEmpty()) {
                applyMcdHighlightItemsAsync(delayedItems, highlighter, cpackBranch) {
                    // 切回主线程再改 state，保证已显示行立即重组。
                    withContext(Dispatchers.Main.immediate) { highlightRevision++ }
                }
            }
            yield()
        }
    }
    val visibleRows =
        remember(allRows, visibleCount) { allRows.take(visibleCount.coerceIn(0, allRows.size)) }

    MCDRowsContent(visibleRows, highlightRevision, modifier, batch = false, onCopy = copy)
}
