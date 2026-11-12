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

package yancey.chelper.domain.rawtext

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class RawtextPreferences(
    val copyFormat: String? = null, // "formatted" / "compressed"，null 表示还没问过
    val autoSavePrompted: Boolean = false, // 是否已经弹过"开启动态保存"询问
    val autoSaveEnabled: Boolean = false,
    val draft: String? = null, // 段结构序列化
    val mockSettings: String? = null, // 模拟器 mock 值序列化
)

interface RawtextRepository {
    val edits: RawtextEditTracker

    fun preferences(): Flow<RawtextPreferences>

    suspend fun savePreferences(preferences: RawtextPreferences, token: RawtextEditToken)
}
