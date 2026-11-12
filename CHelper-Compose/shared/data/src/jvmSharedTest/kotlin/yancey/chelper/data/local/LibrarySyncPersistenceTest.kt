package yancey.chelper.data.local

import androidx.datastore.core.DataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import yancey.chelper.data.local.library.LocalLibraryData
import yancey.chelper.data.local.library.LocalLibrarySerializer
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.sync.prepareAutoSync

class LibrarySyncPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `pending sync revision and account survive data store recreation`() = runBlocking {
        val file = temporary.root.resolve("libraries.json")
        val library =
            LibraryFunction(localEntryId = "stable", content = "say 中文", autoSync = true)
                .prepareAutoSync(42)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            DataStoreFactory.create(
                    LocalLibrarySerializer.asStreamSerializer(),
                    scope = firstScope,
                ) {
                    file
                }
                .updateData { LocalLibraryData(listOf(library)) }
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val nextScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val restored =
                DataStoreFactory.create(
                        LocalLibrarySerializer.asStreamSerializer(),
                        scope = nextScope,
                    ) {
                        file
                    }
                    .data
                    .first()
                    .functions
                    .single()
            assertEquals("stable", restored.localEntryId)
            assertEquals(42, restored.localSyncOwnerId)
            assertEquals(library.localSyncRevision, restored.localSyncRevision)
            assertTrue(restored.localUnsynced)
            assertEquals(library.uuid, restored.uuid)
        } finally {
            nextScope.coroutineContext.job.cancelAndJoin()
        }
    }
}
