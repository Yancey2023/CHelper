package yancey.chelper.nativecore.command

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.core.ClickSuggestionResult
import yancey.chelper.core.ErrorReason
import yancey.chelper.core.Suggestion

interface CommandEngine : Closeable {
    fun parse(text: String): ParsedCommand
}

interface ParsedCommand : Closeable {
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
)

/** All native access, including lazy suggestion queries and release, uses one serial queue. */
class CommandSession(
    private val load: (String) -> CommandEngine?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : Closeable {
    private val closed = AtomicBoolean(false)
    private var engine: CommandEngine? = null
    private var parsed: ParsedCommand? = null
    private var branch: String? = null
    private var text: String? = null
    private var revision = -1L
    private var index = 0

    suspend fun analyze(request: CommandAnalysisRequest): CommandAnalysis =
        withContext(dispatcher) {
            ensureActive()
            check(!closed.get())
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
                    structure = if (request.text.isEmpty()) "欢迎使用CHelper" else current?.structure,
                    hint =
                        if (request.text.isEmpty()) "作者：Yancey" else current?.hint(request.index),
                    errors = if (request.errors) current?.errors else null,
                    tokens = if (request.highlight) current?.tokens else null,
                    nodeCount = if (request.text.isEmpty()) 0 else current?.nodeCount ?: 0,
                    suggestionCount = current?.suggestionCount(request.index) ?: 0,
                )
            revision = request.revision
            index = request.index
            result
        }

    suspend fun suggestion(expectedRevision: Long, which: Int): Suggestion? =
        withContext(dispatcher) {
            if (closed.get() || expectedRevision < 0 || revision != expectedRevision) null
            else parsed?.suggestion(index, which)
        }

    suspend fun apply(expectedRevision: Long, which: Int): ClickSuggestionResult? =
        withContext(dispatcher) {
            if (closed.get() || expectedRevision < 0 || revision != expectedRevision) null
            else parsed?.apply(index, which)
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // This scope outlives ViewModel cancellation, so native cleanup cannot be skipped.
        val cleanup = CoroutineScope(SupervisorJob() + dispatcher)
        cleanup.launch {
            try {
                parsed?.close()
                parsed = null
                engine?.close()
                engine = null
            } finally {
                cleanup.cancel()
            }
        }
    }
}
