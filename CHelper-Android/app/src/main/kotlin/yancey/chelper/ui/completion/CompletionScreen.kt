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

package yancey.chelper.ui.completion

import android.app.Application
import android.content.ClipData
import android.util.TypedValue
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.hjq.toast.Toaster
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.android.widget.CommandEditText
import yancey.chelper.core.ErrorReason
import yancey.chelper.core.SelectedString
import yancey.chelper.core.Theme
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.ui.HistoryScreenKey
import yancey.chelper.ui.LocalLibraryListScreenKey
import yancey.chelper.ui.PublicLibraryListScreenKey
import yancey.chelper.core.Suggestion
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.SuggestionUi.oneLineText
import yancey.chelper.ui.common.SuggestionUi.sourceLabel
import yancey.chelper.ui.common.layout.RootView
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text


@Composable
fun ToolbarItem(@DrawableRes id: Int, description: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(5.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            id = id,
            modifier = Modifier.size(24.dp),
            contentDescription = description
        )
        Text(
            text = description,
            style = TextStyle(fontSize = 14.sp)
        )
    }
}

@Composable
fun CompletionScreenTopBar(
    structure: String?,
    paramHint: String?,
    errorReasons: Array<ErrorReason>?,
    fontSize: TextUnit = TextUnit.Unspecified,
    onErrorClick: (ErrorReason) -> Unit = {}
) {
    Column {
        Text(
            text = structure ?: "欢迎使用CHelper",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp),
            style = TextStyle(
                fontSize = fontSize,
            )
        )
        Text(
            text = paramHint ?: "作者：Yancey",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp),
            style = TextStyle(
                color = CHelperTheme.colors.textSecondary,
                fontSize = fontSize,
            )
        )
        if (!errorReasons.isNullOrEmpty()) {
            if (errorReasons.size > 1) {
                Text(
                    text = "可能的错误原因：",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 5.dp),
                    style = TextStyle(
                        color = CHelperTheme.colors.textErrorReason,
                        fontSize = fontSize,
                    )
                )
            }
            errorReasons.forEachIndexed { index, error ->
                Text(
                    text = if (errorReasons.size == 1) {
                        "${error.errorReason ?: "未知错误"} · 点此定位"
                    } else {
                        "${index + 1}. ${error.errorReason ?: "未知错误"} · 点此定位"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onErrorClick(error) }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    style = TextStyle(
                        color = CHelperTheme.colors.textErrorReason,
                        fontSize = fontSize,
                    )
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(CHelperTheme.colors.line)
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun CompletionScreen(
    viewModel: CompletionViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    shutdown: () -> Unit = {},
    hideView: () -> Unit = {},
    isScreenVisible: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val commandEditorHeight = (LocalConfiguration.current.screenHeightDp / 3)
        .coerceIn(120, 220)
        .dp
    val settingsDataStore = remember(context) { SettingsDataStore(context) }
    val cpackBranch by settingsDataStore.cpackBranch()
        .collectAsState(initial = "")
    val isCrowded by settingsDataStore.isCrowded()
        .collectAsState(initial = false)
    val isHideWindowWhenCopying by settingsDataStore.isHideWindowWhenCopying()
        .collectAsState(initial = false)
    val isSavingWhenPausing by settingsDataStore.isSavingWhenPausing()
        .collectAsState(initial = false)
    val isCheckingBySelection by settingsDataStore.isCheckingBySelection()
        .collectAsState(initial = true)
    val isSyntaxHighlight by settingsDataStore.isSyntaxHighlight()
        .collectAsState(initial = false)
    val isShowErrorReason by settingsDataStore.isShowErrorReason()
        .collectAsState(initial = false)
    val syntaxHighlightMaxLength by settingsDataStore.syntaxHighlightMaxLength()
        .collectAsState(initial = 20000)
    var isCommandEditorHintVisible by remember { mutableStateOf(false) }
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { source, _ ->
            isLifecycleResumed = source.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        viewModel.nodeCount,
        viewModel.isCommandEditorMode,
        isScreenVisible,
        isLifecycleResumed
    ) {
        if (!shouldShowCommandEditorHint(
                viewModel.nodeCount,
                viewModel.isCommandEditorMode,
                isScreenVisible && isLifecycleResumed
            )
        ) {
            isCommandEditorHintVisible = false
        } else if (settingsDataStore.claimCommandEditorHint()) {
            isCommandEditorHintVisible = true
        }
    }

    DisposableEffect(viewModel, syntaxHighlightMaxLength) {
        viewModel.syntaxHighlightMaxLength = syntaxHighlightMaxLength
        onDispose { }
    }

    DisposableEffect(isSavingWhenPausing) {
        if (isSavingWhenPausing) {
            viewModel.resumeText()
        }
        onDispose { }
    }
    DisposableEffect(viewModel, cpackBranch) {
        viewModel.refreshCHelperCore(
            context,
            cpackBranch,
            isCheckingBySelection,
            isSyntaxHighlight,
            isShowErrorReason
        )
        onDispose { }
    }
    // 回到前台（含从设置/资源包管理页返回）时按最新段与启用拓展包配置刷新内核
    // （refresh 内部会比对段与配置指纹，无变化时零成本跳过）
    LaunchedEffect(isLifecycleResumed) {
        if (isLifecycleResumed) {
            viewModel.refreshCHelperCore(
                context,
                cpackBranch,
                isCheckingBySelection,
                isSyntaxHighlight,
                isShowErrorReason
            )
        }
    }
    val clipboard = LocalClipboard.current
    val commandEditText = remember { arrayOfNulls<CommandEditText>(1) }
    val onErrorClick: (ErrorReason) -> Unit = { error ->
        commandEditText[0]?.focusErrorRange(error.start, error.end)
    }
    RootView {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CHelperTheme.colors.backgroundComponent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (!isCrowded) {
                    CompletionScreenTopBar(
                        viewModel.structure,
                        viewModel.paramHint,
                        if (isShowErrorReason) viewModel.errorReasons else null,
                        onErrorClick = onErrorClick
                    )
                }
                // tellraw/titleraw 的 JSON 参数位：以"补全项"样式在列表首行提供 rawtext 编辑器入口
                val commandText = viewModel.command.text.toString()
                val caretPosition = viewModel.command.selection.start
                val jsonParamStart = remember(commandText) { detectJsonParameterStart(commandText) }
                val jsonEntryActive = jsonParamStart != null && caretPosition >= jsonParamStart
                // 返回本页时自动回填编辑器产物（会话 revision 驱动，与页面生命周期无关）
                JsonEditorSessionConsumer(
                    viewModel = viewModel,
                    isCheckingBySelection = isCheckingBySelection,
                    isSyntaxHighlight = isSyntaxHighlight,
                    isShowErrorReason = isShowErrorReason,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(
                        viewModel.suggestionsSize +
                                (if (isCrowded) 1 else 0) +
                                (if (jsonEntryActive) 1 else 0)
                    ) { listIndex ->
                        if (isCrowded) {
                            when {
                                listIndex == 0 -> CompletionScreenTopBar(
                                    viewModel.structure,
                                    viewModel.paramHint,
                                    if (isShowErrorReason) viewModel.errorReasons else null,
                                    14.sp,
                                    onErrorClick
                                )

                                jsonEntryActive && listIndex == 1 -> JsonEditorActionRow(
                                    text = commandText,
                                    jsonStart = jsonParamStart,
                                    navController = navController,
                                )

                                else -> {
                                    val realIndex = listIndex - 1 - (if (jsonEntryActive) 1 else 0)
                                    val suggestionText =
                                        remember(viewModel.suggestionsUpdateTimes, realIndex, listIndex) {
                                            viewModel.getSuggestion(realIndex)?.oneLineText().orEmpty()
                                        }
                                    Text(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = {
                                                viewModel.onItemClick(realIndex)
                                                viewModel.onSelectionChanged(
                                                    isCheckingBySelection,
                                                    isSyntaxHighlight,
                                                    isShowErrorReason
                                                )
                                            })
                                            .padding(5.dp),
                                        text = suggestionText,
                                        style = TextStyle(
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                            }
                        } else {
                            if (jsonEntryActive && listIndex == 0) {
                                JsonEditorActionRow(
                                    text = commandText,
                                    jsonStart = jsonParamStart,
                                    navController = navController,
                                )
                            } else {
                                val realIndex = listIndex - (if (jsonEntryActive) 1 else 0)
                                SuggestionDoubleLineRow(
                                    suggestionProvider = { viewModel.getSuggestion(realIndex) },
                                    updateTimes = viewModel.suggestionsUpdateTimes,
                                    index = realIndex,
                                    listIndex = listIndex,
                                    onClick = {
                                        viewModel.onItemClick(realIndex)
                                        viewModel.onSelectionChanged(
                                            isCheckingBySelection,
                                            isSyntaxHighlight,
                                            isShowErrorReason
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (viewModel.isShowMenu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CHelperTheme.colors.background)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ToolbarItem(
                        id = R.drawable.arrow_back_up,
                        description = stringResource(R.string.layout_completion_undo),
                        onClick = {
                            viewModel.command.undoState.undo()
                        }
                    )
                    ToolbarItem(
                        id = R.drawable.arrow_forward_up,
                        description = stringResource(R.string.layout_completion_redo),
                        onClick = {
                            viewModel.command.undoState.redo()
                        }
                    )
                    ToolbarItem(
                        id = R.drawable.x,
                        description = stringResource(R.string.layout_completion_clear),
                        onClick = {
                            viewModel.command.clearText()
                        }
                    )
                    ToolbarItem(
                        id = R.drawable.history,
                        description = stringResource(R.string.layout_completion_history),
                        onClick = {
                            navController.navigate(HistoryScreenKey)
                        }
                    )
                    ToolbarItem(
                        id = R.drawable.box,
                        description = stringResource(R.string.layout_completion_local_library),
                        onClick = {
                            navController.navigate(LocalLibraryListScreenKey)
                        }
                    )
                    ToolbarItem(
                        id = R.drawable.book,
                        description = "网络库",
                        onClick = {
                            navController.navigate(PublicLibraryListScreenKey)
                        }
                    )
                    ToolbarItem(
                        id = R.drawable.power,
                        description = stringResource(R.string.layout_completion_shut_down),
                        onClick = {
                            shutdown()
                        }
                    )
                }
            }
            if (isCommandEditorHintVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CHelperTheme.colors.background)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CHelperTheme.colors.mainColor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "命令有点长了，长按下方箭头可进入多行编辑",
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.isCommandEditorMode = true
                                isCommandEditorHintVisible = false
                                Toaster.show("已开启多行编辑，再次长按箭头退出")
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        style = TextStyle(color = Color.White, fontSize = 13.sp)
                    )
                    Text(
                        text = "×",
                        modifier = Modifier
                            .clickable { isCommandEditorHintVisible = false }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        style = TextStyle(color = Color.White, fontSize = 18.sp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (viewModel.isCommandEditorMode) commandEditorHeight else 40.dp)
                    .background(CHelperTheme.colors.background),
                verticalAlignment = if (viewModel.isCommandEditorMode) Alignment.Top else Alignment.CenterVertically
            ) {
                Icon(
                    id = if (viewModel.isShowMenu) R.drawable.chevron_down else R.drawable.chevron_up,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { viewModel.isShowMenu = !viewModel.isShowMenu },
                            onLongClickLabel = if (viewModel.isCommandEditorMode) "退出多行编辑" else "进入多行编辑",
                            onLongClick = {
                                viewModel.isCommandEditorMode = !viewModel.isCommandEditorMode
                                isCommandEditorHintVisible = false
                                if (viewModel.isCommandEditorMode) {
                                    viewModel.viewModelScope.launch {
                                        settingsDataStore.claimCommandEditorHint()
                                    }
                                }
                                Toaster.show(
                                    if (viewModel.isCommandEditorMode) {
                                        "已开启多行编辑，再次长按箭头退出"
                                    } else {
                                        "已退出多行编辑"
                                    }
                                )
                            }
                        )
                        .padding(8.dp)
                        .size(24.dp),
                    contentDescription = stringResource(R.string.layout_completion_icon_show_menu_content_description) +
                            if (viewModel.isCommandEditorMode) "，长按退出多行编辑" else "，长按进入多行编辑"
                )
                val theme = CHelperTheme.theme
                val textMain = CHelperTheme.colors.textMain
                val textSecondary = CHelperTheme.colors.textSecondary
                val hintStr = stringResource(R.string.layout_completion_command_hint)
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    factory = { context ->
                        CommandEditText(context).apply {
                            commandEditText[0] = this
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            background = null
                            hint = hintStr
                            setEditorMode(viewModel.isCommandEditorMode)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                            setListener({ str ->
                                viewModel.command.edit {
                                    replace(0, length, str)
                                    selection = TextRange(selectionStart, selectionEnd)
                                }
                            }, {
                                viewModel.command.edit {
                                    selection = TextRange(selectionStart, selectionEnd)
                                }
                            })
                        }
                    },
                    update = { view ->
                        view.setEditorMode(viewModel.isCommandEditorMode)
                        // 主题相关的样式在 update 中应用，
                        // 否则悬浮窗首帧或运行时切换主题后，EditText 仍停留在亮色文本主题
                        view.setTextColor(textMain.toArgb())
                        view.setHintTextColor(textSecondary.toArgb())
                        view.setTheme(
                            if (theme == CHelperTheme.Theme.Light) Theme.THEME_DAY else Theme.THEME_NIGHT,
                            textMain.toArgb()
                        )
                        val str = viewModel.command.text.toString()
                        val selectionStart = viewModel.command.selection.start
                        val selectionEnd = viewModel.command.selection.end
                        if (view.text.toString() != str ||
                            view.selectionStart != selectionStart ||
                            view.selectionEnd != selectionEnd
                        ) {
                            view.setSelectedString(
                                SelectedString(
                                    str,
                                    selectionStart,
                                    selectionEnd
                                )
                            )
                        }
                        view.setErrorReasons(viewModel.errorReasons)
                        view.setColors(viewModel.syntaxHighlightTokens)
                    }
                )
                Icon(
                    id = R.drawable.copy,
                    modifier = Modifier
                        .clickable {
                            viewModel.onCopy(viewModel.command.text.toString())
                            viewModel.viewModelScope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        ClipData.newPlainText(
                                            null,
                                            viewModel.command.text
                                        )
                                    )
                                )
                                Toaster.show("已复制")
                            }
                            if (isHideWindowWhenCopying) {
                                hideView()
                            }
                        }
                        .padding(8.dp)
                        .size(24.dp),
                    contentDescription = stringResource(R.string.common_icon_copy_content_description)
                )
            }
        }
        DisposableEffect(viewModel.command.text, viewModel.command.selection) {
            viewModel.onSelectionChanged(
                isCheckingBySelection,
                isSyntaxHighlight,
                isShowErrorReason
            )
            onDispose { }
        }
    }
}

@Preview
@Composable
fun CompletionScreenLightThemePreview() {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = remember {
        CompletionViewModel(application).apply {
            isShowMenu = true
            suggestionsSize = 20
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        CompletionScreen(
            viewModel = viewModel
        )
    }
}

@Preview
@Composable
fun CompletionScreenDarkThemePreview() {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = remember {
        CompletionViewModel(application).apply {
            isShowMenu = true
            suggestionsSize = 20
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        CompletionScreen(
            viewModel = viewModel
        )
    }
}

/** 双行建议行：名字 + 说明 +（来源徽标）。点击整行应用该建议。 */
@Composable
private fun SuggestionDoubleLineRow(
    suggestionProvider: () -> Suggestion?,
    updateTimes: Int,
    index: Int,
    listIndex: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(5.dp)
    ) {
        val suggestion = remember(updateTimes, index, listIndex) { suggestionProvider() }
        suggestion?.name?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(fontSize = 14.sp)
            )
        }
        suggestion?.description?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = CHelperTheme.colors.textSecondary, fontSize = 14.sp)
            )
        }
        // 来源标注：拓展包候选在说明下方显示"来自 XX"（文案统一见 SuggestionUi）
        suggestion?.sourceLabel()?.let { source ->
            Text(
                text = source,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = CHelperTheme.colors.mainColorSecondary, fontSize = 12.sp)
            )
        }
    }
}
