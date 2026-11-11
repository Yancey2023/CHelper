/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import yancey.chelper.network.library.data.LibraryFunction

private val Context.localLibraryDataStore: DataStore<LocalLibraryData> by
    dataStore(
        fileName = "local_library.json",
        serializer = LocalLibrarySerializer,
        produceMigrations = {
            listOf(
                LocalLibraryDataMigrationToV75(it),
                LocalLibraryStableIdMigration(),
            )
        },
    )

private val Context.localLibraryEditDraftDataStore: DataStore<LocalLibraryEditDraftData> by
    dataStore(
        fileName = "local_library_edit_drafts.json",
        serializer = LocalLibraryEditDraftSerializer,
    )

@Serializable data class LocalLibraryData(val functions: List<LibraryFunction> = emptyList())

@Serializable
data class LocalLibraryEditDraftData(val drafts: Map<String, LocalLibraryEditDraft> = emptyMap())

@Serializable
data class LocalLibraryEditDraft(
    val name: String = "",
    val version: String = "",
    val description: String = "",
    val tags: String = "",
    val commands: String = "",
    val autoSync: Boolean = false,
    val useV2: Boolean = true,
    val updatedAt: Long = 0L,
)

object LocalLibrarySerializer : Serializer<LocalLibraryData> {

    override val defaultValue: LocalLibraryData = LocalLibraryData()

    override suspend fun readFrom(input: InputStream): LocalLibraryData =
        try {
            withContext(Dispatchers.IO) {
                Json.decodeFromString<LocalLibraryData>(input.readBytes().decodeToString())
            }
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read LocalLibraryData", serialization)
        }

    override suspend fun writeTo(t: LocalLibraryData, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(Json.encodeToString(t).encodeToByteArray())
        }
    }
}

object LocalLibraryEditDraftSerializer : Serializer<LocalLibraryEditDraftData> {
    override val defaultValue: LocalLibraryEditDraftData = LocalLibraryEditDraftData()

    override suspend fun readFrom(input: InputStream): LocalLibraryEditDraftData =
        try {
            withContext(Dispatchers.IO) {
                Json.decodeFromString<LocalLibraryEditDraftData>(input.readBytes().decodeToString())
            }
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read LocalLibraryEditDraftData", serialization)
        }

    override suspend fun writeTo(t: LocalLibraryEditDraftData, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(Json.encodeToString(t).encodeToByteArray())
        }
    }
}

class LocalCommandLabDataStore(private val context: Context) {

    fun localLibraryFunctions(): Flow<List<LibraryFunction>> =
        context.localLibraryDataStore.data.map { it.functions }

    fun localLibraryFunction(id: Int?): Flow<LibraryFunction?> =
        context.localLibraryDataStore.data.map {
            if (id != null && id in it.functions.indices) {
                it.functions[id]
            } else null
        }

    fun localLibraryFunction(localEntryId: String?): Flow<LibraryFunction?> =
        context.localLibraryDataStore.data.map { data ->
            localEntryId?.let { target -> data.functions.firstOrNull { it.localEntryId == target } }
        }

    fun localLibraryEditDraft(key: String): Flow<LocalLibraryEditDraft?> =
        context.localLibraryEditDraftDataStore.data.map { it.drafts[key] }

    suspend fun saveLocalLibraryEditDraft(key: String, draft: LocalLibraryEditDraft) {
        context.localLibraryEditDraftDataStore.updateData {
            it.copy(drafts = it.drafts + (key to draft))
        }
    }

    suspend fun clearLocalLibraryEditDraft(key: String) {
        context.localLibraryEditDraftDataStore.updateData {
            it.copy(drafts = it.drafts - key)
        }
    }

    suspend fun addLocalLibraryFunction(function: LibraryFunction): String {
        var assignedId = ""
        context.localLibraryDataStore.updateData { data ->
            val usedIds = data.functions.mapNotNull { it.localEntryId }.toMutableSet()
            assignedId = uniqueLocalEntryId(function.localEntryId, usedIds)
            data.copy(functions = data.functions + function.copy(localEntryId = assignedId))
        }
        return assignedId
    }

    suspend fun addLocalLibraryFunctions(functions: List<LibraryFunction>): List<String> {
        val assignedIds = mutableListOf<String>()
        context.localLibraryDataStore.updateData { data ->
            val usedIds = data.functions.mapNotNull { it.localEntryId }.toMutableSet()
            val normalized = functions.map { function ->
                val assignedId = uniqueLocalEntryId(function.localEntryId, usedIds)
                assignedIds += assignedId
                function.copy(localEntryId = assignedId)
            }
            data.copy(functions = data.functions + normalized)
        }
        return assignedIds
    }

    suspend fun updateLocalLibraryFunction(id: Int, function: LibraryFunction) {
        context.localLibraryDataStore.updateData {
            val newFunctions = it.functions.toMutableList()
            if (id in newFunctions.indices) {
                val stableId = newFunctions[id].localEntryId ?: function.localEntryId
                newFunctions[id] =
                    function.copy(localEntryId = stableId).trackLocalChanges(it.functions[id])
            }
            it.copy(functions = newFunctions)
        }
    }

    suspend fun updateLocalLibraryFunction(
        localEntryId: String,
        function: LibraryFunction,
    ): Boolean {
        var updated = false
        context.localLibraryDataStore.updateData { data ->
            val newFunctions =
                data.functions.map { existing ->
                    if (existing.localEntryId == localEntryId) {
                        updated = true
                        function.copy(localEntryId = localEntryId).trackLocalChanges(existing)
                    } else {
                        existing
                    }
                }
            data.copy(functions = newFunctions)
        }
        return updated
    }

    suspend fun markLocalLibrarySynced(
        localEntryId: String,
        uuid: String? = null,
        syncedLibrary: LibraryFunction? = null,
    ): Boolean {
        var updated = false
        context.localLibraryDataStore.updateData { data ->
            val newFunctions =
                data.functions.map { existing ->
                    if (existing.localEntryId == localEntryId) {
                        updated = true
                        existing.withLocalSyncResult(uuid, syncedLibrary)
                    } else {
                        existing
                    }
                }
            data.copy(functions = newFunctions)
        }
        return updated
    }

    suspend fun completeQueuedSync(
        localEntryId: String,
        ownerId: Int,
        revision: String,
        cloudId: Int?,
        cloudUuid: String?,
    ): Boolean {
        var completed = false
        context.localLibraryDataStore.updateData { data ->
            data.copy(
                functions =
                    data.functions.map { current ->
                        if (
                            current.localEntryId != localEntryId ||
                                current.localSyncOwnerId != ownerId
                        )
                            current
                        else {
                            completed = current.localSyncRevision == revision
                            current.afterQueuedSync(
                                ownerId,
                                revision,
                                SyncedLibraryIdentity(cloudId, cloudUuid),
                            )
                        }
                    }
            )
        }
        return completed
    }

    suspend fun failQueuedSync(localEntryId: String, revision: String, message: String) {
        context.localLibraryDataStore.updateData { data ->
            data.copy(
                functions =
                    data.functions.map { current ->
                        if (
                            current.localEntryId == localEntryId &&
                                current.localSyncRevision == revision
                        )
                            current.copy(localSyncError = message)
                        else current
                    }
            )
        }
    }

    suspend fun removeLocalLibraryFunction(id: Int) {
        context.localLibraryDataStore.updateData {
            val newFunctions = it.functions.toMutableList()
            if (id in newFunctions.indices) {
                newFunctions.removeAt(id)
            }
            it.copy(functions = newFunctions)
        }
    }

    suspend fun removeLocalLibraryFunction(localEntryId: String): Boolean {
        var removed = false
        context.localLibraryDataStore.updateData { data ->
            val newFunctions =
                data.functions.filterNot {
                    val matches = it.localEntryId == localEntryId
                    if (matches) removed = true
                    matches
                }
            data.copy(functions = newFunctions)
        }
        return removed
    }

    suspend fun removeLocalLibraryFunctions(localEntryIds: Set<String>): Int {
        if (localEntryIds.isEmpty()) return 0
        var removedCount = 0
        context.localLibraryDataStore.updateData { data ->
            val newFunctions =
                data.functions.filterNot {
                    val matches = it.localEntryId in localEntryIds
                    if (matches) removedCount++
                    matches
                }
            data.copy(functions = newFunctions)
        }
        return removedCount
    }

    private fun uniqueLocalEntryId(candidate: String?, usedIds: MutableSet<String>): String {
        val validCandidate = candidate?.takeIf { it.isNotBlank() && it !in usedIds }
        val assigned =
            validCandidate
                ?: generateSequence { UUID.randomUUID().toString() }.first { it !in usedIds }
        usedIds += assigned
        return assigned
    }
}

internal fun LibraryFunction.withLocalSyncResult(
    uuid: String?,
    syncedLibrary: LibraryFunction?,
): LibraryFunction {
    val matchesSyncedSnapshot =
        syncedLibrary == null ||
            name == syncedLibrary.name &&
                version == syncedLibrary.version &&
                note == syncedLibrary.note &&
                tags == syncedLibrary.tags &&
                content == syncedLibrary.content &&
                localIsV2 == syncedLibrary.localIsV2
    return copy(
        uuid = uuid?.takeIf(String::isNotBlank) ?: this.uuid,
        localUnsynced = !matchesSyncedSnapshot,
    )
}

class LocalLibraryStableIdMigration : DataMigration<LocalLibraryData> {
    override suspend fun shouldMigrate(currentData: LocalLibraryData): Boolean {
        val ids = currentData.functions.map { it.localEntryId }
        return ids.any { it.isNullOrBlank() } ||
            ids.filterNotNull().distinct().size != ids.filterNotNull().size
    }

    override suspend fun migrate(currentData: LocalLibraryData): LocalLibraryData {
        val usedIds = mutableSetOf<String>()
        val migrated =
            currentData.functions.map { function ->
                val existing = function.localEntryId?.takeIf { it.isNotBlank() && it !in usedIds }
                val assigned =
                    existing
                        ?: generateSequence { UUID.randomUUID().toString() }
                            .first { it !in usedIds }
                usedIds += assigned
                function.copy(localEntryId = assigned)
            }
        return currentData.copy(functions = migrated)
    }

    override suspend fun cleanUp() = Unit
}

/** 0.4.1 版本之后，私有命令库存储从自己写的框架改为使用官方方案 DataScore，该文件用于数据迁移 */
class LocalLibraryDataMigrationToV75(private val context: Context) :
    DataMigration<LocalLibraryData> {
    override suspend fun shouldMigrate(currentData: LocalLibraryData): Boolean {
        return context.dataDir.resolve("localLibrary").resolve("data.json").exists()
    }

    override suspend fun migrate(currentData: LocalLibraryData): LocalLibraryData {
        return try {
            val oldFile = context.dataDir.resolve("localLibrary").resolve("data.json")
            val oldFunctions =
                Json.decodeFromString<List<LibraryFunction>>(oldFile.readBytes().decodeToString())
            LocalLibraryData(functions = oldFunctions)
        } catch (_: Throwable) {
            currentData
        }
    }

    override suspend fun cleanUp() {
        val oldFile = context.dataDir.resolve("localLibrary").resolve("data.json")
        if (oldFile.exists()) {
            oldFile.delete()
        }
        val oldDir = context.dataDir.resolve("localLibrary")
        if (oldDir.exists() && oldDir.listFiles()?.isEmpty() == true) {
            oldDir.delete()
        }
    }
}
