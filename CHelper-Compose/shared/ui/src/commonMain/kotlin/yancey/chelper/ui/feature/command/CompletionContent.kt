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
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.layout.RootView
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text

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
                LazyColumn(state = suggestionsState, modifier = Modifier.fillMaxSize().weight(1f)) {
                    items(if (isCrowded) (suggestionCount + 1) else suggestionCount) {
                        suggestionIndex ->
                        if (isCrowded) {
                            if (suggestionIndex == 0) {
                                CompletionScreenTopBar(
                                    state.structure,
                                    state.hint,
                                    if (isShowErrorReason) state.problems else null,
                                    14.sp,
                                    onErrorClick,
                                    resultsEnabled,
                                )
                            } else {
                                val realIndex = suggestionIndex - 1
                                val suggestion = suggestionAt(realIndex)
                                val suggestionText =
                                    suggestion?.let {
                                        it.name +
                                            (it.description?.let { description ->
                                                " - $description"
                                            } ?: "")
                                    } ?: ""
                                Text(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .clickable(
                                                enabled = resultsEnabled && suggestion != null,
                                                onClick = { onSuggestion(realIndex) },
                                            )
                                            .heightIn(min = 32.dp)
                                            .padding(5.dp),
                                    text = suggestionText,
                                    style = TextStyle(fontSize = 14.sp),
                                )
                            }
                        } else {
                            val suggestion = suggestionAt(suggestionIndex)
                            Column(
                                modifier =
                                    Modifier.clickable(
                                            enabled = resultsEnabled && suggestion != null,
                                            onClick = { onSuggestion(suggestionIndex) },
                                        )
                                        .heightIn(min = 32.dp)
                                        .padding(5.dp)
                            ) {
                                suggestion?.name?.let {
                                    Text(
                                        text = it,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = TextStyle(fontSize = 14.sp),
                                    )
                                }
                                suggestion?.description?.let {
                                    Text(
                                        text = it,
                                        modifier = Modifier.fillMaxWidth(),
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
                        id = Res.drawable.arrow_back_up,
                        description = stringResource(Res.string.layout_completion_undo),
                        onClick = { onAction(CompletionAction.Undo) },
                    )
                    ToolbarItem(
                        id = Res.drawable.arrow_forward_up,
                        description = stringResource(Res.string.layout_completion_redo),
                        onClick = { onAction(CompletionAction.Redo) },
                    )
                    ToolbarItem(
                        id = Res.drawable.x,
                        description = stringResource(Res.string.layout_completion_clear),
                        onClick = { onAction(CompletionAction.Clear) },
                    )
                    ToolbarItem(
                        id = Res.drawable.history,
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
                    Text(
                        text = "×",
                        modifier =
                            Modifier.clickable { onAction(CompletionAction.DismissHint) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        style = TextStyle(color = Color.White, fontSize = 18.sp),
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
