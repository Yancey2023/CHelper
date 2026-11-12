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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import yancey.chelper.core.ErrorReason
import yancey.chelper.core.Suggestion
import yancey.chelper.data.logging.ClientLog as Log
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.nativecore.command.CommandAnalysisRequest
import yancey.chelper.nativecore.command.CommandSession
import yancey.chelper.nativecore.command.NativeCommandEngine
import yancey.chelper.ui.platform.ClientDiagnostics as MonitorUtil
import yancey.chelper.ui.platform.ClientServices

class CompletionViewModel
@JvmOverloads
constructor(
    services: ClientServices,
    draftRepository: yancey.chelper.domain.command.CommandDraftRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    var isShowMenu by mutableStateOf(false)
    var isCommandEditorMode by mutableStateOf(savedState["editorMode"] ?: false)
        private set

    var command by mutableStateOf(TextFieldState())
        private set

    var structure by mutableStateOf<String?>(null)
        private set

    var paramHint by mutableStateOf<String?>(null)
        private set

    var errorReasons by mutableStateOf<Array<ErrorReason>?>(null)
        private set

    var suggestionsSize by mutableIntStateOf(0)
        private set

    var suggestionsUpdateTimes by mutableIntStateOf(0)
        private set

    var syntaxHighlightTokens by mutableStateOf<IntArray?>(null)
        private set

    var nodeCount by mutableIntStateOf(0)
        private set

    private val session =
        CommandSession(
            load = { NativeCommandEngine.load(services.packFilenames, services.openCore, it) }
        )
    private val history = services.history
    private val drafts =
        CommandDraftController(
            draftRepository,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            ::currentDraft,
            { draft -> command = TextFieldState(draft.text, TextRange(draft.start, draft.end)) },
        )
    private var analysisJob: Job? = null
    private var revision = 0L
    private var publishedRevision = -1L
    private var publishedInput: Pair<String, Int>? = null
    private var checkingSelection = true
    private var lastRequest: CommandAnalysisRequest? = null

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
            CommandAnalysisRequest(
                0,
                branch,
                text,
                index,
                highlight && text.length < highlightMaxLength,
                showErrors || highlight && text.length < highlightMaxLength,
            )
        if (next == lastRequest) return
        val previous = lastRequest
        lastRequest = next
        val request = next.copy(revision = ++revision)
        publishedRevision = -1L
        publishedInput = null
        suggestionsSize = 0
        if (previous?.text != next.text || previous?.branch != next.branch) {
            syntaxHighlightTokens = null
            errorReasons = null
        }
        if (!next.highlight) syntaxHighlightTokens = null
        if (!next.errors) errorReasons = null
        analysisJob?.cancel()
        analysisJob =
            viewModelScope.launch {
                try {
                    val result = session.analyze(request)
                    if (
                        revision != request.revision ||
                            currentInput() != (request.text to request.index)
                    )
                        return@launch
                    structure = result.structure
                    paramHint = result.hint
                    errorReasons = result.errors
                    syntaxHighlightTokens = result.tokens
                    nodeCount = result.nodeCount
                    suggestionsSize = result.suggestionCount
                    publishedRevision = request.revision
                    publishedInput = request.text to request.index
                    suggestionsUpdateTimes++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (revision != request.revision) return@launch
                    lastRequest = null
                    structure = "命令解析失败"
                    paramHint = e.message
                    nodeCount = 0
                    Log.w("CompletionViewModel", "Unable to analyze command", e)
                    MonitorUtil.generateCustomLog(e, "CommandAnalysisException")
                }
            }
    }

    suspend fun getSuggestion(which: Int): Suggestion? =
        session.suggestion(publishedRevision, which)

    private fun currentInput(): Pair<String, Int> {
        val text = command.text.toString()
        return text to if (checkingSelection) command.selection.min else text.length
    }

    fun onItemClick(which: Int) {
        val expected = publishedRevision
        val before = currentDraft()
        if (publishedInput != currentInput()) return
        viewModelScope.launch {
            val result = session.apply(expected, which) ?: return@launch
            if (expected < 0 || expected != publishedRevision || currentDraft() != before)
                return@launch
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
