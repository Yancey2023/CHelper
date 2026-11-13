package yancey.chelper.data.local

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okio.Path.Companion.toPath
import yancey.chelper.data.library.document.toLibraryFunction
import yancey.chelper.data.local.command.CommandDraftSerializer
import yancey.chelper.data.local.command.CommandDraftStore
import yancey.chelper.data.local.command.LegacyCommandDraftMigration
import yancey.chelper.data.local.history.CopyHistoryDataStore
import yancey.chelper.data.local.history.CopyHistorySerializer
import yancey.chelper.data.local.library.FavoriteLibraryDataStore
import yancey.chelper.data.local.library.FavoriteLibrarySerializer
import yancey.chelper.data.local.library.LocalCommandLabDataStore
import yancey.chelper.data.local.library.LocalLibraryData
import yancey.chelper.data.local.library.LocalLibraryEditDraft
import yancey.chelper.data.local.library.LocalLibraryEditDraftData
import yancey.chelper.data.local.library.LocalLibraryEditDraftSerializer
import yancey.chelper.data.local.library.LocalLibrarySerializer
import yancey.chelper.data.local.library.LocalLibraryStableIdMigration
import yancey.chelper.data.local.migration.CopyHistoryDataMigrationToV75
import yancey.chelper.data.local.migration.LocalLibraryDataMigrationToV75
import yancey.chelper.data.local.migration.LocalLibraryDraftKeyMigration
import yancey.chelper.data.local.migration.SettingsMigrationToV74
import yancey.chelper.data.local.migration.originalLibraryDraftKey
import yancey.chelper.data.local.settings.SettingsDataStore
import yancey.chelper.data.local.settings.SettingsSerializer
import yancey.chelper.data.migration.desktop.LegacyCommandWorkspace
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.domain.library.LibraryEntry
import yancey.chelper.domain.library.LibraryFunction

/**
 * One store graph for Android and Windows. Hosts supply directories, not repository
 * implementations.
 */
class ClientDataStores(
    private val directory: File,
    private val legacyDirectory: File = directory,
    private val legacyCommandDraftFile: File? = null,
) : Closeable {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private fun <T> store(
        name: String,
        serializer: OkioSerializer<T>,
        migrations: List<DataMigration<T>> = emptyList(),
    ) = createDataStore(File(directory, name).absolutePath.toPath(), serializer, scope, migrations)

    val settings =
        SettingsDataStore(
            store(
                "settings.json",
                SettingsSerializer,
                listOf(SettingsMigrationToV74(legacyDirectory, File(directory, "settings.json"))),
            )
        )
    val rawtext = RawtextDataStore(store("rawtext_studio.json", RawtextPreferencesSerializer))
    val history =
        CopyHistoryDataStore(
            store(
                "copy_history.json",
                CopyHistorySerializer,
                listOf(
                    CopyHistoryDataMigrationToV75(
                        legacyDirectory,
                        File(directory, "copy_history.json"),
                    ),
                    migration("history") { current ->
                        val old = File(legacyDirectory, "history.json")
                        if (current.history.isEmpty() && old.exists())
                            current.copy(
                                history = json.decodeFromString<List<String>>(old.readText())
                            )
                        else current
                    },
                ),
            )
        )
    val libraries =
        LocalCommandLabDataStore(
            store(
                "local_library.json",
                LocalLibrarySerializer,
                listOf(
                    LocalLibraryDataMigrationToV75(
                        legacyDirectory,
                        File(directory, "local_library.json"),
                    ),
                    migration("libraries") { current ->
                        val old = File(legacyDirectory, "libraries.json")
                        if (current.functions.isNotEmpty() || !old.exists()) current
                        else {
                            val element = json.parseToJsonElement(old.readText())
                            val entries =
                                if (element is JsonArray) element
                                else element.jsonObject["libraries"] ?: JsonArray(emptyList())
                            val loaded =
                                LocalLibraryData(
                                    entries.jsonArray.map { entry ->
                                        if (
                                            entry.jsonObject.keys.any {
                                                it in setOf("body", "localId", "isV2")
                                            }
                                        ) {
                                            val oldEntry =
                                                json.decodeFromJsonElement<LibraryEntry>(entry)
                                            oldEntry
                                                .toLibraryFunction()
                                                .copy(id = oldEntry.remoteId)
                                        } else json.decodeFromJsonElement<LibraryFunction>(entry)
                                    }
                                )
                            LocalLibraryStableIdMigration().migrate(loaded)
                        }
                    },
                    LocalLibraryStableIdMigration(),
                ),
            ),
            store(
                "local_library_edit_drafts.json",
                LocalLibraryEditDraftSerializer,
                listOf(
                    LocalLibraryDraftKeyMigration(),
                    migration("library-drafts") { current ->
                        val old = File(legacyDirectory, "libraries.json")
                        if (current.drafts.isNotEmpty() || !old.exists()) current
                        else {
                            val element = json.parseToJsonElement(old.readText())
                            val drafts = (element as? JsonObject)?.get("drafts")
                            if (drafts == null) current
                            else
                                LocalLibraryEditDraftData(
                                    json
                                        .decodeFromJsonElement<Map<String, LibraryEntry>>(drafts)
                                        .mapKeys { (key, _) -> originalLibraryDraftKey(key) }
                                        .mapValues { (_, entry) ->
                                            LocalLibraryEditDraft(
                                                name = entry.name,
                                                version = entry.version,
                                                description = entry.note,
                                                tags = entry.tags.joinToString(","),
                                                commands = entry.body,
                                                useV2 = entry.isV2,
                                            )
                                        }
                                )
                        }
                    },
                ),
            ),
        )
    val favorites =
        FavoriteLibraryDataStore(store("favorite_library.json", FavoriteLibrarySerializer))
    val commandDrafts =
        CommandDraftStore(
            DataStoreFactory.create(
                serializer = CommandDraftSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { null },
                migrations =
                    listOfNotNull(
                        legacyCommandDraftFile?.let {
                            LegacyCommandDraftMigration(it, File(directory, "command_draft.dat"))
                        },
                        migration("command") { current ->
                            val old = File(legacyDirectory, "workspace.json")
                            if (current != null || !old.exists()) current
                            else {
                                val draft =
                                    json.decodeFromString<LegacyCommandWorkspace>(old.readText())
                                CommandDraft(draft.command, draft.selection, draft.selection)
                                    .clamped()
                            }
                        },
                    ),
                scope = scope,
                produceFile = { File(directory, "command_draft.dat") },
            )
        )

    private fun <T> migration(name: String, block: suspend (T) -> T): DataMigration<T> {
        val (sourceName, targetName) =
            when (name) {
                "history" -> "history.json" to "copy_history.json"
                "libraries" -> "libraries.json" to "local_library.json"
                "library-drafts" -> "libraries.json" to "local_library_edit_drafts.json"
                "command" -> "workspace.json" to "command_draft.dat"
                else -> error("Unknown legacy store: $name")
            }
        return object : DataMigration<T> {
            override suspend fun shouldMigrate(currentData: T) =
                !File(directory, "$name.datastore-migration.complete").exists() &&
                    File(legacyDirectory, sourceName).isFile

            override suspend fun migrate(currentData: T) =
                if (File(directory, targetName).exists()) currentData else block(currentData)

            override suspend fun cleanUp() {
                check(directory.isDirectory || directory.mkdirs()) {
                    "Unable to create DataStore directory"
                }
                val marker = File(directory, "$name.datastore-migration.complete")
                check(marker.createNewFile() || marker.isFile) {
                    "Unable to record completed migration"
                }
            }
        }
    }

    override fun close() = runBlocking { job.cancelAndJoin() }
}
