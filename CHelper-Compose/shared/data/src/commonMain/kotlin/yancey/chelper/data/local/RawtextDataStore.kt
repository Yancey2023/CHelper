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

package yancey.chelper.data.local

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource
import yancey.chelper.domain.rawtext.RawtextEditToken
import yancey.chelper.domain.rawtext.RawtextEditTracker
import yancey.chelper.domain.rawtext.RawtextPreferences
import yancey.chelper.domain.rawtext.RawtextRepository

/*
 * RawJSON 生成器的偏好与草稿持久化。
 * 对应网页版的 localStorage：复制格式偏好、自动保存开关、编辑草稿、模拟器 mock 值。
 * 草稿直接存生成器自己的段结构 JSON（draft 字段），跟最终 rawtext 输出无关。
 */

object RawtextPreferencesSerializer : OkioSerializer<RawtextPreferences> {
    override val defaultValue: RawtextPreferences = RawtextPreferences()

    override suspend fun readFrom(source: BufferedSource): RawtextPreferences =
        try {
            Json.decodeFromString<RawtextPreferences>(source.readByteArray().decodeToString())
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read RawtextPreferences", serialization)
        }

    override suspend fun writeTo(t: RawtextPreferences, sink: BufferedSink) {
        sink.write(Json.encodeToString(t).encodeToByteArray())
    }
}

// Every wrapper points at the same DataStore and shares the ordering of user edits.
private val rawtextEdits = RawtextEditTracker()

class RawtextDataStore(private val store: DataStore<RawtextPreferences>) : RawtextRepository {
    override val edits
        get() = rawtextEdits

    override fun preferences(): Flow<RawtextPreferences> = store.data

    override suspend fun savePreferences(preferences: RawtextPreferences, token: RawtextEditToken) {
        store.updateData { current -> edits.merge(current, preferences, token) }
    }
}
