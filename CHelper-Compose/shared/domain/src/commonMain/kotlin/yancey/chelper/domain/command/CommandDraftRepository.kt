package yancey.chelper.domain.command

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

data class CommandDraft(val text: String, val start: Int, val end: Int) {
    fun clamped() = copy(start = start.coerceIn(0, text.length), end = end.coerceIn(0, text.length))
}

data class CommandDraftWrite(val draft: CommandDraft, val revision: Long, val replaceText: Boolean)

/** Lifecycle events never acquire a new version; only text edits can supersede another editor. */
@OptIn(ExperimentalAtomicApi::class)
class CommandDraftEdits {
    private data class Revision(val value: Long)

    private val state = AtomicReference(Revision(0))

    fun claimText(): Long {
        while (true) {
            val before = state.load()
            val next = Revision(before.value + 1)
            if (state.compareAndSet(before, next)) return next.value
        }
    }

    fun currentRevision(): Long = state.load().value

    fun merge(current: CommandDraft?, write: CommandDraftWrite): CommandDraft? {
        if (write.revision != currentRevision()) return current
        if (!write.replaceText && current?.text != write.draft.text) return current
        return write.draft.clamped()
    }
}

interface CommandDraftRepository {
    val edits: CommandDraftEdits

    suspend fun read(): CommandDraft?

    suspend fun save(write: CommandDraftWrite)
}
