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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import yancey.chelper.domain.command.CommandProblem
import yancey.chelper.domain.command.CommandResult
import yancey.chelper.domain.command.CommandSuggestion
import yancey.chelper.domain.command.Suggestion
import yancey.chelper.resources.*
import yancey.chelper.ui.navigation.HistoryScreenKey
import yancey.chelper.ui.navigation.LocalLibraryListScreenKey
import yancey.chelper.ui.navigation.PublicLibraryListScreenKey
import yancey.chelper.ui.platform.LocalClientServices
import yancey.chelper.ui.platform.rememberCopyText

@Composable
private fun rememberSuggestion(viewModel: CompletionViewModel, index: Int): Suggestion? {
    val revision = viewModel.suggestionsUpdateTimes
    var suggestion by remember(viewModel, revision, index) { mutableStateOf<Suggestion?>(null) }
    LaunchedEffect(viewModel, revision, index) { suggestion = viewModel.getSuggestion(index) }
    return suggestion
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun CompletionScreen(
    viewModel: CompletionViewModel = rememberCompletionViewModel(),
    navController: NavHostController = rememberNavController(),
    shutdown: () -> Unit = {},
    hideView: () -> Unit = {},
    isScreenVisible: Boolean = true,
) {
    val context = LocalClientServices.current
    val copy = rememberCopyText()
    val lifecycleOwner = LocalLifecycleOwner.current
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val commandEditorHeight =
        with(density) { (windowSize.height.toDp() / 3).coerceIn(120.dp, 220.dp) }
    val settingsDataStore = remember(context) { context.settings }
    val cpackBranch by
        settingsDataStore.cpackBranch().collectAsStateWithLifecycle(initialValue = "")
    val isCrowded by settingsDataStore.isCrowded().collectAsStateWithLifecycle(initialValue = false)
    val isHideWindowWhenCopying by
        settingsDataStore
            .isHideWindowWhenCopying()
            .collectAsStateWithLifecycle(initialValue = false)
    val isSavingWhenPausing by
        settingsDataStore.isSavingWhenPausing().collectAsStateWithLifecycle(initialValue = false)
    val isCheckingBySelection by
        settingsDataStore.isCheckingBySelection().collectAsStateWithLifecycle(initialValue = true)
    val isSyntaxHighlight by
        settingsDataStore.isSyntaxHighlight().collectAsStateWithLifecycle(initialValue = false)
    val isShowErrorReason by
        settingsDataStore.isShowErrorReason().collectAsStateWithLifecycle(initialValue = false)
    val syntaxHighlightMaxLength by
        settingsDataStore
            .syntaxHighlightMaxLength()
            .collectAsStateWithLifecycle(initialValue = 20000)
    var isCommandEditorHintVisible by remember { mutableStateOf(false) }
    var isLifecycleResumed by
        remember(lifecycleOwner) {
            mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { source, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushDraft()
            isLifecycleResumed = source.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.flushDraft()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(
        viewModel.nodeCount,
        viewModel.isCommandEditorMode,
        isScreenVisible,
        isLifecycleResumed,
    ) {
        if (
            !shouldShowCommandEditorHint(
                viewModel.nodeCount,
                viewModel.isCommandEditorMode,
                isScreenVisible && isLifecycleResumed,
            )
        ) {
            isCommandEditorHintVisible = false
        } else if (settingsDataStore.claimCommandEditorHint()) {
            isCommandEditorHintVisible = true
        }
    }

    DisposableEffect(viewModel, isSavingWhenPausing) {
        viewModel.setDraftSaving(isSavingWhenPausing)
        onDispose { viewModel.flushDraft() }
    }
    LaunchedEffect(
        viewModel,
        cpackBranch,
        isCheckingBySelection,
        isSyntaxHighlight,
        isShowErrorReason,
        syntaxHighlightMaxLength,
    ) {
        snapshotFlow { viewModel.command.text.toString() to viewModel.command.selection }
            .collect {
                viewModel.analyze(
                    cpackBranch,
                    isCheckingBySelection,
                    isSyntaxHighlight,
                    isShowErrorReason,
                    syntaxHighlightMaxLength,
                )
            }
    }
    var errorRange by remember { mutableStateOf<CommandErrorSelection?>(null) }
    CompletionContent(
        state =
            CommandResult(
                structure = viewModel.structure.orEmpty(),
                hint = viewModel.paramHint.orEmpty(),
                problems =
                    viewModel.errorReasons.orEmpty().map {
                        CommandProblem(it.errorReason.orEmpty(), it.start, it.end)
                    },
            ),
        suggestionCount = viewModel.suggestionsSize,
        suggestionAt = { index ->
            rememberSuggestion(viewModel, index)?.let {
                CommandSuggestion(it.name.orEmpty(), it.description)
            }
        },
        showMenu = viewModel.isShowMenu,
        editorMode = viewModel.isCommandEditorMode,
        isCrowded = isCrowded,
        isShowErrorReason = isShowErrorReason,
        isCommandEditorHintVisible = isCommandEditorHintVisible,
        commandEditorHeight = commandEditorHeight,
        onErrorClick = { error ->
            errorRange =
                CommandErrorSelection(error.start, error.end, (errorRange?.request ?: 0) + 1)
        },
        onSuggestion = viewModel::onItemClick,
        onAction = { action ->
            when (action) {
                CompletionAction.Undo -> viewModel.command.undoState.undo()
                CompletionAction.Redo -> viewModel.command.undoState.redo()
                CompletionAction.Clear -> viewModel.command.clearText()
                CompletionAction.History -> navController.navigate(HistoryScreenKey)
                CompletionAction.LocalLibrary -> navController.navigate(LocalLibraryListScreenKey)
                CompletionAction.PublicLibrary -> navController.navigate(PublicLibraryListScreenKey)
                CompletionAction.Back -> shutdown()
                CompletionAction.ToggleMenu -> viewModel.isShowMenu = !viewModel.isShowMenu
                CompletionAction.ToggleEditor,
                CompletionAction.EnableEditor -> {
                    viewModel.setEditorMode(
                        action == CompletionAction.EnableEditor || !viewModel.isCommandEditorMode
                    )
                    isCommandEditorHintVisible = false
                    if (viewModel.isCommandEditorMode)
                        viewModel.viewModelScope.launch {
                            settingsDataStore.claimCommandEditorHint()
                        }
                }
                CompletionAction.DismissHint -> isCommandEditorHintVisible = false
                CompletionAction.Copy -> {
                    viewModel.onCopy(viewModel.command.text.toString())
                    copy(viewModel.command.text.toString())
                    if (isHideWindowWhenCopying) hideView()
                }
            }
        },
    ) { modifier ->
        ComposeCommandEditor(
            command = viewModel.command,
            editorMode = viewModel.isCommandEditorMode,
            tokens = viewModel.syntaxHighlightTokens,
            errors =
                viewModel.errorReasons.orEmpty().map {
                    CommandProblem(it.errorReason.orEmpty(), it.start, it.end)
                },
            modifier = modifier,
            errorSelection = errorRange,
        )
    }
}

@Composable
fun rememberCompletionViewModel(): CompletionViewModel {
    val services = LocalClientServices.current
    return viewModel {
        CompletionViewModel(services, services.commandDrafts, createSavedStateHandle())
    }
}
