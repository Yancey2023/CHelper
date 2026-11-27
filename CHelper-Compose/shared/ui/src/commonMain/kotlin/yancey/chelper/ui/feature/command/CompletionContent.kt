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

package yancey.chelper.ui.feature.command

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.*
import yancey.chelper.domain.command.CommandProblem
import yancey.chelper.domain.command.CommandResult
import yancey.chelper.domain.command.CommandSuggestion
import yancey.chelper.domain.command.SuggestionAudioPlayer
import yancey.chelper.domain.command.SuggestionPreview
import yancey.chelper.domain.command.isWikiSuggestionAudioUrl
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.layout.RootView
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.platform.ClientMessages

enum class CompletionAction {
    Undo,
    Redo,
    Clear,
    History,
    LocalLibrary,
    PublicLibrary,
    Back,
    ToggleMenu,
    ToggleEditor,
    EnableEditor,
    DismissHint,
    Copy,
}

@Composable
fun ToolbarItem(id: DrawableResource, description: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.padding(5.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(id = id, modifier = Modifier.size(24.dp), contentDescription = description)
        Text(text = description, style = TextStyle(fontSize = 14.sp))
    }
}

@Composable
fun CompletionScreenTopBar(
    structure: String?,
    paramHint: String?,
    errorReasons: List<CommandProblem>?,
    fontSize: TextUnit = TextUnit.Unspecified,
    onErrorClick: (CommandProblem) -> Unit = {},
    resultsEnabled: Boolean = true,
) {
    Column {
        Text(
            text = structure?.takeIf(String::isNotEmpty) ?: "欢迎使用CHelper",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
            style = TextStyle(fontSize = fontSize),
        )
        Text(
            text = paramHint?.takeIf(String::isNotEmpty) ?: "作者：Yancey",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
            style = TextStyle(color = CHelperTheme.colors.textSecondary, fontSize = fontSize),
        )
        if (!errorReasons.isNullOrEmpty()) {
            if (errorReasons.size > 1) {
                Text(
                    text = "可能的错误原因：",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                    style =
                        TextStyle(color = CHelperTheme.colors.textErrorReason, fontSize = fontSize),
                )
            }
            errorReasons.forEachIndexed { index, error ->
                Text(
                    text =
                        if (errorReasons.size == 1) {
                            "${error.message} · 点此定位"
                        } else {
                            "${index + 1}. ${error.message} · 点此定位"
                        },
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable(enabled = resultsEnabled) { onErrorClick(error) }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    style =
                        TextStyle(color = CHelperTheme.colors.textErrorReason, fontSize = fontSize),
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CHelperTheme.colors.line))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompletionContent(
    state: CommandResult,
    suggestionCount: Int = state.suggestions.size,
    suggestionAt: @Composable (Int) -> CommandSuggestion? = { state.suggestions.getOrNull(it) },
    resultsEnabled: Boolean = true,
    previewAudioPlayer: SuggestionAudioPlayer? = null,
    previewRevision: Long = 0,
    onVisibleSuggestions: (List<Int>) -> Unit = {},
    showMenu: Boolean,
    editorMode: Boolean,
    isCrowded: Boolean = false,
    isShowErrorReason: Boolean = true,
    isCommandEditorHintVisible: Boolean = false,
    commandEditorHeight: Dp = 180.dp,
    onErrorClick: (CommandProblem) -> Unit,
    onSuggestion: (Int) -> Unit,
    onAction: (CompletionAction) -> Unit,
    editor: @Composable (Modifier) -> Unit,
) {
    val suggestionsState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val audioController =
        remember(previewRevision, resultsEnabled, previewAudioPlayer) {
            previewAudioPlayer?.let { player ->
                AudioPreviewController(scope, player) { ClientMessages.show("无法试听该音频") }
            }
        }
    DisposableEffect(audioController) {
        onDispose { audioController?.clear() }
    }
    val reportVisible by rememberUpdatedState(onVisibleSuggestions)
    LaunchedEffect(suggestionsState, isCrowded) {
        snapshotFlow {
            suggestionsState.layoutInfo.visibleItemsInfo.mapNotNull { row ->
                val index = row.index - if (isCrowded) 1 else 0
                index.takeIf { it >= 0 }
            }
        }
            .distinctUntilChanged()
            .collect { reportVisible(it) }
    }
    RootView {
        Column(
            modifier = Modifier.fillMaxSize().background(CHelperTheme.colors.backgroundComponent)
        ) {
            Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (!isCrowded) {
                    CompletionScreenTopBar(
                        state.structure,
                        state.hint,
                        if (isShowErrorReason) state.problems else null,
                        onErrorClick = onErrorClick,
                        resultsEnabled = resultsEnabled,
                    )
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val stackAudio = maxWidth <= 576.dp && maxHeight >= 128.dp
                    val audioWidth = (maxWidth * 0.32f).coerceIn(112.dp, 192.dp)
                    LazyColumn(state = suggestionsState, modifier = Modifier.fillMaxSize()) {
                        items(if (isCrowded) (suggestionCount + 1) else suggestionCount) {
                            suggestionIndex ->
                            if (isCrowded && suggestionIndex == 0) {
                                CompletionScreenTopBar(
                                    state.structure,
                                    state.hint,
                                    if (isShowErrorReason) state.problems else null,
                                    14.sp,
                                    onErrorClick,
                                    resultsEnabled,
                                )
                            } else {
                                val index = suggestionIndex - if (isCrowded) 1 else 0
                                val suggestion = suggestionAt(index)
                                val audio =
                                    (suggestion?.preview as? SuggestionPreview.Audio)
                                        ?.url
                                        ?.takeIf {
                                            previewAudioPlayer != null &&
                                                isWikiSuggestionAudioUrl(it)
                                        }
                                        ?.let { AudioPreviewRequest(index, it) }
                                val audioState by
                                    remember(audio, audioController) {
                                        derivedStateOf {
                                            audioController?.state?.takeIf { it.request == audio }
                                        }
                                    }
                                val controls: (@Composable (Modifier) -> Unit)? =
                                    if (audio != null && audioController != null) {
                                        { modifier ->
                                            SuggestionAudioControls(
                                                audioState,
                                                resultsEnabled,
                                                onToggle = { audioController.toggle(audio) },
                                                onSeek = { audioController.seek(audio, it) },
                                                modifier = modifier,
                                            )
                                        }
                                    } else null
                                CompletionSuggestionRow(
                                    suggestion = suggestion,
                                    compact = isCrowded,
                                    resultsEnabled = resultsEnabled,
                                    onApply = { onSuggestion(index) },
                                    stackAudio = stackAudio && audioState != null,
                                    audioModifier =
                                        if (audioState != null) Modifier.width(audioWidth)
                                        else Modifier,
                                    audioControls = controls,
                                )
                            }
                        }
                    }
                }
            }
            if (showMenu) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(CHelperTheme.colors.background)
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    ToolbarItem(
                        id = Res.drawable.undo,
                        description = stringResource(Res.string.layout_completion_undo),
                        onClick = { onAction(CompletionAction.Undo) },
                    )
                    ToolbarItem(
                        id = Res.drawable.redo,
                        description = stringResource(Res.string.layout_completion_redo),
                        onClick = { onAction(CompletionAction.Redo) },
                    )
                    ToolbarItem(
                        id = Res.drawable.x,
                        description = stringResource(Res.string.layout_completion_clear),
                        onClick = { onAction(CompletionAction.Clear) },
                    )
                    ToolbarItem(
                        id = Res.drawable.rotate_ccw_clock,
                        description = stringResource(Res.string.layout_completion_history),
                        onClick = { onAction(CompletionAction.History) },
                    )
                    ToolbarItem(
                        id = Res.drawable.box,
                        description = stringResource(Res.string.layout_completion_local_library),
                        onClick = { onAction(CompletionAction.LocalLibrary) },
                    )
                    ToolbarItem(
                        id = Res.drawable.book,
                        description = "网络库",
                        onClick = { onAction(CompletionAction.PublicLibrary) },
                    )
                    ToolbarItem(
                        id = Res.drawable.power,
                        description = stringResource(Res.string.layout_completion_shut_down),
                        onClick = { onAction(CompletionAction.Back) },
                    )
                }
            }
            if (isCommandEditorHintVisible) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(CHelperTheme.colors.background)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CHelperTheme.colors.mainColor),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "命令有点长了，长按下方箭头可进入多行编辑",
                        modifier =
                            Modifier.weight(1f)
                                .clickable { onAction(CompletionAction.EnableEditor) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        style = TextStyle(color = Color.White, fontSize = 13.sp),
                    )
                    Icon(
                        id = Res.drawable.x,
                        modifier =
                            Modifier.clickable { onAction(CompletionAction.DismissHint) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .size(18.dp),
                        contentDescription = "关闭提示",
                        tint = Color.White,
                    )
                }
            }
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(if (editorMode) commandEditorHeight else 40.dp)
                        .background(CHelperTheme.colors.background),
                verticalAlignment = if (editorMode) Alignment.Top else Alignment.CenterVertically,
            ) {
                Icon(
                    id = if (showMenu) Res.drawable.chevron_down else Res.drawable.chevron_up,
                    modifier =
                        Modifier.combinedClickable(
                                onClick = { onAction(CompletionAction.ToggleMenu) },
                                onLongClickLabel = if (editorMode) "退出多行编辑" else "进入多行编辑",
                                onLongClick = { onAction(CompletionAction.ToggleEditor) },
                            )
                            .padding(8.dp)
                            .size(24.dp),
                    contentDescription =
                        stringResource(
                            Res.string.layout_completion_icon_show_menu_content_description
                        ) + if (editorMode) "，长按退出多行编辑" else "，长按进入多行编辑",
                )

                editor(Modifier.fillMaxSize().weight(1f))
                Icon(
                    id = Res.drawable.copy,
                    modifier =
                        Modifier.clickable { onAction(CompletionAction.Copy) }
                            .padding(8.dp)
                            .size(24.dp),
                    contentDescription =
                        stringResource(Res.string.common_icon_copy_content_description),
                )
            }
        }
    }
}

@Composable
private fun CompletionSuggestionRow(
    suggestion: CommandSuggestion?,
    compact: Boolean,
    resultsEnabled: Boolean,
    onApply: () -> Unit,
    stackAudio: Boolean,
    audioModifier: Modifier,
    audioControls: (@Composable (Modifier) -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier =
                    Modifier.weight(1f)
                        .clickable(
                            enabled = resultsEnabled && suggestion != null,
                            onClick = onApply,
                        )
                        .heightIn(min = 32.dp)
                        .padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SuggestionVisualPreview(suggestion?.preview)
                Column(modifier = Modifier.weight(1f)) {
                    if (suggestion != null) {
                        val description = suggestion.description
                        Text(
                            text =
                                if (compact && description != null)
                                    "${suggestion.name} - $description"
                                else suggestion.name,
                            style = TextStyle(fontSize = 14.sp),
                        )
                        if (!compact && description != null) {
                            Text(
                                text = description,
                                style =
                                    TextStyle(
                                        color = CHelperTheme.colors.textSecondary,
                                        fontSize = 14.sp,
                                    ),
                            )
                        }
                    }
                }
            }
            if (!stackAudio) audioControls?.invoke(audioModifier.padding(end = 5.dp))
        }
        if (stackAudio)
            audioControls?.invoke(Modifier.fillMaxWidth().padding(start = 5.dp, end = 5.dp))
    }
}
