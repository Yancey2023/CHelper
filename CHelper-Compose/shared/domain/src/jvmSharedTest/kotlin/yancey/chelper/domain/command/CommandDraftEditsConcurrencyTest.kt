package yancey.chelper.domain.command

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class CommandDraftEditsConcurrencyTest {
    @Test
    fun concurrentEditorsReceiveUniqueRevisionsAndOnlyTheNewestCanCommit() {
        val edits = CommandDraftEdits()
        val workers = Executors.newFixedThreadPool(8)
        try {
            val revisions =
                workers.invokeAll(List(512) { Callable { edits.claimText() } }).map { it.get() }
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
            workers.shutdownNow()
        }
    }
}
