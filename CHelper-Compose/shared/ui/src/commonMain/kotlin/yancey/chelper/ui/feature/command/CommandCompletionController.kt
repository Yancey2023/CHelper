package yancey.chelper.ui.feature.command

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import yancey.chelper.domain.command.ClickSuggestionResult
import yancey.chelper.domain.command.CommandAnalysis
import yancey.chelper.domain.command.CommandAnalysisRequest
import yancey.chelper.domain.command.CommandSession
import yancey.chelper.domain.command.CommandSuggestion

data class CompletionSnapshot(
    val revision: Long = -1,
    val text: String = "",
    val index: Int = 0,
    val analysis: CommandAnalysis = CommandAnalysis(null, null, null, null, 0, 0),
)

/** Main-thread presentation of immutable results; native reads remain in CommandSession's queue. */
class CommandCompletionController(
    private val session: CommandSession,
    private val scope: CoroutineScope,
    private val currentInput: () -> Pair<String, Int>,
    private val onFailure: (Exception) -> Unit = {},
) {
    var snapshot by mutableStateOf(CompletionSnapshot())
        private set

    var isAnalyzing by mutableStateOf(false)
        private set

    var failure by mutableStateOf<String?>(null)
        private set

    private var revision = 0L
    private var lastRequest: CommandAnalysisRequest? = null
    private var analysisJob: Job? = null
    private var suggestionJob: Job? = null
    private var visible = emptyList<Int>()

    val resultsEnabled: Boolean
        get() =
            !isAnalyzing &&
                failure == null &&
                snapshot.revision >= 0 &&
                currentInput() == (snapshot.text to snapshot.index)

    private fun desiredIndices(count: Int): List<Int> {
        val head = (0 until minOf(32, count)).toList()
        val viewport = visible.filter { it in 0 until count }
        val after =
            viewport
                .lastOrNull()
                ?.let { last ->
                    (last + 1 until minOf(last.toLong() + 9, count.toLong()).toInt()).toList()
                }
                .orEmpty()
        return (head + viewport + after).distinct()
    }

    private fun isCurrent(request: CommandAnalysisRequest) =
        request.revision == revision && currentInput() == (request.text to request.index)

    fun analyze(input: CommandAnalysisRequest) {
        val next = input.copy(revision = 0)
        if (next == lastRequest && (isAnalyzing || snapshot.revision == revision)) return
        lastRequest = next
        val request = next.copy(revision = ++revision)
        analysisJob?.cancel()
        suggestionJob?.cancel()
        isAnalyzing = true
        analysisJob = scope.launch {
            try {
                var result = session.analyze(request, desiredIndices(Int.MAX_VALUE))
                ensureActive()
                if (!isCurrent(request)) return@launch
                // The viewport can move while parsing. Finish its batch before publishing any UI.
                while (true) {
                    val missing =
                        desiredIndices(result.suggestionCount).filterNot {
                            it in result.suggestions
                        }
                    if (missing.isEmpty()) break
                    val batch = session.suggestions(request.revision, missing)
                    ensureActive()
                    if (!isCurrent(request) || batch.isEmpty()) return@launch
                    result = result.copy(suggestions = result.suggestions + batch)
                }
                val keep = desiredIndices(result.suggestionCount).toSet()
                snapshot =
                    CompletionSnapshot(
                        request.revision,
                        request.text,
                        request.index,
                        result.copy(suggestions = result.suggestions.filterKeys { it in keep }),
                    )
                failure = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!isCurrent(request)) return@launch
                lastRequest = null
                failure = error.message ?: "命令解析失败"
                onFailure(error)
            } finally {
                if (request.revision == revision) isAnalyzing = false
            }
        }
    }

    fun suggestionAt(index: Int): CommandSuggestion? = snapshot.analysis.suggestions[index]

    fun showSuggestions(indices: List<Int>) {
        val viewport = indices.filter { it >= 0 }.distinct().sorted()
        if (visible == viewport) return
        visible = viewport
        suggestionJob?.cancel()
        if (!resultsEnabled) return
        val before = snapshot
        val wanted = desiredIndices(before.analysis.suggestionCount)
        val missing = wanted.filterNot { it in before.analysis.suggestions }
        if (missing.isEmpty()) return
        suggestionJob = scope.launch {
            try {
                val batch = session.suggestions(before.revision, missing)
                ensureActive()
                if (!resultsEnabled || snapshot.revision != before.revision) return@launch
                val keep = wanted.toSet()
                snapshot =
                    snapshot.copy(
                        analysis =
                            snapshot.analysis.copy(
                                suggestions =
                                    (snapshot.analysis.suggestions + batch).filterKeys {
                                        it in keep
                                    }
                            )
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error)
            }
        }
    }

    suspend fun apply(which: Int, expectedRevision: Long): ClickSuggestionResult? {
        if (!resultsEnabled || snapshot.revision != expectedRevision) return null
        val result = session.apply(expectedRevision, which)
        return result.takeIf { resultsEnabled && snapshot.revision == expectedRevision }
    }
}
