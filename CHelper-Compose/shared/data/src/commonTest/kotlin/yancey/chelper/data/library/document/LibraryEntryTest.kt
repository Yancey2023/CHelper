package yancey.chelper.data.library.document

import kotlin.test.*
import kotlinx.serialization.json.Json
import yancey.chelper.domain.library.LibraryEntry
import yancey.chelper.domain.library.LibraryFunction

class LibraryEntryTest {
    @Test
    fun backupExportContainsEditableDocumentsWithoutCloudWriteMetadata() {
        val entry =
            LibraryEntry(
                localId = "local",
                remoteId = 77,
                name = "one",
                body = "say 中文",
                tags = listOf("test"),
            )
        val backup = McdLibraryDocuments.encodeBackup(listOf(entry))
        val wire = Json.decodeFromString<List<LibraryFunction>>(backup).single()
        assertNull(wire.id)
        assertNull(wire.localSyncOwnerId)
        assertNull(wire.localSyncRevision)
        assertFalse(wire.autoSync == true)
        assertEquals(
            entry.copy(localId = null, remoteId = null),
            McdLibraryDocuments.decodeBackup(backup).single(),
        )
    }

    @Test
    fun editingProjectionContainsMcdBodyAndPreservesLegacyVersion() {
        val function =
            LibraryFunction(
                name = "legacy",
                content = "say legacy",
                localIsV2 = false,
                localEntryId = "id",
            )
        val document = function.toLibraryEntry()
        assertEquals("say legacy", document.body)
        assertFalse(document.isV2)
        assertEquals("id", document.localId)
        assertContains(McdLibraryDocuments.toMcd(document), "say legacy")
    }
}
