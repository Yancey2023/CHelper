package yancey.chelper.ui.feature.command

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.domain.command.CommandDraftEdits
import yancey.chelper.domain.command.CommandDraftRepository
import yancey.chelper.domain.command.CommandDraftWrite
import yancey.chelper.ui.app.AppSession

class CommandDraftControllerTest {
    private class Repository : CommandDraftRepository {
        override val edits = CommandDraftEdits()
        var stored: CommandDraft? = null
        val loaded = CompletableDeferred<CommandDraft?>()
        val saved = mutableListOf<CommandDraft>()

        override suspend fun read() = loaded.await().also { stored = it }

        override suspend fun save(write: CommandDraftWrite) {
            val next = edits.merge(stored, write)
            if (next != stored) {
                stored = next
                saved.add(requireNotNull(next))
            }
        }
    }

    @Test
    fun `close while loading preserves new text and selection through final save`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository()
        var current = CommandDraft("", 0, 0)
        try {
            val controller =
                CommandDraftController(repo, scope, { current }, { current = it }, 60_000)
            controller.setEnabled(true)
            current = CommandDraft("say latest", 2, 6)
            // The snapshot observer may already have been cancelled when onCleared is called.
            controller.finish()
            controller.flush()
            repo.loaded.complete(CommandDraft("old", 3, 3))
            assertEquals(listOf(CommandDraft("say latest", 2, 6)), repo.saved)
            assertFalse(scope.isActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `close before an untouched draft loads retains the saved text`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository()
        var current = CommandDraft("", 0, 0)
        try {
            val controller =
                CommandDraftController(repo, scope, { current }, { current = it }, 60_000)
            controller.setEnabled(true)
            controller.finish()
            val previous = CommandDraft("saved command", 1, 5)
            repo.loaded.complete(previous)
            assertEquals(previous, current)
            assertEquals(previous, repo.stored)
            assertTrue(repo.saved.isEmpty())
            assertFalse(scope.isActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `stop before restore requests an immediate save after restore`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository()
        var current = CommandDraft("", 0, 0)
        try {
            val controller =
                CommandDraftController(repo, scope, { current }, { current = it }, 60_000)
            controller.setEnabled(true)
            current = CommandDraft("typed", 5, 5)
            controller.flush()
            repo.loaded.complete(CommandDraft("saved", 5, 5))
            assertEquals(listOf(current), repo.saved)
            controller.finish()
            assertFalse(scope.isActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `disabling during restore prevents any stale restore or write`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository()
        var current = CommandDraft("", 0, 0)
        try {
            val controller =
                CommandDraftController(repo, scope, { current }, { current = it }, 60_000)
            controller.setEnabled(true)
            controller.setEnabled(false)
            repo.loaded.complete(CommandDraft("old", 3, 3))
            controller.finish()
            assertEquals("", current.text)
            assertTrue(repo.saved.isEmpty())
            assertFalse(scope.isActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `failed restore never overwrites an unread draft`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository()
        try {
            val controller =
                CommandDraftController(repo, scope, { CommandDraft("new", 3, 3) }, {}, 60_000)
            controller.setEnabled(true)
            repo.loaded.completeExceptionally(IOException("read failed"))
            controller.flush()
            controller.finish()
            assertTrue(repo.saved.isEmpty())
            assertFalse(scope.isActive)
        } finally {
            scope.cancel()
        }
    }

    private class SharedRepository : CommandDraftRepository {
        override val edits = CommandDraftEdits()
        var stored: CommandDraft? = CommandDraft("old", 3, 3)
        val writes = mutableListOf<CommandDraftWrite>()
        var saveBarrier: suspend () -> Unit = {}

        override suspend fun read() = stored

        override suspend fun save(write: CommandDraftWrite) {
            saveBarrier()
            stored = edits.merge(stored, write)
            writes.add(write)
        }
    }

    @Test
    fun `session close retries a failed final write after the editor scope is released`() =
        runBlocking {
            val repo = SharedRepository()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            var value = CommandDraft("", 0, 0)
            val controller = CommandDraftController(repo, scope, { value }, { value = it }, 60_000)
            val session = AppSession()
            session.register(repo, controller::flushAndAwait)
            try {
                controller.setEnabled(true)
                value = CommandDraft("say latest", 2, 7)
                controller.changed(value)
                repo.saveBarrier = { throw IOException("temporarily unavailable") }
                controller.finish()
                assertFalse(scope.isActive)
                assertTrue(runCatching { session.flush() }.exceptionOrNull() is IOException)
                assertTrue(repo.writes.isEmpty())

                repo.saveBarrier = {}
                session.flush()
                session.flush()
                assertEquals(value, repo.stored)
                assertEquals(1, repo.writes.size)
                assertFalse(scope.isActive)
            } finally {
                controller.finish()
                scope.cancel()
            }
        }

    @Test
    fun `retrying a finished editor cannot overwrite a newer shared draft`() = runBlocking {
        val repo = SharedRepository()
        val aScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var a = CommandDraft("", 0, 0)
        var b = a
        val first = CommandDraftController(repo, aScope, { a }, { a = it }, 60_000)
        val second = CommandDraftController(repo, bScope, { b }, { b = it }, 60_000)
        try {
            first.setEnabled(true)
            second.setEnabled(true)
            a = CommandDraft("older", 1, 4)
            first.changed(a)
            repo.saveBarrier = { throw IOException("temporarily unavailable") }
            first.finish()
            repo.saveBarrier = {}
            b = CommandDraft("latest", 2, 6)
            second.changed(b)
            second.flushAndAwait()
            first.flushAndAwait()
            assertEquals(b, repo.stored)
        } finally {
            first.finish()
            second.finish()
            aScope.cancel()
            bScope.cancel()
        }
    }

    @Test
    fun `close retry after a failed restore cannot overwrite an unread draft`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository()
        var value = CommandDraft("", 0, 0)
        val controller = CommandDraftController(repo, scope, { value }, { value = it }, 60_000)
        try {
            controller.setEnabled(true)
            value = CommandDraft("typed", 1, 5)
            controller.changed(value)
            repo.loaded.completeExceptionally(IOException("read failed"))
            controller.finish()
            assertTrue(runCatching { controller.flushAndAwait() }.isFailure)
            assertTrue(repo.saved.isEmpty())
            assertFalse(scope.isActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `unchanged editor stop and close preserve a newer shared draft`() = runBlocking {
        val repo = SharedRepository()
        val aScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var a = CommandDraft("", 0, 0)
        var b = a
        val first = CommandDraftController(repo, aScope, { a }, { a = it }, 60_000)
        val second = CommandDraftController(repo, bScope, { b }, { b = it }, 60_000)
        try {
            first.setEnabled(true)
            second.setEnabled(true)
            b = CommandDraft("say latest", 4, 9)
            second.changed(b)
            second.flush()
            first.flush()
            first.finish()
            assertEquals(b, repo.stored)
            assertEquals(1, repo.writes.size)
        } finally {
            first.finish()
            second.finish()
            aScope.cancel()
            bScope.cancel()
        }
    }

    @Test
    fun `old selection changes cannot supersede a newer pending text edit`() = runBlocking {
        val repo = SharedRepository()
        val aScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var a = CommandDraft("", 0, 0)
        var b = a
        val first = CommandDraftController(repo, aScope, { a }, { a = it }, 60_000)
        val second = CommandDraftController(repo, bScope, { b }, { b = it }, 60_000)
        try {
            first.setEnabled(true)
            second.setEnabled(true)
            a = CommandDraft("older unsaved", 3, 3)
            first.changed(a)
            b = CommandDraft("newer pending", 2, 7)
            second.changed(b)
            a = a.copy(start = 1, end = 1)
            first.changed(a)
            first.flush()
            second.flush()
            assertEquals(b, repo.stored)
            a = a.copy(start = 2, end = 2)
            first.changed(a)
            first.flush()
            assertEquals(b, repo.stored)
        } finally {
            first.finish()
            second.finish()
            aScope.cancel()
            bScope.cancel()
        }
    }

    @Test
    fun `selection updates retain uncommitted text and restore positions`() = runBlocking {
        val repo = SharedRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var value = CommandDraft("", 0, 0)
        val controller = CommandDraftController(repo, scope, { value }, { value = it }, 60_000)
        try {
            controller.setEnabled(true)
            value = CommandDraft("typed", 5, 5)
            controller.changed(value)
            value = value.copy(start = 1, end = 4)
            controller.changed(value)
            controller.flush()
            assertEquals(value, repo.stored)
            value = value.copy(start = 0, end = 0)
            controller.changed(value)
            controller.flush()
            assertEquals(value, repo.stored)
        } finally {
            controller.finish()
            scope.cancel()
        }
    }

    @Test
    fun `delayed obsolete write cannot replace a newer committed text`() = runBlocking {
        val repo = SharedRepository()
        val aScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var a = CommandDraft("", 0, 0)
        var b = a
        val first = CommandDraftController(repo, aScope, { a }, { a = it }, 60_000)
        val second = CommandDraftController(repo, bScope, { b }, { b = it }, 60_000)
        val gate = CompletableDeferred<Unit>()
        try {
            first.setEnabled(true)
            second.setEnabled(true)
            a = CommandDraft("older", 5, 5)
            first.changed(a)
            repo.saveBarrier = { withContext(NonCancellable) { gate.await() } }
            first.flush()
            repo.saveBarrier = {}
            b = CommandDraft("latest", 6, 6)
            second.changed(b)
            second.flush()
            gate.complete(Unit)
            assertEquals(b, repo.stored)
        } finally {
            gate.complete(Unit)
            first.finish()
            second.finish()
            aScope.cancel()
            bScope.cancel()
        }
    }

    @Test
    fun `clearing input before enabling saving is not undone by restoration`() = runBlocking {
        val repo = SharedRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var value = CommandDraft("", 0, 0)
        val controller = CommandDraftController(repo, scope, { value }, { value = it }, 60_000)
        try {
            value = CommandDraft("typed while disabled", 3, 3)
            controller.changed(value)
            value = CommandDraft("", 0, 0)
            controller.changed(value)
            controller.setEnabled(true)
            controller.flush()
            assertEquals("", value.text)
            assertEquals(value, repo.stored)
        } finally {
            controller.finish()
            scope.cancel()
        }
    }

    @Test
    fun `reenabling saving after only moving an old caret preserves newer text`() = runBlocking {
        val repo = SharedRepository()
        val aScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var a = CommandDraft("", 0, 0)
        var b = a
        val first = CommandDraftController(repo, aScope, { a }, { a = it }, 60_000)
        val second = CommandDraftController(repo, bScope, { b }, { b = it }, 60_000)
        try {
            first.setEnabled(true)
            second.setEnabled(true)
            first.setEnabled(false)
            b = CommandDraft("latest", 6, 6)
            second.changed(b)
            second.flush()
            a = a.copy(start = 1, end = 1)
            first.changed(a)
            first.setEnabled(true)
            first.flush()
            assertEquals(b, repo.stored)
        } finally {
            first.finish()
            second.finish()
            aScope.cancel()
            bScope.cancel()
        }
    }

    @Test
    fun `a newer draft returned by initial read accepts subsequent selection updates`() =
        runBlocking {
            val repo = Repository()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            var value = CommandDraft("", 0, 0)
            val controller = CommandDraftController(repo, scope, { value }, { value = it }, 60_000)
            try {
                controller.setEnabled(true)
                repo.edits.claimText()
                repo.loaded.complete(CommandDraft("newer restored text", 4, 4))
                value = value.copy(start = 1, end = 3)
                controller.changed(value)
                controller.flush()
                assertEquals(value, repo.stored)
            } finally {
                controller.finish()
                scope.cancel()
            }
        }
}
