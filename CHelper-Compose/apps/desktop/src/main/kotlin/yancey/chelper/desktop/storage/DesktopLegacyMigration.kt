package yancey.chelper.desktop.storage

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import yancey.chelper.data.migration.desktop.LegacyWorkspaceDraft
import yancey.chelper.domain.rawtext.RawtextPreferences
import yancey.chelper.domain.settings.Settings

/** Migrate before any editor can replace the old combined document; retain an exact backup. */
internal class DesktopLegacyMigration(private val directory: File) {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var complete = false

    @Synchronized
    fun ensure() {
        if (complete) return
        val source = File(directory, "workspace.json")
        if (source.exists()) {
            val bytes = source.readBytes()
            val text = bytes.decodeToString()
            val document = json.parseToJsonElement(text).jsonObject
            if (document.keys.any { it in setOf("segments", "mock", "darkMode") }) {
                val draft = json.decodeFromString<LegacyWorkspaceDraft>(text)
                val backup = File(directory, "workspace.legacy.json")
                if (!backup.exists()) atomicWrite(backup, bytes)
                val theme = File(directory, "settings.json")
                if (!theme.exists())
                    atomicWrite(
                        theme,
                        json
                            .encodeToString(
                                Settings(
                                    themeId =
                                        draft.darkMode?.let {
                                            if (it) "MODE_NIGHT_YES" else "MODE_NIGHT_NO"
                                        },
                                    cpackBranch =
                                        draft.packId
                                            .removeSuffix(".cpack")
                                            .substringBeforeLast('-', "")
                                            .takeIf { it.isNotBlank() },
                                )
                            )
                            .encodeToByteArray(),
                    )
                val rawtext = File(directory, "rawtext_studio.json")
                val empty = LegacyWorkspaceDraft()
                if (
                    !rawtext.exists() &&
                        (draft.segments != empty.segments || draft.mock != empty.mock)
                ) {
                    atomicWrite(
                        rawtext,
                        json
                            .encodeToString(
                                RawtextPreferences(
                                    copyFormat = "formatted",
                                    autoSavePrompted = true,
                                    autoSaveEnabled = true,
                                    draft = json.encodeToString(draft.segments),
                                    mockSettings = json.encodeToString(draft.mock),
                                )
                            )
                            .encodeToByteArray(),
                    )
                }
            }
        }
        complete = true
    }
}
