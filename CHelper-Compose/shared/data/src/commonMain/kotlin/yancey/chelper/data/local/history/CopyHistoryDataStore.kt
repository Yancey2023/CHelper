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

package yancey.chelper.data.local.history

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

const val COPY_HISTORY_MAX_SIZE = 1000

@Serializable data class CopyHistoryData(val history: List<String> = emptyList())

object CopyHistorySerializer : OkioSerializer<CopyHistoryData> {

    override val defaultValue: CopyHistoryData = CopyHistoryData()

    override suspend fun readFrom(source: BufferedSource): CopyHistoryData =
        try {
            Json.decodeFromString<CopyHistoryData>(source.readByteArray().decodeToString())
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read CopyHistoryData", serialization)
        }

    override suspend fun writeTo(t: CopyHistoryData, sink: BufferedSink) {
        sink.write(Json.encodeToString(t).encodeToByteArray())
    }
}

class CopyHistoryDataStore(private val store: DataStore<CopyHistoryData>) :
    yancey.chelper.domain.history.CopyHistoryRepository {
    override fun observe() = history()

    override suspend fun record(text: String) = add(text)

    fun history(): Flow<List<String>> = store.data.map { it.history }

    suspend fun add(content: String) {
        if (content.isEmpty()) {
            return
        }
        store.updateData {
            val newHistory = it.history.toMutableList()
            newHistory.remove(content)
            if (newHistory.size >= COPY_HISTORY_MAX_SIZE) {
                newHistory.removeAt(newHistory.lastIndex)
            }
            newHistory.add(0, content)
            it.copy(history = newHistory)
        }
    }
}
