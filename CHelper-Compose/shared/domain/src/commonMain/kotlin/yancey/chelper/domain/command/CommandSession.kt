@file:OptIn(ExperimentalAtomicApi::class)

package yancey.chelper.domain.command

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface CommandEngine : AutoCloseable {
    suspend fun parse(text: String): ParsedCommand
}

interface ParsedCommand : AutoCloseable {
    val structure: String?
    val errors: Array<ErrorReason>?
    val tokens: IntArray?
    val nodeCount: Int

    fun hint(index: Int): String?

    fun suggestionCount(index: Int): Int

    fun suggestion(index: Int, which: Int): Suggestion?

    fun apply(index: Int, which: Int): ClickSuggestionResult?
}

data class CommandAnalysisRequest(
    val revision: Long,
    val branch: String,
    val text: String,
    val index: Int,
    val highlight: Boolean,
    val errors: Boolean,
)

data class CommandAnalysis(
    val structure: String?,
    val hint: String?,
    val errors: Array<ErrorReason>?,
    val tokens: IntArray?,
    val nodeCount: Int,
    val suggestionCount: Int,
    val suggestions: Map<Int, CommandSuggestion?> = emptyMap(),
)

/** All native access, including lazy suggestion queries and release, uses one serial queue. */
class CommandSession(
    private val load: (String) -> CommandEngine?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : AutoCloseable {
    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)
    private var engine: CommandEngine? = null
    private var parsed: ParsedCommand? = null
    private var branch: String? = null
    private var text: String? = null
    private var revision = -1L
    private var index = 0

    suspend fun analyze(
        request: CommandAnalysisRequest,
        suggestionIndices: List<Int> = emptyList(),
    ): CommandAnalysis = analyze(request, suggestionIndices, includeEditorMetadata = true)

    /**
     * Document coloring must not generate hints or build native completion lists just to count
     * them.
     */
    suspend fun highlight(branch: String, text: String): IntArray? =
        analyze(
                CommandAnalysisRequest(0, branch, text, 0, true, false),
                emptyList(),
                includeEditorMetadata = false,
            )
            .tokens

    private suspend fun analyze(
        request: CommandAnalysisRequest,
        suggestionIndices: List<Int>,
        includeEditorMetadata: Boolean,
    ): CommandAnalysis =
        withContext(dispatcher) {
            mutex.withLock {
                ensureActive()
                check(!closed.load())
                revision = -1L
                if (branch != request.branch || (engine == null && request.branch.isNotEmpty())) {
                    // Load first: a failed resource switch must not destroy the previous session.
                    val next = if (request.branch.isEmpty()) null else load(request.branch)
                    parsed?.close()
                    parsed = null
                    text = null
                    engine?.close()
                    engine = next
                    branch = request.branch
                }
                ensureActive()
                if (text != request.text || parsed == null) {
                    parsed?.close()
                    parsed = null
                    text = null
                    parsed = engine?.parse(request.text)
                    text = request.text
                }
                ensureActive()
                val current = parsed
                val result =
                    CommandAnalysis(
                        structure =
                            if (!includeEditorMetadata) null
                            else if (request.text.isEmpty()) "欢迎使用CHelper" else current?.structure,
                        hint =
                            if (!includeEditorMetadata) null
                            else if (request.text.isEmpty()) "作者：Yancey"
                            else current?.hint(request.index),
                        errors = if (request.errors) current?.errors else null,
                        tokens = if (request.highlight) current?.tokens else null,
                        nodeCount =
                            if (!includeEditorMetadata || request.text.isEmpty()) 0
                            else current?.nodeCount ?: 0,
                        suggestionCount =
                            if (includeEditorMetadata) current?.suggestionCount(request.index) ?: 0
                            else 0,
                        suggestions =
                            if (includeEditorMetadata)
                                readSuggestions(current, request.index, suggestionIndices)
                            else emptyMap(),
                    )
                revision = request.revision
                index = request.index
                result
            }
        }

    /** Capture only the viewport's values while holding the same queue as analysis and apply. */
    suspend fun suggestions(
        expectedRevision: Long,
        indices: List<Int>,
    ): Map<Int, CommandSuggestion?> =
        withContext(dispatcher) {
            mutex.withLock {
                if (closed.load() || expectedRevision < 0 || revision != expectedRevision)
                    emptyMap()
                else readSuggestions(parsed, index, indices)
            }
        }

    private fun readSuggestions(
        current: ParsedCommand?,
        index: Int,
        indices: List<Int>,
    ): Map<Int, CommandSuggestion?> {
        if (current == null || indices.isEmpty()) return emptyMap()
        val count = current.suggestionCount(index)
        return indices
            .distinct()
            .filter { it in 0 until count }
            .associateWith { which ->
                current.suggestion(index, which)?.let {
                    CommandSuggestion(it.name.orEmpty(), it.description)
                }
            }
    }

    suspend fun suggestion(expectedRevision: Long, which: Int): Suggestion? =
        withContext(dispatcher) {
            mutex.withLock {
                if (closed.load() || expectedRevision < 0 || revision != expectedRevision) null
                else parsed?.suggestion(index, which)
            }
        }

    suspend fun apply(expectedRevision: Long, which: Int): ClickSuggestionResult? =
        withContext(dispatcher) {
            mutex.withLock {
                if (closed.load() || expectedRevision < 0 || revision != expectedRevision) null
                else parsed?.apply(index, which)
            }
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // This scope outlives ViewModel cancellation, so native cleanup cannot be skipped.
        val cleanup = CoroutineScope(SupervisorJob() + dispatcher)
        cleanup.launch {
            try {
                mutex.withLock {
                    parsed?.close()
                    parsed = null
                    engine?.close()
                    engine = null
                }
            } finally {
                cleanup.cancel()
            }
        }
    }
}
