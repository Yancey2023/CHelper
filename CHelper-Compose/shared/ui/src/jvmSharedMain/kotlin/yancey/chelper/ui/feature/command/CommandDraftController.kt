package yancey.chelper.ui.feature.command

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import yancey.chelper.data.logging.ClientLog as Log
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.domain.command.CommandDraftRepository
import yancey.chelper.domain.command.CommandDraftWrite

/** Owns its scope until restore and the final save complete, including after the editor closes. */
class CommandDraftController(
    private val repository: CommandDraftRepository,
    private val scope: CoroutineScope,
    private val current: () -> CommandDraft,
    private val restore: (CommandDraft) -> Unit,
    private val delayMillis: Long = 300,
) {
    private var enabled = false
    private var restored = false
    private var finished = false
    private var flushAfterRestore = false
    private var restoreJob: Job? = null
    private var saveJob: Job? = null
    private var generation = 0L
    private var observed = current()
    private var contentEdited = false
    private var accepted: CommandDraft? = null
    private var textRevision = repository.edits.currentRevision()
    private var pending: CommandDraftWrite? = null

    fun setEnabled(value: Boolean) {
        if (finished || enabled == value) return
        enabled = value
        val version = ++generation
        if (!value) {
            restoreJob?.cancel()
            saveJob?.cancel()
            flushAfterRestore = false
            pending = null
            return
        }
        if (restored) {
            capture(current())
            if (pending == null && current() != accepted) queueDifference(current(), accepted)
            savePending()
            return
        }
        capture(current())
        if (contentEdited && pending == null) queueText(current())
        val before = current()
        if (pending == null) textRevision = repository.edits.currentRevision()
        restoreJob =
            scope.launch {
                try {
                    val saved = repository.read()
                    if (!enabled || generation != version) return@launch
                    capture(current())
                    if (pending == null) textRevision = repository.edits.currentRevision()
                    if (
                        pending == null &&
                            !contentEdited &&
                            current() == before &&
                            before.text.isEmpty() &&
                            saved != null
                    ) {
                        restore(saved)
                        observed = saved
                    }
                    accepted = saved
                    if (
                        pending == null &&
                            current() != saved &&
                            (contentEdited || current().text.isNotEmpty())
                    )
                        queueDifference(current(), saved)
                    restored = true
                    savePending(immediately = finished || flushAfterRestore)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: IOException) {
                    Log.w("CommandDraft", "Unable to restore command draft", error)
                } finally {
                    if (generation == version && finished) releaseAfterSave()
                }
            }
    }

    fun changed(draft: CommandDraft) {
        if (finished) return
        capture(draft)
        if (enabled && restored) savePending()
    }

    private fun queueText(draft: CommandDraft) {
        textRevision = repository.edits.claimText()
        pending = CommandDraftWrite(draft, textRevision, replaceText = true)
    }

    private fun queueDifference(draft: CommandDraft, baseline: CommandDraft?) {
        if (draft.text != baseline?.text) queueText(draft)
        else pending = CommandDraftWrite(draft, textRevision, replaceText = false)
    }

    private fun capture(draft: CommandDraft) {
        if (draft == observed) return
        val textChanged = draft.text != observed.text
        if (textChanged) contentEdited = true
        observed = draft
        if (!enabled) return
        if (textChanged) queueText(draft)
        else {
            val textPending =
                pending?.replaceText == true &&
                    pending?.revision == repository.edits.currentRevision()
            pending = CommandDraftWrite(draft, textRevision, replaceText = textPending)
        }
    }

    fun flush() {
        if (finished || !enabled) return
        capture(current())
        if (restored) savePending(immediately = true) else flushAfterRestore = true
    }

    suspend fun flushAndAwait() {
        if (!enabled) return
        restoreJob?.join()
        if (finished) {
            saveJob?.join()
            // The editor's scope is already released. Retry in the closing caller's scope,
            // keeping the original revision so an older editor cannot replace a newer draft.
            if (restored) pending?.let { save(it) }
        } else {
            flush()
            saveJob?.join()
        }
        check(pending == null) { "命令草稿保存失败" }
    }

    private suspend fun save(request: CommandDraftWrite) {
        repository.save(request)
        accepted = request.draft
        if (pending == request) pending = null
    }

    private fun savePending(immediately: Boolean = false) {
        val request = pending ?: return
        saveJob?.cancel()
        saveJob =
            scope.launch {
                if (!immediately) delay(delayMillis)
                try {
                    save(request)
                } catch (error: IOException) {
                    Log.w("CommandDraft", "Unable to save command draft", error)
                }
            }
    }

    fun finish() {
        if (finished) return
        capture(current())
        finished = true
        if (enabled && !restored && restoreJob?.isActive == true) {
            flushAfterRestore = true
            return
        }
        if (enabled && restored) savePending(immediately = true)
        releaseAfterSave()
    }

    private fun releaseAfterSave() {
        saveJob?.invokeOnCompletion { scope.cancel() } ?: scope.cancel()
    }
}
