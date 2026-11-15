package yancey.chelper.desktop.storage

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.data.library.document.localLibraryEditDraftKey
import yancey.chelper.data.local.ClientDataStores
import yancey.chelper.data.local.library.LocalLibraryEditDraft
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.domain.command.CommandDraftWrite
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.rawtext.RawtextField
import yancey.chelper.domain.rawtext.RawtextPreferences
import yancey.chelper.ui.feature.library.local.LocalLibraryEditViewModel

/** Uses the very same original repositories and serializers as Android. */
class DesktopOriginalStoresTest {
    @Test
    fun previousDesktopEntryDocumentsRetainBodyIdentityAndMatchingEditDraft() = runBlocking {
        val directory = Files.createTempDirectory("CHelper-desktop-entry-schema-").toFile()
        try {
            File(directory, "libraries.json")
                .writeText(
                    """{"libraries":[{"localId":"stable","name":"old library","body":"say preserved","isV2":false}],"drafts":{"stable":{"name":"draft","body":"say editing","isV2":false}}}"""
                )
            ClientDataStores(directory.toOkioPath()).use {
                val library = it.libraries.localLibraryFunctions().first().single()
                assertEquals("stable", library.localEntryId)
                assertEquals("say preserved", library.content)
                assertEquals(false, library.localIsV2)
                assertEquals(
                    "say editing",
                    it.libraries
                        .localLibraryEditDraft(localLibraryEditDraftKey(library.localEntryId))
                        .first()
                        ?.commands,
                )
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun allSevenOriginalStoresSurviveRestartOnWindows() = runBlocking {
        val directory = Files.createTempDirectory("CHelper-original-stores-").toFile()
        val rawtext =
            RawtextPreferences(
                copyFormat = "compressed",
                autoSavePrompted = true,
                autoSaveEnabled = true,
                draft = """[{"type":"Text","text":"草稿"}]""",
                mockSettings = "{}",
            )
        try {
            val id =
                ClientDataStores(directory.toOkioPath()).use { stores ->
                    val id =
                        stores.libraries.addLocalLibraryFunction(
                            LibraryFunction(name = "original", content = "say hello")
                        )
                    stores.libraries.saveLocalLibraryEditDraft(
                        localLibraryEditDraftKey(id),
                        LocalLibraryEditDraft(name = "edited", commands = "say draft"),
                    )
                    stores.settings.setThemeId("MODE_NIGHT_YES")
                    stores.rawtext.savePreferences(
                        rawtext,
                        stores.rawtext.edits.claim(*RawtextField.entries.toTypedArray()),
                    )
                    stores.history.add("say history")
                    stores.favorites.addFavorite(LibraryFunction(id = 42, name = "favorite"))
                    val draft = stores.commandDrafts
                    draft.save(
                        CommandDraftWrite(
                            CommandDraft("say command", 11, 11),
                            draft.edits.claimText(),
                            true,
                        )
                    )
                    id
                }
            ClientDataStores(directory.toOkioPath()).use { stores ->
                assertEquals(
                    id,
                    stores.libraries.localLibraryFunctions().first().single().localEntryId,
                )
                assertEquals(
                    "say draft",
                    stores.libraries
                        .localLibraryEditDraft(localLibraryEditDraftKey(id))
                        .first()
                        ?.commands,
                )
                assertEquals("MODE_NIGHT_YES", stores.settings.themeId().first())
                assertEquals(rawtext, stores.rawtext.preferences().first())
                assertEquals(listOf("say history"), stores.history.history().first())
                assertEquals(42, stores.favorites.favorites().first().single().id)
                assertEquals("say command", stores.commandDrafts.read()?.text)
                listOf(
                        "settings.json",
                        "rawtext_studio.json",
                        "copy_history.json",
                        "favorite_library.json",
                        "local_library.json",
                        "local_library_edit_drafts.json",
                        "command_draft.dat",
                    )
                    .forEach { assertTrue("Missing store $it", File(directory, it).isFile) }
                stores.libraries.removeLocalLibraryFunction(id)
            }
            ClientDataStores(directory.toOkioPath()).use {
                assertTrue(it.libraries.localLibraryFunctions().first().isEmpty())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun oldDesktopDraftKeysRestoreInTheOriginalAndroidEditor() = runBlocking {
        val directory = Files.createTempDirectory("CHelper-desktop-draft-keys-").toFile()
        try {
            File(directory, "libraries.json")
                .writeText(
                    """{"libraries":[],"drafts":{"new":{"name":"draft name","body":"say recovered","isV2":false}}}"""
                )
            ClientDataStores(directory.toOkioPath()).use { stores ->
                val editor = LocalLibraryEditViewModel()
                editor.ensureEditingTarget(null, null, null)
                editor.restoreDraft(stores.libraries.localLibraryEditDraft(editor.draftKey).first())
                assertEquals("draft name", editor.name.text.toString())
                assertEquals("say recovered", editor.commands.text.toString())
                assertFalse(editor.useV2)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun previouslyMigratedUnprefixedDraftsAreRepairedWithoutReplacingNativeDrafts() = runBlocking {
        val directory = Files.createTempDirectory("CHelper-repair-draft-keys-").toFile()
        try {
            File(directory, "local_library_edit_drafts.json")
                .writeText(
                    """{"drafts":{"new":{"name":"new draft","commands":"say new"},"stable":{"name":"old key"},"local-library:update:stable":{"name":"current draft"}}}"""
                )
            ClientDataStores(directory.toOkioPath()).use { stores ->
                assertEquals(
                    "new draft",
                    stores.libraries.localLibraryEditDraft(localLibraryEditDraftKey()).first()?.name,
                )
                assertEquals(
                    "current draft",
                    stores.libraries
                        .localLibraryEditDraft(localLibraryEditDraftKey("stable"))
                        .first()
                        ?.name,
                )
                assertNull(stores.libraries.localLibraryEditDraft("stable").first())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun previousDesktopDocumentsMigrateOnlyOnceAndRetainTheSourceFile() = runBlocking {
        val directory = Files.createTempDirectory("CHelper-old-desktop-").toFile()
        try {
            val original =
                """[{"name":"one"},{"name":"two","localEntryId":"same"},{"name":"three","localEntryId":"same"}]"""
            File(directory, "libraries.json").writeText(original)
            ClientDataStores(directory.toOkioPath()).use { stores ->
                val entries = stores.libraries.localLibraryFunctions().first()
                assertEquals(3, entries.map { it.localEntryId }.toSet().size)
                stores.libraries.removeLocalLibraryFunctions(
                    entries.mapNotNull { it.localEntryId }.toSet()
                )
            }
            ClientDataStores(directory.toOkioPath()).use {
                assertTrue(it.libraries.localLibraryFunctions().first().isEmpty())
            }
            assertEquals(original, File(directory, "libraries.json").readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
