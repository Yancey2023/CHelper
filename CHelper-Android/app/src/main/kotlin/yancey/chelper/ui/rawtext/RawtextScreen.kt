/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.ui.rawtext

import android.content.ClipData
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.hjq.toast.Toaster
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.ui.JsonEditorSession
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.common.dialog.MenuDialog
import yancey.chelper.ui.common.layout.Copyright
import yancey.chelper.ui.common.layout.Header
import yancey.chelper.ui.common.layout.RootView
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text

/** 需要确认时由元素卡片向屏幕级请求（避免在 LazyColumn 内直接渲染全屏弹窗导致崩溃） */
data class RawtextConfirmRequest(
    val title: String,
    val content: String,
    val onConfirm: () -> Unit,
)

val LocalRawtextConfirm = compositionLocalOf<((RawtextConfirmRequest) -> Unit)?> { null }

/** 帮助说明弹窗请求（标题, 内容） */
val LocalRawtextHelp = compositionLocalOf<((String, String) -> Unit)?> { null }

@Composable
fun RawtextScreen(
    viewModel: RawtextViewModel = viewModel(),
    navController: NavHostController? = null,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var section by rememberSaveable { mutableIntStateOf(0) }
    var showImport by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var presetOpen by remember { mutableStateOf(false) }
    var confirmRequest by remember { mutableStateOf<RawtextConfirmRequest?>(null) }
    var helpRequest by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 选择器条件目标：按修订号缓存，避免每次重组都重算
    val targets = remember(viewModel.revision) { viewModel.collectTargets() }
    // 首帧后再加载大体积翻译键，避免进入编辑器时卡顿
    LaunchedEffect(Unit) { viewModel.ensureTranslate() }

    // —— 嵌入会话（命令编辑器 tellraw/titleraw 的 JSON 参数位进入本页）——
    // 预填命令里已输入的 JSON（可解析则载入元素继续编辑；写了一半不可解析则打开空编辑器并提示，
    // 原命令文本保持不动，只有点"插入到命令"才整体替换；取消/系统返回 = 放弃，原样保留）。
    val embeddedSession = remember { JsonEditorSession.pending }
    var embedNotice by remember { mutableStateOf<String?>(null) }
    if (embeddedSession != null) {
        LaunchedEffect(Unit) {
            viewModel.awaitRestored()
            // 嵌入会话不写 rawtext 持久草稿
            viewModel.setAutoSave(false)
            val initial = embeddedSession.initialJson?.trim().orEmpty()
            if (initial.isNotEmpty()) {
                val result = viewModel.importFromText(initial)
                if (result.isFailure) {
                    embedNotice = "原 JSON 不完整，未能载入元素；点\"插入到命令\"将整体替换"
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                // 未完成（返回/被关闭）→ 放弃会话；完成路径由 finish() 置入结果，这里不动
                if (JsonEditorSession.resultJson == null) {
                    JsonEditorSession.cancel()
                }
            }
        }
    }

    RootView {
        CompositionLocalProvider(
            LocalRawtextConfirm provides { confirmRequest = it },
            LocalRawtextHelp provides { title, content -> helpRequest = title to content },
        ) {
        Column(Modifier.fillMaxSize()) {
            if (embeddedSession != null) {
                // 嵌入模式顶栏：插入到命令并返回 / 放弃
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.layout_rawtext_title),
                        modifier = Modifier.weight(1f),
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = CHelperTheme.colors.textMain,
                        ),
                    )
                    RawtextSmallButton("放弃", onClick = {
                        JsonEditorSession.cancel()
                        navController?.popBackStack()
                    })
                    RawtextSmallButton("插入到命令", onClick = {
                        JsonEditorSession.finish(viewModel.compact())
                        navController?.popBackStack()
                    })
                }
                embedNotice?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = CHelperTheme.colors.textSecondary,
                        ),
                    )
                }
            } else if (!isLandscape) {
                Header(
                    title = stringResource(R.string.layout_rawtext_title),
                    showBack = true,
                    right = {
                        Icon(
                            id = R.drawable.file_arrow_left,
                            modifier = Modifier
                                .clickable { showImport = true }
                                .padding(5.dp)
                                .size(24.dp),
                            contentDescription = stringResource(R.string.layout_rawtext_import),
                        )
                    },
                )
            }
            val onCopy: () -> Unit = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, viewModel.compact())))
                    Toaster.show(context.getString(R.string.layout_rawtext_copied))
                }
            }
            if (isLandscape) {
                // 横屏：左右双栏（左=元素+输出切换，右=预览+调试切换），无页眉/切换条/页脚
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        RawtextLandscapeElementsPane(viewModel, targets, onImport = { showImport = true }, onPreset = { presetOpen = true }, onCopy = onCopy)
                    }
                    Spacer(Modifier.width(1.dp).fillMaxHeight().background(CHelperTheme.colors.line))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        RawtextLandscapePreviewPane(viewModel, targets)
                    }
                }
            } else {
                RawtextSectionBar(section) { section = it }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (section) {
                        0 -> RawtextElementsScreen(viewModel, targets, onImport = { showImport = true }, onPreset = { presetOpen = true })
                        1 -> RawtextDebugScreen(viewModel, targets)
                        else -> RawtextOutputScreen(viewModel, onCopy = onCopy, onImport = { showImport = true })
                    }
                }
                Copyright(Modifier.align(Alignment.CenterHorizontally))
            }
        }
        }
    }

    if (presetOpen) {
        MenuDialog(
            data = RawtextPresets.all.map { it.name to it.name }.toTypedArray(),
            onChoose = { name ->
                scope.launch { viewModel.applyPreset(RawtextPresets.all.first { it.name == name }) }
            },
            onDismissRequest = { presetOpen = false },
        )
    }
    if (showImport) {
        RawtextImportDialog(
            loading = importing,
            error = importError,
            onDismiss = { showImport = false; importError = null },
            onImport = { text ->
                importError = null
                scope.launch {
                    importing = true
                    val result = viewModel.importFromText(text)
                    importing = false
                    result.onSuccess { showImport = false }
                        .onFailure { e -> importError = e.message ?: context.getString(R.string.layout_rawtext_import_error) }
                }
            },
        )
    }
    confirmRequest?.let { req ->
        IsConfirmDialog(
            onDismissRequest = { confirmRequest = null },
            title = req.title,
            content = req.content,
            onCancel = { confirmRequest = null },
            onConfirm = {
                req.onConfirm()
                confirmRequest = null
            },
        )
    }
    helpRequest?.let { (title, content) ->
        IsConfirmDialog(
            onDismissRequest = { helpRequest = null },
            title = title,
            content = content,
            cancelText = stringResource(R.string.layout_rawtext_help_close),
            confirmText = stringResource(R.string.layout_rawtext_help_ok),
            onCancel = { helpRequest = null },
            onConfirm = { helpRequest = null },
        )
    }
}

/** 元素 / 调试 / 输出 分区切换条 */
@Composable
private fun RawtextSectionBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(R.string.layout_rawtext_section_elements, R.string.layout_rawtext_section_debug, R.string.layout_rawtext_section_output)
            .forEachIndexed { i, id ->
                RawtextChip(stringResource(id), selected == i, onClick = { onSelect(i) })
            }
    }
}

/** 横屏左栏：默认元素编辑，右下角按钮切换到输出 */
@Composable
private fun RawtextLandscapeElementsPane(
    viewModel: RawtextViewModel,
    targets: RawtextDebugTargets,
    onImport: () -> Unit,
    onPreset: () -> Unit,
    onCopy: () -> Unit,
) {
    var showOutput by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (showOutput) {
            RawtextOutputScreen(viewModel, onCopy = onCopy, onImport = onImport)
            RawtextSmallButton(
                stringResource(R.string.layout_rawtext_collapse),
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                onClick = { showOutput = false },
            )
        } else {
            RawtextElementsScreen(viewModel, targets, onImport = onImport, onPreset = onPreset)
            RawtextSmallButton(
                stringResource(R.string.layout_rawtext_section_output),
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                onClick = { showOutput = true },
            )
        }
    }
}

/** 横屏右栏：默认预览，右下角按钮切换到调试 */
@Composable
private fun RawtextLandscapePreviewPane(viewModel: RawtextViewModel, targets: RawtextDebugTargets) {
    var showDebug by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (showDebug) {
            RawtextDebugScreen(viewModel, targets)
            RawtextSmallButton(
                stringResource(R.string.layout_rawtext_collapse),
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                onClick = { showDebug = false },
            )
        } else {
            viewModel.revision
            val preview = remember(viewModel.revision) { RawtextPreviewEngine.render(viewModel.elements, viewModel.debug) }
            RawtextPreviewPanel(viewModel, preview, Modifier.fillMaxSize())
            RawtextSmallButton(
                stringResource(R.string.layout_rawtext_section_debug),
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                onClick = { showDebug = true },
            )
        }
    }
}

/** 元素列表 */
@Composable
private fun RawtextElementsScreen(viewModel: RawtextViewModel, targets: RawtextDebugTargets, onImport: () -> Unit, onPreset: () -> Unit) {
    viewModel.revision
    var addOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RawtextSmallButton("+ " + stringResource(R.string.layout_rawtext_add_element), onClick = { addOpen = true })
            RawtextSmallButton(stringResource(R.string.layout_rawtext_preset), onClick = onPreset)
            RawtextSmallButton(stringResource(R.string.layout_rawtext_import), onClick = onImport)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            items(viewModel.elements.size) { i ->
                RawtextElementCard(
                    el = viewModel.elements[i],
                    targets = targets,
                    onChanged = { viewModel.touch() },
                    onRemove = { viewModel.removeAt(i) },
                    onMove = { d -> viewModel.move(i, d) },
                )
            }
        }
    }
    if (addOpen) {
        MenuDialog(
            data = RawtextAddKind.entries.map { it.label to it.name }.toTypedArray(),
            onChoose = { name -> viewModel.add(createElement(RawtextAddKind.valueOf(name))) },
            onDismissRequest = { addOpen = false },
        )
    }
}
