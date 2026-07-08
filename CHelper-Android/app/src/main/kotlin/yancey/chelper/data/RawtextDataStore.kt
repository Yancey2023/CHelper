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
 * RawJSON 生成器的偏好与草稿持久化。
 * 对应网页版的 localStorage：复制格式偏好、自动保存开关、编辑草稿、模拟器 mock 值。
 * 草稿直接存生成器自己的段结构 JSON（draft 字段），跟最终 rawtext 输出无关。
 */
private val Context.rawtextDataStore: DataStore<RawtextPreferences> by dataStore(
    fileName = "rawtext_studio.json",
    serializer = RawtextPreferencesSerializer
)

@Serializable
data class RawtextPreferences(
    val copyFormat: String? = null,         // "formatted" / "compressed"，null 表示还没问过
    val autoSavePrompted: Boolean = false,   // 是否已经弹过"开启动态保存"询问
    val autoSaveEnabled: Boolean = false,
    val draft: String? = null,               // 段结构序列化
    val mockSettings: String? = null,        // 模拟器 mock 值序列化
)

object RawtextPreferencesSerializer : Serializer<RawtextPreferences> {
    override val defaultValue: RawtextPreferences = RawtextPreferences()

    override suspend fun readFrom(input: InputStream): RawtextPreferences =
        try {
            withContext(Dispatchers.IO) {
                Json.decodeFromString<RawtextPreferences>(input.readBytes().decodeToString())
            }
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read RawtextPreferences", serialization)
        }

    override suspend fun writeTo(t: RawtextPreferences, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(Json.encodeToString(t).encodeToByteArray())
        }
    }
}

class RawtextDataStore(private val context: Context) {

    fun preferences(): Flow<RawtextPreferences> = context.rawtextDataStore.data

    fun copyFormat(): Flow<String?> = context.rawtextDataStore.data.map { it.copyFormat }

    suspend fun setCopyFormat(format: String) {
        context.rawtextDataStore.updateData { it.copy(copyFormat = format) }
    }

    suspend fun setAutoSave(prompted: Boolean, enabled: Boolean) {
        context.rawtextDataStore.updateData {
            it.copy(autoSavePrompted = prompted, autoSaveEnabled = enabled)
        }
    }

    suspend fun saveDraft(draft: String?) {
        context.rawtextDataStore.updateData { it.copy(draft = draft) }
    }

    suspend fun saveMockSettings(mock: String) {
        context.rawtextDataStore.updateData { it.copy(mockSettings = mock) }
    }
}
