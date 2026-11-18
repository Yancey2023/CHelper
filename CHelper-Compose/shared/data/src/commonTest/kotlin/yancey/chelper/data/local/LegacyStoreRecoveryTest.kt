package yancey.chelper.data.local

import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.domain.command.CommandDraftWrite
import yancey.chelper.domain.library.LibraryFunction

class LegacyStoreRecoveryTest {
    private val fs = FakeFileSystem()

    @Test
    fun independentStoreDirectoriesDoNotInvalidateEachOthersCommandWrites() = runTest {
        val root = "/CHelper-independent-drafts-".toPath().also { fs.createDirectories(it) }
        try {
            ClientDataStores(root / "one", fileSystem = fs).use { first ->
                ClientDataStores(root / "two", fileSystem = fs).use { second ->
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
            fs.deleteRecursively(root)
        }
    }

    @Test
    fun corruptSourcesAreRetainedAndCanBeRepairedForTheNextStartup() = runTest {
        val root = "/CHelper-legacy-recovery-".toPath().also { fs.createDirectories(it) }
        val settings =
            (root / "settings/settings.json").apply {
                fs.createDirectories(parent!!)
                fs.writeText(this, "{")
            }
        val libraries =
            (root / "localLibrary/data.json").apply {
                fs.createDirectories(parent!!)
                fs.writeText(this, "[")
            }
        val target = (root / "files/datastore")
        try {
            ClientDataStores(target, root, fileSystem = fs).use {
                it.settings.settings().first()
                assertTrue(it.libraries.localLibraryFunctions().first().isEmpty())
            }
            assertEquals("{", fs.readText(settings))
            assertEquals("[", fs.readText(libraries))
            fs.writeText(settings, """{"themeId":"MODE_NIGHT_YES"}""")
            fs.writeText(libraries, """[{"name":"recovered","content":"say recovered"}]""")
            ClientDataStores(target, root, fileSystem = fs).use {
                assertEquals("MODE_NIGHT_YES", it.settings.themeId().first())
                assertEquals(
                    "say recovered",
                    it.libraries.localLibraryFunctions().first().single().content,
                )
            }
            assertFalse(fs.exists(settings))
            assertFalse(fs.exists(libraries))
        } finally {
            fs.deleteRecursively(root)
        }
    }

    @Test
    fun leftoverLegacyFilesCannotOverwriteCurrentStoresIncludingDeliberateDeletions() = runTest {
        val root = "/CHelper-legacy-leftovers-".toPath().also { fs.createDirectories(it) }
        val target = (root / "files/datastore")
        try {
            ClientDataStores(target, root, fileSystem = fs).use {
                it.settings.setThemeId("MODE_NIGHT_NO")
                it.history.add("say current")
                val id = it.libraries.addLocalLibraryFunction(LibraryFunction(name = "deleted"))
                it.libraries.removeLocalLibraryFunction(id)
            }
            (root / "settings/settings.json").apply {
                fs.createDirectories(parent!!)
                fs.writeText(this, """{"themeId":"MODE_NIGHT_YES"}""")
            }
            fs.writeText(root / "history.txt", "say stale")
            (root / "localLibrary/data.json").apply {
                fs.createDirectories(parent!!)
                fs.writeText(this, """[{"name":"deleted"}]""")
            }
            ClientDataStores(target, root, fileSystem = fs).use {
                assertEquals("MODE_NIGHT_NO", it.settings.themeId().first())
                assertEquals(listOf("say current"), it.history.history().first())
                assertTrue(it.libraries.localLibraryFunctions().first().isEmpty())
            }
        } finally {
            fs.deleteRecursively(root)
        }
    }
}
