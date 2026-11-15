package yancey.chelper.domain.library.sync

import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import yancey.chelper.domain.library.LibraryFunction

class LibrarySyncProcessorTest {
    @Test
    fun `editing an old form preserves a completed upload identity and queues a fresh revision`() {
        val form =
            LibraryFunction(
                content = "old",
                autoSync = true,
                localUnsynced = true,
                localSyncOwnerId = 7,
                localSyncRevision = "uploaded-revision",
            )
        val current =
            form.afterQueuedSync(7, "uploaded-revision", SyncedLibraryIdentity(42, "cloud-uuid"))

        val saved = current.withDocumentEdits(form.copy(content = "new"))

        assertEquals(42, saved.id)
        assertEquals("cloud-uuid", saved.uuid)
        assertEquals("new", saved.content)
        assertTrue(saved.localUnsynced)
        assertNotNull(saved.localSyncRevision)
        assertNotEquals("uploaded-revision", saved.localSyncRevision)
    }

    @Test
    fun `a stale form cannot reenable synchronization disabled in another window`() {
        val current =
            LibraryFunction(
                id = 42,
                uuid = "cloud",
                content = "old",
                autoSync = false,
                localSyncOwnerId = 7,
            )
        val saved =
            current.withDocumentEdits(
                current.copy(content = "new", autoSync = true, localSyncRevision = "stale-pending")
            )

        assertFalse(saved.autoSync == true)
        assertNull(saved.localSyncRevision)
        assertTrue(saved.localUnsynced)
        assertEquals(42, saved.id)
    }

    @Test
    fun `legacy local update receives a new revision before an old acknowledgement arrives`() {
        val previous = entry()
        val edited = previous.copy(content = "say changed").trackLocalChanges(previous)
        assertNotEquals(previous.localSyncRevision, edited.localSyncRevision)
        val acknowledged =
            edited.afterQueuedSync(
                1,
                previous.localSyncRevision!!,
                SyncedLibraryIdentity(10, "remote"),
            )
        assertTrue(acknowledged.localUnsynced)
        assertEquals(edited.localSyncRevision, acknowledged.localSyncRevision)
    }

    private class FakeStore(var library: LibraryFunction?) : LibrarySyncStore {
        var failures = 0

        override suspend fun read(localEntryId: String) = library

        override suspend fun complete(
            localEntryId: String,
            ownerId: Int,
            revision: String,
            identity: SyncedLibraryIdentity,
        ) {
            library = library?.afterQueuedSync(ownerId, revision, identity)
        }

        override suspend fun fail(localEntryId: String, revision: String, message: String) {
            failures++
        }
    }

    private fun entry() =
        LibraryFunction(
                localEntryId = "local",
                name = "test",
                content = "say hello",
                autoSync = true,
            )
            .prepareAutoSync(1)

    @Test
    fun `successful sync clears only the submitted revision`() = runTest {
        val store = FakeStore(entry())
        val processor =
            LibrarySyncProcessor(store, { 1 }) { _, _ -> SyncedLibraryIdentity(10, "remote") }
        assertEquals(LibrarySyncOutcome.COMPLETE, processor.run("local", 1))
        assertEquals(10, store.library?.id)
        assertEquals("remote", store.library?.uuid)
        assertFalse(store.library!!.localUnsynced)
        assertNull(store.library?.localSyncRevision)
    }

    @Test
    fun `new edits during upload retain their pending revision and receive remote identity`() =
        runTest {
            val store = FakeStore(entry())
            var newRevision: String? = null
            val processor =
                LibrarySyncProcessor(store, { 1 }) { _, _ ->
                    store.library = store.library!!.copy(content = "say newer").prepareAutoSync(1)
                    newRevision = store.library!!.localSyncRevision
                    SyncedLibraryIdentity(10, "remote")
                }
            processor.run("local", 1)
            assertEquals("say newer", store.library?.content)
            assertEquals(newRevision, store.library?.localSyncRevision)
            assertTrue(store.library!!.localUnsynced)
            assertEquals(10, store.library?.id)
        }

    @Test
    fun `switching accounts before work prevents the request`() = runTest {
        val processor =
            LibrarySyncProcessor(FakeStore(entry()), { 2 }) { _, _ -> error("Wrong account") }
        assertEquals(LibrarySyncOutcome.ACCOUNT_CHANGED, processor.run("local", 1))
    }

    @Test
    fun `account change during upload prevents any local acknowledgement`() = runTest {
        val store = FakeStore(entry())
        var account = 1
        val processor =
            LibrarySyncProcessor(store, { account }) { _, _ ->
                account = 2
                SyncedLibraryIdentity(10, "remote")
            }
        assertEquals(LibrarySyncOutcome.ACCOUNT_CHANGED, processor.run("local", 1))
        assertNull(store.library?.id)
        assertTrue(store.library!!.localUnsynced)
    }

    @Test
    fun `network failure keeps the durable request for a later worker`() = runTest {
        val store = FakeStore(entry())
        val before = store.library?.localSyncRevision
        val processor =
            LibrarySyncProcessor(store, { 1 }) { _, _ -> throw IllegalStateException("offline") }
        assertEquals(LibrarySyncOutcome.RETRY, processor.run("local", 1))
        assertEquals(before, store.library?.localSyncRevision)
        assertEquals(1, store.failures)
    }

    @Test
    fun `worker cancellation propagates without recording a business failure`() = runTest {
        val store = FakeStore(entry())
        val processor = LibrarySyncProcessor(store, { 1 }) { _, _ -> throw CancellationException() }
        try {
            processor.run("local", 1)
            fail("Expected cancellation")
        } catch (_: CancellationException) {}
        assertEquals(0, store.failures)
    }

    @Test
    fun `deleted and disabled entries never issue network requests`() = runTest {
        for (entry in listOf(null, entry().copy(autoSync = false))) {
            val processor =
                LibrarySyncProcessor(FakeStore(entry), { 1 }) { _, _ -> error("Unexpected upload") }
            assertEquals(LibrarySyncOutcome.COMPLETE, processor.run("local", 1))
        }
    }

    @Test
    fun `resaving creates a new revision and switching owner creates a new cloud binding`() {
        val first = entry().copy(id = 10)
        val edited = first.prepareAutoSync(1)
        assertNotEquals(first.localSyncRevision, edited.localSyncRevision)
        assertEquals(first.uuid, edited.uuid)
        val otherAccount = edited.prepareAutoSync(2)
        assertNull(otherAccount.id)
        assertNotEquals(edited.uuid, otherAccount.uuid)
        assertEquals(2, otherAccount.localSyncOwnerId)
    }

    @Test
    fun `editing while logged out retains the original owner for deferred sync`() {
        val original = entry()
        val offlineEdit = original.copy(content = "say offline").prepareAutoSync(null)
        assertEquals(1, offlineEdit.localSyncOwnerId)
        assertNotNull(offlineEdit.localSyncRevision)
        assertNotEquals(original.localSyncRevision, offlineEdit.localSyncRevision)
    }
}
