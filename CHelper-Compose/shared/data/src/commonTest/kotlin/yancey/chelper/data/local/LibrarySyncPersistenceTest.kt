package yancey.chelper.data.local

import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import yancey.chelper.data.local.library.LocalLibraryData
import yancey.chelper.data.local.library.LocalLibrarySerializer
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.sync.prepareAutoSync

class LibrarySyncPersistenceTest {
    private val fs = FakeFileSystem()

    @Test
    fun `pending sync revision and account survive data store recreation`() = runTest {
        val file = "/libraries.json".toPath()
        val library =
            LibraryFunction(localEntryId = "stable", content = "say 中文", autoSync = true)
                .prepareAutoSync(42)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            createDataStore(file, LocalLibrarySerializer, firstScope, fileSystem = fs).updateData {
                LocalLibraryData(listOf(library))
            }
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val nextScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val restored =
                createDataStore(file, LocalLibrarySerializer, nextScope, fileSystem = fs)
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
