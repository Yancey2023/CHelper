package yancey.chelper.data.library.document

import kotlin.test.*
import kotlinx.serialization.json.Json
import yancey.chelper.data.library.decodeLocalLibraryImport
import yancey.chelper.domain.library.LibraryFunction

class LibraryBackupTest {
    @Test
    fun importingPendingBackupClearsCloudWritesOnBothPlatforms() {
        val source =
            LibraryFunction(
                id = 12,
                uuid = "remote",
                content = "say old",
                autoSync = true,
                localUnsynced = true,
                localSyncOwnerId = 7,
                localSyncRevision = "old-write",
                localEntryId = "old-local",
            )
        val imported = decodeLocalLibraryImport(Json.encodeToString(source)).single()
        assertNull(imported.id)
        assertNull(imported.uuid)
        assertNull(imported.localSyncRevision)
        assertNull(imported.localSyncOwnerId)
        assertNull(imported.localEntryId)
        assertFalse(imported.autoSync == true)
        assertEquals(source.content, imported.content)
    }
}
