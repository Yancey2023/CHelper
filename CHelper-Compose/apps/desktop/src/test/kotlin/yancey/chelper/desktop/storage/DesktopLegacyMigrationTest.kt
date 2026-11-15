package yancey.chelper.desktop.storage

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.data.local.ClientDataStores
import yancey.chelper.data.migration.desktop.LegacyWorkspaceDraft
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.domain.command.CommandDraftWrite
import yancey.chelper.domain.rawtext.RawtextPreferences
import yancey.chelper.domain.rawtext.RawtextSegmentSnapshot
import yancey.chelper.domain.settings.Settings

class DesktopLegacyMigrationTest {
    @Test
    fun oldRawtextAndThemeSurviveTheFirstCommandDraftWrite() = runBlocking {
        val directory = Files.createTempDirectory("CHelper-legacy-").toFile()
        val original =
            """{"command":"say old","darkMode":true,"segments":[{"type":"Text","text":"旧草稿"}]}"""
        val source = File(directory, "workspace.json").apply { writeText(original) }
        val migration = DesktopLegacyMigration(directory)
        migration.ensure()
        ClientDataStores(directory.toOkioPath()).use { stores ->
            val repo = stores.commandDrafts
            assertEquals("say old", repo.read()?.text)
            repo.save(
                CommandDraftWrite(CommandDraft("say new", 7, 7), repo.edits.claimText(), true)
            )
        }
        ClientDataStores(directory.toOkioPath()).use {
            assertEquals("say new", it.commandDrafts.read()?.text)
        }
        assertEquals(original, File(directory, "workspace.legacy.json").readText())
        val prefs =
            Json.decodeFromString<RawtextPreferences>(
                File(directory, "rawtext_studio.json").readText()
            )
        assertTrue(prefs.autoSaveEnabled)
        assertEquals(
            "旧草稿",
            Json.decodeFromString<List<RawtextSegmentSnapshot>>(prefs.draft!!).single().text,
        )
        assertEquals(
            "MODE_NIGHT_YES",
            Json.decodeFromString<Settings>(File(directory, "settings.json").readText()).themeId,
        )
        assertEquals(original, source.readText())
        assertTrue(directory.deleteRecursively())
    }

    @Test
    fun migratingAgainDoesNotOverwriteNewerPreferences() {
        val directory = Files.createTempDirectory("CHelper-legacy-existing-").toFile()
        File(directory, "workspace.json")
            .writeText(Json.encodeToString(LegacyWorkspaceDraft(darkMode = true)))
        val current = Settings(themeId = "MODE_NIGHT_NO")
        File(directory, "settings.json").writeText(Json.encodeToString(current))
        DesktopLegacyMigration(directory).ensure()
        DesktopLegacyMigration(directory).ensure()
        assertEquals(
            current,
            Json.decodeFromString<Settings>(File(directory, "settings.json").readText()),
        )
        assertTrue(directory.deleteRecursively())
    }

    @Test
    fun failedWritesCanBeRetriedWithoutLosingTheLatestSnapshot() = runBlocking {
        val directory = Files.createTempDirectory("CHelper-retry-").toFile()
        val blocked = File(directory, "parent").apply { writeText("not a directory") }
        ClientDataStores(blocked.toOkioPath()).use { stores ->
            val repo = stores.commandDrafts
            val write =
                CommandDraftWrite(
                    CommandDraft("say retained", 12, 12),
                    repo.edits.claimText(),
                    true,
                )
            try {
                repo.save(write)
                fail("The invalid parent must fail")
            } catch (_: IOException) {}
            assertTrue(blocked.delete())
            assertTrue(blocked.mkdir())
            repo.save(write)
            assertEquals("say retained", repo.read()?.text)
        }
        assertTrue(directory.deleteRecursively())
    }
}
