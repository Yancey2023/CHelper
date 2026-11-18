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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import yancey.chelper.data.logging.ClientLog as Log
import yancey.chelper.domain.command.CommandAnalysisRequest
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.domain.command.CommandDraftRepository
import yancey.chelper.domain.command.CommandSession
import yancey.chelper.ui.platform.ClientDiagnostics as MonitorUtil
import yancey.chelper.ui.platform.ClientServices

class CompletionViewModel
constructor(
    services: ClientServices,
    draftRepository: CommandDraftRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    var isShowMenu by mutableStateOf(false)
    var isCommandEditorMode by mutableStateOf(savedState["editorMode"] ?: false)
        private set

    var command by mutableStateOf(TextFieldState())
        private set

    private val session = CommandSession(load = services.loadCommandEngine)
    private val completion =
        CommandCompletionController(session, viewModelScope, ::currentInput) { error ->
            Log.w("CompletionViewModel", "Unable to analyze command", error)
            MonitorUtil.generateCustomLog(error, "CommandAnalysisException")
        }
    val structure
        get() = if (completion.failure != null) "命令解析失败" else completion.snapshot.analysis.structure

    val paramHint
        get() = completion.failure ?: completion.snapshot.analysis.hint

    val errorReasons
        get() = completion.snapshot.analysis.errors

    val suggestionsSize
        get() = completion.snapshot.analysis.suggestionCount

    val syntaxHighlightTokens
        get() = completion.snapshot.analysis.tokens

    val nodeCount
        get() = completion.snapshot.analysis.nodeCount

    val analysisRevision
        get() = completion.snapshot.revision

    val resultsEnabled
        get() = completion.resultsEnabled

    private val history = services.history
    private val drafts =
        CommandDraftController(
            draftRepository,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            ::currentDraft,
            { draft -> command = TextFieldState(draft.text, TextRange(draft.start, draft.end)) },
        )
    private var checkingSelection = true

    init {
        services.session.register(draftRepository, drafts::flushAndAwait)
        viewModelScope.launch {
            snapshotFlow { currentDraft() }.collect { draft -> drafts.changed(draft) }
        }
    }

    fun setEditorMode(enabled: Boolean) {
        isCommandEditorMode = enabled
        savedState["editorMode"] = enabled
    }

    fun setDraftSaving(enabled: Boolean) = drafts.setEnabled(enabled)

    private fun currentDraft() =
        CommandDraft(command.text.toString(), command.selection.start, command.selection.end)

    fun flushDraft() = drafts.flush()

    suspend fun flushAndAwait() = drafts.flushAndAwait()

    fun analyze(
        branch: String,
        checkingBySelection: Boolean,
        highlight: Boolean,
        showErrors: Boolean,
        highlightMaxLength: Int,
    ) {
        checkingSelection = checkingBySelection
        val text = command.text.toString()
        val index = if (checkingBySelection) command.selection.min else text.length
        val next =
            commandAnalysisRequest(
                branch,
                text,
                index,
                highlight,
                showErrors,
                highlightMaxLength,
            )
        completion.analyze(next)
    }

    fun suggestionAt(index: Int) = completion.suggestionAt(index)

    fun showSuggestions(indices: List<Int>) = completion.showSuggestions(indices)

    private fun currentInput(): Pair<String, Int> {
        val text = command.text.toString()
        return text to if (checkingSelection) command.selection.min else text.length
    }

    fun onItemClick(which: Int, expectedRevision: Long = analysisRevision) {
        val before = currentDraft()
        viewModelScope.launch {
            val result = completion.apply(which, expectedRevision) ?: return@launch
            if (currentDraft() != before) return@launch
            command.edit {
                replace(0, length, result.text)
                selection = TextRange(result.selection)
            }
        }
    }

    fun onCopy(content: String) {
        viewModelScope.launch { history.add(content) }
    }

    override fun onCleared() {
        drafts.finish()
        session.close()
        super.onCleared()
    }
}

/** Keep the stored auto-disable threshold separate from the renderer's visible color window. */
internal fun commandAnalysisRequest(
    branch: String,
    text: String,
    index: Int,
    highlight: Boolean,
    showErrors: Boolean,
    highlightMaxLength: Int,
): CommandAnalysisRequest {
    val highlighting = highlight && text.length < highlightMaxLength
    return CommandAnalysisRequest(0, branch, text, index, highlighting, showErrors || highlighting)
}
