package yancey.chelper.domain.command

import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest

class CommandDraftEditsConcurrencyTest {
    @Test
    fun concurrentEditorsReceiveUniqueRevisionsAndOnlyTheNewestCanCommit() = runTest {
        val edits = CommandDraftEdits()
        val ready = CompletableDeferred<Unit>()
        try {
            val revisions =
                List(8) {
                        async(Dispatchers.Default) {
                            ready.await()
                            List(64) { edits.claimText() }
                        }
                    }
                    .also { ready.complete(Unit) }
                    .awaitAll()
                    .flatten()
            assertEquals(512, revisions.toSet().size)
            assertEquals(512L, edits.currentRevision())
            val current = CommandDraft("saved", 0, 0)
            revisions
                .filter { it != edits.currentRevision() }
                .forEach {
                    assertEquals(
                        current,
                        edits.merge(
                            current,
                            CommandDraftWrite(CommandDraft("stale", 0, 0), it, true),
                        ),
                    )
                }
            val latest = CommandDraft("latest", 6, 6)
            assertEquals(
                latest,
                edits.merge(current, CommandDraftWrite(latest, edits.currentRevision(), true)),
            )
        } finally {
            ready.complete(Unit)
        }
    }
}
