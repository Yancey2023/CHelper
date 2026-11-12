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

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package yancey.chelper.data.local.library

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.sync.SyncedLibraryIdentity
import yancey.chelper.domain.library.sync.afterQueuedSync
import yancey.chelper.domain.library.sync.trackLocalChanges
import yancey.chelper.domain.library.sync.withDocumentEdits

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

object LocalLibrarySerializer : OkioSerializer<LocalLibraryData> {

    override val defaultValue: LocalLibraryData = LocalLibraryData()

    override suspend fun readFrom(source: BufferedSource): LocalLibraryData =
        try {
            Json.decodeFromString<LocalLibraryData>(source.readByteArray().decodeToString())
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read LocalLibraryData", serialization)
        }

    override suspend fun writeTo(t: LocalLibraryData, sink: BufferedSink) {
        sink.write(Json.encodeToString(t).encodeToByteArray())
    }
}

object LocalLibraryEditDraftSerializer : OkioSerializer<LocalLibraryEditDraftData> {
    override val defaultValue: LocalLibraryEditDraftData = LocalLibraryEditDraftData()

    override suspend fun readFrom(source: BufferedSource): LocalLibraryEditDraftData =
        try {
            withContext(Dispatchers.IO) {
                Json.decodeFromString<LocalLibraryEditDraftData>(
                    source.readByteArray().decodeToString()
                )
            }
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read LocalLibraryEditDraftData", serialization)
        }

    override suspend fun writeTo(t: LocalLibraryEditDraftData, sink: BufferedSink) {
        sink.write(Json.encodeToString(t).encodeToByteArray())
    }
}

class LocalCommandLabDataStore(
    private val store: DataStore<LocalLibraryData>,
    private val draftStore: DataStore<LocalLibraryEditDraftData>,
) {

    fun localLibraryFunctions(): Flow<List<LibraryFunction>> = store.data.map { it.functions }

    fun localLibraryFunction(id: Int?): Flow<LibraryFunction?> =
        store.data.map {
            if (id != null && id in it.functions.indices) {
                it.functions[id]
            } else null
        }

    fun localLibraryFunction(localEntryId: String?): Flow<LibraryFunction?> =
        store.data.map { data ->
            localEntryId?.let { target -> data.functions.firstOrNull { it.localEntryId == target } }
        }

    fun localLibraryEditDraft(key: String): Flow<LocalLibraryEditDraft?> =
        draftStore.data.map { it.drafts[key] }

    suspend fun saveLocalLibraryEditDraft(key: String, draft: LocalLibraryEditDraft) {
        draftStore.updateData { it.copy(drafts = it.drafts + (key to draft)) }
    }

    suspend fun clearLocalLibraryEditDraft(key: String) {
        draftStore.updateData { it.copy(drafts = it.drafts - key) }
    }

    suspend fun addLocalLibraryFunction(function: LibraryFunction): String {
        var assignedId = ""
        store.updateData { data ->
            val usedIds = data.functions.mapNotNull { it.localEntryId }.toMutableSet()
            assignedId = uniqueLocalEntryId(function.localEntryId, usedIds)
            data.copy(functions = data.functions + function.copy(localEntryId = assignedId))
        }
        return assignedId
    }

    suspend fun addLocalLibraryFunctions(functions: List<LibraryFunction>): List<String> {
        val assignedIds = mutableListOf<String>()
        store.updateData { data ->
            val usedIds = data.functions.mapNotNull { it.localEntryId }.toMutableSet()
            val normalized =
                functions.map { function ->
                    val assignedId = uniqueLocalEntryId(function.localEntryId, usedIds)
                    assignedIds += assignedId
                    function.copy(localEntryId = assignedId)
                }
            data.copy(functions = data.functions + normalized)
        }
        return assignedIds
    }

    suspend fun updateLocalLibraryFunction(id: Int, function: LibraryFunction) {
        store.updateData {
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
        store.updateData { data ->
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

    suspend fun updateLocalLibraryDocument(
        localEntryId: String,
        document: LibraryFunction,
    ): Boolean {
        var updated = false
        store.updateData { data ->
            data.copy(
                functions =
                    data.functions.map { current ->
                        if (current.localEntryId == localEntryId) {
                            updated = true
                            current.withDocumentEdits(document)
                        } else current
                    }
            )
        }
        return updated
    }

    suspend fun markLocalLibrarySynced(
        localEntryId: String,
        uuid: String? = null,
        syncedLibrary: LibraryFunction? = null,
    ): Boolean {
        var updated = false
        store.updateData { data ->
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
        store.updateData { data ->
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
        store.updateData { data ->
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
        store.updateData {
            val newFunctions = it.functions.toMutableList()
            if (id in newFunctions.indices) {
                newFunctions.removeAt(id)
            }
            it.copy(functions = newFunctions)
        }
    }

    suspend fun removeLocalLibraryFunction(localEntryId: String): Boolean {
        var removed = false
        store.updateData { data ->
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
        store.updateData { data ->
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
                ?: generateSequence { kotlin.uuid.Uuid.random().toString() }
                    .first { it !in usedIds }
        usedIds += assigned
        return assigned
    }
}

fun LibraryFunction.withLocalSyncResult(
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
                        ?: generateSequence { kotlin.uuid.Uuid.random().toString() }
                            .first { it !in usedIds }
                usedIds += assigned
                function.copy(localEntryId = assigned)
            }
        return currentData.copy(functions = migrated)
    }

    override suspend fun cleanUp() = Unit
}
