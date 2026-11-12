package yancey.chelper.data.local

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.domain.command.CommandDraftWrite
import yancey.chelper.domain.library.LibraryFunction

class LegacyStoreRecoveryTest {
    @Test
    fun independentStoreDirectoriesDoNotInvalidateEachOthersCommandWrites() = runBlocking {
        val root = Files.createTempDirectory("CHelper-independent-drafts-").toFile()
        try {
            ClientDataStores(File(root, "one")).use { first ->
                ClientDataStores(File(root, "two")).use { second ->
                    val firstRevision = first.commandDrafts.edits.claimText()
                    val secondRevision = second.commandDrafts.edits.claimText()
                    first.commandDrafts.save(
                        CommandDraftWrite(CommandDraft("say one", 7, 7), firstRevision, true)
                    )
                    second.commandDrafts.save(
                        CommandDraftWrite(CommandDraft("say two", 7, 7), secondRevision, true)
                    )
                    assertEquals("say one", first.commandDrafts.read()?.text)
                    assertEquals("say two", second.commandDrafts.read()?.text)
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptSourcesAreRetainedAndCanBeRepairedForTheNextStartup() = runBlocking {
        val root = Files.createTempDirectory("CHelper-legacy-recovery-").toFile()
        val settings =
            File(root, "settings/settings.json").apply {
                parentFile.mkdirs()
                writeText("{")
            }
        val libraries =
            File(root, "localLibrary/data.json").apply {
                parentFile.mkdirs()
                writeText("[")
            }
        val target = File(root, "files/datastore")
        try {
            ClientDataStores(target, root).use {
                it.settings.settings().first()
                assertTrue(it.libraries.localLibraryFunctions().first().isEmpty())
            }
            assertEquals("{", settings.readText())
            assertEquals("[", libraries.readText())
            settings.writeText("""{"themeId":"MODE_NIGHT_YES"}""")
            libraries.writeText("""[{"name":"recovered","content":"say recovered"}]""")
            ClientDataStores(target, root).use {
                assertEquals("MODE_NIGHT_YES", it.settings.themeId().first())
                assertEquals(
                    "say recovered",
                    it.libraries.localLibraryFunctions().first().single().content,
                )
            }
            assertFalse(settings.exists())
            assertFalse(libraries.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun leftoverLegacyFilesCannotOverwriteCurrentStoresIncludingDeliberateDeletions() =
        runBlocking {
            val root = Files.createTempDirectory("CHelper-legacy-leftovers-").toFile()
            val target = File(root, "files/datastore")
            try {
                ClientDataStores(target, root).use {
                    it.settings.setThemeId("MODE_NIGHT_NO")
                    it.history.add("say current")
                    val id = it.libraries.addLocalLibraryFunction(LibraryFunction(name = "deleted"))
                    it.libraries.removeLocalLibraryFunction(id)
                }
                File(root, "settings/settings.json").apply {
                    parentFile.mkdirs()
                    writeText("""{"themeId":"MODE_NIGHT_YES"}""")
                }
                File(root, "history.txt").writeText("say stale")
                File(root, "localLibrary/data.json").apply {
                    parentFile.mkdirs()
                    writeText("""[{"name":"deleted"}]""")
                }
                ClientDataStores(target, root).use {
                    assertEquals("MODE_NIGHT_NO", it.settings.themeId().first())
                    assertEquals(listOf("say current"), it.history.history().first())
                    assertTrue(it.libraries.localLibraryFunctions().first().isEmpty())
                }
            } finally {
                root.deleteRecursively()
            }
        }
}
