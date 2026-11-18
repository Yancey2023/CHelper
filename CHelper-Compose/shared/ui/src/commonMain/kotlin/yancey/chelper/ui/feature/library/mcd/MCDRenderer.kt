/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * MCD (Minecraft Command Data) 可视化渲染器
 * 支持 v1 (纯指令列表) 和 v2 (带状态行/链分隔符的结构化格式) 两种格式
 */

package yancey.chelper.ui.feature.library.mcd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yancey.chelper.data.logging.ClientLog as Log
import yancey.chelper.domain.command.CommandEngine
import yancey.chelper.domain.command.CommandSession
import yancey.chelper.domain.library.ChainItem
import yancey.chelper.domain.library.parseMCDStructure
import yancey.chelper.domain.settings.Settings
import yancey.chelper.domain.settings.withDefaults
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.platform.LocalClientServices
import yancey.chelper.ui.platform.rememberCopyText

/** Bound native parsing of unusually large execute/JSON commands. */
private const val MCD_HIGHLIGHT_MAX_CMD_LEN = 2_500

/** A render-ready document using the persisted preferences, published as one snapshot. */
class McdPreview(
    val rows: List<MCDRenderRow>,
    val cpackBranch: String,
    val highlight: Boolean,
    val ambiguousDefault: String = "comment",
    val showMetadata: Boolean = true,
) {
    fun matches(settings: Settings): Boolean {
        val preferences = settings.withDefaults()
        return cpackBranch == preferences.cpackBranch &&
            highlight == preferences.isEnableMcdHighlight &&
            ambiguousDefault == preferences.ambiguousLineDefault &&
            showMetadata == !preferences.isHideMetadataPreview!!
    }
}

suspend fun prepareMcdPreview(
    content: String?,
    settings: Settings,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): McdPreview =
    withContext(dispatcher) {
        val preferences = settings.withDefaults()
        McdPreview(
            rows =
                flattenParsedMCD(
                    parseMCDStructure(content, preferences.ambiguousLineDefault!!),
                    !preferences.isHideMetadataPreview!!,
                ),
            cpackBranch = preferences.cpackBranch!!,
            highlight = preferences.isEnableMcdHighlight!!,
            ambiguousDefault = preferences.ambiguousLineDefault!!,
            showMetadata = !preferences.isHideMetadataPreview!!,
        )
    }

/** All callers provide a bounded viewport, never a vertically scrolling parent. */
@Composable
fun MCDContentView(
    content: String?,
    modifier: Modifier = Modifier,
    ambiguousDefault: String = "comment",
    showMetadata: Boolean = true,
    rowModifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    val services = LocalClientServices.current
    val branch by services.settings.cpackBranch().collectAsStateWithLifecycle(initialValue = null)
    val highlight by
        services.settings.isEnableMcdHighlight().collectAsStateWithLifecycle(initialValue = false)
    key(content, ambiguousDefault, showMetadata) {
        val rows by
            produceState<List<MCDRenderRow>?>(null) {
                value =
                    withContext(Dispatchers.Default) {
                        flattenParsedMCD(parseMCDStructure(content, ambiguousDefault), showMetadata)
                    }
            }
        val document = rows
        if (document == null) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("解析中...") }
        } else {
            PreparedMCDContentView(
                McdPreview(document, branch.orEmpty(), highlight, ambiguousDefault, showMetadata),
                modifier,
                rowModifier,
                header,
                footer,
            )
        }
    }
}

/** Render prepared rows directly; there is no second parsing/loading phase in this composable. */
@Composable
fun PreparedMCDContentView(
    preview: McdPreview,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    highlightingEnabled: Boolean = true,
    loadCommandEngine: (String) -> CommandEngine = LocalClientServices.current.loadCommandEngine,
) {
    val highlighter = remember(loadCommandEngine) { CommandSession(loadCommandEngine) }
    DisposableEffect(highlighter) { onDispose { highlighter.close() } }
    val copy = rememberCopyText()
    MCDRowsContent(
        rows = preview.rows,
        modifier = modifier,
        rowModifier = rowModifier,
        onCopy = copy,
        header = header,
        footer = footer,
        tokensFor = { item ->
            McdHighlightTokens(
                item,
                highlighter,
                preview.cpackBranch,
                preview.highlight && highlightingEnabled,
            )
        },
    )
}

/**
 * Row effects are cancelled when they leave the lazy viewport; no whole-document highlight queue.
 */
@Composable
private fun McdHighlightTokens(
    item: ChainItem,
    session: CommandSession,
    branch: String?,
    enabled: Boolean,
): IntArray? {
    val command =
        when (item) {
            is ChainItem.RawCommand -> item.command
            is ChainItem.Block -> item.block.command
            is ChainItem.Comment -> return null
        }
    if (
        !enabled ||
            branch.isNullOrEmpty() ||
            command.isEmpty() ||
            command.length > MCD_HIGHLIGHT_MAX_CMD_LEN
    )
        return null
    return key(command, session, branch) {
        var tokens by remember { mutableStateOf<IntArray?>(null) }
        LaunchedEffect(Unit) {
            try {
                tokens = session.highlight(branch, command)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("MCDRenderer", "Failed to highlight MCD command", error)
            }
        }
        tokens
    }
}
