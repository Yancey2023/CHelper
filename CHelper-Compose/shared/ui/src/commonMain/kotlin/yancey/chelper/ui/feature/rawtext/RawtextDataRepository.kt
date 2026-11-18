/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
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

package yancey.chelper.ui.feature.rawtext

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import yancey.chelper.ui.platform.ClientServices

/*
 * 物品 / 槽位 / 族类型查询库。
 * items.json、slots.json 来自网页版 static/data，打包在 assets/rawtext 下。
 * 族类型条目较少，直接内联，省去再拉一个文件。
 */
object RawtextDataRepository {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var items: List<NamedEntry>? = null

    @Volatile private var slots: List<NamedEntry>? = null

    suspend fun loadItems(context: ClientServices): List<NamedEntry> {
        items?.let {
            return it
        }
        return withContext(Dispatchers.IO) {
            val loaded = readMap(context, "rawtext/items.json")
            items = loaded
            loaded
        }
    }

    suspend fun loadSlots(context: ClientServices): List<NamedEntry> {
        slots?.let {
            return it
        }
        return withContext(Dispatchers.IO) {
            val loaded = readMap(context, "rawtext/slots.json")
            slots = loaded
            loaded
        }
    }

    private suspend fun readMap(context: ClientServices, path: String): List<NamedEntry> =
        try {
            val text = context.assets.readText(path)
            json.parseToJsonElement(text).jsonObject.entries.map { (id, value) ->
                NamedEntry(id, value.jsonPrimitive.contentOrNull ?: id)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }

    val familyTypes
        get() = RawtextLookup.familyTypes
}
