package yancey.chelper.ui.feature.library.search

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.IOException
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.LibrarySearchRepository
import yancey.chelper.domain.library.PublicLibraryPage

class LibrarySearchViewModelTest {
    // UI work and local filtering share a deterministic thread unless a test explicitly queues it.

    private class FakeRepository : LibrarySearchRepository {
        data class PublicCall(
            val query: String,
            val page: Int,
            val result: CompletableDeferred<PublicLibraryPage>,
        )

        data class PrivateCall(
            val query: String,
            val result: CompletableDeferred<List<LibraryFunction>>,
        )

        val public = Channel<PublicCall>(Channel.UNLIMITED)
        val private = Channel<PrivateCall>(Channel.UNLIMITED)

        override suspend fun publicLibraries(query: String, page: Int): PublicLibraryPage {
            val result = CompletableDeferred<PublicLibraryPage>()
            public.send(PublicCall(query, page, result))
            return withContext(NonCancellable) { result.await() }
        }

        override suspend fun privateLibraries(query: String): List<LibraryFunction> {
            val result = CompletableDeferred<List<LibraryFunction>>()
            private.send(PrivateCall(query, result))
            return withContext(NonCancellable) { result.await() }
        }
    }

    private fun page(vararg ids: Int) = PublicLibraryPage(ids.map { LibraryFunction(id = it) }, 3)

    @Test
    fun `account switch replaces private search and rejects a late old account callback`() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val accounts = MutableStateFlow<Int?>(7)
                val repo = FakeRepository()
                val vm = LibrarySearchViewModel(repo, scope, Dispatchers.Unconfined, accounts)
                vm.keyword.setTextAndPlaceCursorAtEnd("query")
                vm.search(listOf(LibraryFunction(id = 3, name = "query local")))
                val oldPrivate = repo.private.receive()
                repo.public.receive().result.complete(page(1))

                accounts.value = 8
                val newPrivate = repo.private.receive()
                oldPrivate.result.complete(listOf(LibraryFunction(id = 7)))
                assertTrue(vm.uiState.value.privateLibraries.isEmpty())
                assertTrue(vm.uiState.value.privateLoading)
                newPrivate.result.complete(listOf(LibraryFunction(id = 8)))

                assertEquals(listOf(8), vm.uiState.value.privateLibraries.map { it.id })
                assertEquals(listOf(3), vm.uiState.value.localLibraries.map { it.id })
                assertEquals(listOf(1), vm.uiState.value.publicLibraries.map { it.id })
                assertFalse(vm.uiState.value.privateLoading)
                assertTrue(repo.public.tryReceive().isFailure)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `logout clears private results while preserving local and public results`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val accounts = MutableStateFlow<Int?>(7)
            val repo = FakeRepository()
            val vm = LibrarySearchViewModel(repo, scope, Dispatchers.Unconfined, accounts)
            vm.keyword.setTextAndPlaceCursorAtEnd("query")
            vm.search(listOf(LibraryFunction(id = 3, name = "query local")))
            repo.private.receive().result.complete(listOf(LibraryFunction(id = 7)))
            repo.public.receive().result.complete(page(1))
            assertFalse(vm.uiState.value.privateLibraries.isEmpty())

            accounts.value = null

            assertTrue(vm.uiState.value.privateLibraries.isEmpty())
            assertFalse(vm.uiState.value.privateLoading)
            assertNull(vm.uiState.value.privateError)
            assertEquals(listOf(3), vm.uiState.value.localLibraries.map { it.id })
            assertEquals(listOf(1), vm.uiState.value.publicLibraries.map { it.id })
            assertTrue(repo.private.tryReceive().isFailure)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `new keyword supersedes both old requests even if callbacks ignore cancellation`() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val repo = FakeRepository()
                val vm =
                    LibrarySearchViewModel(repo, scope, localDispatcher = Dispatchers.Unconfined)
                vm.keyword.setTextAndPlaceCursorAtEnd("old")
                vm.search(null)
                val oldPublic = repo.public.receive()
                val oldPrivate = repo.private.receive()
                vm.keyword.setTextAndPlaceCursorAtEnd("new")
                vm.search(null)
                val currentPublic = repo.public.receive()
                val currentPrivate = repo.private.receive()
                oldPublic.result.complete(page(99))
                oldPrivate.result.complete(listOf(LibraryFunction(id = 99)))
                assertTrue(vm.uiState.value.publicLoading)
                assertTrue(vm.uiState.value.privateLoading)
                currentPublic.result.complete(page(1))
                currentPrivate.result.complete(emptyList())
                assertEquals("new", vm.uiState.value.query)
                assertEquals(listOf(1), vm.uiState.value.publicLibraries.map { it.id })
                assertTrue(vm.uiState.value.privateLibraries.isEmpty())
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `clearing a keyword prevents old public and private results from reappearing`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = FakeRepository()
            val vm = LibrarySearchViewModel(repo, scope, localDispatcher = Dispatchers.Unconfined)
            vm.keyword.setTextAndPlaceCursorAtEnd("query")
            vm.search(null)
            val public = repo.public.receive()
            val private = repo.private.receive()
            vm.keyword.setTextAndPlaceCursorAtEnd("")
            vm.search(null)
            public.result.complete(page(9))
            private.result.complete(listOf(LibraryFunction(id = 9)))
            assertEquals(LibrarySearchUiState(), vm.uiState.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `failed page retries the same page and deduplicates successful append`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = FakeRepository()
            val vm = LibrarySearchViewModel(repo, scope, localDispatcher = Dispatchers.Unconfined)
            vm.keyword.setTextAndPlaceCursorAtEnd("query")
            vm.search(null)
            repo.private.receive().result.complete(emptyList())
            repo.public.receive().result.complete(page(1))
            vm.loadMore()
            val failed = repo.public.receive()
            assertEquals(2, failed.page)
            failed.result.completeExceptionally(IOException("offline"))
            assertEquals(1, vm.uiState.value.currentPage)
            vm.loadMore()
            assertTrue(repo.public.tryReceive().isFailure)
            vm.retryPublic()
            val retried = repo.public.receive()
            assertEquals(2, retried.page)
            retried.result.complete(page(1, 2, 2))
            assertEquals(listOf(1, 2), vm.uiState.value.publicLibraries.map { it.id })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `private failures keep local matches and public results visible`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = FakeRepository()
            val vm = LibrarySearchViewModel(repo, scope, localDispatcher = Dispatchers.Unconfined)
            vm.keyword.setTextAndPlaceCursorAtEnd("say")
            vm.search(listOf(LibraryFunction(id = 7, name = "say hello")))
            repo.private.receive().result.completeExceptionally(IOException("private unavailable"))
            repo.public.receive().result.complete(page(1))
            withTimeout(5000) { vm.uiState.first { !it.localLoading } }
            assertEquals(listOf(7), vm.uiState.value.localLibraries.map { it.id })
            assertEquals(listOf(1), vm.uiState.value.publicLibraries.map { it.id })
            assertNotNull(vm.uiState.value.privateError)
            assertNull(vm.uiState.value.publicError)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `local data arriving later refreshes matches without restarting remote search`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = FakeRepository()
            val vm = LibrarySearchViewModel(repo, scope, localDispatcher = Dispatchers.Unconfined)
            vm.keyword.setTextAndPlaceCursorAtEnd("hello")
            vm.search(null)
            repo.private.receive().result.complete(emptyList())
            repo.public.receive().result.complete(page(1))
            vm.refreshLocal(listOf(LibraryFunction(id = 8, name = "HELLO")))
            withTimeout(5000) {
                vm.uiState.first { !it.localLoading && it.localLibraries.isNotEmpty() }
            }
            assertEquals(8, vm.uiState.value.localLibraries.single().id)
            assertTrue(repo.public.tryReceive().isFailure)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `delayed local filtering preserves remote results and private errors`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val worker = QueuedDispatcher()
        try {
            val repo = FakeRepository()
            val vm = LibrarySearchViewModel(repo, scope, localDispatcher = worker)
            vm.keyword.setTextAndPlaceCursorAtEnd("hello")
            vm.search(listOf(LibraryFunction(id = 8, name = "hello local")))
            val public = repo.public.receive()
            val private = repo.private.receive()
            assertTrue(vm.uiState.value.localLoading)
            public.result.complete(page(1))
            private.result.completeExceptionally(IOException("private unavailable"))
            worker.runAll()
            assertFalse(vm.uiState.value.localLoading)
            assertEquals(listOf(8), vm.uiState.value.localLibraries.map { it.id })
            assertEquals(listOf(1), vm.uiState.value.publicLibraries.map { it.id })
            assertNotNull(vm.uiState.value.privateError)
            assertFalse(vm.uiState.value.publicLoading)
            assertFalse(vm.uiState.value.privateLoading)
        } finally {
            scope.cancel()
            worker.runAll()
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
