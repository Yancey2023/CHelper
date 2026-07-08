/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hjq.toast.Toaster
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CustomDialog
import yancey.chelper.ui.common.dialog.CustomDialogProperties
import yancey.chelper.ui.common.dialog.DialogContainer
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Switch
import yancey.chelper.ui.common.widget.Text

@Composable
fun RawtextScreen(viewModel: RawtextViewModel = viewModel()) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    RawtextScreenContent(
        segments = viewModel.segments,
        activeTextSegmentId = viewModel.activeTextSegmentId,
        onUpdateActiveTextSegmentId = { viewModel.activeTextSegmentId = it },
        copyFormat = viewModel.copyFormat,
        onUpdateCopyFormat = viewModel::updateCopyFormat,
        autoSaveEnabled = viewModel.autoSaveEnabled,
        onSetAutoSave = viewModel::setAutoSave,
        showAutoSavePrompt = viewModel.showAutoSavePrompt,
        onConfirmAutoSave = viewModel::confirmAutoSave,
        mockPlayerP = viewModel.mockPlayerP,
        onUpdateMockPlayerP = { viewModel.mockPlayerP = it },
        mockPlayerR = viewModel.mockPlayerR,
        onUpdateMockPlayerR = { viewModel.mockPlayerR = it },
        mockPlayerA = viewModel.mockPlayerA,
        onUpdateMockPlayerA = { viewModel.mockPlayerA = it },
        mockPlayerS = viewModel.mockPlayerS,
        onUpdateMockPlayerS = { viewModel.mockPlayerS = it },
        mockScore = viewModel.mockScore,
        onUpdateMockScore = { viewModel.mockScore = it },
        mockTranslate = viewModel.mockTranslate,
        onUpdateMockTranslate = { viewModel.mockTranslate = it },
        mockCondition = viewModel.mockCondition,
        onUpdateMockCondition = { viewModel.mockCondition = it },
        generatedJson = viewModel.getRawJson(pretty = true),
        previewText = viewModel.buildPreviewText(),
        validationMessages = viewModel.validationMessages(),
        isPreviewEmpty = viewModel.isPreviewEmpty(),
        onUpdateTextSegment = viewModel::updateTextSegment,
        onBackspaceAtStart = viewModel::onBackspaceAtStart,
        onMoveSegment = viewModel::moveSegment,
        onRemoveSegment = viewModel::removeSegment,
        onAddFeature = viewModel::addFeature,
        onApplyFormat = viewModel::applyFormat,
        onClearAll = viewModel::clearAll,
        onPersistMockSettings = viewModel::persistMockSettings,
        onDecodeRawJson = viewModel::decodeRawJson,
        onCommitSegmentEdit = viewModel::commitSegmentEdit,
        onFindSegment = viewModel::findSegment,
        onCopyByPreference = {
            val compressed = viewModel.copyFormat == "compressed"
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, viewModel.getRawJson(pretty = !compressed))))
                Toaster.show(if (compressed) "已复制压缩 JSON" else "已复制格式化 JSON")
            }
        },
        onPasteFromClipboard = { setText ->
            scope.launch {
                val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.text?.toString()
                if (text.isNullOrBlank()) Toaster.show("剪贴板没有可解析文本") else setText(text)
            }
        }
    )
}

@Composable
fun RawtextScreenContent(
    segments: List<RawtextSegment>,
    activeTextSegmentId: Long?,
    onUpdateActiveTextSegmentId: (Long?) -> Unit,
    copyFormat: String,
    onUpdateCopyFormat: (String) -> Unit,
    autoSaveEnabled: Boolean,
    onSetAutoSave: (Boolean) -> Unit,
    showAutoSavePrompt: Boolean,
    onConfirmAutoSave: (Boolean) -> Unit,
    mockPlayerP: String,
    onUpdateMockPlayerP: (String) -> Unit,
    mockPlayerR: String,
    onUpdateMockPlayerR: (String) -> Unit,
    mockPlayerA: String,
    onUpdateMockPlayerA: (String) -> Unit,
    mockPlayerS: String,
    onUpdateMockPlayerS: (String) -> Unit,
    mockScore: String,
    onUpdateMockScore: (String) -> Unit,
    mockTranslate: String,
    onUpdateMockTranslate: (String) -> Unit,
    mockCondition: String,
    onUpdateMockCondition: (String) -> Unit,
    generatedJson: String,
    previewText: AnnotatedString,
    validationMessages: List<String>,
    isPreviewEmpty: Boolean,
    onUpdateTextSegment: (Long, TextFieldValue) -> Unit,
    onBackspaceAtStart: (Long) -> Boolean,
    onMoveSegment: (Long, Int) -> Unit,
    onRemoveSegment: (Long) -> Unit,
    onAddFeature: (RawtextSegmentType) -> Long?,
    onApplyFormat: (RawtextFormat) -> Unit,
    onClearAll: () -> Unit,
    onPersistMockSettings: () -> Unit,
    onDecodeRawJson: (String) -> Result<Unit>,
    onCommitSegmentEdit: () -> Unit,
    onFindSegment: (Long) -> RawtextSegment?,
    onCopyByPreference: () -> Unit,
    onPasteFromClipboard: ((String) -> Unit) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var items by remember { mutableStateOf<List<NamedEntry>>(emptyList()) }
    var slots by remember { mutableStateOf<List<NamedEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        items = RawtextDataRepository.loadItems(context)
        slots = RawtextDataRepository.loadSlots(context)
    }

    var decodeDialogVisible by remember { mutableStateOf(false) }
    var clearDialogVisible by remember { mutableStateOf(false) }
    var simulatorDialogVisible by remember { mutableStateOf(false) }
    var settingsDialogVisible by remember { mutableStateOf(false) }
    var aboutDialogVisible by remember { mutableStateOf(false) }
    var editingSegmentId by remember { mutableStateOf<Long?>(null) }
    var menuVisible by remember { mutableStateOf(false) }

    RootViewWithHeaderAndCopyright(
        title = stringResource(R.string.layout_rawtext_title),
        headerRight = {
            Box {
                HeaderIconButton(id = R.drawable.more, contentDescription = "菜单") {
                    menuVisible = true
                }
                RawtextHeaderMenu(
                    expanded = menuVisible,
                    onDismiss = { menuVisible = false },
                    onDecode = { menuVisible = false; decodeDialogVisible = true },
                    onSimulator = { menuVisible = false; simulatorDialogVisible = true },
                    onClear = { menuVisible = false; clearDialogVisible = true },
                    onSettings = { menuVisible = false; settingsDialogVisible = true },
                    onAbout = { menuVisible = false; aboutDialogVisible = true }
                )
            }
        }
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.28f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(14.dp))
                    ExperimentalFeatureWarningBanner()
                    Spacer(Modifier.height(12.dp))
                    InlineEditorPanel(
                        segments = segments,
                        activeTextSegmentId = activeTextSegmentId,
                        onUpdateActiveTextSegmentId = onUpdateActiveTextSegmentId,
                        onUpdateTextSegment = onUpdateTextSegment,
                        onBackspaceAtStart = onBackspaceAtStart,
                        onMoveSegment = onMoveSegment,
                        onRemoveSegment = onRemoveSegment,
                        onAddFeature = { type -> onAddFeature(type)?.let { editingSegmentId = it } },
                        onEditSegment = { editingSegmentId = it }
                    )
                    Spacer(Modifier.height(14.dp))
                    PreviewPanel(previewText = previewText, isPreviewEmpty = isPreviewEmpty)
                    Spacer(Modifier.height(12.dp))
                    QuickActionRow(
                        onCopy = onCopyByPreference,
                        onDecode = { decodeDialogVisible = true },
                        onSimulator = { simulatorDialogVisible = true }
                    )
                    Spacer(Modifier.height(14.dp))
                    StylePanel(columns = 4, onApplyFormat = onApplyFormat)
                }
                Column(
                    modifier = Modifier
                        .weight(0.72f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    JsonOutputPanel(
                        generatedJson = generatedJson,
                        validationMessages = validationMessages,
                        onCopy = { onCopyByPreference() }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                Spacer(Modifier.height(14.dp))
                ExperimentalFeatureWarningBanner()
                Spacer(Modifier.height(12.dp))
                InlineEditorPanel(
                    segments = segments,
                    activeTextSegmentId = activeTextSegmentId,
                    onUpdateActiveTextSegmentId = onUpdateActiveTextSegmentId,
                    onUpdateTextSegment = onUpdateTextSegment,
                    onBackspaceAtStart = onBackspaceAtStart,
                    onMoveSegment = onMoveSegment,
                    onRemoveSegment = onRemoveSegment,
                    onAddFeature = { type -> onAddFeature(type)?.let { editingSegmentId = it } },
                    onEditSegment = { editingSegmentId = it }
                )
                Spacer(Modifier.height(14.dp))
                PreviewPanel(previewText = previewText, isPreviewEmpty = isPreviewEmpty)
                Spacer(Modifier.height(12.dp))
                QuickActionRow(
                    onCopy = onCopyByPreference,
                    onDecode = { decodeDialogVisible = true },
                    onSimulator = { simulatorDialogVisible = true }
                )
                Spacer(Modifier.height(14.dp))
                StylePanel(columns = 4, onApplyFormat = onApplyFormat)
                Spacer(Modifier.height(14.dp))
                JsonOutputPanel(
                    generatedJson = generatedJson,
                    validationMessages = validationMessages,
                    onCopy = { onCopyByPreference() }
                )
            }
        }
    }

    // 功能段编辑：根据类型分发到不同弹窗
    editingSegmentId?.let { id ->
        onFindSegment(id)?.let { segment ->
            when (segment.type) {
                RawtextSegmentType.Selector -> SelectorEditorDialog(
                    initialSelector = segment.selector.ifBlank { "@p" },
                    items = items,
                    slots = slots,
                    onApply = {
                        segment.selector = it
                        onCommitSegmentEdit()
                        editingSegmentId = null
                    },
                    onDismiss = { editingSegmentId = null }
                )

                RawtextSegmentType.Conditional -> ConditionalEditorDialog(
                    initialConditionJson = segment.conditionJson,
                    initialThenJson = segment.thenJson,
                    items = items,
                    slots = slots,
                    onApply = { conditionJson, thenJson ->
                        segment.conditionJson = conditionJson
                        segment.thenJson = thenJson
                        onCommitSegmentEdit()
                        editingSegmentId = null
                    },
                    onDismiss = { editingSegmentId = null }
                )

                RawtextSegmentType.Score, RawtextSegmentType.Translate -> SimpleFeatureEditDialog(
                    segment = segment,
                    items = items,
                    slots = slots,
                    onCommit = { onCommitSegmentEdit() },
                    onDismiss = { editingSegmentId = null }
                )

                RawtextSegmentType.Text, RawtextSegmentType.LineBreak -> Unit
            }
        }
    }

    if (decodeDialogVisible) {
        RawtextDecodeDialog(
            onPasteFromClipboard = onPasteFromClipboard,
            onDecode = { input ->
                onDecodeRawJson(input).fold(
                    onSuccess = {
                        decodeDialogVisible = false
                        Toaster.show("JSON 已解析")
                    },
                    onFailure = { Toaster.show("JSON 解析失败：${it.message ?: "格式错误"}") }
                )
            },
            onDismiss = { decodeDialogVisible = false }
        )
    }

    if (simulatorDialogVisible) {
        RawtextSimulatorDialog(
            mockPlayerP = mockPlayerP,
            onUpdateMockPlayerP = onUpdateMockPlayerP,
            mockPlayerR = mockPlayerR,
            onUpdateMockPlayerR = onUpdateMockPlayerR,
            mockPlayerA = mockPlayerA,
            onUpdateMockPlayerA = onUpdateMockPlayerA,
            mockPlayerS = mockPlayerS,
            onUpdateMockPlayerS = onUpdateMockPlayerS,
            mockScore = mockScore,
            onUpdateMockScore = onUpdateMockScore,
            mockTranslate = mockTranslate,
            onUpdateMockTranslate = onUpdateMockTranslate,
            mockCondition = mockCondition,
            onUpdateMockCondition = onUpdateMockCondition,
            onPersistMockSettings = onPersistMockSettings,
            onDismiss = { simulatorDialogVisible = false }
        )
    }

    if (settingsDialogVisible) {
        RawtextSettingsDialog(
            copyFormat = copyFormat,
            onUpdateCopyFormat = onUpdateCopyFormat,
            autoSaveEnabled = autoSaveEnabled,
            onSetAutoSave = onSetAutoSave,
            onDismiss = { settingsDialogVisible = false }
        )
    }

    if (aboutDialogVisible) {
        RawtextAboutDialog(onDismiss = { aboutDialogVisible = false })
    }

    if (clearDialogVisible) {
        IsConfirmDialog(
            onDismissRequest = { clearDialogVisible = false },
            title = "清空内容",
            content = "确定要清空当前 RawJSON 工作区吗？这将清除所有编辑内容，且无法恢复。",
            confirmText = "清空",
            onConfirm = {
                onClearAll()
                Toaster.show("已清空")
            }
        )
    }

    if (showAutoSavePrompt) {
        AutoSavePromptDialog(
            onChoose = { onConfirmAutoSave(it) }
        )
    }
}


@Composable
private fun ExperimentalFeatureWarningBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFA000).copy(alpha = 0.14f))
            .border(BorderStroke(1.dp, Color(0xFFFFA000).copy(alpha = 0.32f)), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFA000)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "实验性功能",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFA000))
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "RawJSON 编辑器还在打磨中，复杂条件、翻译参数或旧版 JSON 解析可能不完全稳定。正式使用前建议先在测试世界验证。",
                style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary, lineHeight = 17.sp)
            )
        }
    }
}


@Composable
private fun HeaderIconButton(id: Int, contentDescription: String, onClick: () -> Unit) {
    Icon(
        id = id,
        contentDescription = contentDescription,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp)
            .size(22.dp)
    )
}

@Composable
private fun HeroMetric(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (value.isNotEmpty()) {
            Text(text = value, style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp))
        }
        Text(text = label, style = TextStyle(color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp))
    }
}

@Composable
private fun RawtextHeaderMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDecode: () -> Unit,
    onSimulator: () -> Unit,
    onClear: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    if (!expanded) return
    // 锚在顶栏菜单按钮右下角的下拉菜单，对齐网页版的右上角菜单
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 90),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 168.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CHelperTheme.colors.backgroundComponentNoTranslate)
                .padding(vertical = 6.dp)
        ) {
            RawtextMenuItem("解析 JSON", Color(0xFF3367D6), onDecode)
            RawtextMenuItem("模拟器", Color(0xFF00796B), onSimulator)
            RawtextMenuItem("清空内容", Color(0xFFD84315), onClear)
            RawtextMenuItem("设置", CHelperTheme.colors.textMain, onSettings)
            RawtextMenuItem("关于", CHelperTheme.colors.textMain, onAbout)
        }
    }
}

@Composable
private fun RawtextMenuItem(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        style = TextStyle(color = color, fontSize = 15.sp)
    )
}

/**
 * 内联混排编辑器：把段按 LineBreak 切成多行，每行用 FlowRow 流式排列。
 * 文本段是行内输入框，功能段是彩色标签。
 * 支持在标签间直接打字、退格删标签/合并行、回车换行（标签也跟着换行）。
 */
@Composable
private fun InlineEditorPanel(
    segments: List<RawtextSegment>,
    activeTextSegmentId: Long?,
    onUpdateActiveTextSegmentId: (Long?) -> Unit,
    onUpdateTextSegment: (Long, TextFieldValue) -> Unit,
    onBackspaceAtStart: (Long) -> Boolean,
    onMoveSegment: (Long, Int) -> Unit,
    onRemoveSegment: (Long) -> Unit,
    onAddFeature: (RawtextSegmentType) -> Unit,
    onEditSegment: (Long) -> Unit,
) {
    // 焦点请求器，按段 id 缓存，用于换行/退格后把光标移到目标段
    val focusRequesters = remember { mutableMapOf<Long, FocusRequester>() }

    SectionCard(
        title = "编辑内容",
        subtitle = "标签之间可直接输入；行首退格删除；回车换行。"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, CHelperTheme.colors.line), RoundedCornerShape(16.dp))
                .background(CHelperTheme.colors.background)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 按 LineBreak 把段分行
            val lines = remember(segments.toList()) { splitIntoLines(segments) }
            lines.forEach { line ->
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (line.isEmpty()) {
                        // 纯空行占位，给个最小高度
                        Box(Modifier.height(26.dp))
                    }
                    line.forEach { segment ->
                        val index = segments.indexOf(segment)
                        if (segment.type == RawtextSegmentType.Text) {
                            val requester = focusRequesters.getOrPut(segment.id) { createFocusRequester() }
                            InlineTextField(
                                segment = segment,
                                isActive = activeTextSegmentId == segment.id,
                                focusRequester = requester,
                                onValueChange = { onUpdateTextSegment(segment.id, it) },
                                onFocus = { onUpdateActiveTextSegmentId(segment.id) },
                                onBackspaceAtStart = { onBackspaceAtStart(segment.id) }
                            )
                        } else {
                            InlineFeatureChip(
                                segment = segment,
                                canMoveUp = index > 0,
                                canMoveDown = index < segments.lastIndex,
                                onEdit = { onEditSegment(segment.id) },
                                onMoveUp = { onMoveSegment(segment.id, -1) },
                                onMoveDown = { onMoveSegment(segment.id, 1) },
                                onRemove = { onRemoveSegment(segment.id) }
                            )
                        }
                    }
                }
            }
        }

        // 换行/退格后，把焦点移到 ViewModel 标记的活动文本段
        LaunchedEffect(activeTextSegmentId, segments.size) {
            val target = activeTextSegmentId ?: return@LaunchedEffect
            focusRequesters[target]?.let { runCatching { it.requestFocus() } }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "插入到当前光标位置；功能标签会自动打开编辑器。",
            style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RawtextFilledButton("计分板", Color(0xFFE53935), Modifier.weight(1f)) {
                onAddFeature(RawtextSegmentType.Score)
            }
            RawtextFilledButton("选择器", Color(0xFF43A047), Modifier.weight(1f)) {
                onAddFeature(RawtextSegmentType.Selector)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RawtextFilledButton("翻译", Color(0xFFF9A825), Modifier.weight(1f)) {
                onAddFeature(RawtextSegmentType.Translate)
            }
            RawtextFilledButton("条件", Color(0xFF7E57C2), Modifier.weight(1f)) {
                onAddFeature(RawtextSegmentType.Conditional)
            }
        }
        Spacer(Modifier.height(8.dp))
        RawtextFilledButton("换行", Color(0xFF546E7A), Modifier.fillMaxWidth()) {
            onAddFeature(RawtextSegmentType.LineBreak)
        }
    }
}

/** 按 LineBreak 段把扁平段列表切成多行（LineBreak 本身不进任何行）。 */
private fun splitIntoLines(segments: List<RawtextSegment>): List<List<RawtextSegment>> {
    val lines = mutableListOf<MutableList<RawtextSegment>>()
    var current = mutableListOf<RawtextSegment>()
    for (segment in segments) {
        if (segment.type == RawtextSegmentType.LineBreak) {
            lines.add(current)
            current = mutableListOf()
        } else {
            current.add(segment)
        }
    }
    lines.add(current)
    return lines
}

/**
 * Workaround for @RememberInComposition warning when creating FocusRequesters dynamically.
 */
private fun createFocusRequester(): FocusRequester = FocusRequester()

@Composable
private fun InlineTextField(
    segment: RawtextSegment,
    isActive: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onFocus: () -> Unit,
    onBackspaceAtStart: () -> Boolean,
) {
    BasicTextField(
        value = segment.textValue,
        onValueChange = onValueChange,
        modifier = Modifier
            .widthIn(min = if (segment.textValue.text.isEmpty()) 28.dp else 1.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { event ->
                // 行首退格：拦截并删前一个标签 / 合并行
                if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace) {
                    val sel = segment.textValue.selection
                    if (sel.collapsed && sel.start == 0) {
                        return@onPreviewKeyEvent onBackspaceAtStart()
                    }
                }
                false
            },
        textStyle = TextStyle(
            color = CHelperTheme.colors.textMain,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace
        ),
        cursorBrush = SolidColor(CHelperTheme.colors.mainColor),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .then(
                        if (isActive) Modifier.background(
                            CHelperTheme.colors.mainColorSecondary.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)
                        ) else Modifier
                    )
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                inner()
            }
        }
    )
}

@Composable
private fun InlineFeatureChip(
    segment: RawtextSegment,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuVisible by remember { mutableStateOf(false) }
    val color = featureColor(segment.type)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable { onEdit() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chipLabel(segment),
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 112.dp)
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f))
                .clickable { menuVisible = !menuVisible }
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(text = "⋮", style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }

    if (menuVisible) {
        ChipActionDialog(
            title = chipLabel(segment),
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onEdit = { menuVisible = false; onEdit() },
            onMoveUp = { menuVisible = false; onMoveUp() },
            onMoveDown = { menuVisible = false; onMoveDown() },
            onRemove = { menuVisible = false; onRemove() },
            onDismiss = { menuVisible = false }
        )
    }
}

@Composable
private fun ChipActionDialog(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    CustomDialog(onDismissRequest = onDismiss) {
        DialogContainer(modifier = Modifier.widthIn(max = 320.dp), backgroundNoTranslate = true, cornerSize = 18.dp) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(text = title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(14.dp))
                RawtextFilledButton("编辑", CHelperTheme.colors.mainColor, Modifier.fillMaxWidth(), onClick = onEdit)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RawtextFilledButton("← 左移", CHelperTheme.colors.textSecondary, Modifier.weight(1f), enabled = canMoveUp, onClick = onMoveUp)
                    RawtextFilledButton("右移 →", CHelperTheme.colors.textSecondary, Modifier.weight(1f), enabled = canMoveDown, onClick = onMoveDown)
                }
                Spacer(Modifier.height(8.dp))
                RawtextFilledButton("删除", Color(0xFFD84315), Modifier.fillMaxWidth(), onClick = onRemove)
            }
        }
    }
}

private fun featureColor(type: RawtextSegmentType): Color = when (type) {
    RawtextSegmentType.Score -> Color(0xFFE53935)
    RawtextSegmentType.Selector -> Color(0xFF43A047)
    RawtextSegmentType.Translate -> Color(0xFFF9A825)
    RawtextSegmentType.Conditional -> Color(0xFF7E57C2)
    RawtextSegmentType.Text, RawtextSegmentType.LineBreak -> Color(0xFFEA5947)
}

private fun chipLabel(segment: RawtextSegment): String = when (segment.type) {
    RawtextSegmentType.Score -> "${segment.scoreName.ifBlank { "@p" }}:${segment.scoreObjective.ifBlank { "score" }}"
    RawtextSegmentType.Selector -> segment.selector.ifBlank { "@p" }
    RawtextSegmentType.Translate -> "t:${segment.translateKey.ifBlank { "key" }}"
    RawtextSegmentType.Conditional -> "IF…THEN…"
    RawtextSegmentType.Text, RawtextSegmentType.LineBreak -> "文本"
}

@Composable
private fun StylePanel(columns: Int, onApplyFormat: (RawtextFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(
        title = "颜色 & 格式",
        subtitle = "常用项直接点；完整颜色板需要时再展开，少占点地盘。"
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RawtextViewModel.styleFormats.forEach { format ->
                FormatChip(format = format) { onApplyFormat(format) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RawtextViewModel.colorFormats.take(10).forEach { format ->
                CompactColorChip(format = format) { onApplyFormat(format) }
            }
        }
        Spacer(Modifier.height(10.dp))
        RawtextFilledButton(
            text = if (expanded) "收起全部颜色" else "展开全部颜色",
            color = CHelperTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
            height = 36.dp,
            onClick = { expanded = !expanded }
        )
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(RawtextViewModel.colorFormats) { format ->
                    ColorFormatButton(format = format) { onApplyFormat(format) }
                }
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    onCopy: () -> Unit,
    onDecode: () -> Unit,
    onSimulator: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RawtextFilledButton("复制 JSON", CHelperTheme.colors.mainColor, Modifier.weight(1.2f), onClick = onCopy)
        RawtextFilledButton("解析", Color(0xFF3367D6), Modifier.weight(1f), onClick = onDecode)
        RawtextFilledButton("模拟器", Color(0xFF00796B), Modifier.weight(1f), onClick = onSimulator)
    }
}

@Composable
private fun CompactColorChip(format: RawtextFormat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(format.color)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = format.code,
            style = TextStyle(
                color = if (format.isLight) Color.Black else Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = format.name,
            style = TextStyle(
                color = if (format.isLight) Color.Black.copy(alpha = 0.74f) else Color.White.copy(alpha = 0.84f),
                fontSize = 12.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun PreviewPanel(previewText: AnnotatedString, isPreviewEmpty: Boolean) {
    SectionCard(
        title = "实时预览",
        subtitle = "按 Minecraft 习惯模拟颜色和格式，实际游戏渲染可能略有差异。"
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            if (isPreviewEmpty) {
                Text(
                    text = "预览将显示在这里...",
                    style = TextStyle(color = Color.White.copy(alpha = 0.46f), fontStyle = FontStyle.Italic)
                )
            } else {
                Text(
                    text = previewText,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }
}

@Composable
private fun JsonOutputPanel(
    generatedJson: String,
    validationMessages: List<String>,
    onCopy: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(title = "生成的 JSON", subtitle = "复制按钮已前置；这里只在需要排查时展开查看原文。") {
        if (validationMessages.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFA000).copy(alpha = 0.14f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                validationMessages.forEach { message ->
                    Text(text = message, style = TextStyle(fontSize = 12.sp, color = Color(0xFFFFA000)))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RawtextFilledButton("复制", CHelperTheme.colors.mainColor, Modifier.weight(1f), onClick = onCopy)
            RawtextFilledButton(
                text = if (expanded) "收起源码" else "查看源码",
                color = CHelperTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
                onClick = { expanded = !expanded }
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CHelperTheme.colors.background)
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = generatedJson,
                    style = TextStyle(
                        color = CHelperTheme.colors.textMain,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CHelperTheme.colors.backgroundComponent)
            .padding(15.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CHelperTheme.colors.textMain)
        )
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(text = it, style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary))
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun FormatChip(format: RawtextFormat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(format.color.copy(alpha = 0.18f))
            .border(BorderStroke(1.dp, format.color.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = format.code,
            style = TextStyle(
                color = format.color,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (format.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (format.italic) FontStyle.Italic else FontStyle.Normal
            )
        )
        Spacer(Modifier.width(5.dp))
        Text(text = format.name, style = TextStyle(color = CHelperTheme.colors.textMain, fontSize = 13.sp))
    }
}

@Composable
private fun ColorFormatButton(format: RawtextFormat, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(format.color)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = format.code,
            style = TextStyle(
                color = if (format.isLight) Color.Black else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = format.name,
            style = TextStyle(
                color = if (format.isLight) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.82f),
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 计分板 / 翻译这类只有简单字段的功能段编辑弹窗 */
@Composable
private fun SimpleFeatureEditDialog(
    segment: RawtextSegment,
    items: List<NamedEntry>,
    slots: List<NamedEntry>,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var scoreName by remember(segment.id) { mutableStateOf(segment.scoreName) }
    var scoreObjective by remember(segment.id) { mutableStateOf(segment.scoreObjective) }
    var translateKey by remember(segment.id) { mutableStateOf(segment.translateKey) }
    var translateWithJson by remember(segment.id) { mutableStateOf(segment.translateWithJson) }
    var selectorEditorVisible by remember { mutableStateOf(false) }

    CustomDialog(
        onDismissRequest = onDismiss,
        properties = CustomDialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogContainer(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 520.dp),
            backgroundNoTranslate = true,
            cornerSize = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Text(
                    text = "编辑${segment.title}",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(14.dp))
                when (segment.type) {
                    RawtextSegmentType.Score -> {
                        RawtextLabeledField(
                            label = "计分对象",
                            value = scoreName,
                            onValueChange = { scoreName = it },
                            hint = "@p 或玩家名",
                            trailing = {
                                RawtextSmallIconButton(
                                    text = "编辑",
                                    color = CHelperTheme.colors.mainColor,
                                    onClick = { selectorEditorVisible = true }
                                )
                            }
                        )
                        RawtextLabeledField(
                            label = "计分项",
                            value = scoreObjective,
                            onValueChange = { scoreObjective = it },
                            hint = "money / score"
                        )
                    }

                    RawtextSegmentType.Translate -> {
                        RawtextLabeledField(
                            label = "翻译键",
                            value = translateKey,
                            onValueChange = { translateKey = it },
                            hint = "commands.give.success"
                        )
                        RawtextLabeledField(
                            label = "with 参数 JSON",
                            value = translateWithJson,
                            onValueChange = { translateWithJson = it },
                            hint = "[{\"text\":\"Steve\"}]",
                            minHeight = 100.dp
                        )
                    }

                    else -> Unit
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RawtextFilledButton("取消", CHelperTheme.colors.textSecondary, Modifier.weight(1f), onClick = onDismiss)
                    RawtextFilledButton("保存", CHelperTheme.colors.mainColor, Modifier.weight(1f)) {
                        segment.scoreName = scoreName
                        segment.scoreObjective = scoreObjective
                        segment.translateKey = translateKey
                        segment.translateWithJson = translateWithJson
                        onCommit()
                        onDismiss()
                    }
                }
            }
        }
    }

    if (selectorEditorVisible) {
        SelectorEditorDialog(
            initialSelector = scoreName.ifBlank { "@p" },
            items = items,
            slots = slots,
            onApply = { scoreName = it; selectorEditorVisible = false },
            onDismiss = { selectorEditorVisible = false }
        )
    }
}

@Composable
private fun RawtextDecodeDialog(
    onPasteFromClipboard: ((String) -> Unit) -> Unit,
    onDecode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    CustomDialog(
        onDismissRequest = onDismiss,
        properties = CustomDialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogContainer(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp),
            backgroundNoTranslate = true,
            cornerSize = 22.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(text = "解析 RawJSON", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "粘贴已有 {\"rawtext\":[...]}，会拆回文本段和功能段。",
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
                )
                Spacer(Modifier.height(12.dp))
                RawtextLabeledField(
                    label = "JSON 内容",
                    value = input,
                    onValueChange = { input = it },
                    hint = "{\n  \"rawtext\": [\n    {\"text\":\"Hello\"}\n  ]\n}",
                    minHeight = 220.dp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RawtextFilledButton("粘贴", Color(0xFF3367D6), Modifier.weight(1f)) {
                        onPasteFromClipboard { input = it }
                    }
                    RawtextFilledButton("取消", CHelperTheme.colors.textSecondary, Modifier.weight(1f), onClick = onDismiss)
                    RawtextFilledButton("解析", CHelperTheme.colors.mainColor, Modifier.weight(1f), enabled = input.isNotBlank()) {
                        onDecode(input)
                    }
                }
            }
        }
    }
}

@Composable
private fun RawtextSimulatorDialog(
    mockPlayerP: String,
    onUpdateMockPlayerP: (String) -> Unit,
    mockPlayerR: String,
    onUpdateMockPlayerR: (String) -> Unit,
    mockPlayerA: String,
    onUpdateMockPlayerA: (String) -> Unit,
    mockPlayerS: String,
    onUpdateMockPlayerS: (String) -> Unit,
    mockScore: String,
    onUpdateMockScore: (String) -> Unit,
    mockTranslate: String,
    onUpdateMockTranslate: (String) -> Unit,
    mockCondition: String,
    onUpdateMockCondition: (String) -> Unit,
    onPersistMockSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    CustomDialog(
        onDismissRequest = onDismiss,
        properties = CustomDialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogContainer(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 650.dp),
            backgroundNoTranslate = true,
            cornerSize = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Text(text = "预览模拟器", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "这些值只影响本页预览，不会写进生成 JSON。",
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
                )
                Spacer(Modifier.height(12.dp))
                RawtextLabeledField("@p 显示名", mockPlayerP, onUpdateMockPlayerP, "Steve")
                RawtextLabeledField("@r 显示名", mockPlayerR, onUpdateMockPlayerR, "Alex")
                RawtextLabeledField("@a 显示名", mockPlayerA, onUpdateMockPlayerA, "Steve, Alex, ...")
                RawtextLabeledField("@s 显示名", mockPlayerS, onUpdateMockPlayerS, "执行者")
                RawtextLabeledField("计分板显示值", mockScore, onUpdateMockScore, "100")
                RawtextLabeledField("翻译显示值", mockTranslate, onUpdateMockTranslate, "翻译文本")
                RawtextLabeledField("条件显示值", mockCondition, onUpdateMockCondition, "条件内容")
                RawtextFilledButton("保存设置", CHelperTheme.colors.mainColor, Modifier.fillMaxWidth()) {
                    onPersistMockSettings()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun RawtextSettingsDialog(
    copyFormat: String,
    onUpdateCopyFormat: (String) -> Unit,
    autoSaveEnabled: Boolean,
    onSetAutoSave: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    CustomDialog(
        onDismissRequest = onDismiss,
        properties = CustomDialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogContainer(
            modifier = Modifier.fillMaxWidth(0.9f),
            backgroundNoTranslate = true,
            cornerSize = 22.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(text = "设置", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(14.dp))
                RawtextSectionLabel("复制 JSON 格式")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RawtextChipButton(
                        text = "格式化",
                        color = CHelperTheme.colors.mainColor,
                        selected = copyFormat == "formatted",
                        modifier = Modifier.weight(1f),
                        onClick = { onUpdateCopyFormat("formatted") }
                    )
                    RawtextChipButton(
                        text = "压缩",
                        color = CHelperTheme.colors.mainColor,
                        selected = copyFormat == "compressed",
                        modifier = Modifier.weight(1f),
                        onClick = { onUpdateCopyFormat("compressed") }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        RawtextSectionLabel("动态保存")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "自动保存编辑内容，下次进来恢复。",
                            style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
                        )
                    }
                    Switch(checked = autoSaveEnabled, onCheckedChange = { onSetAutoSave(it) })
                }
                Spacer(Modifier.height(18.dp))
                RawtextFilledButton("完成", CHelperTheme.colors.mainColor, Modifier.fillMaxWidth(), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun RawtextAboutDialog(onDismiss: () -> Unit) {
    CustomDialog(onDismissRequest = onDismiss) {
        DialogContainer(modifier = Modifier.widthIn(max = 360.dp), backgroundNoTranslate = true, cornerSize = 20.dp) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(text = "关于", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Minecraft 基岩版 RawJSON 文本生成器。\n" +
                        "交互机制参考开源项目 Akanyi/AkayiRawjsonweb，已适配 CHelper 的移动端与主题系统。",
                    style = TextStyle(fontSize = 13.sp, color = CHelperTheme.colors.textMain, lineHeight = 20.sp)
                )
                Spacer(Modifier.height(16.dp))
                RawtextFilledButton("知道了", CHelperTheme.colors.mainColor, Modifier.fillMaxWidth(), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun AutoSavePromptDialog(onChoose: (Boolean) -> Unit) {
    CustomDialog(onDismissRequest = { onChoose(false) }) {
        DialogContainer(modifier = Modifier.widthIn(max = 340.dp), backgroundNoTranslate = true, cornerSize = 20.dp) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(text = "开启动态保存？", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "开启后编辑内容会自动保存到本地，下次进来不会丢失。可在设置里随时改。",
                    style = TextStyle(fontSize = 13.sp, color = CHelperTheme.colors.textSecondary, lineHeight = 20.sp)
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RawtextFilledButton("不用了", CHelperTheme.colors.textSecondary, Modifier.weight(1f)) { onChoose(false) }
                    RawtextFilledButton("开启", CHelperTheme.colors.mainColor, Modifier.weight(1f)) { onChoose(true) }
                }
            }
        }
    }
}

@Composable
private fun RawtextScreenPreviewStub() {
    val segments = remember {
        listOf(
            RawtextSegment(1, RawtextSegmentType.Text, TextFieldValue("Hello ")),
            RawtextSegment(2, RawtextSegmentType.Selector, selector = "@a"),
            RawtextSegment(3, RawtextSegmentType.Text, TextFieldValue("!"))
        )
    }
    RawtextScreenContent(
        segments = segments,
        activeTextSegmentId = 1L,
        onUpdateActiveTextSegmentId = {},
        copyFormat = "formatted",
        onUpdateCopyFormat = {},
        autoSaveEnabled = false,
        onSetAutoSave = {},
        showAutoSavePrompt = false,
        onConfirmAutoSave = {},
        mockPlayerP = "Steve",
        onUpdateMockPlayerP = {},
        mockPlayerR = "Alex",
        onUpdateMockPlayerR = {},
        mockPlayerA = "Steve, Alex, ...",
        onUpdateMockPlayerA = {},
        mockPlayerS = "执行者",
        onUpdateMockPlayerS = {},
        mockScore = "100",
        onUpdateMockScore = {},
        mockTranslate = "翻译文本",
        onUpdateMockTranslate = {},
        mockCondition = "条件内容",
        onUpdateMockCondition = {},
        generatedJson = "{\n  \"rawtext\": [\n    {\"text\": \"Hello \"},\n    {\"selector\": \"@a\"},\n    {\"text\": \"!\"}\n  ]\n}",
        previewText = AnnotatedString("Hello Steve, Alex, ...!"),
        validationMessages = emptyList(),
        isPreviewEmpty = false,
        onUpdateTextSegment = { _, _ -> },
        onBackspaceAtStart = { false },
        onMoveSegment = { _, _ -> },
        onRemoveSegment = { },
        onAddFeature = { null },
        onApplyFormat = { },
        onClearAll = { },
        onPersistMockSettings = { },
        onDecodeRawJson = { Result.success(Unit) },
        onCommitSegmentEdit = { },
        onFindSegment = { null },
        onCopyByPreference = { },
        onPasteFromClipboard = { }
    )
}

@Preview(device = Devices.DESKTOP)
@Composable
fun RawtextScreenLightThemeDesktopPreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        RawtextScreenPreviewStub()
    }
}

@Preview
@Composable
fun RawtextScreenLightThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        RawtextScreenPreviewStub()
    }
}

@Preview
@Composable
fun RawtextScreenDarkThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        RawtextScreenPreviewStub()
    }
}
