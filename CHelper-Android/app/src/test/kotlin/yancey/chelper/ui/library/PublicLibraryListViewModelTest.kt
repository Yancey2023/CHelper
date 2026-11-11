package yancey.chelper.ui.library

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.data.LibraryFavoriteResult
import yancey.chelper.data.PublicLibraryPage
import yancey.chelper.data.PublicLibraryRepository
import yancey.chelper.network.library.data.LibraryFunction

class PublicLibraryListViewModelTest {
    private class FakeRepository : PublicLibraryRepository {
        data class Call(
            val page: Int,
            val mode: Boolean,
            val response: CompletableDeferred<PublicLibraryPage>,
        )

        val calls = Channel<Call>(Channel.UNLIMITED)

        override suspend fun load(page: Int, recommended: Boolean): PublicLibraryPage {
            val response = CompletableDeferred<PublicLibraryPage>()
            calls.send(Call(page, recommended, response))
            return response.await()
        }

        override fun canFavorite() = true

        override suspend fun toggleFavorite(id: Int) = LibraryFavoriteResult(true, null)

        override suspend fun unreadCount() = 0
    }

    private fun page(vararg ids: Int) = PublicLibraryPage(ids.map { LibraryFunction(id = it) }, 3)

    @Test
    fun `failed append retries the same page and retains existing rows`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = FakeRepository()
            val vm = PublicLibraryListViewModel(repository, scope)
            vm.initialize(false)
            repository.calls.receive().response.complete(page(1))
            vm.loadMore()
            val failed = repository.calls.receive()
            assertEquals(2, failed.page)
            failed.response.completeExceptionally(IOException("offline"))
            assertEquals(1, vm.uiState.value.currentPage)
            assertEquals(listOf(1), vm.uiState.value.libraries.map { it.id })
            vm.retry()
            val retry = repository.calls.receive()
            assertEquals(2, retry.page)
            retry.response.complete(page(1, 2, 2))
            assertEquals(2, vm.uiState.value.currentPage)
            assertEquals(listOf(1, 2), vm.uiState.value.libraries.map { it.id })
            assertNull(vm.uiState.value.errorMessage)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `switching to a cached mode cancels a pending request without a network error`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val repository = FakeRepository()
                val vm = PublicLibraryListViewModel(repository, scope)
                vm.switchMode(false)
                repository.calls.receive().response.complete(page(1))
                vm.switchMode(true)
                val old = repository.calls.receive()
                vm.switchMode(false)
                old.response.complete(page(99))
                assertEquals(listOf(1), vm.uiState.value.libraries.map { it.id })
                assertFalse(vm.uiState.value.isRecommendMode)
                assertFalse(vm.uiState.value.isLoading)
                assertNull(vm.uiState.value.errorMessage)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `obsolete request cannot clear a new request loading state`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = FakeRepository()
            val vm = PublicLibraryListViewModel(repository, scope)
            vm.switchMode(true)
            val old = repository.calls.receive()
            vm.switchMode(false)
            val current = repository.calls.receive()
            old.response.completeExceptionally(IOException("obsolete"))
            assertTrue(vm.uiState.value.isLoading)
            assertNull(vm.uiState.value.errorMessage)
            current.response.complete(page(2))
            assertEquals(listOf(2), vm.uiState.value.libraries.map { it.id })
            assertFalse(vm.uiState.value.isLoading)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `failed refresh retries page one without discarding the visible list`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = FakeRepository()
            val vm = PublicLibraryListViewModel(repository, scope)
            vm.initialize(false)
            repository.calls.receive().response.complete(page(1))
            vm.loadMore()
            repository.calls.receive().response.complete(page(2))
            vm.refresh()
            repository.calls.receive().response.completeExceptionally(IOException("offline"))
            assertEquals(listOf(1, 2), vm.uiState.value.libraries.map { it.id })
            vm.retry()
            val retry = withTimeout(1000) { repository.calls.receive() }
            assertEquals(1, retry.page)
            retry.response.complete(page(3))
            assertEquals(listOf(3), vm.uiState.value.libraries.map { it.id })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `favorite state updates both mode caches`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = FakeRepository()
            val vm = PublicLibraryListViewModel(repository, scope)
            vm.switchMode(false)
            repository.calls.receive().response.complete(page(1))
            vm.switchMode(true)
            repository.calls.receive().response.complete(page(1))
            vm.toggleFavorite(vm.uiState.value.libraries.single())
            assertTrue(vm.uiState.value.libraries.single().isFavorited == true)
            vm.switchMode(false)
            assertTrue(vm.uiState.value.libraries.single().isFavorited == true)
        } finally {
            scope.cancel()
        }
    }
}
