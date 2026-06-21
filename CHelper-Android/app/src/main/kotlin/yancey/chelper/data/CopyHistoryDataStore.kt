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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private const val MAX_SIZE = 1000

private val Context.copyHistoryDataStore: DataStore<CopyHistoryData> by dataStore(
    fileName = "copy_history.json",
    serializer = CopyHistorySerializer,
    produceMigrations = {
        listOf(CopyHistoryDataMigrationToV75(it))
    }
)

@Serializable
data class CopyHistoryData(
    val history: List<String> = emptyList()
)

object CopyHistorySerializer : Serializer<CopyHistoryData> {

    override val defaultValue: CopyHistoryData = CopyHistoryData()

    override suspend fun readFrom(input: InputStream): CopyHistoryData =
        try {
            withContext(Dispatchers.IO) {
                Json.decodeFromString<CopyHistoryData>(
                    input.readBytes().decodeToString()
                )
            }
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read CopyHistoryData", serialization)
        }

    override suspend fun writeTo(t: CopyHistoryData, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(
                Json.encodeToString(t)
                    .encodeToByteArray()
            )
        }
    }
}

class CopyHistoryDataStore(private val context: Context) {

    fun history(): Flow<List<String>> =
        context.copyHistoryDataStore.data.map { it.history }

    suspend fun add(content: String) {
        if (content.isEmpty()) {
            return
        }
        context.copyHistoryDataStore.updateData {
            val newHistory = it.history.toMutableList()
            newHistory.remove(content)
            if (newHistory.size >= MAX_SIZE) {
                newHistory.removeAt(newHistory.lastIndex)
            }
            newHistory.add(0, content)
            it.copy(history = newHistory)
        }
    }
}

/**
 * 0.4.1 版本之后，复制历史记录从自己写的框架改为使用官方方案 DataStore，该文件用于数据迁移
 */
class CopyHistoryDataMigrationToV75(private val context: Context) : DataMigration<CopyHistoryData> {
    override suspend fun shouldMigrate(currentData: CopyHistoryData): Boolean {
        return context.dataDir.resolve("history.txt").exists()
    }

    override suspend fun migrate(currentData: CopyHistoryData): CopyHistoryData {
        return try {
            val oldFile = context.dataDir.resolve("history.txt")
            val content = oldFile.readBytes().decodeToString()
            val oldHistoryList = content.split("\n").filter { it.isNotEmpty() }
            CopyHistoryData(history = oldHistoryList.take(MAX_SIZE))
        } catch (_: Throwable) {
            currentData
        }
    }

    override suspend fun cleanUp() {
        val oldFile = context.dataDir.resolve("history.txt")
        if (oldFile.exists()) {
            oldFile.delete()
        }
    }
}
