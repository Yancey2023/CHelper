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
import yancey.chelper.network.library.data.LibraryFunction
import java.io.InputStream
import java.io.OutputStream

private val Context.localLibraryDataStore: DataStore<LocalLibraryData> by dataStore(
    fileName = "local_library.json",
    serializer = LocalLibrarySerializer,
    produceMigrations = {
        listOf(MigrationToV75(it))
    }
)

@Serializable
data class LocalLibraryData(
    val functions: List<LibraryFunction> = emptyList()
)

object LocalLibrarySerializer : Serializer<LocalLibraryData> {

    override val defaultValue: LocalLibraryData = LocalLibraryData()

    override suspend fun readFrom(input: InputStream): LocalLibraryData =
        try {
            withContext(Dispatchers.IO) {
                Json.decodeFromString<LocalLibraryData>(
                    input.readBytes().decodeToString()
                )
            }
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read LocalLibraryData", serialization)
        }

    override suspend fun writeTo(t: LocalLibraryData, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(
                Json.encodeToString(t)
                    .encodeToByteArray()
            )
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

    suspend fun addLocalLibraryFunction(function: LibraryFunction) {
        context.localLibraryDataStore.updateData { it.copy(functions = it.functions + function) }
    }

    suspend fun addLocalLibraryFunctions(functions: List<LibraryFunction>) {
        context.localLibraryDataStore.updateData { it.copy(functions = it.functions + functions) }
    }

    suspend fun updateLocalLibraryFunction(id: Int, function: LibraryFunction) {
        context.localLibraryDataStore.updateData {
            val newFunctions = it.functions.toMutableList()
            if (id in newFunctions.indices) {
                newFunctions[id] = function
            }
            it.copy(functions = newFunctions)
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
}

/**
 * 0.4.1 版本之后，私有命令库存储从自己写的框架改为使用官方方案 DataScore，该文件用于数据迁移
 */
class MigrationToV75(private val context: Context) : DataMigration<LocalLibraryData> {
    override suspend fun shouldMigrate(currentData: LocalLibraryData): Boolean {
        return context.dataDir.resolve("localLibrary").resolve("data.json").exists()
    }

    override suspend fun migrate(currentData: LocalLibraryData): LocalLibraryData {
        return try {
            val oldFile = context.dataDir.resolve("localLibrary").resolve("data.json")
            val oldFunctions = Json.decodeFromString<List<LibraryFunction>>(
                oldFile.readBytes().decodeToString()
            )
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
