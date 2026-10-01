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

/*
 * 原始 JSON 文本（titleraw）编辑器的偏好与草稿持久化。
 * 草稿直接存编辑器自己的元素树 JSON（draft 字段），调试模拟值存 debug 字段，
 * 与最终 rawtext 输出无关。自动保存默认开启，不再弹首次询问。
 */
private val Context.rawtextDataStore: DataStore<RawtextPreferences> by dataStore(
    fileName = "rawtext_studio.json",
    serializer = RawtextPreferencesSerializer
)

@Serializable
data class RawtextPreferences(
    val autoSaveEnabled: Boolean = true,
    val draft: String? = null,               // 元素树序列化（List<RawtextElement>）
    val debug: String? = null,               // 调试状态序列化（RawtextDebugSnapshot）
)

object RawtextPreferencesSerializer : Serializer<RawtextPreferences> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: RawtextPreferences = RawtextPreferences()

    override suspend fun readFrom(input: InputStream): RawtextPreferences =
        try {
            withContext(Dispatchers.IO) {
                json.decodeFromString<RawtextPreferences>(input.readBytes().decodeToString())
            }
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read RawtextPreferences", serialization)
        }

    override suspend fun writeTo(t: RawtextPreferences, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(json.encodeToString(t).encodeToByteArray())
        }
    }
}

class RawtextDataStore(private val context: Context) {

    fun preferences(): Flow<RawtextPreferences> = context.rawtextDataStore.data

    fun autoSaveEnabled(): Flow<Boolean> = context.rawtextDataStore.data.map { it.autoSaveEnabled }

    suspend fun setAutoSave(enabled: Boolean) {
        context.rawtextDataStore.updateData { it.copy(autoSaveEnabled = enabled) }
    }

    suspend fun saveDraft(draft: String?) {
        context.rawtextDataStore.updateData { it.copy(draft = draft) }
    }

    suspend fun saveDebug(debug: String) {
        context.rawtextDataStore.updateData { it.copy(debug = debug) }
    }
}
