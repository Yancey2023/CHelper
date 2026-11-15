@file:OptIn(ExperimentalCoroutinesApi::class)

package yancey.chelper.desktop.sync

import androidx.datastore.core.DataStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import yancey.chelper.data.local.library.LocalCommandLabDataStore
import yancey.chelper.data.local.library.LocalLibraryData
import yancey.chelper.data.local.library.LocalLibraryEditDraftData
import yancey.chelper.data.remote.NetworkServices
import yancey.chelper.domain.auth.CredentialStore
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.sync.SyncedLibraryIdentity

class DesktopLibrarySyncTest {
    private class Store<T>(initial: T) : DataStore<T> {
        val state = MutableStateFlow(initial)
        var collections = 0
        var failReadOn: Int? = null
        var failingUpdates = 0

        override val data: Flow<T> = flow {
            collections++
            if (collections == failReadOn) throw IOException("temporary read failure")
            emitAll(state)
        }

        override suspend fun updateData(transform: suspend (T) -> T): T {
            if (failingUpdates > 0) {
                failingUpdates--
                throw IOException("temporary write failure")
            }
            return transform(state.value).also { state.value = it }
        }
    }

    private var cloudName = "old"
    private val network =
        NetworkServices({
            MockEngine {
                respond(
                    """{"status":0,"data":{"list":[{"id":1,"name":"$cloudName"}],"total":1}}""",
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        })
    private val entry =
        LibraryFunction(
            id = 1,
            uuid = "test-uuid",
            localEntryId = "test-entry",
            name = "new",
            content = "say new",
            autoSync = true,
            localUnsynced = true,
            localSyncOwnerId = 17,
            localSyncRevision = "r1",
        )
    private val store = Store(LocalLibraryData(listOf(entry)))
    private val local = LocalCommandLabDataStore(store, Store(LocalLibraryEditDraftData()))

    @Before
    fun login() {
        network.login.init(
            object : CredentialStore {
                override fun read() =
                    """{"token":"test-token","lastLoginTimestamp":${Clock.System.now().toEpochMilliseconds()},"user":{"id":17,"is_guest":false}}"""

                override fun write(content: String) = Unit

                override fun clear() = Unit
            }
        ) {
            throw AssertionError(it)
        }
        assertEquals(17, network.login.accountId.value)
        network.libraryContent.invalidate()
    }

    @After
    fun logout() {
        network.login.logout()
        network.libraryContent.invalidate()
        network.close()
    }

    @Test fun `queue observation resumes after a temporary read failure`() = recoverReadFailure(1)

    @Test fun `queue consumer retries after a temporary read failure`() = recoverReadFailure(2)

    @Test fun `entry read failure does not terminate the queue consumer`() = recoverReadFailure(3)

    private fun recoverReadFailure(collection: Int) = runTest {
        // The observer subscribes first, followed by the queue snapshot and then the entry read.
        store.failReadOn = collection
        var uploads = 0
        val sync =
            DesktopLibrarySync(local, network, StandardTestDispatcher(testScheduler)) { library, _
                ->
                uploads++
                SyncedLibraryIdentity(library.id, library.uuid)
            }
        try {
            sync.start()
            runCurrent()
            assertTrue(store.state.value.functions.single().localUnsynced)
            assertEquals(0, uploads)
            advanceTimeBy(30_000)
            runCurrent()
            assertFalse(store.state.value.functions.single().localUnsynced)
            assertEquals(1, uploads)

            // Recovery keeps processing later revisions, rather than handling just one retry.
            store.state.value = LocalLibraryData(listOf(entry.copy(localSyncRevision = "r2")))
            runCurrent()
            assertFalse(store.state.value.functions.single().localUnsynced)
            assertEquals(2, uploads)
        } finally {
            sync.close()
        }
    }

    @Test
    fun `failed acknowledgement and error persistence are retried`() = runTest {
        store.failingUpdates = 2
        var uploads = 0
        val sync =
            DesktopLibrarySync(local, network, StandardTestDispatcher(testScheduler)) { library, _
                ->
                uploads++
                SyncedLibraryIdentity(library.id, library.uuid)
            }
        try {
            sync.start()
            runCurrent()
            assertEquals(1, uploads)
            assertTrue(store.state.value.functions.single().localUnsynced)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(2, uploads)
            assertFalse(store.state.value.functions.single().localUnsynced)
        } finally {
            sync.close()
        }
    }

    @Test
    fun `remote failure with unavailable error storage does not stop later sync`() = runTest {
        store.failingUpdates = 1
        var uploads = 0
        val sync =
            DesktopLibrarySync(local, network, StandardTestDispatcher(testScheduler)) { library, _
                ->
                uploads++
                if (uploads == 1) throw IOException("offline")
                SyncedLibraryIdentity(library.id, library.uuid)
            }
        try {
            sync.start()
            runCurrent()
            assertEquals(1, uploads)
            assertTrue(store.state.value.functions.single().localUnsynced)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(2, uploads)
            assertFalse(store.state.value.functions.single().localUnsynced)
        } finally {
            sync.close()
        }
    }

    @Test
    fun `successful sync invalidates the cloud library cache`() = runTest {
        assertEquals("old", network.libraryContent.mine().data?.functions?.single()?.name)
        cloudName = "new"
        val sync =
            DesktopLibrarySync(local, network, StandardTestDispatcher(testScheduler)) { library, _
                ->
                SyncedLibraryIdentity(library.id, library.uuid)
            }
        try {
            sync.start()
            runCurrent()
            assertFalse(store.state.value.functions.single().localUnsynced)
            assertEquals("new", network.libraryContent.mine().data?.functions?.single()?.name)
        } finally {
            sync.close()
        }
    }

    @Test
    fun `closing during upload cancels without acknowledging or scheduling a retry`() = runTest {
        var uploads = 0
        val sync =
            DesktopLibrarySync(local, network, StandardTestDispatcher(testScheduler)) { _, _ ->
                uploads++
                awaitCancellation()
            }
        try {
            sync.start()
            runCurrent()
            sync.close()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, uploads)
            assertTrue(store.state.value.functions.single().localUnsynced)
            assertNull(store.state.value.functions.single().localSyncError)
        } finally {
            sync.close()
        }
    }
}
